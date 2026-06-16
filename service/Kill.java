package service;

import persistence.ProcessKiller;
import exception.KillProcessException;
import exception.InvalidPidException;

/**
 * Orquestrador de encerramento de processos na camada de serviço.
 *
 * Separa a lógica de "quando e como matar" (responsabilidade desta classe)
 * da lógica de "como executar o comando no SO" (responsabilidade de ProcessKiller).
 *
 * Recebe ProcessKiller por injeção de dependência no construtor, o que garante
 * que esta classe depende da interface — não da implementação concreta —
 * exatamente como está no diagrama UML.
 *
 * Fluxo de encerramento gracioso:
 *   1. Envia SIGTERM (pede ao processo que encerre sozinho).
 *   2. Aguarda WAIT_MS milissegundos.
 *   3. Se o processo ainda estiver em /proc, envia SIGKILL (força o kernel a matar).
 */
public class Kill {

    // Tempo máximo em milissegundos que esta classe aguarda após enviar SIGTERM
    // antes de verificar se o processo ainda está vivo e, se sim, forçar SIGKILL.
    // 3000 ms = 3 segundos é um valor comum em gerenciadores de tarefas.
    private static final long WAIT_MS = 3_000;

    // Referência à implementação de ProcessKiller injetada no construtor.
    // Declarada como final porque não muda após a construção do objeto.
    // O tipo é a interface, não a classe concreta — princípio da inversão de dependência.
    private final ProcessKiller killer;

    /**
     * Construtor: recebe a implementação de ProcessKiller a ser usada.
     *
     * @param killer objeto responsável por executar o comando "kill" no sistema operacional
     */
    public Kill(ProcessKiller killer) {
        this.killer = killer;
    }

    /**
     * Encerra o processo de forma graciosa enviando apenas SIGTERM.
     *
     * Não aguarda nem verifica se o processo realmente encerrou.
     * Use forceKillIfNeeded() se quiser garantir o encerramento.
     *
     * @param pid PID do processo que deve ser encerrado
     * @throws InvalidPidException  se o PID for inválido (pid <= 0)
     * @throws KillProcessException se o sinal não puder ser enviado
     */
    public void kill(int pid) throws KillProcessException, InvalidPidException {
        // Delega diretamente para a implementação de ProcessKiller.
        killer.killProcess(pid);
    }

    /**
     * Tenta encerrar o processo graciosamente (SIGTERM) e, se necessário,
     * força o encerramento (SIGKILL) após WAIT_MS milissegundos.
     *
     * Etapas:
     *   1. Envia SIGTERM via killer.killProcess().
     *   2. Dorme WAIT_MS ms para dar tempo ao processo de encerrar sozinho.
     *   3. Verifica se o diretório /proc/<pid> ainda existe.
     *   4. Se o processo ainda estiver vivo e killer for LinuxProcessKiller,
     *      chama forceKill() que envia SIGKILL.
     *   5. Se killer não suportar forceKill(), lança KillProcessException.
     *
     * @param pid PID do processo alvo
     * @throws InvalidPidException  se o PID for inválido
     * @throws KillProcessException se nenhum dos sinais conseguir encerrar o processo
     *         ou se a implementação concreta não suportar forceKill
     */
    public void forceKillIfNeeded(int pid)
            throws KillProcessException, InvalidPidException {

        // Passo 1: tenta encerramento gracioso.
        killer.killProcess(pid);

        // Passo 2: aguarda o processo ter tempo de encerrar.
        try {
            Thread.sleep(WAIT_MS);
        } catch (InterruptedException e) {
            // Restaura o flag de interrupção para que chamadores acima possam detectá-lo.
            Thread.currentThread().interrupt();
        }

        // Passo 3: verifica se o processo ainda está rodando.
        if (isAlive(pid)) {

            // Passo 4: tenta SIGKILL se a implementação concreta suportar.
            // O instanceof é necessário porque a interface ProcessKiller não declara
            // forceKill() — esse método é específico de LinuxProcessKiller.
            if (killer instanceof persistence.LinuxProcessKiller) {
                ((persistence.LinuxProcessKiller) killer).forceKill(pid);
            } else {
                // Passo 5: implementação sem suporte a SIGKILL — informa o chamador.
                throw new KillProcessException(
                        "Processo PID=" + pid +
                        " nao respondeu ao SIGTERM e a implementacao atual" +
                        " nao suporta forceKill.");
            }
        }
        // Se isAlive() retornar false, o processo já encerrou — nada mais a fazer.
    }

    /**
     * Verifica se um processo ainda está ativo consultando o sistema de arquivos /proc.
     *
     * No Linux, cada processo em execução tem um diretório em /proc/<pid>.
     * Quando o processo termina, o kernel remove esse diretório automaticamente.
     * Verificar a existência do diretório é, portanto, uma forma confiável e
     * eficiente de saber se o processo ainda está vivo.
     *
     * @param pid PID do processo a verificar
     * @return true se /proc/<pid> existir (processo ainda ativo), false caso contrário
     */
    public boolean isAlive(int pid) {
        return java.nio.file.Files.exists(java.nio.file.Paths.get("/proc/" + pid));
    }
}
