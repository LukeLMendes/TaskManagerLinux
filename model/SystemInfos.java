package model;

import java.util.List;

public class SystemInfos {
  double mem_total;
  double mem_avaiable;

  List<long[]> cpu_infos;

  double uptime;
  long num_tasks;
  long num_threads;
  long num_kthreads;

  public void setMemTotal(double mem_total) {this.mem_total = mem_total;}
  public void setMemAvailable(double mem_avaiable) {this.mem_avaiable = mem_avaiable;}
  public void setCpuInfos(List<long[]> cpu_infos) {this.cpu_infos = cpu_infos;}
  public void setUptime(double uptime) {this.uptime = uptime;}
  public void setNumTasks(long num_tasks) {this.num_tasks = num_tasks;}
  public void setNumThreads(long num_threads) {this.num_threads = num_threads;}
  public void setNumKthreads(long num_kthreads) {this.num_kthreads = num_kthreads;}

  public double getMemTotal() {return mem_total;}
  public double getMemAvaialable() {return mem_avaiable;}
  public List<long[]> getCpuInfos() {return cpu_infos;}
  public double getUptime() {return uptime;}
  public long getNumTasks() {return num_tasks;}
  public long getNumThreads() {return num_threads;}
  public long getNumKthreads() {return num_kthreads;}
}
