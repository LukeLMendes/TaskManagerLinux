package persistence;

import exception.KillProcessException;
import exception.InvalidPidException;

public class LinuxProcessKiller implements ProcessKiller {

    private static final int SIGTERM = 15;
    private static final int SIGKILL = 9;

    @Override
    public void killProcess(int pid) throws KillProcessException, InvalidPidException {
        send(pid, SIGTERM);
    }

    public void forceKill(int pid) throws KillProcessException, InvalidPidException {
        send(pid, SIGKILL);
    }

    private void send(int pid, int signal)
            throws KillProcessException, InvalidPidException {

        if (pid <= 0) {
            throw new InvalidPidException(
                    "PID inválido: " + pid + ". Deve ser um número positivo.", pid);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "kill", "-" + signal, String.valueOf(pid));
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            int exit = proc.waitFor();
            if (exit != 0) {
                String errMsg = new String(proc.getInputStream().readAllBytes()).trim();
                throw new KillProcessException(
                        "kill -" + signal + " " + pid + " falhou: " + errMsg);
            }
        } catch (KillProcessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KillProcessException(
                    "Operação interrompida ao encerrar PID=" + pid, e);
        } catch (Exception e) {
            throw new KillProcessException(
                    "Erro inesperado ao encerrar PID=" + pid, e);
        }
    }
}
