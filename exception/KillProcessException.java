package exception;

public class KillProcessException extends DomainException {

    public KillProcessException(String message) {
        super(message);
    }

    public KillProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
