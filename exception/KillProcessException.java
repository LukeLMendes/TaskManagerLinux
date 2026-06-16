package exception;

/**
 * Exceção lançada quando uma tentativa de encerrar um processo falha.
 *
 * Causas mais comuns no Linux:
 *   - Permissão negada: o usuário atual não tem privilégio para matar
 *     um processo de outro usuário (seria necessário ser root).
 *   - Processo já encerrado: o processo morreu entre o momento em que
 *     foi listado e o momento em que o sinal foi enviado (race condition comum).
 *   - Erro ao iniciar o comando: o executável "kill" não foi encontrado
 *     ou houve falha ao criar o processo filho.
 *
 * Lançada por LinuxProcessKiller e tratada por service.Kill,
 * que pode repassá-la para a camada de view exibir ao usuário.
 */
public class KillProcessException extends DomainException {

    /**
     * Cria a exceção com uma mensagem descritiva do erro.
     *
     * @param message texto explicando por que o processo não pôde ser encerrado
     */
    public KillProcessException(String message) {
        super(message);
    }

    /**
     * Cria a exceção encapsulando a causa original.
     *
     * @param message texto explicando o erro
     * @param cause   exceção original (ex: IOException ao executar o comando "kill")
     */
    public KillProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
