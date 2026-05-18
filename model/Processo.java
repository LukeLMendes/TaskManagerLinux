package model;

import java.util.ArrayList;
import java.util.List;

public abstract class Processo {
  private int pid;
  private int threads;
  private int pri;
  private int nice;
  private int ppid;
  private List<Integer> children;

  public Processo() {
    // Lista de PIDs filhos do processo, coletada via /proc/<pid>/task/<pid>/children
    children = new ArrayList<>();
  }

  public void setPid(int pid) {this.pid = pid;}
  public void setThreads(int threads) {this.threads = threads;}
  public void setPri(int pri) {this.pri = pri;}
  public void setNice (int nice) {this.nice = nice;}
  public void setPpid(int ppid) {this.ppid = ppid;}

  public void addChild(int child) {
    // Guarda o PID do filho; não garante que o filho ainda exista quando você for montar a árvore.
    children.add(child);
  }

  public int getPid() {return pid;}
  public int getThreads() {return threads;}
  public int getPri() {return pri;}
  public int getNice() {return nice;}
  public int getPpid() {return ppid;}

  public List<Integer> getChildren() {return children;}
}
