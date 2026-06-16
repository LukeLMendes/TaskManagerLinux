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
 *   - memória total e disponível (lidos de /proc/meminfo)
 *   - informações de CPU por núcleo (lidas de /proc/stat)
 *   - uptime do sistema (lido de /proc/uptime)
 *   - contagem de tasks, threads e kthreads no momento do snapshot
 */
public class SystemSnapshot implements Serializable {

    // Número de versão da serialização. Se a estrutura desta classe mudar,
    // incremente este valor para evitar erros ao ler arquivos antigos.
    private static final long serialVersionUID = 1L;

    // Memória total do sistema em kilobytes, lida de /proc/meminfo (campo MemTotal).
    private double mem_total;

    // Memória disponível para novos processos em kilobytes (/proc/meminfo campo MemAvailable).
    private double mem_available;

    // Lista de arrays de long, um por linha "cpu*" em /proc/stat.
    // Cada array contém: [user, nice, system, idle, iowait, irq, softirq, ...].
    // Usado para calcular o percentual de uso de CPU entre dois snapshots consecutivos.
    private List<long[]> cpu_infos;

    // Tempo em segundos desde o boot do sistema (lido de /proc/uptime).
    private double uptime;

    // Quantidade de processos de usuário (Tasks) no momento do snapshot.
    private long num_tasks;

    // Total de threads (soma de Processo.getThreads() de tasks + kthreads).
    private long num_threads;

    // Quantidade de kernel threads (Kthr) no momento do snapshot.
    private long num_kthreads;

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /** Define a memória total do sistema em kB. */
    public void setMemTotal(double mem_total) { this.mem_total = mem_total; }

    /** Define a memória disponível em kB. */
    public void setMemAvailable(double mem_available) { this.mem_available = mem_available; }

    /** Define as informações de CPU por núcleo (lista de arrays de contadores). */
    public void setCpuInfos(List<long[]> cpu_infos) { this.cpu_infos = cpu_infos; }

    /** Define o uptime do sistema em segundos. */
    public void setUptime(double uptime) { this.uptime = uptime; }

    /** Define a contagem de processos de usuário. */
    public void setNumTasks(long num_tasks) { this.num_tasks = num_tasks; }

    /** Define o total de threads (usuário + kernel). */
    public void setNumThreads(long num_threads) { this.num_threads = num_threads; }

    /** Define a contagem de kernel threads. */
    public void setNumKthreads(long num_kthreads) { this.num_kthreads = num_kthreads; }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** Retorna a memória total em kB. */
    public double getMemTotal() { return mem_total; }

    /** Retorna a memória disponível em kB. */
    public double getMemAvailable() { return mem_available; }

    /** Retorna os contadores de CPU por núcleo. */
    public List<long[]> getCpuInfos() { return cpu_infos; }

    /** Retorna o uptime em segundos. */
    public double getUptime() { return uptime; }

    /** Retorna a contagem de tasks. */
    public long getNumTasks() { return num_tasks; }

    /** Retorna o total de threads. */
    public long getNumThreads() { return num_threads; }

    /** Retorna a contagem de kthreads. */
    public long getNumKthreads() { return num_kthreads; }
}
