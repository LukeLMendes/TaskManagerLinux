package model;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

public class SnapshotData implements Serializable {

    private static final long serialVersionUID = 1L;

    private long timestamp;
    private SystemSnapshot systemInfos;
    private List<Processo> tasks;
    private List<Processo> kthreads;
    private List<ProcessIOStat> taskIOStats;

    public SnapshotData() {
        this.timestamp   = System.currentTimeMillis();
        this.tasks       = new ArrayList<>();
        this.kthreads    = new ArrayList<>();
        this.taskIOStats = new ArrayList<>();
    }

    public SnapshotData(SystemSnapshot systemInfos,
                        List<Processo> tasks,
                        List<Processo> kthreads) {
        this();
        this.systemInfos = systemInfos;
        this.tasks       = tasks;
        this.kthreads    = kthreads;
    }

    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setSystemSnapshot(SystemSnapshot systemInfos) { this.systemInfos = systemInfos; }
    public void setTasks(List<Processo> tasks) { this.tasks = tasks; }
    public void setKthreads(List<Processo> kthreads) { this.kthreads = kthreads; }
    public void setTaskIOStats(List<ProcessIOStat> stats) { this.taskIOStats = stats; }

    public long getTimestamp() { return timestamp; }
    public SystemSnapshot getSystemSnapshot() { return systemInfos; }
    public List<Processo> getTasks() { return tasks; }
    public List<Processo> getKthreads() { return kthreads; }
    public List<ProcessIOStat> getTaskIOStats() { return taskIOStats; }

    public int totalProcesses() {
        return tasks.size() + kthreads.size();
    }

    @Override
    public String toString() {
        return "SnapshotData{" +
                "timestamp=" + timestamp +
                ", tasks=" + tasks.size() +
                ", kthreads=" + kthreads.size() +
                '}';
    }
}
