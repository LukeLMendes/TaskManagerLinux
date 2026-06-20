package model;

public class Kthr extends Processo {
    private static final long serialVersionUID = 1L;

    private int kthreads;
    private long virt;
    private long res;
    private long shr;

    public Kthr() {
        virt = 0;
        res = 0;
        shr = 0;
    }

    public void setKthreads(int kthreads) { this.kthreads = kthreads; }

    public int getKthreads() { return kthreads; }
    public long getVirt() { return virt; }
    public long getRes() { return res; }
    public long getShr() { return shr; }

    @Override
    public String getTipo() { return "Kthr"; }
}
