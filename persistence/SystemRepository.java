package persistence;

import java.util.List;
import java.util.Map;

import model.Processo;

/**
 * Interface que define o contrato para leitura de informações gerais do sistema.
 *
 * No diagrama UML, SystemRepository é uma interface implementada por
 * ProcfsSystemRepository. O TaskManagerService depende desta interface
 * (não da implementação concreta), seguindo o princípio DIP.
 *
 * Todos os dados vêm do pseudo-filesystem /proc, que o kernel Linux mantém
 * em memória e expõe como arquivos de texto para leitura em tempo real.
 */
public interface SystemRepository {

  /**
   * Conta o número de processos de usuário (Tasks) na lista fornecida.
   * @param tasks lista de processos retornada por ProcessRepository.readTask()
   * @return número de Tasks
   */
  int countTasks(List<Processo> tasks);

  /**
   * Soma o total de threads de todos os processos e kernel threads.
   * Cada Processo tem getThreads() que retorna quantas threads ele possui.
   * @param tasks  lista de processos de usuário
   * @param kthr   lista de kernel threads
   * @return total de threads no sistema
   */
  int countThreads(List<Processo> tasks, List<Processo> kthr);

  /**
   * Conta o número de kernel threads (Kthr) na lista fornecida.
   * @param kthr lista de kernel threads retornada por ProcessRepository.readKthr()
   * @return número de Kthrs
   */
  int countKthr(List<Processo> kthr);

  /**
   * Retorna o número de processos no estado 'R' (Running).
   * Atualmente não utilizado na lógica principal — a contagem de running
   * é feita diretamente na view filtrando a lista de tasks pelo estado.
   */
  int running();

  /**
   * Retorna o tempo em segundos desde o último boot do sistema.
   * Lido de /proc/uptime (primeiro valor da linha).
   * @return uptime em segundos (com decimais)
   */
  double uptime();

  /**
   * Retorna os contadores de uso de CPU por núcleo, lidos de /proc/stat.
   * Cada elemento da lista é um array com os ticks acumulados desde o boot:
   *   [user, nice, system, idle, iowait, irq, softirq, ...]
   * A lista é ordenada por número de núcleo (cpu0, cpu1, cpu2...).
   * Para calcular o uso ATUAL, é necessário comparar com a leitura anterior.
   * @return lista de arrays de ticks por núcleo
   */
  List<long[]> cpu_infos();

  /**
   * Retorna os campos de /proc/meminfo como mapa "nome_do_campo → valor em kB".
   * Inclui: MemTotal, MemFree, MemAvailable, Buffers, Cached, SReclaimable,
   *         SwapTotal, SwapFree, entre outros.
   * @return mapa com todos os campos de /proc/meminfo
   */
  Map<String, Long> memInfo();

  /**
   * Retorna o load average do sistema lido de /proc/loadavg.
   * Load average é a média de processos prontos para executar (ou esperando I/O)
   * nos últimos 1, 5 e 15 minutos.
   * @return array com 3 elementos: [média 1min, média 5min, média 15min]
   */
  double[] loadAvg();
}
