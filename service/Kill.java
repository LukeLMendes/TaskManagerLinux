package service;

import persistence.ProcessKiller;
import exception.KillProcessException;
import exception.InvalidPidException;

public class Kill {

    private static final long WAIT_MS = 3_000;

    private final ProcessKiller killer;

    public Kill(ProcessKiller killer) {
        this.killer = killer;
    }

    public void kill(int pid) throws KillProcessException, InvalidPidException {
        killer.killProcess(pid);
    }

    public void forceKillIfNeeded(int pid)
            throws KillProcessException, InvalidPidException {

        killer.killProcess(pid);

        try {
            Thread.sleep(WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (isAlive(pid)) {
            if (killer instanceof persistence.LinuxProcessKiller) {
                ((persistence.LinuxProcessKiller) killer).forceKill(pid);
            } else {
                throw new KillProcessException(
                        "Processo PID=" + pid +
                        " nao respondeu ao SIGTERM e a implementacao atual" +
                        " nao suporta forceKill.");
            }
        }
    }

    public boolean isAlive(int pid) {
        return java.nio.file.Files.exists(java.nio.file.Paths.get("/proc/" + pid));
    }
}
