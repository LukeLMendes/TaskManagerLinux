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
import persistence.FileAnnotationRepository;

import exception.SnapshotReadException;
import exception.SnapshotWriteException;
import exception.KillProcessException;
import exception.InvalidPidException;
import java.util.List;
import java.util.ArrayList;

public class TaskManagerService {

    private final SystemRepository systemRepository;
    private final ProcessRepository processRepository;
    private final IORepository ioRepository;
    private final SnapshotRepository snapshotRepository;
    private final ProcessKiller processKiller;

    private List<Processo> tasks;
    private List<Processo> kthreads;
    private List<ProcessIOStat> taskIOStats;
    private SystemSnapshot systemSnapshot;

    private final AnnotationService annotationService =
            new AnnotationService(new FileAnnotationRepository());

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
        try { annotationService.load(); } catch (exception.SnapshotReadException ignored) {}
    }

    public void refresh() {
        tasks    = processRepository.readTask();
        kthreads = processRepository.readKthr();

        taskIOStats = ioRepository.readTaskIO(tasks.stream()
                .filter(p -> p instanceof Task)
                .map(p -> (Task) p)
                .collect(java.util.stream.Collectors.toList()));

        systemSnapshot = new SystemSnapshot();
        systemSnapshot.setUptime(systemRepository.uptime());
        systemSnapshot.setCpuInfos(systemRepository.cpu_infos());
        systemSnapshot.setNumTasks(systemRepository.countTasks(tasks));
        systemSnapshot.setNumKthreads(systemRepository.countKthr(kthreads));
        systemSnapshot.setNumThreads(systemRepository.countThreads(tasks, kthreads));
        systemSnapshot.setNumRunning(systemRepository.running());

        java.util.Map<String, Long> mem = systemRepository.memInfo();

        long memTotal     = mem.getOrDefault("MemTotal",      0L);
        long memFree      = mem.getOrDefault("MemFree",       0L);
        long buffers      = mem.getOrDefault("Buffers",       0L);
        long cached       = mem.getOrDefault("Cached",        0L);
        long sreclaimable = mem.getOrDefault("SReclaimable",  0L);

        long memUsed = memTotal - memFree - buffers - cached - sreclaimable;

        systemSnapshot.setMemTotal(memTotal);
        systemSnapshot.setMemAvailable(mem.getOrDefault("MemAvailable", 0L));
        systemSnapshot.setMemUsed(memUsed);
        systemSnapshot.setSwapTotal(mem.getOrDefault("SwapTotal", 0L));
        systemSnapshot.setSwapFree(mem.getOrDefault("SwapFree",  0L));

        systemSnapshot.setLoadAvg(systemRepository.loadAvg());
    }

    public void saveSnapshot() throws SnapshotWriteException {
        SnapshotData data = new SnapshotData(systemSnapshot, tasks, kthreads);
        data.setTaskIOStats(taskIOStats);
        snapshotRepository.save(data);
    }

    public SnapshotData loadLatestSnapshot() throws SnapshotReadException {
        return snapshotRepository.loadLatest();
    }

    public List<SnapshotData> loadAllSnapshots() throws SnapshotReadException {
        return snapshotRepository.loadAll();
    }

    public void killProcess(int pid) throws KillProcessException, InvalidPidException {
        processKiller.killProcess(pid);
    }

    public List<Processo> buscarPorNome(String keyword) {
        String k = keyword.trim().toLowerCase();
        List<Processo> resultado = new ArrayList<>();
        if (k.isEmpty()) return resultado;
        List<Processo> todos = new ArrayList<>();
        if (tasks    != null) todos.addAll(tasks);
        if (kthreads != null) todos.addAll(kthreads);
        for (Processo p : todos) {
            String cmd = p.getCmdline().isEmpty() ? p.getComm() : p.getCmdline();
            if (cmd.toLowerCase().contains(k) || String.valueOf(p.getPid()).contains(k)) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    public void reniceProceso(int pid, int novoNice) throws exception.DomainException {
        try {
            Process proc = new ProcessBuilder(
                "renice", "-n", String.valueOf(novoNice), "-p", String.valueOf(pid))
                .redirectErrorStream(true)
                .start();
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                String out = new String(proc.getInputStream().readAllBytes()).trim();
                throw new exception.DomainException(
                    "renice falhou para PID " + pid + ": " + out);
            }
        } catch (java.io.IOException | InterruptedException e) {
            throw new exception.DomainException("Erro ao chamar renice: " + e.getMessage());
        }
    }

    public TreeNode<Processo> getProcessTree() {
        if (tasks == null || tasks.isEmpty()) return null;
        return ProcsTree.toTree(tasks);
    }

    public void adicionarAnotacao(int pid, String texto) throws exception.SnapshotWriteException {
        annotationService.add(pid, texto);
        annotationService.save();
    }

    public void removerAnotacao(int pid) throws exception.SnapshotWriteException {
        annotationService.remove(pid);
        annotationService.save();
    }

    public AnnotationService getAnnotationService() { return annotationService; }

    public List<Processo> getTasks() { return tasks; }
    public List<Processo> getKthreads() { return kthreads; }
    public List<ProcessIOStat> getTaskIOStats() { return taskIOStats; }
    public SystemSnapshot getSystemSnapshot() { return systemSnapshot; }
}
