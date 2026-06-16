package persistence;

import exception.KillProcessException;
import exception.InvalidPidException;

/**
 * Implementação de ProcessKiller para sistemas Linux.
 *
 * Usa o comando nativo "kill" do Linux (via ProcessBuilder) para enviar
 * sinais POSIX aos processos. Esta classe apenas executa o comando —
 * a lógica de decisão (quando tentar SIGTERM, quando forçar SIGKILL)
 * fica na camada de serviço, na classe service.Kill.
 *
 * Sinais usados:
 *   SIGTERM (15) — pede ao processo que encerre de forma graciosa.
 *                  O processo pode capturar e ignorar este sinal.
 *   SIGKILL  (9) — força o encerramento imediato pelo kernel.
 *                  Não pode ser capturado nem ignorado pelo processo.
 */
public class LinuxProcessKiller implements ProcessKiller {

    // Número do sinal POSIX SIGTERM (encerramento gracioso).
    // Equivale a rodar "kill -15 <pid>" no terminal.
    private static final int SIGTERM = 15;

    // Número do sinal POSIX SIGKILL (encerramento forçado pelo kernel).
    // Equivale a rodar "kill -9 <pid>" no terminal.
    private static final int SIGKILL = 9;

    /**
     * Envia SIGTERM ao processo identificado por pid.
     * Implementa o método obrigatório da interface ProcessKiller.
     *
     * Equivale a executar: kill -15 <pid>
     *
     * @param pid identificador do processo alvo (deve ser > 0)
     * @throws InvalidPidException  se pid <= 0
     * @throws KillProcessException se o comando "kill" retornar código de erro
     *         (processo não encontrado, permissão negada, etc.)
     */
    @Override
    public void killProcess(int pid) throws KillProcessException, InvalidPidException {
        send(pid, SIGTERM);
    }

    /**
     * Envia SIGKILL ao processo identificado por pid.
     * Método adicional (além da interface) para uso quando SIGTERM não basta.
     *
     * Equivale a executar: kill -9 <pid>
     * Deve ser chamado por service.Kill.forceKillIfNeeded() quando o processo
     * não responder ao SIGTERM dentro do tempo limite.
     *
     * @param pid identificador do processo alvo (deve ser > 0)
     * @throws InvalidPidException  se pid <= 0
     * @throws KillProcessException se o comando falhar
     */
    public void forceKill(int pid) throws KillProcessException, InvalidPidException {
        send(pid, SIGKILL);
    }

    // -------------------------------------------------------------------------
    // Método auxiliar privado — contém a lógica comum a killProcess e forceKill.
    // -------------------------------------------------------------------------

    /**
     * Executa "kill -<signal> <pid>" como processo filho do sistema operacional
     * e verifica se o comando foi bem-sucedido pelo código de saída.
     *
     * @param pid    PID do processo que receberá o sinal
     * @param signal número do sinal POSIX a enviar (SIGTERM=15 ou SIGKILL=9)
     * @throws InvalidPidException  se pid <= 0 (verificado antes de qualquer syscall)
     * @throws KillProcessException se o processo filho "kill" retornar exit code != 0,
     *         ou se ocorrer erro ao iniciar/aguardar o processo filho
     */
    private void send(int pid, int signal)
            throws KillProcessException, InvalidPidException {

        // Valida o PID antes de tentar qualquer operação no sistema operacional.
        // PIDs válidos no Linux são sempre inteiros positivos.
        if (pid <= 0) {
            throw new InvalidPidException(
                    "PID inválido: " + pid + ". Deve ser um número positivo.", pid);
        }

        try {
            // ProcessBuilder monta o comando como lista de strings.
            // Equivale a: kill -<signal> <pid>
            // redirectErrorStream(true) faz com que stderr seja mesclado ao stdout,
            // permitindo capturar mensagens de erro do comando "kill" em um único stream.
            ProcessBuilder pb = new ProcessBuilder(
                    "kill", "-" + signal, String.valueOf(pid));
            pb.redirectErrorStream(true);

            // Inicia o processo filho (o comando "kill") e aguarda sua conclusão.
            Process proc = pb.start();
            int exit = proc.waitFor(); // bloqueia até o processo filho terminar

            // Exit code 0 significa sucesso. Qualquer outro valor indica falha.
            if (exit != 0) {
                // Lê a mensagem de erro emitida pelo comando "kill" no terminal.
                String errMsg = new String(proc.getInputStream().readAllBytes()).trim();
                throw new KillProcessException(
                        "kill -" + signal + " " + pid + " falhou: " + errMsg);
            }

        } catch (KillProcessException e) {
            // Re-lança sem encapsular para não perder a mensagem original.
            throw e;
        } catch (InterruptedException e) {
            // A thread foi interrompida enquanto aguardava o processo filho.
            // Boa prática: restaurar o flag de interrupção antes de lançar outra exceção.
            Thread.currentThread().interrupt();
            throw new KillProcessException(
                    "Operação interrompida ao encerrar PID=" + pid, e);
        } catch (Exception e) {
            // Captura qualquer outro erro inesperado (ex: comando "kill" não encontrado).
            throw new KillProcessException(
                    "Erro inesperado ao encerrar PID=" + pid, e);
        }
    }
}
