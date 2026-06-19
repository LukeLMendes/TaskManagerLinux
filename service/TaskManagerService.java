package service;

import model.Processo;
import model.SnapshotData;
import model.SystemSnapshot;
import model.ProcessIOStat;
import model.Task;
import model.Kthr;

import persistence.SystemRepository;
import persistence.ProcessRepository;
import persistence.IORepository;
import persistence.SnapshotRepository;
import persistence.ProcessKiller;

import exception.SnapshotReadException;
import exception.SnapshotWriteException;
import exception.KillProcessException;
import exception.InvalidPidException;

import java.util.List;

/**
 * Camada de serviço central do TaskManager.
 *
 * No diagrama UML, TaskManagerService é o único ponto de entrada para
 * toda a lógica de negócio: a view só fala com esta classe, e esta
 * classe só fala com as interfaces de persistence.
 *
 * Recebe todas as dependências via injeção de dependência no construtor
 * (princípio DIP — Dependency Inversion Principle): depende de interfaces,
 * nunca das classes concretas como ProcfsProcessRepository ou LinuxProcessKiller.
 * Isso permite trocar implementações sem alterar este código.
 *
 * Responsabilidades:
 *   1. Coletar dados do sistema (processos, kthreads, CPU, memória, IO).
 *   2. Expor os dados coletados para a view via getters.
 *   3. Salvar e carregar snapshots do estado do sistema.
 *   4. Encerrar processos a pedido da view.
 *   5. Montar a árvore hierárquica de processos.
 */
public class TaskManagerService {

    // -------------------------------------------------------------------------
    // Dependências injetadas (interfaces de persistence)
    // -------------------------------------------------------------------------

    /**
     * Fornece informações gerais do sistema:
     * uptime, contagem de tasks/threads/kthreads, dados de CPU.
     * Implementação concreta atual: ProcfsSystemRepository.
     */
    private final SystemRepository systemRepository;

    /**
     * Lê a lista de processos de usuário (Tasks) e kernel threads (Kthrs) do /proc.
     * Implementação concreta atual: ProcfsProcessRepository.
     */
    private final ProcessRepository processRepository;

    /**
     * Lê as estatísticas de I/O (bytes lidos/escritos) de cada processo.
     * Implementação concreta atual: ProcfsIORepository.
     */
    private final IORepository ioRepository;

    /**
     * Persiste e recupera snapshots do estado do sistema em disco.
     * Implementação concreta atual: FileSnapshotRepository.
     */
    private final SnapshotRepository snapshotRepository;

    /**
     * Envia sinais de encerramento (SIGTERM/SIGKILL) a processos do SO.
     * Implementação concreta atual: LinuxProcessKiller.
     */
    private final ProcessKiller processKiller;

    // -------------------------------------------------------------------------
    // Estado interno — dados coletados na última chamada a refresh()
    // -------------------------------------------------------------------------

    /**
     * Lista de processos de usuário coletados no último refresh().
     * Cada elemento é uma instância de Task (subclasse de Processo).
     */
    private List<Processo> tasks;

    /**
     * Lista de kernel threads coletadas no último refresh().
     * Cada elemento é uma instância de Kthr (subclasse de Processo).
     */
    private List<Processo> kthreads;

    /**
     * Estatísticas de I/O dos processos de usuário do último refresh().
     * A posição i corresponde ao processo na posição i de tasks.
     */
    private List<ProcessIOStat> taskIOStats;

    /**
     * Informações gerais do sistema (CPU, memória, uptime, contagens)
     * coletadas no último refresh(). Pode ser null antes do primeiro refresh().
     */
    private SystemSnapshot systemSnapshot;

    // -------------------------------------------------------------------------
    // Construtor
    // -------------------------------------------------------------------------

    /**
     * Cria o serviço recebendo todas as dependências por injeção.
     *
     * Nenhuma dependência pode ser null; se alguma for, um NullPointerException
     * será lançado cedo e o erro ficará evidente.
     *
     * @param systemRepository   repositório de informações gerais do sistema
     * @param processRepository  repositório de processos e kthreads
     * @param ioRepository       repositório de estatísticas de I/O
     * @param snapshotRepository repositório de snapshots em disco
     * @param processKiller      executor de sinais de encerramento de processos
     */
    public TaskManagerService(SystemRepository systemRepository,
                               ProcessRepository processRepository,
                               IORepository ioRepository,
                               SnapshotRepository snapshotRepository,
                               ProcessKiller processKiller) {
        this.systemRepository   = systemRepository;
        this.processRepository  = processRepository;
        this.ioRepository       = ioRepository;
        this.snapshotRepository = snapshotRepository;
        this.processKiller      = processKiller;
    }

    // -------------------------------------------------------------------------
    // Coleta de dados
    // -------------------------------------------------------------------------

    /**
     * Coleta todos os dados do sistema e atualiza o estado interno do serviço.
     *
     * Deve ser chamado periodicamente pela view para manter as informações atualizadas.
     * Após este método retornar, os getters (getTasks, getKthreads, etc.) refletem
     * o estado mais recente do sistema.
     *
     * Ordem de coleta:
     *   1. Processos e kthreads (via ProcessRepository).
     *   2. Estatísticas de I/O das tasks (via IORepository).
     *   3. Informações gerais do sistema (via SystemRepository).
     */
    public void refresh() {
        // 1. Coleta a lista de processos de usuário e kernel threads.
        tasks    = processRepository.readTask();
        kthreads = processRepository.readKthr();

        // 2. Coleta IO apenas de tasks (kthreads geralmente não têm /proc/<pid>/io acessível).
        //    O cast é seguro porque readTask() retorna instâncias de Task.
        taskIOStats = ioRepository.readTaskIO(tasks.stream()
                .map(p -> (Task) p)
                .collect(java.util.stream.Collectors.toList()));

        // 3. Monta o SystemSnapshot com os dados gerais.
        systemSnapshot = new SystemSnapshot();
        systemSnapshot.setUptime(systemRepository.uptime());
        systemSnapshot.setCpuInfos(systemRepository.cpu_infos());
        systemSnapshot.setNumTasks(systemRepository.countTasks(tasks));
        systemSnapshot.setNumKthreads(systemRepository.countKthr(kthreads));
        systemSnapshot.setNumThreads(systemRepository.countThreads(tasks, kthreads));

        // 4. Lê /proc/meminfo uma vez e preenche memória e swap no snapshot.
        java.util.Map<String, Long> mem = systemRepository.memInfo();

        long memTotal      = mem.getOrDefault("MemTotal",      0L);
        long memFree       = mem.getOrDefault("MemFree",       0L);
        long buffers       = mem.getOrDefault("Buffers",       0L);
        long cached        = mem.getOrDefault("Cached",        0L);
        // SReclaimable: parte do slab de kernel que pode ser recuperada pelo SO.
        // O htop subtrai esse valor para não inflar o "usado" com cache reclaimável.
        long sreclaimable  = mem.getOrDefault("SReclaimable",  0L);

        // Fórmula idêntica ao htop:
        //   used = MemTotal - MemFree - Buffers - Cached - SReclaimable
        long memUsed = memTotal - memFree - buffers - cached - sreclaimable;

        systemSnapshot.setMemTotal(memTotal);
        systemSnapshot.setMemAvailable(mem.getOrDefault("MemAvailable", 0L));
        systemSnapshot.setMemUsed(memUsed);
        systemSnapshot.setSwapTotal(mem.getOrDefault("SwapTotal", 0L));
        systemSnapshot.setSwapFree(mem.getOrDefault("SwapFree",  0L));

        // 5. Load average de /proc/loadavg.
        systemSnapshot.setLoadAvg(systemRepository.loadAvg());
    }

    // -------------------------------------------------------------------------
    // Snapshot — salvar e restaurar estado
    // -------------------------------------------------------------------------

    /**
     * Cria um SnapshotData com o estado atual e persiste em disco.
     *
     * O snapshot captura: listas de tasks e kthreads, SystemSnapshot e IOStats.
     * O FileSnapshotRepository grava o objeto serializado como arquivo .dat.
     *
     * Deve ser chamado após refresh() para garantir que os dados estejam atualizados.
     *
     * @throws SnapshotWriteException se falhar ao gravar o arquivo
     *         (sem espaço em disco, permissão negada, etc.)
     */
    public void saveSnapshot() throws SnapshotWriteException {
        // Monta o objeto SnapshotData com o estado coletado no último refresh().
        SnapshotData data = new SnapshotData(systemSnapshot, tasks, kthreads);
        data.setTaskIOStats(taskIOStats);

        // Delega a gravação para a implementação de SnapshotRepository.
        snapshotRepository.save(data);
    }

    /**
     * Carrega e retorna o snapshot mais recente gravado em disco.
     *
     * @return o SnapshotData com o maior timestamp disponível
     * @throws SnapshotReadException se nenhum snapshot existir ou falhar a leitura
     */
    public SnapshotData loadLatestSnapshot() throws SnapshotReadException {
        return snapshotRepository.loadLatest();
    }

    /**
     * Carrega e retorna todos os snapshots gravados, do mais antigo ao mais recente.
     *
     * @return lista de SnapshotData; vazia se nenhum snapshot foi salvo ainda
     * @throws SnapshotReadException se falhar ao listar ou ler os arquivos
     */
    public List<SnapshotData> loadAllSnapshots() throws SnapshotReadException {
        return snapshotRepository.loadAll();
    }

    // -------------------------------------------------------------------------
    // Encerramento de processos
    // -------------------------------------------------------------------------

    /**
     * Envia SIGTERM ao processo identificado por pid (encerramento gracioso).
     *
     * Delega para processKiller.killProcess(), que usa LinuxProcessKiller
     * para executar "kill -15 <pid>" no sistema operacional.
     *
     * @param pid PID do processo a ser encerrado (deve ser > 0)
     * @throws InvalidPidException  se pid <= 0
     * @throws KillProcessException se o sinal não puder ser enviado
     */
    public void killProcess(int pid) throws KillProcessException, InvalidPidException {
        processKiller.killProcess(pid);
    }

    // -------------------------------------------------------------------------
    // Árvore de processos
    // -------------------------------------------------------------------------

    /**
     * Constrói e retorna a árvore hierárquica de processos de usuário.
     *
     * Usa ProcsTree.toTree() para transformar a lista plana de tasks em
     * uma estrutura TreeNode<Processo> onde cada nó contém seus filhos.
     *
     * A raiz da árvore é o primeiro processo da lista (normalmente init/systemd, PID 1),
     * mas depende da ordem retornada por processRepository.readTask().
     *
     * @return nó raiz da árvore; null se tasks estiver vazia
     */
    public TreeNode<Processo> getProcessTree() {
        // Não é possível montar árvore sem processos.
        if (tasks == null || tasks.isEmpty()) return null;

        // ProcsTree.toTree() monta a hierarquia recursivamente usando os PIDs filhos
        // registrados em cada Processo via addChild().
        return ProcsTree.toTree(tasks);
    }

    // -------------------------------------------------------------------------
    // Getters — expõem o estado coletado no último refresh() para a view
    // -------------------------------------------------------------------------

    /**
     * Retorna a lista de processos de usuário do último refresh().
     * Cada elemento é uma instância de Task.
     *
     * @return lista de tasks; null se refresh() nunca foi chamado
     */
    public List<Processo> getTasks() {
        return tasks;
    }

    /**
     * Retorna a lista de kernel threads do último refresh().
     * Cada elemento é uma instância de Kthr.
     *
     * @return lista de kthreads; null se refresh() nunca foi chamado
     */
    public List<Processo> getKthreads() {
        return kthreads;
    }

    /**
     * Retorna as estatísticas de I/O das tasks do último refresh().
     * A posição i corresponde ao processo na posição i de getTasks().
     *
     * @return lista de ProcessIOStat; null se refresh() nunca foi chamado
     */
    public List<ProcessIOStat> getTaskIOStats() {
        return taskIOStats;
    }

    /**
     * Retorna o SystemSnapshot com as informações gerais do sistema
     * coletadas no último refresh().
     *
     * @return SystemSnapshot; null se refresh() nunca foi chamado
     */
    public SystemSnapshot getSystemSnapshot() {
        return systemSnapshot;
    }
}
