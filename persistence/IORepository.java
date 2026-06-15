package persistence;

import java.util.List;
import model.ProcessIOStat;
import model.Task;
import model.Kthr;

public interface IORepository {
  List<ProcessIOStat> readTaskIO(List<Task> tasks);
  List<ProcessIOStat> readKthrIO(List<Kthr> kthrs);
}
