package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe abstrata que representa qualquer entidade gerenciada pelo SO —
 * tanto processos de usuário (Task) quanto kernel threads (Kthr).
 *
 * É abstrata porque nunca faz sentido instanciar um "Processo genérico";
 * você sempre cria ou um Task ou um Kthr, que herdam desta classe.
 *
 * Implementa Serializable para que as subclasses possam ser gravadas em disco
 * pelo FileSnapshotRepository (via ObjectOutputStream).
 *
 * Todos os campos aqui são comuns às duas subclasses. Campos exclusivos de
 * cada subclasse ficam em Task.java ou Kthr.java.
 */
public abstract class Processo implements Serializable {

  // Versão de serialização — incrementar sempre que campos forem adicionados/removidos,
  // para que ObjectInputStream detecte incompatibilidade com snapshots antigos.
  // Histórico: 1L → campos iniciais; 2L → cmdline adicionado.
  private static final long serialVersionUID = 2L;

  // PID (Process ID): identificador único do processo no sistema operacional.
  // Lido do campo "Pid:" em /proc/<pid>/status.
  private int pid;

  // Número total de threads que este processo possui.
  // Lido do campo "Threads:" em /proc/<pid>/status.
  private int threads;

  // Prioridade de escalonamento do processo (quanto menor, mais prioritário).
  // Lido do campo 15 (0-indexed após o comm) em /proc/<pid>/stat.
  // Valores típicos: de -100 (tempo real) a 39 (muito baixa prioridade).
  private int pri;

  // Nice value: ajuste manual de prioridade feito pelo usuário/sistema.
  // Lido do campo 16 (0-indexed após o comm) em /proc/<pid>/stat.
  // Valores de -20 (mais prioritário) a +19 (menos prioritário). Padrão = 0.
  private int nice;

  // PPID (Parent Process ID): PID do processo pai (quem criou este processo).
  // Lido do campo "PPid:" em /proc/<pid>/status.
  private int ppid;

  // EUID (Effective User ID): ID numérico do usuário "efetivo" do processo.
  // "Efetivo" pode diferir do real quando o processo usa setuid (ex: sudo).
  // Lido do campo "Uid:" em /proc/<pid>/status (segunda coluna dos 4 valores).
  private int euid;

  // Estado atual do processo: um único caractere.
  // Lido do campo "State:" em /proc/<pid>/status.
  // Valores possíveis:
  //   'R' = Running       (em execução ou pronto para executar)
  //   'S' = Sleeping      (aguardando evento; estado mais comum)
  //   'D' = Disk sleep    (aguardando I/O; não pode ser interrompido)
  //   'Z' = Zombie        (processo encerrado, aguardando o pai coletar o status)
  //   'T' = Stopped       (pausado por SIGSTOP ou por um debugger)
  //   'I' = Idle          (kernel thread ocioso)
  private char state;

  // Nome curto do processo (comm = command name), extraído de /proc/<pid>/stat.
  // Fica entre parênteses na primeira linha: "1234 (bash) S ..."
  // Limitado a 15 caracteres pelo kernel; pode conter espaços.
  private String comm;

  // Linha de comando completa do processo, lida de /proc/<pid>/cmdline.
  // Os argumentos são separados por '\0' no arquivo; aqui são convertidos para espaços.
  // Exemplos: "/usr/lib/firefox/firefox", "/usr/bin/python3 script.py --arg"
  // Vazio para kernel threads (que não têm cmdline) e para processos zumbis.
  private String cmdline = "";

  // Lista de PIDs dos processos filhos diretos deste processo.
  // Coletada de /proc/<pid>/task/<pid>/children.
  // Usada pelo ProcsTree para montar a árvore hierárquica de processos.
  private List<Integer> children;

  public Processo() {
    // Inicializa a lista de filhos vazia; filhos são adicionados depois via addChild().
    children = new ArrayList<>();
  }

  // -------------------------------------------------------------------------
  // Setters — chamados pelo ProcfsProcessRepository ao popular o objeto
  // -------------------------------------------------------------------------

  /** Define o PID do processo. */
  public void setPid(int pid) {this.pid = pid;}

  /** Define o número de threads. */
  public void setThreads(int threads) {this.threads = threads;}

  /** Define a prioridade de escalonamento. */
  public void setPri(int pri) {this.pri = pri;}

  /** Define o nice value (-20 a +19). */
  public void setNice (int nice) {this.nice = nice;}

  /** Define o PID do processo pai. */
  public void setPpid(int ppid) {this.ppid = ppid;}

  /** Define o estado do processo ('R', 'S', 'D', 'Z', 'T', 'I'). */
  public void setState(char state) {this.state = state;}

  /** Define o EUID (effective user ID). */
  public void setEUID(int euid) {this.euid = euid;}

  /** Define o nome curto do processo (comm), extraído de /proc/<pid>/stat. */
  public void setComm(String comm) {this.comm = comm;}

  /**
   * Define a linha de comando completa do processo, lida de /proc/<pid>/cmdline.
   * Deve ser chamado com os '\0' do arquivo já substituídos por espaços e com trim().
   */
  public void setCmdline(String cmdline) {this.cmdline = cmdline;}

  /**
   * Adiciona um PID filho à lista de filhos deste processo.
   * Não verifica se o filho ainda existe — a árvore pode ter "fantasmas"
   * se um filho morrer entre a leitura do arquivo children e a montagem da árvore.
   */
  public void addChild(int child) {
    children.add(child);
  }

  // -------------------------------------------------------------------------
  // Getters — usados pela view e pelo serviço para ler os dados coletados
  // -------------------------------------------------------------------------

  /** Retorna o PID do processo. */
  public int getPid() {return pid;}

  /** Retorna o número de threads do processo. */
  public int getThreads() {return threads;}

  /** Retorna a prioridade de escalonamento. */
  public int getPri() {return pri;}

  /** Retorna o nice value. */
  public int getNice() {return nice;}

  /** Retorna o PID do processo pai. */
  public int getPpid() {return ppid;}

  /** Retorna o estado do processo como caractere ('R', 'S', etc.). */
  public char getState() {return state;}

  /** Retorna o EUID (effective user ID). */
  public int getEUID() {return euid;}

  /**
   * Retorna o nome curto do processo (comm), de no máximo 15 chars.
   * Nunca retorna null: se comm não foi preenchido, retorna string vazia.
   */
  public String getComm() {return comm != null ? comm : "";}

  /**
   * Retorna a linha de comando completa do processo.
   * Para processos de usuário: o que foi passado para exec(), com argumentos.
   * Para kernel threads: string vazia (não têm cmdline).
   */
  public String getCmdline() {return cmdline != null ? cmdline : "";}

  /** Retorna a lista de PIDs dos processos filhos. */
  public List<Integer> getChildren() {return children;}
}
