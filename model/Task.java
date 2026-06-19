package model;

/**
 * Representa um processo de usuário — o que o htop chama de "Task".
 *
 * Herda todos os campos comuns de Processo (pid, ppid, state, comm, etc.)
 * e acrescenta os dados que só fazem sentido para processos de usuário:
 * uso de memória virtual/residente/compartilhada e percentual de CPU.
 *
 * Kernel threads (Kthr) não têm memória mapeada visível da mesma forma,
 * por isso esses campos ficam aqui e não em Processo.
 *
 * serialVersionUID é necessário porque Task é Serializable (via Processo)
 * e pode ser gravada em disco pelo FileSnapshotRepository. Se a estrutura
 * da classe mudar, incremente este valor para evitar erros ao ler arquivos antigos.
 */
public class Task extends Processo {
  private static final long serialVersionUID = 1L;

  // Número de kernel threads associadas a este processo.
  // Mantido por compatibilidade com o modelo UML; na prática sempre 0 para Tasks.
  private int kthreads;

  // Memória virtual total reservada pelo processo em kB (VmSize em /proc/<pid>/status).
  // Inclui código, dados, bibliotecas, stack, mapeamentos anônimos e de arquivo.
  // Costuma ser muito maior que RES porque o SO reserva espaço virtual sem alocar
  // memória física de imediato (lazy allocation).
  private long virt;

  // Memória residente (RAM física realmente ocupada) em kB (VmRSS em /proc/<pid>/status).
  // É o valor mais relevante para saber quanto de RAM o processo está usando de verdade.
  // Inclui: páginas de código (RssFile) + memória anônima (RssAnon) + compartilhada (RssShmem).
  private long res;

  // Memória compartilhada em kB: páginas que podem ser lidas por outros processos.
  // Calculado como RssFile + RssShmem (ambos em /proc/<pid>/status).
  // Exemplos: bibliotecas .so carregadas em memória, segmentos de memória compartilhada.
  private long shr;

  // Percentual de CPU usado por este processo no último intervalo de 1 segundo.
  // Calculado em ProcfsProcessRepository como:
  //   cpuPct = (delta_utime + delta_stime) / CLK_TCK * 100
  // onde utime = ticks em modo usuário, stime = ticks em modo kernel (sys calls),
  // e CLK_TCK = 100 (resolução do relógio do kernel em ticks por segundo no Linux x86-64).
  // Pode ultrapassar 100% em processos multi-thread que usam vários núcleos ao mesmo tempo.
  // Fica em 0.0 no primeiro ciclo de leitura (não há leitura anterior para calcular o delta).
  private double cpuPct;

  public Task() {
    // Task é processo de usuário, não tem kernel threads próprias.
    kthreads = 0;
  }

  // -------------------------------------------------------------------------
  // Setters — chamados pelo ProcfsProcessRepository ao popular o objeto
  // -------------------------------------------------------------------------

  /** Define a memória virtual total em kB (VmSize). */
  public void setVirt(long virt) {this.virt = virt;}

  /** Define a memória residente (RAM física) em kB (VmRSS). */
  public void setRes(long res) {this.res = res;}

  /** Define a memória compartilhada em kB (RssFile + RssShmem). */
  public void setShr(long shr) {this.shr = shr;}

  /**
   * Define o percentual de CPU calculado pelo repositório.
   * Valor entre 0.0 e (100.0 * número de núcleos) para processos multi-thread.
   */
  public void setCpuPct(double cpuPct) {this.cpuPct = cpuPct;}

  // -------------------------------------------------------------------------
  // Getters — usados pela view (MainPanel) para exibir os dados
  // -------------------------------------------------------------------------

  /** Retorna o número de kernel threads (sempre 0 para Tasks). */
  public int getKthreads () {return kthreads;}

  /** Retorna a memória virtual total em kB. */
  public long getVirt () {return virt;}

  /** Retorna a memória residente (RAM física) em kB. */
  public long getRes () {return res;}

  /** Retorna a memória compartilhada em kB. */
  public long getShr () {return shr;}

  /** Retorna o percentual de uso de CPU no último segundo. */
  public double getCpuPct() {return cpuPct;}
}
