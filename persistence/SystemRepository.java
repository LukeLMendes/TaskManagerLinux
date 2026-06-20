package persistence;

import java.util.List;
import java.util.Map;

import model.Processo;

public interface SystemRepository {
    int countTasks(List<Processo> tasks);
    int countThreads(List<Processo> tasks, List<Processo> kthr);
    int countKthr(List<Processo> kthr);
    int running();
    double uptime();
    List<long[]> cpu_infos();
    Map<String, Long> memInfo();
    double[] loadAvg();
}
