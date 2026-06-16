package exception;

/**
 * Exceção lançada quando um PID fornecido é inválido antes mesmo de qualquer
 * operação no sistema operacional ser tentada.
 *
 * Um PID é considerado inválido neste projeto quando:
 *   - É menor ou igual a zero (PIDs válidos no Linux são sempre inteiros positivos).
 *
 * Lançada por LinuxProcessKiller.send() como primeira verificação,
 * evitando chamar o comando "kill" com um argumento que já sabemos ser inválido.
 *
 * O PID problemático fica armazenado no atributo pid e pode ser recuperado
 * via getPid() para exibir uma mensagem de erro mais informativa ao usuário.
 */
public class InvalidPidException extends DomainException {

    // Armazena o PID que causou o erro para facilitar a depuração e
    // a exibição de mensagens de erro mais específicas na interface.
    // Valor -1 indica que o PID não foi informado ao construtor.
    private final int pid;

    /**
     * Cria a exceção sem armazenar o PID específico.
     * Use quando o PID problemático não estiver disponível no ponto do erro.
     *
     * @param message texto explicando por que o PID é inválido
     */
    public InvalidPidException(String message) {
        super(message);
        this.pid = -1; // sentinela: indica "PID não informado"
    }

    /**
     * Cria a exceção armazenando o PID que causou o problema.
     * Versão preferida porque permite inspecionar o valor via getPid().
     *
     * @param message texto explicando por que o PID é inválido
     * @param pid     o valor do PID que foi rejeitado
     */
    public InvalidPidException(String message, int pid) {
        super(message);
        this.pid = pid;
    }

    /**
     * Cria a exceção encapsulando a causa original, sem PID específico.
     *
     * @param message texto explicando o erro
     * @param cause   exceção original que causou a invalidação do PID
     */
    public InvalidPidException(String message, Throwable cause) {
        super(message, cause);
        this.pid = -1;
    }

    /**
     * Retorna o PID que provocou a exceção.
     *
     * @return o valor do PID inválido, ou -1 se não foi informado ao construtor
     */
    public int getPid() {
        return pid;
    }
}
