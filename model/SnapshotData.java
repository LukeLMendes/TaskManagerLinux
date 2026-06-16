package model;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 * Representa o conjunto de dados do sistema capturado em um único instante (snapshot).
 *
 * No diagrama UML, SnapshotData possui composição com SystemSnapshot (0..*) e
 * agrega Processo (0..*). Cada instância guarda a lista de tasks e kthreads
 * registrados no momento da captura, junto com as informações gerais do sistema
 * (via SystemSnapshot) e as estatísticas de I/O dos processos.
 *
 * Implementa Serializable para que objetos desta classe possam ser convertidos
 * em bytes e salvos em arquivo pelo FileSnapshotRepository.
 */
public class SnapshotData implements Serializable {

    // Número de versão usado pelo mecanismo de serialização Java.
    // Se a estrutura da classe mudar, este número deve ser incrementado
    // para evitar erros ao tentar ler arquivos salvos com a versão anterior.
    private static final long serialVersionUID = 1L;

    // Momento em que este snapshot foi criado, em milissegundos desde epoch Unix.
    // Usado para nomear o arquivo e para ordenar snapshots do mais antigo ao mais recente.
    private long timestamp;

    // Informações gerais do sistema no momento do snapshot:
    // memória total/disponível, uptime, uso de CPU, contagem de tasks/kthreads/threads.
    // Corresponde à classe SystemSnapshot já existente no repositório.
    private SystemSnapshot systemInfos;

    // Lista de processos de usuário (Tasks) registrados no momento do snapshot.
    // Cada entrada é uma instância de Task (subclasse de Processo).
    private List<Processo> tasks;

    // Lista de threads de kernel (Kthr) registradas no momento do snapshot.
    // Cada entrada é uma instância de Kthr (subclasse de Processo).
    private List<Processo> kthreads;

    // Estatísticas de I/O (bytes lidos/escritos) de cada processo de usuário.
    // A posição i desta lista corresponde ao processo na posição i de tasks.
    private List<ProcessIOStat> taskIOStats;

    /**
     * Construtor padrão: registra o timestamp atual e inicializa as listas vazias.
     * Use os setters para preencher os dados antes de salvar.
     */
    public SnapshotData() {
        // Captura o momento exato da criação do snapshot em milissegundos.
        this.timestamp   = System.currentTimeMillis();
        // Inicializa as listas para evitar NullPointerException ao adicionar elementos.
        this.tasks       = new ArrayList<>();
        this.kthreads    = new ArrayList<>();
        this.taskIOStats = new ArrayList<>();
    }

    /**
     * Construtor de conveniência: cria o snapshot já preenchido com os dados principais.
     *
     * @param systemInfos informações gerais do sistema (CPU, memória, uptime, contagens)
     * @param tasks       lista de processos de usuário coletados via /proc
     * @param kthreads    lista de kernel threads coletadas via /proc
     */
    public SnapshotData(SystemSnapshot systemInfos,
                        List<Processo> tasks,
                        List<Processo> kthreads) {
        // Chama o construtor padrão para inicializar timestamp e listas.
        this();
        this.systemInfos = systemInfos;
        this.tasks       = tasks;
        this.kthreads    = kthreads;
    }

    // -------------------------------------------------------------------------
    // Setters — permitem preencher cada campo separadamente após a criação.
    // -------------------------------------------------------------------------

    /** Define manualmente o timestamp (útil ao restaurar um snapshot do disco). */
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    /** Define as informações gerais do sistema (CPU, memória, uptime...). */
    public void setSystemSnapshot(SystemSnapshot systemInfos) { this.systemInfos = systemInfos; }

    /** Substitui a lista de processos de usuário deste snapshot. */
    public void setTasks(List<Processo> tasks) { this.tasks = tasks; }

    /** Substitui a lista de kernel threads deste snapshot. */
    public void setKthreads(List<Processo> kthreads) { this.kthreads = kthreads; }

    /** Define as estatísticas de I/O dos processos de usuário. */
    public void setTaskIOStats(List<ProcessIOStat> stats) { this.taskIOStats = stats; }

    // -------------------------------------------------------------------------
    // Getters — retornam os dados armazenados.
    // -------------------------------------------------------------------------

    /** Retorna o instante em que este snapshot foi criado (ms desde epoch). */
    public long getTimestamp() { return timestamp; }

    /** Retorna as informações gerais do sistema no momento do snapshot. */
    public SystemSnapshot getSystemSnapshot() { return systemInfos; }

    /** Retorna a lista de processos de usuário capturados. */
    public List<Processo> getTasks() { return tasks; }

    /** Retorna a lista de kernel threads capturadas. */
    public List<Processo> getKthreads() { return kthreads; }

    /** Retorna as estatísticas de I/O dos processos de usuário. */
    public List<ProcessIOStat> getTaskIOStats() { return taskIOStats; }

    // -------------------------------------------------------------------------
    // Métodos utilitários
    // -------------------------------------------------------------------------

    /**
     * Retorna o total de entradas neste snapshot (tasks + kthreads).
     * Útil para exibir um contador rápido na interface sem iterar as listas.
     */
    public int totalProcesses() {
        return tasks.size() + kthreads.size();
    }

    /**
     * Representação textual resumida do snapshot.
     * Exibe o timestamp e o tamanho de cada lista, sem despejar todo o conteúdo.
     */
    @Override
    public String toString() {
        return "SnapshotData{" +
                "timestamp=" + timestamp +
                ", tasks=" + tasks.size() +
                ", kthreads=" + kthreads.size() +
                '}';
    }
}
