package model;

public class Task extends Processo {
  private int kthreads;
  private long virt;
  private long res;
  private long shr;

  public Task() {
    // Task é “processo de usuário” no seu modelo, então kthreads = 0 (não é kernel thread).
    kthreads = 0;
  }

  public void setVirt(long virt) {this.virt = virt;}
  public void setRes(long res) {this.res = res;}
  public void setShr(long shr) {this.shr = shr;}

  public int getKthreads () {return kthreads;}
  public long getVirt () {return virt;}
  public long getRes () {return res;}
  public long getShr () {return shr;}
}
