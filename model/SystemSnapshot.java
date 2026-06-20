package model;

import java.io.Serializable;
import java.util.List;

public class SystemSnapshot implements Serializable {

    private static final long serialVersionUID = 5L;

    private double mem_total;
    private double mem_available;
    private double mem_used;
    private double swap_total;
    private double swap_free;
    private List<long[]> cpu_infos;
    private double uptime;
    private double[] loadAvg = new double[]{0, 0, 0};
    private long num_tasks;
    private long num_threads;
    private long num_kthreads;
    private long num_running;

    public void setMemTotal(double mem_total) { this.mem_total = mem_total; }
    public void setMemAvailable(double mem_available) { this.mem_available = mem_available; }
    public void setMemUsed(double mem_used) { this.mem_used = mem_used; }
    public void setSwapTotal(double swap_total) { this.swap_total = swap_total; }
    public void setSwapFree(double swap_free) { this.swap_free = swap_free; }
    public void setCpuInfos(List<long[]> cpu_infos) { this.cpu_infos = cpu_infos; }
    public void setUptime(double uptime) { this.uptime = uptime; }
    public void setLoadAvg(double[] loadAvg) { this.loadAvg = loadAvg; }
    public void setNumTasks(long num_tasks) { this.num_tasks = num_tasks; }
    public void setNumThreads(long num_threads) { this.num_threads = num_threads; }
    public void setNumKthreads(long num_kthreads) { this.num_kthreads = num_kthreads; }
    public void setNumRunning(long num_running) { this.num_running = num_running; }

    public double getMemTotal() { return mem_total; }
    public double getMemAvailable() { return mem_available; }
    public double getMemUsed() { return mem_used; }
    public double getSwapTotal() { return swap_total; }
    public double getSwapFree() { return swap_free; }
    public List<long[]> getCpuInfos() { return cpu_infos; }
    public double getUptime() { return uptime; }
    public double[] getLoadAvg() { return loadAvg; }
    public long getNumTasks() { return num_tasks; }
    public long getNumThreads() { return num_threads; }
    public long getNumKthreads() { return num_kthreads; }
    public long getNumRunning() { return num_running; }
}
