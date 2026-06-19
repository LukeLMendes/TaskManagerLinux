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

/**
 * Implementação concreta de ProcessRepository que lê processos do /proc no Linux.
 *
 * Em /proc, cada processo possui um diretório com seu PID como nome:
 *   /proc/1/        → init/systemd
 *   /proc/1234/     → algum processo com PID 1234
 *
 * Dentro de cada diretório, os arquivos mais usados aqui são:
 *   status  → informações gerais (PID, PPID, estado, memória, UID...)
 *   stat    → contadores numéricos (ticks de CPU, prioridade, nome do processo...)
 *   task/<pid>/children → PIDs dos filhos diretos
 *
 * Esta classe é STATEFUL: mantém o mapa prevTicks entre chamadas para calcular
 * o delta de CPU% por processo. Por isso deve ser instanciada uma única vez
 * e reutilizada a cada refresh (o que o MainFrame já faz).
 */
public class ProcfsProcessRepository implements ProcessRepository{

  // Resolução do relógio do kernel: 100 ticks por segundo em praticamente todo
  // Linux x86-64 (valor de SC_CLK_TCK). Cada tick = 10ms.
  // Usado para converter delta de ticks em percentual de CPU:
  //   cpuPct = delta_ticks / CLK_TCK * 100
  // Exemplo: 50 ticks em 1s → 50 / 100 * 100 = 50% de um núcleo.
  private static final long CLK_TCK = 100;

  // Mapa PID → [ticks_totais_na_última_leitura].
  // "Ticks totais" = utime + stime: tempo que o processo passou executando em
  // modo usuário (utime) + modo kernel (stime), ambos em ticks acumulados desde
  // que o processo foi criado.
  // A cada refresh, calculamos: delta = ticks_agora - ticks_antes.
  // Se delta = 50 em ~1 segundo de intervalo, o processo usou ~50% de um núcleo.
  // Na primeira leitura de um PID novo não há entrada anterior, então cpuPct = 0.
  private final Map<Integer, long[]> prevTicks = new HashMap<>();

  // -------------------------------------------------------------------------
  // Helpers privados de leitura e parsing
  // -------------------------------------------------------------------------

  /**
   * Lê o arquivo /proc/<pid>/status e retorna um mapa "chave → valor".
   *
   * Cada linha do arquivo tem o formato "Chave:  valor aqui\n".
   * Exemplo: "VmRSS:  1234 kB" vira kv.get("VmRSS") = "1234 kB".
   *
   * @param statusFile caminho para o arquivo status do processo
   * @return mapa com todos os campos do status
   * @throws IOException se o arquivo não puder ser lido (processo pode ter morrido)
   */
  private static Map<String, String> readStatusKeyValues(Path statusFile) throws IOException {
    Map<String, String> kv = new HashMap<>();

    try (BufferedReader reader = Files.newBufferedReader(statusFile)) {
      reader.lines().forEach(line -> {
        int idx = line.indexOf(':');
        if (idx > 0) {
          String key = line.substring(0, idx).trim();        // ex: "VmRSS"
          String val = line.substring(idx + 1).trim();       // ex: "1234 kB"
          kv.put(key, val);
        }
      });
    }

    return kv;
  }

  /**
   * Extrai o primeiro número inteiro de uma string como "1234 kB".
   * Usado para ler campos numéricos de /proc/<pid>/status.
   *
   * @param value string com o valor (ex: "1234 kB", "42", etc.)
   * @return primeiro número inteiro encontrado, ou 0 se value for null
   */
  private static int firstInt(String value) {
    if (value == null) return 0;
    String[] parts = value.trim().split("\\s+");
    if (parts.length == 0) return 0;
    return Integer.parseInt(parts[0]);
  }

  /**
   * Extrai o nome curto do processo (comm) de uma linha de /proc/<pid>/stat.
   *
   * A linha tem o formato: "PID (nome do processo) estado ppid ..."
   * O comm fica entre o primeiro '(' e o último ')'.
   * Usamos o ÚLTIMO ')' porque o comm pode conter parênteses internos
   * (ex: um processo chamado "bash(2)" teria "(bash(2))" na linha).
   *
   * @param line linha completa de /proc/<pid>/stat
   * @return nome do processo, ou string vazia se o formato for inválido
   */
  private static String parseComm(String line) {
    int firstParen = line.indexOf('(');
    int lastParen  = line.lastIndexOf(')');
    if (firstParen < 0 || lastParen <= firstParen) return "";
    return line.substring(firstParen + 1, lastParen);
  }

  /**
   * Divide a linha de /proc/<pid>/stat nos campos que vêm APÓS o "(comm)".
   *
   * O problema: o comm pode conter espaços (ex: "Web Content", "kworker/0:1H").
   * Isso impossibilita um simples split() na linha inteira para acessar campos
   * por índice fixo — o índice mudaria dependendo do número de espaços no nome.
   *
   * Solução: encontrar o ÚLTIMO ')' e pegar tudo o que vem depois.
   * A partir daí os campos são separados por espaço e têm posição fixa:
   *   índice 0:  state    (S, R, D, Z, T...)
   *   índice 1:  ppid
   *   índice 2:  pgrp     (process group)
   *   índice 3:  session
   *   ...
   *   índice 11: utime    (ticks em modo usuário desde criação do processo)
   *   índice 12: stime    (ticks em modo kernel desde criação do processo)
   *   ...
   *   índice 15: priority (prioridade de escalonamento)
   *   índice 16: nice     (nice value, -20 a +19)
   *
   * @param line linha completa de /proc/<pid>/stat
   * @return array de strings com os campos após o "(comm)", ou array vazio se inválido
   */
  private static String[] parseStatLine(String line) {
    int lastParen = line.lastIndexOf(')');
    if (lastParen < 0 || lastParen + 2 >= line.length()) return new String[0];
    // +2 para pular o ') ' (parêntese + espaço) antes do primeiro campo.
    return line.substring(lastParen + 2).trim().split("\\s+");
  }

  /**
   * Determina se o processo no diretório dado é uma kernel thread.
   *
   * Kernel threads (kthreads) são processos do kernel que rodam totalmente em
   * espaço de kernel — não têm memória de usuário mapeada e geralmente aparecem
   * com nomes como [kworker/0:1], [ksoftirqd/0], [migration/0].
   *
   * A identificação é feita pelo campo "Kthread:" em /proc/<pid>/status:
   *   0 = processo de usuário (Task)
   *   1 = kernel thread (Kthr)
   *
   * @param procDir diretório /proc/<pid> do processo
   * @return true se for kernel thread, false se for processo de usuário ou em caso de erro
   */
  private static boolean isKthreadProcess(Path procDir) {
    Path statusFile = procDir.resolve("status");
    try {
      Map<String, String> kv = readStatusKeyValues(statusFile);
      int kthread = firstInt(kv.get("Kthread"));
      return kthread != 0;
    } catch (IOException e) {
      // Se não conseguiu ler (processo morreu, sem permissão), ignora este PID.
      return false;
    }
  }

  // -------------------------------------------------------------------------
  // readTask() — coleta todos os processos de usuário
  // -------------------------------------------------------------------------

  /**
   * Lê /proc e retorna a lista de todos os processos de usuário (Tasks).
   *
   * Para cada PID encontrado em /proc:
   *   1. Verifica se é kernel thread (isKthreadProcess) → pula se for
   *   2. Lê /proc/<pid>/status → PID, PPID, threads, estado, memória, EUID
   *   3. Lê /proc/<pid>/stat  → comm, prioridade, nice, ticks de CPU
   *   4. Lê /proc/<pid>/task/<pid>/children → lista de PIDs filhos
   *   5. Calcula CPU% usando o delta de ticks em relação à leitura anterior
   *
   * Erros de leitura são comuns em /proc: um processo pode morrer entre o
   * momento em que listamos o diretório e o momento em que lemos seus arquivos.
   * Nesses casos, o processo simplesmente é ignorado (não derruba a varredura).
   *
   * @return lista de Tasks; pode ser vazia mas nunca null
   */
  @Override
  public List<Processo> readTask() {
    Path pathDir = Paths.get("/proc");
    List<Processo> list = new ArrayList<>();

    try (Stream<Path> procs = Files.list(pathDir)) {
      procs
        // Considera apenas diretórios (cada PID é um diretório em /proc).
        .filter(path -> Files.isDirectory(path))
        // Diretórios numéricos = PIDs. Outros (net, sys, bus...) são ignorados.
        .filter(path -> path.getFileName().toString().matches("\\d+"))
        // Mantém apenas processos de usuário (não kernel threads).
        .filter(path -> !isKthreadProcess(path))
        .forEach(new Consumer<Path>() {
          @Override
          public void accept(Path path) {

            Task task = new Task();

            // ── Etapa 1: lê /proc/<pid>/status ──────────────────────────────
            Path statusFile = path.resolve("status");
            try {
              Map<String, String> kv = readStatusKeyValues(statusFile);

              int pid     = firstInt(kv.get("Pid"));
              int ppid    = firstInt(kv.get("PPid"));
              int threads = firstInt(kv.get("Threads"));
              // State vem como "S (sleeping)" — queremos só o primeiro caractere.
              char state  = kv.get("State").charAt(0);

              // VmSize = memória virtual total reservada (nem toda é RAM física).
              int virt = firstInt(kv.get("VmSize"));
              // VmRSS = memória residente (RAM física realmente ocupada).
              int res  = firstInt(kv.get("VmRSS"));

              // "Uid: real effective saved filesystem" — queremos o índice 1 (EUID).
              String uids = kv.get("Uid");
              int euid = Integer.parseInt(uids.split("\\s+")[1]);

              // Memória compartilhada = páginas de arquivo + segmentos compartilhados.
              int rssFile  = firstInt(kv.get("RssFile"));
              int rssShmem = firstInt(kv.get("RssShmem"));
              int shr = rssFile + rssShmem;

              task.setPid(pid);
              task.setPpid(ppid);
              task.setThreads(threads);
              task.setEUID(euid);
              task.setState(state);
              task.setVirt(virt);
              task.setRes(res);
              task.setShr(shr);

            } catch (IOException e) {
              // Processo morreu durante a leitura — comum e esperado em /proc.
              e.printStackTrace();
              return; // pula este processo e vai para o próximo
            }

            // ── Etapa 2: lê /proc/<pid>/stat ────────────────────────────────
            // Obtém: comm (nome), prioridade, nice e ticks de CPU para calcular CPU%.
            Path statFile = path.resolve("stat");
            try (BufferedReader reader = Files.newBufferedReader(statFile)) {
              String line = reader.readLine();
              if (line != null) {
                // Extrai o nome do processo entre parênteses.
                task.setComm(parseComm(line));

                // Campos numéricos após o "(comm)" — ver parseStatLine() para os índices.
                String[] f = parseStatLine(line);
                if (f.length > 16) {
                  task.setPri(Integer.parseInt(f[15]));   // priority
                  task.setNice(Integer.parseInt(f[16]));  // nice value

                  // utime: ticks que o processo gastou em modo usuário (código da aplicação).
                  // stime: ticks que o processo gastou em modo kernel (system calls, I/O, etc.).
                  // Ambos são acumulados desde a criação do processo.
                  long utime = Long.parseLong(f[11]);
                  long stime = Long.parseLong(f[12]);
                  long currTicks = utime + stime; // total de ticks de CPU usados até agora

                  // Calcula o delta em relação à leitura anterior.
                  long[] prev = prevTicks.get(task.getPid());
                  if (prev != null) {
                    // delta = ticks consumidos desde o último refresh (~1 segundo).
                    // Dividimos por CLK_TCK (100) e multiplicamos por 100 para ter %.
                    // Resultado: número de ticks ≈ percentual de uso de um núcleo em 1s.
                    // Math.max(0,...) evita percentual negativo se o contador reiniciar.
                    long delta = currTicks - prev[0];
                    task.setCpuPct(Math.max(0, delta * 100.0 / CLK_TCK));
                  }
                  // Salva os ticks atuais para serem usados como "anterior" no próximo refresh.
                  prevTicks.put(task.getPid(), new long[]{currTicks});
                }
              }
            } catch (IOException e) {
              e.printStackTrace();
            }

            // ── Etapa 3: lê /proc/<pid>/cmdline ─────────────────────────────
            // Contém a linha de comando completa que originou o processo.
            // Os argumentos são separados por '\0' (byte nulo) no arquivo.
            // Convertemos para string substituindo '\0' por espaço.
            // Processos zumbis e kernel threads têm este arquivo vazio — nesse caso
            // a view usará o comm como fallback (entre colchetes para kthreads).
            Path cmdlineFile = path.resolve("cmdline");
            try {
              byte[] bytes = Files.readAllBytes(cmdlineFile);
              if (bytes.length > 0) {
                // Substitui cada byte nulo por espaço e remove espaço final.
                task.setCmdline(new String(bytes).replace('\0', ' ').trim());
              }
            } catch (IOException e) {
              // Processo morreu — cmdline fica vazio, a view exibirá o comm.
            }

            // ── Etapa 4: lê /proc/<pid>/task/<pid>/children ─────────────────
            // Lista os PIDs dos filhos diretos deste processo.
            // Necessário para montar a árvore de processos em ProcsTree.
            Path childrenFile = path.resolve("task/" + task.getPid() + "/children");
            try (BufferedReader reader = Files.newBufferedReader(childrenFile)) {
              // reader.read() consome 1 caractere; se retornar -1, o arquivo está vazio
              // (sem filhos). Caso contrário, lemos o resto da linha.
              if (reader.read() != -1) {
                String[] lines = reader.lines().toArray(String[]::new);
                if (lines.length > 0) {
                  String[] pids = lines[0].trim().split("\\s+");
                  for (String pid : pids) {
                    task.addChild(Integer.parseInt(pid));
                  }
                }
              }
            } catch (IOException e){
              e.printStackTrace();
            }

            list.add(task);
          }
        });
    } catch (IOException e) {
      e.printStackTrace();
    }

    return list;
  }

  // -------------------------------------------------------------------------
  // readKthr() — coleta todas as kernel threads
  // -------------------------------------------------------------------------

  /**
   * Lê /proc e retorna a lista de todas as kernel threads (Kthr).
   *
   * Kernel threads não têm memória de usuário mapeada, então campos como
   * VmSize e VmRSS não existem no status delas — por isso são representadas
   * por Kthr (que não tem virt/res/shr) e não por Task.
   *
   * O processo é análogo ao readTask(), mas o filtro é invertido:
   * só entra no forEach se isKthreadProcess() retornar true.
   * Também não calcula CPU% para kthreads (geralmente irrelevante na view).
   *
   * @return lista de Kthr; pode ser vazia mas nunca null
   */
  public List<Processo> readKthr() {
    Path pathDir = Paths.get("/proc");
    List<Processo> list = new ArrayList<>();

    try (Stream<Path> procs = Files.list(pathDir)) {
      procs
        .filter(path -> Files.isDirectory(path))
        .filter(path -> path.getFileName().toString().matches("\\d+"))
        // Agora mantém APENAS as kernel threads (filtro invertido em relação ao readTask).
        .filter(path -> isKthreadProcess(path))
        .forEach(new Consumer<Path>() {
          @Override
          public void accept(Path path) {

            Kthr kthr = new Kthr();

            // ── Etapa 1: lê /proc/<pid>/status ──────────────────────────────
            Path statusFile = path.resolve("status");
            try {
              Map<String, String> kv = readStatusKeyValues(statusFile);

              int pid      = firstInt(kv.get("Pid"));
              int ppid     = firstInt(kv.get("PPid"));
              int threads  = firstInt(kv.get("Threads"));
              char state   = kv.get("State").charAt(0);

              String uids = kv.get("Uid");
              int euid = Integer.parseInt(uids.split("\\s+")[1]);

              // "Kthreads" é o número de kthreads no grupo — pode não existir em
              // todos os kernels; firstInt(null) retorna 0 com segurança.
              int kthreads = firstInt(kv.get("Kthreads"));

              kthr.setPid(pid);
              kthr.setPpid(ppid);
              kthr.setThreads(threads);
              kthr.setEUID(euid);
              kthr.setState(state);
              kthr.setKthreads(kthreads);

            } catch (IOException e) {
              e.printStackTrace();
              return;
            }

            // ── Etapa 2: lê /proc/<pid>/stat ────────────────────────────────
            Path statFile = path.resolve("stat");
            try (BufferedReader reader = Files.newBufferedReader(statFile)) {
              String line = reader.readLine();
              if (line != null) {
                kthr.setComm(parseComm(line));
                String[] f = parseStatLine(line);
                if (f.length > 16) {
                  kthr.setPri(Integer.parseInt(f[15]));
                  kthr.setNice(Integer.parseInt(f[16]));
                }
              }
            } catch (IOException e) {
              e.printStackTrace();
            }

            // ── Etapa 3: lê /proc/<pid>/task/<pid>/children ─────────────────
            Path childrenFile = path.resolve("task/" + kthr.getPid() + "/children");
            try (BufferedReader reader = Files.newBufferedReader(childrenFile)) {
              if (reader.read() != -1) {
                String[] lines = reader.lines().toArray(String[]::new);
                if (lines.length > 0) {
                  String[] pids = lines[0].trim().split("\\s+");
                  for (String pid : pids) {
                    kthr.addChild(Integer.parseInt(pid));
                  }
                }
              }
            } catch (IOException e){
              e.printStackTrace();
            }

            list.add(kthr);
          }
        });
    } catch (IOException e) {
      e.printStackTrace();
    }

    return list;
  }
}
