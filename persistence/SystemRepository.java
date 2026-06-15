package persistence;

import java.util.List;

import model.Processo;

public interface SystemRepository {
  //List<> readCPU(); //determinar o tipo de objeto guardado dps
  //List<> readMem(); //determinar o tipo de objeto guardado dps
  //List<> readSwap();

  int countTasks(List<Processo> tasks);
  int countThreads(List<Processo> tasks, List<Processo> kthr);
  int countKthr(List<Processo> kthr);
  int running();
  double uptime();
  List<long[]> cpu_infos();


}
