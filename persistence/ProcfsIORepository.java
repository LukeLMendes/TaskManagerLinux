package persistence;

import model.Task;
import model.Kthr;
import model.ProcessIOStat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;


public class ProcfsIORepository implements IORepository {

  private static Map<String, String> readIOKeyValues(Path ioFile) throws IOException {
    Map<String, String> kv = new HashMap<>();

    // Lê o arquivo /proc/<pid>/io e cria um map "chave -> valor"
    try (BufferedReader reader = Files.newBufferedReader(ioFile)) {
      try {
        reader.lines().forEach(line -> {
          int idx = line.indexOf(':');
          if (idx > 0) {
            // Tudo antes do ":" é a chave
            String key = line.substring(0, idx).trim();
            // Tudo depois do ":" é o valor (normalmente começa com espaços)
            String val = line.substring(idx + 1).trim();
            kv.put(key, val);
          }
        });
      } catch (UncheckedIOException e) {
        // reader.lines() usa stream lazy: IOException vira UncheckedIOException.
        // Reempacota para que o catch (IOException) no chamador funcione.
        throw e.getCause();
      }
    }

    return kv;
  }

  private static int firstInt(String value) {
    // Extrai o primeiro número de strings.
    // Se o campo não existir , value vem null e retorna 0 (default seguro).
    if (value == null) return 0;
    String[] parts = value.trim().split("\\s+");
    if (parts.length == 0) return 0;
    return Integer.parseInt(parts[0]);
  }



  @Override
  public List<ProcessIOStat> readTaskIO(List<Task> tasks) {
    List<ProcessIOStat> processIO_list = new java.util.ArrayList<>();
    for (int i = 0; i < tasks.size(); i++) {
      // Um novo objeto é criado por processo para evitar que todas as entradas
      // da lista apontem para o mesmo objeto e se sobrescrevam.
      ProcessIOStat processIO = new ProcessIOStat();
      if (tasks.get(i).getEUID() >= 1000) {
        Path ioFile = Paths.get("/proc" + "/" + tasks.get(i).getPid() + "/io");
        try {
          Map<String, String> kv = readIOKeyValues(ioFile);

          int readBytes  = firstInt(kv.get("read_bytes"));
          int writeBytes = firstInt(kv.get("write_bytes"));

          processIO.setPID(tasks.get(i).getPid());
          processIO.setEUID(tasks.get(i).getEUID());
          processIO.setRead(readBytes);
          processIO.setWrite(writeBytes);
        } catch (IOException e) {
          // -1 sinaliza para a view que o acesso a /proc/<pid>/io foi negado.
          processIO.setPID(tasks.get(i).getPid());
          processIO.setRead(-1);
          processIO.setWrite(-1);
        }
      } else {
        // EUID < 1000: processo de sistema, sem leitura de I/O.
        processIO.setPID(tasks.get(i).getPid());
        processIO.setRead(-1);
        processIO.setWrite(-1);
      }
      processIO_list.add(processIO);
    }

    return processIO_list;
  }

  @Override
  public List<ProcessIOStat> readKthrIO(List<Kthr> kthrs) {
    List<ProcessIOStat> processIO_list = new java.util.ArrayList<>();
    for (int i = 0; i < kthrs.size(); i++) {
      ProcessIOStat processIO = new ProcessIOStat();
      if (kthrs.get(i).getEUID() >= 1000) {
        Path ioFile = Paths.get("/proc" + "/" + kthrs.get(i).getPid() + "/io");
        try {
          Map<String, String> kv = readIOKeyValues(ioFile);

          double readBytes  = firstInt(kv.get("read_bytes"));
          double writeBytes = firstInt(kv.get("write_bytes"));

          processIO.setPID(kthrs.get(i).getPid());
          processIO.setEUID(kthrs.get(i).getEUID());
          processIO.setRead(readBytes);
          processIO.setWrite(writeBytes);
        } catch (IOException e) {
          processIO.setPID(kthrs.get(i).getPid());
          processIO.setRead(-1);
          processIO.setWrite(-1);
        }
      } else {
        processIO.setPID(kthrs.get(i).getPid());
        processIO.setRead(-1);
        processIO.setWrite(-1);
      }
      processIO_list.add(processIO);
    }

    return processIO_list;
  }

}


