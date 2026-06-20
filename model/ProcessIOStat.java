package model;

import java.io.Serializable;

public class ProcessIOStat implements Serializable {
    int pid;
    int euid;
    double readBytes;
    double writeBytes;

    public void setPID(int pid) { this.pid = pid; }
    public void setEUID(int euid) { this.euid = euid; }
    public void setRead(double readBytes) { this.readBytes = readBytes; }
    public void setWrite(double writeBytes) { this.writeBytes = writeBytes; }

    public int getPID() { return pid; }
    public int getEUID() { return euid; }
    public double getRead() { return readBytes; }
    public double getWrite() { return writeBytes; }
}
