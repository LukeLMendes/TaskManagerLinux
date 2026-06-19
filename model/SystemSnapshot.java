package model;

import java.io.Serializable;
import java.util.List;

/**
 * Contém as informações gerais do sistema capturadas em um único instante.
 *
 * No diagrama UML, SystemSnapshot tem composição com SnapshotData:
 * ou seja, SystemSnapshot só existe como parte de um SnapshotData —
 * não faz sentido ter um SystemSnapshot "solto" fora de um snapshot.
 *
 * Implementa Serializable para poder ser gravado em arquivo junto
 * com o SnapshotData pelo FileSnapshotRepository.
 *
 * Campos principais:
 *   - memória total, disponível e em uso (lidos de /proc/meminfo)
 *   - informações de CPU por núcleo (lidas de /proc/stat)
 *   - uptime do sistema (lido de /proc/uptime)
 *   - load average (lido de /proc/loadavg)
 *   - contagem de tasks, threads e kthreads no momento do snapshot
 */
public class SystemSnapshot implements Serializable {

    // Número de versão da serialização.
    // Se você adicionar, remover ou renomear campos nesta classe, incremente este valor.
    // Sem isso, tentar ler um arquivo .dat gerado por uma versão anterior causaria
    // uma InvalidClassException ao desserializar.
    // Histórico: 1→2 (cpu_infos), 2→3 (mem_used), 3→4 (loadAvg).
    private static final long serialVersionUID = 4L;

    // -------------------------------------------------------------------------
    // Memória RAM
    // -------------------------------------------------------------------------

    // Memória total do sistema em kB (campo "MemTotal" de /proc/meminfo).
    // É a RAM física instalada menos o que o kernel reserva para si mesmo no boot.
    private double mem_total;

    // Memória disponível para novos processos em kB (campo "MemAvailable" de /proc/meminfo).
    // É uma estimativa do kernel de quanto pode ser alocado sem swap.
    // Diferente de MemFree: inclui cache reclaimável, que o kernel libera se necessário.
    private double mem_available;

    // Memória efetivamente em uso, calculada com a mesma fórmula do htop:
    //   mem_used = MemTotal - MemFree - Buffers - Cached - SReclaimable
    //
    // Por que subtrair Buffers, Cached e SReclaimable?
    //   - Buffers:       cache de metadados de filesystem (inodes, dentries)
    //   - Cached:        cache de conteúdo de arquivos lidos do disco
    //   - SReclaimable:  parte do slab do kernel que pode ser liberada sob pressão
    // O kernel pode recuperar essas memórias a qualquer momento para outros processos,
    // então elas não estão "presas" — não devem ser contadas como "usadas".
    private double mem_used;

    // -------------------------------------------------------------------------
    // Swap
    // -------------------------------------------------------------------------

    // Espaço de swap total em kB (campo "SwapTotal" de /proc/meminfo).
    // Swap é área de disco usada como extensão da RAM quando ela está cheia.
    private double swap_total;

    // Swap livre (não usado) em kB (campo "SwapFree" de /proc/meminfo).
    // swap_usado = swap_total - swap_free.
    private double swap_free;

    // -------------------------------------------------------------------------
    // CPU
    // -------------------------------------------------------------------------

    // Lista de arrays de long, um por núcleo, lidos de /proc/stat.
    // Cada array representa os contadores acumulados de tempo de CPU desde o boot:
    //   índice 0: user    — ticks em processos de usuário normais
    //   índice 1: nice    — ticks em processos de usuário com nice > 0
    //   índice 2: system  — ticks em código do kernel (system calls, IRQs)
    //   índice 3: idle    — ticks em que o núcleo ficou ocioso
    //   índice 4: iowait  — ticks aguardando I/O (disco, rede)
    //   índice 5: irq     — ticks tratando interrupções de hardware
    //   índice 6: softirq — ticks tratando interrupções de software
    // Os contadores aumentam continuamente; para obter o uso ATUAL, é necessário
    // calcular o delta entre duas leituras consecutivas (feito em MainFrame).
    private List<long[]> cpu_infos;

    // -------------------------------------------------------------------------
    // Tempo e carga
    // -------------------------------------------------------------------------

    // Tempo em segundos desde o boot do sistema, lido de /proc/uptime.
    // É um double porque o kernel registra com precisão de décimos de segundo.
    private double uptime;

    // Load average do sistema: média de processos prontos para executar nos
    // últimos 1, 5 e 15 minutos, lido de /proc/loadavg.
    // Exemplo: loadAvg[0]=0.62 significa que em média 0.62 processos estavam
    // prontos/esperando CPU no último minuto. Se > número de núcleos, o sistema
    // está sobrecarregado. Inicializado com zeros para evitar NullPointerException
    // antes do primeiro refresh().
    private double[] loadAvg = new double[]{0, 0, 0};

    // -------------------------------------------------------------------------
    // Contagens de processos
    // -------------------------------------------------------------------------

    // Quantidade de processos de usuário (Tasks) no momento do snapshot.
    private long num_tasks;

    // Total de threads de todo o sistema: soma de getThreads() de tasks + kthreads.
    private long num_threads;

    // Quantidade de kernel threads (Kthr) no momento do snapshot.
    private long num_kthreads;

    // -------------------------------------------------------------------------
    // Setters — chamados por TaskManagerService.refresh()
    // -------------------------------------------------------------------------

    /** Define a memória total do sistema em kB. */
    public void setMemTotal(double mem_total) { this.mem_total = mem_total; }

    /** Define a memória disponível para novos processos em kB. */
    public void setMemAvailable(double mem_available) { this.mem_available = mem_available; }

    /** Define a memória em uso calculada pela fórmula do htop. */
    public void setMemUsed(double mem_used) { this.mem_used = mem_used; }

    /** Define o swap total em kB. */
    public void setSwapTotal(double swap_total) { this.swap_total = swap_total; }

    /** Define o swap livre em kB. */
    public void setSwapFree(double swap_free) { this.swap_free = swap_free; }

    /** Define os contadores de CPU por núcleo (lista de arrays de ticks acumulados). */
    public void setCpuInfos(List<long[]> cpu_infos) { this.cpu_infos = cpu_infos; }

    /** Define o uptime do sistema em segundos. */
    public void setUptime(double uptime) { this.uptime = uptime; }

    /**
     * Define o load average do sistema.
     * @param loadAvg array com 3 elementos: [média 1min, média 5min, média 15min]
     */
    public void setLoadAvg(double[] loadAvg) { this.loadAvg = loadAvg; }

    /** Define a contagem de processos de usuário. */
    public void setNumTasks(long num_tasks) { this.num_tasks = num_tasks; }

    /** Define o total de threads do sistema. */
    public void setNumThreads(long num_threads) { this.num_threads = num_threads; }

    /** Define a contagem de kernel threads. */
    public void setNumKthreads(long num_kthreads) { this.num_kthreads = num_kthreads; }

    // -------------------------------------------------------------------------
    // Getters — usados por TaskManagerController e MainFrame para exibir os dados
    // -------------------------------------------------------------------------

    /** Retorna a memória total em kB. */
    public double getMemTotal() { return mem_total; }

    /** Retorna a memória disponível em kB. */
    public double getMemAvailable() { return mem_available; }

    /** Retorna a memória em uso (fórmula htop) em kB. */
    public double getMemUsed() { return mem_used; }

    /** Retorna o swap total em kB. */
    public double getSwapTotal() { return swap_total; }

    /** Retorna o swap livre em kB. */
    public double getSwapFree() { return swap_free; }

    /** Retorna os contadores de CPU por núcleo (lista de arrays de ticks). */
    public List<long[]> getCpuInfos() { return cpu_infos; }

    /** Retorna o uptime em segundos. */
    public double getUptime() { return uptime; }

    /**
     * Retorna o load average do sistema.
     * @return array [média 1min, média 5min, média 15min]
     */
    public double[] getLoadAvg() { return loadAvg; }

    /** Retorna a contagem de tasks (processos de usuário). */
    public long getNumTasks() { return num_tasks; }

    /** Retorna o total de threads do sistema. */
    public long getNumThreads() { return num_threads; }

    /** Retorna a contagem de kernel threads. */
    public long getNumKthreads() { return num_kthreads; }
}
