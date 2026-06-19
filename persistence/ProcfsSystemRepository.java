package persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import model.Processo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Implementação concreta de SystemRepository que lê dados de /proc no Linux.
 *
 * Todos os arquivos em /proc são gerados pelo kernel em tempo real:
 * não existem no disco, são criados na memória quando abertos.
 * Por isso a leitura é rápida, mas os dados podem mudar entre aberturas.
 */
public class ProcfsSystemRepository implements SystemRepository {

  /**
   * Retorna o número de processos de usuário na lista.
   * Simples: o tamanho da lista já é a contagem.
   */
  @Override
  public int countTasks(List<Processo> tasks) {
    return tasks.size();
  }

  /**
   * Soma o campo "threads" de todos os processos e kernel threads.
   * Cada Processo.getThreads() retorna o que está em /proc/<pid>/status campo "Threads:".
   */
  @Override
  public int countThreads(List<Processo> tasks, List<Processo> kthrs) {
    int threads = 0;
    for (Processo i : tasks) {
      threads = threads + i.getThreads();
    }
    for (Processo i : kthrs) {
      threads = threads + i.getThreads();
    }
    return threads;
  }

  /**
   * Retorna o número de kernel threads na lista.
   */
  @Override
  public int countKthr(List<Processo> kthrs) {
    return kthrs.size();
  }

  /**
   * Contagem de processos em estado 'R' (running).
   * Não implementado aqui — a view faz essa filtragem diretamente na lista de tasks.
   */
  @Override
  public int running() {
    return 0;
  }

  /**
   * Lê /proc/uptime e retorna o tempo desde o boot em segundos.
   *
   * Formato de /proc/uptime: "12345.67 98765.43"
   *   - Primeiro número:  uptime total do sistema em segundos
   *   - Segundo número:   soma do tempo ocioso de todos os núcleos (pode ser > uptime)
   * Só precisamos do primeiro.
   */
  @Override
  public double uptime() {
    Path uptimeFile = Paths.get("/proc/uptime");
    double uptimeSec = 0;

    try (BufferedReader reader = Files.newBufferedReader(uptimeFile)) {
      String line = reader.readLine();
      // Pega apenas o primeiro valor da linha (separa pelo espaço).
      uptimeSec = Double.parseDouble(line.split("\\s+")[0]);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return uptimeSec;
  }

  /**
   * Lê /proc/stat e retorna um mapa "cpuN → valores da linha" para cada núcleo.
   *
   * Formato de /proc/stat:
   *   cpu  12345 67 8901 ...  ← soma de todos os núcleos (ignorada aqui)
   *   cpu0 1234  12 890  ...  ← núcleo 0
   *   cpu1 1100  10 800  ...  ← núcleo 1
   *   ...
   *
   * Usamos um TreeMap com comparador numérico para garantir a ordem cpu0, cpu1, cpu2...
   * Um HashMap comum não garante ordem de inserção, e sem o comparador numérico
   * a ordem lexicográfica colocaria cpu10 antes de cpu2.
   */
  private static Map<String, String> readStatKeyValues(Path statFile) throws IOException {
    Map<String, String> kv = new java.util.TreeMap<>((a, b) -> {
      // Extrai apenas os dígitos de cada chave ("cpu0" → 0, "cpu12" → 12) e compara.
      int na = Integer.parseInt(a.replaceAll("\\D+", ""));
      int nb = Integer.parseInt(b.replaceAll("\\D+", ""));
      return Integer.compare(na, nb);
    });

    try (BufferedReader reader = Files.newBufferedReader(statFile)) {
      // Filtra apenas linhas que começam com "cpu" seguido de um ou mais dígitos.
      // O regex "^\\p{Lower}{3}+\\d+.*" significa: começa com 3+ letras minúsculas
      // seguidas de 1+ dígitos. Isso captura "cpu0", "cpu1", etc., e exclui "cpu " (sem número).
      reader.lines().filter(line -> line.matches("^\\p{Lower}{3}+\\d+.*"))
      .forEach(new Consumer<String>() {
        @Override
        public void accept(String line) {
          // Divide em no máximo 2 partes: chave ("cpu0") e valor ("1234 12 890 ...").
          String[] line_parts = line.split("\\s+", 2);
          kv.put(line_parts[0], line_parts[1]);
        }
      });
    }

    return kv;
  }

  /**
   * Lê /proc/meminfo e retorna um mapa "campo → valor em kB".
   *
   * Formato de /proc/meminfo:
   *   MemTotal:       16384000 kB
   *   MemFree:         1234567 kB
   *   ...
   *
   * Campos relevantes para o TaskManager:
   *   MemTotal, MemFree, Buffers, Cached, SReclaimable → cálculo de memória usada
   *   MemAvailable → memória disponível para novos processos
   *   SwapTotal, SwapFree → uso de swap
   */
  @Override
  public Map<String, Long> memInfo() {
    Path memFile = Paths.get("/proc/meminfo");
    Map<String, Long> map = new HashMap<>();

    try (BufferedReader reader = Files.newBufferedReader(memFile)) {
      String line;
      while ((line = reader.readLine()) != null) {
        int idx = line.indexOf(':');
        if (idx > 0) {
          String key = line.substring(0, idx).trim();
          // O valor vem como "1234567 kB" — pegamos só o número e ignoramos " kB".
          String val = line.substring(idx + 1).trim().split("\\s+")[0];
          map.put(key, Long.parseLong(val));
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return map;
  }

  /**
   * Lê /proc/loadavg e retorna o load average dos últimos 1, 5 e 15 minutos.
   *
   * Formato de /proc/loadavg: "0.62 0.81 0.94 1/543 12345"
   *   - Campos 0, 1, 2: load average de 1min, 5min e 15min
   *   - Campo 3: "processos_rodando/total_de_processos"
   *   - Campo 4: PID do último processo criado
   *
   * Load average = média de processos prontos para CPU + processos em D (aguardando I/O).
   * Um valor acima do número de núcleos indica que o sistema está sobrecarregado.
   *
   * @return array [load1min, load5min, load15min], ou [0, 0, 0] em caso de erro
   */
  @Override
  public double[] loadAvg() {
    Path loadFile = Paths.get("/proc/loadavg");
    try (BufferedReader reader = Files.newBufferedReader(loadFile)) {
      String[] parts = reader.readLine().trim().split("\\s+");
      return new double[]{
        Double.parseDouble(parts[0]),  // média do último minuto
        Double.parseDouble(parts[1]),  // média dos últimos 5 minutos
        Double.parseDouble(parts[2])   // média dos últimos 15 minutos
      };
    } catch (IOException | NumberFormatException e) {
      e.printStackTrace();
      return new double[]{0, 0, 0};
    }
  }

  /**
   * Lê /proc/stat e retorna uma lista ordenada de arrays de ticks por núcleo.
   *
   * Cada array representa os contadores acumulados de CPU de um núcleo desde o boot:
   *   [user, nice, system, idle, iowait, irq, softirq, steal, guest, guest_nice]
   *
   * Os contadores são acumulados (crescem continuamente); para calcular o uso
   * atual, o MainFrame calcula o delta entre dois snapshots consecutivos:
   *   uso% = (delta_total - delta_idle) / delta_total * 100
   *
   * @return lista ordenada (cpu0, cpu1...) de arrays de ticks
   */
  @Override
  public List<long[]> cpu_infos() {
    Path statFile = Paths.get("/proc/stat");
    Map<String, String> kv;
    try {
      kv = readStatKeyValues(statFile);
    } catch (IOException e) {
      e.printStackTrace();
      return new ArrayList<>();
    }

    List<long[]> cpu_infoList = new ArrayList<>();

    // Para cada núcleo, converte a string de valores para um array de long.
    kv.forEach((chave, valor) -> {
      String[] valor_parts = valor.split("\\s+");
      long[] infos = new long[valor_parts.length];
      for (int i = 0; i < valor_parts.length; i++) {
        infos[i] = Long.parseLong(valor_parts[i]);
      }
      cpu_infoList.add(infos);
    });

    return cpu_infoList;
  }
}
