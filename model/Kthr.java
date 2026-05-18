package model;

public class Kthr extends Processo {
  private int kthreads;
  private long virt;
  private long res;
  private long shr;

  public Kthr() {
    // Para kthreads, normalmente não faz sentido medir VmSize/VmRSS/Rss... como em processos de usuário.
    // Aqui você zera para manter um estado consistente.
    virt = 0;
    res = 0;
    shr = 0;
  }

  public void setKthreads (int kthreads) {this.kthreads = kthreads;}

  public int getKthreads () {return kthreads;}
  public long getVirt () {return virt;}
  public long getRes () {return res;}
  public long getShr () {return shr;}
}
