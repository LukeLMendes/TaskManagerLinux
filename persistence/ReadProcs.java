package persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import model.Processo;
import model.Task;
import model.Kthr;

public class ReadProcs {

  private static Map<String, String> readStatusKeyValues(Path statusFile) throws IOException {
    Map<String, String> kv = new HashMap<>();

    // Lê o arquivo /proc/<pid>/status e cria um map "chave -> valor"
    // Ex: "VmRSS:  1234 kB" vira kv["VmRSS"] = "1234 kB"
    try (BufferedReader reader = Files.newBufferedReader(statusFile)) {
      reader.lines().forEach(line -> {
        int idx = line.indexOf(':');
        if (idx > 0) {
          // Tudo antes do ":" é a chave (Pid, PPid, Threads, VmRSS, etc.)
          String key = line.substring(0, idx).trim();
          // Tudo depois do ":" é o valor (normalmente começa com espaços)
          String val = line.substring(idx + 1).trim();
          kv.put(key, val);
        }
      });
    }

    return kv;
  }

  private static int firstInt(String value) {
    // Extrai o primeiro número de strings como "1234 kB".
    // Se o campo não existir no status, value vem null e retorna 0 (default seguro).
    if (value == null) return 0;
    String[] parts = value.trim().split("\\s+");
    if (parts.length == 0) return 0;
    return Integer.parseInt(parts[0]);
  }

  private static boolean isKthreadProcess(Path procDir) {
    Path statusFile = procDir.resolve("status");
    try {
      Map<String, String> kv = readStatusKeyValues(statusFile);
      // Campo "Kthread" costuma ser 0 (processo usuário) ou 1 (kthread).
      int kthread = firstInt(kv.get("Kthread"));
      return kthread != 0;
    } catch (IOException e) {
      // Se não conseguiu ler, evita derrubar a varredura inteira e simplesmente ignora esse PID.
      return false;
    }
  }

  public static List<Processo> readTask () { 
    Path pathDir = Paths.get("/proc");
    List<Processo> list = new ArrayList<>();

    try (Stream<Path> procs = Files.list(pathDir)) {
      procs.filter(path -> Files.isDirectory(path))
          // Em /proc, diretórios numéricos são PIDs (processos).
          .filter(path -> path.getFileName().toString().matches("\\d+"))
          // Aqui você mantém apenas processos "não-kthread".
          .filter(path -> !isKthreadProcess(path))
          .forEach(new Consumer<Path>() {
            @Override
            public void accept(Path path) {

              Task task = new Task();
              Path statusFile = path.resolve("status");

              try {
                Map<String, String> kv = readStatusKeyValues(statusFile);

                int pid = firstInt(kv.get("Pid"));
                int ppid = firstInt(kv.get("PPid"));
                int threads = firstInt(kv.get("Threads"));

                int virt = firstInt(kv.get("VmSize"));
                int res  = firstInt(kv.get("VmRSS"));

                // Nem todo processo expõe todos os campos; por isso firstInt(null) -> 0
                int rssFile  = firstInt(kv.get("RssFile"));
                int rssShmem = firstInt(kv.get("RssShmem"));
                int shr = rssFile + rssShmem;

                task.setPid(pid);
                task.setPpid(ppid);
                task.setThreads(threads);
                task.setVirt(virt);
                task.setRes(res);
                task.setShr(shr);

              } catch (IOException e) {
                // Falha de leitura é comum em /proc (processo pode morrer entre listar e ler).
                e.printStackTrace();
                return; // pula esse processo
              }

              Path statFile = path.resolve("stat");
              try (BufferedReader reader = Files.newBufferedReader(statFile)) {
                // /proc/<pid>/stat é uma linha com vários campos fixos por posição.
                // Atenção: o nome do processo (comm) vem entre parênteses e pode ter espaços.
                String[] infos = reader.lines().toArray(String[]::new);
                String[] splited = infos[0].trim().split("\\s+");
                task.setPri(Integer.parseInt(splited[17]));
                task.setNice(Integer.parseInt(splited[18]));
              } catch (IOException e) {
                e.printStackTrace();
              }

              // O arquivo "children" pertence à thread líder do processo:
              // /proc/<pid>/task/<pid>/children
              Path childrenFile = path.resolve("task/" + task.getPid() + "/children");

              try (BufferedReader reader = Files.newBufferedReader(childrenFile)) {
                // Se o arquivo estiver vazio, não há filhos listados.
                // Observação: reader.read() consome 1 caractere do stream.
                if (reader.read() != -1) {
                  String[] lines = reader.lines().toArray(String[]::new);
                  String[] pids = lines[0].trim().split("\\s+");
                  for (String pid : pids) {
                    task.addChild(Integer.parseInt(pid));
                  }
                }
              } catch (IOException e){
                e.printStackTrace();
              }

              // Adiciona o processo coletado na lista final.
              list.add(task);
            }
          });
    } catch (IOException e ) {
      e.printStackTrace();
    }

    return list;
  }

  public static List<Processo> readKthr() { //mudar isso depois para HashMap
    Path pathDir = Paths.get("/proc");
    List<Processo> list = new ArrayList<>();

    try (Stream<Path> procs = Files.list(pathDir)) {
      procs.filter(path -> Files.isDirectory(path))
          .filter(path -> path.getFileName().toString().matches("\\d+"))
          // Aqui você mantém apenas os PIDs marcados como kernel thread.
          .filter(path -> isKthreadProcess(path))
          .forEach(new Consumer<Path>() {
            @Override
            public void accept(Path path) {

              Kthr kthr = new Kthr();
              Path statusFile = path.resolve("status");

              try {
                Map<String, String> kv = readStatusKeyValues(statusFile);

                int pid = firstInt(kv.get("Pid"));
                int ppid = firstInt(kv.get("PPid"));
                int threads = firstInt(kv.get("Threads"));

                // "Kthreads" pode não existir em alguns kernels; firstInt(null) vira 0.
                int kthreads = firstInt(kv.get("Kthreads"));

                kthr.setPid(pid);
                kthr.setPpid(ppid);
                kthr.setThreads(threads);
                kthr.setKthreads(kthreads);

              } catch (IOException e) {
                e.printStackTrace();
                return; // pula esse processo
              }

              Path statFile = path.resolve("stat");
              try (BufferedReader reader = Files.newBufferedReader(statFile)) {
                String[] infos = reader.lines().toArray(String[]::new);
                String[] splited = infos[0].trim().split("\\s+");
                kthr.setPri(Integer.parseInt(splited[17]));
                kthr.setNice(Integer.parseInt(splited[18]));
              } catch (IOException e) {
                e.printStackTrace();
              }

              Path childrenFile = path.resolve("task/" + kthr.getPid() + "/children");

              try (BufferedReader reader = Files.newBufferedReader(childrenFile)) {
                if (reader.read() != -1) {
                  String[] lines = reader.lines().toArray(String[]::new);
                  String[] pids = lines[0].trim().split("\\s+");
                  for (String pid : pids) {
                    kthr.addChild(Integer.parseInt(pid));
                  }
                }
              } catch (IOException e){
                e.printStackTrace();
              }

              list.add(kthr);
            }
          });
    } catch (IOException e ) {
      e.printStackTrace();
    }

    return list;
  }
}
