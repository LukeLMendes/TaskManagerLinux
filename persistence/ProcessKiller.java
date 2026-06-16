package persistence;

import exception.KillProcessException;
import exception.InvalidPidException;

/**
 * Interface que define o contrato para encerrar processos do sistema operacional.
 *
 * No diagrama UML, TaskManagerService depende desta interface (não da implementação
 * concreta), permitindo que a lógica de matar processos seja substituída sem
 * alterar a camada de serviço — útil, por exemplo, para testes ou para portar
 * o programa para outro sistema operacional.
 *
 * A implementação concreta atualmente usada é LinuxProcessKiller, que invoca
 * o comando nativo "kill" do Linux.
 */
public interface ProcessKiller {

    /**
     * Envia o sinal padrão de encerramento (SIGTERM) ao processo identificado por pid.
     *
     * SIGTERM pede educadamente que o processo encerre. O processo pode capturar
     * esse sinal e fazer uma finalização graciosa (fechar arquivos, liberar recursos).
     * Se o processo ignorar o sinal, é necessário usar SIGKILL via forceKill().
     *
     * @param pid identificador do processo a ser encerrado (deve ser > 0)
     * @throws InvalidPidException  se o PID for menor ou igual a zero
     * @throws KillProcessException se o sinal não puder ser enviado
     *                              (permissão negada, processo inexistente, etc.)
     */
    void killProcess(int pid) throws KillProcessException, InvalidPidException;
}
