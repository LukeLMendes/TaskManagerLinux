package exception;

/**
 * Classe base para todas as exceções de domínio do TaskManager.
 *
 * "Exceção de domínio" significa um erro relacionado às regras de negócio
 * da aplicação (ex: PID inválido, falha ao matar processo, erro de snapshot),
 * em oposição a erros de infraestrutura genéricos como NullPointerException.
 *
 * Ter uma classe base comum permite capturar qualquer erro de domínio
 * com um único bloco catch na camada de serviço ou na view:
 *
 *   try {
 *       killer.killProcess(pid);
 *   } catch (DomainException e) {
 *       mostrarErroNaTela(e.getMessage());
 *   }
 *
 * Extende RuntimeException (unchecked), então não obriga os chamadores a
 * declarar "throws DomainException" — mas as subclasses podem ser capturadas
 * quando necessário.
 */
public class DomainException extends RuntimeException {

    /**
     * Cria a exceção com uma mensagem descritiva do erro.
     *
     * @param message texto explicando o que deu errado (exibido em logs e na UI)
     */
    public DomainException(String message) {
        super(message);
    }

    /**
     * Cria a exceção encapsulando a causa original (ex: um IOException).
     *
     * @param message texto explicando o que deu errado
     * @param cause   exceção original que causou este erro (preservada para log/debug)
     */
    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
