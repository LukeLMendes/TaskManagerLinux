package model;

public class Task extends Processo {
    private static final long serialVersionUID = 1L;

    private int kthreads;
    private long virt;
    private long res;
    private long shr;
    private double cpuPct;

    public Task() {
        kthreads = 0;
    }

    public void setVirt(long virt) { this.virt = virt; }
    public void setRes(long res) { this.res = res; }
    public void setShr(long shr) { this.shr = shr; }
    public void setCpuPct(double cpuPct) { this.cpuPct = cpuPct; }

    public int getKthreads() { return kthreads; }
    public long getVirt() { return virt; }
    public long getRes() { return res; }
    public long getShr() { return shr; }
    public double getCpuPct() { return cpuPct; }

    @Override
    public String getTipo() { return "Task"; }
}
