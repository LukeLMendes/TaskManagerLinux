package persistence;

import exception.KillProcessException;
import exception.InvalidPidException;

public interface ProcessKiller {
    void killProcess(int pid) throws KillProcessException, InvalidPidException;
}
