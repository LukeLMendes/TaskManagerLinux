package persistence;

import java.util.List;
import model.Processo;

public interface ProcessRepository {
  public List<Processo> readTask();
  public List<Processo> readKthr();
}
