package exception;

public class ProcfsAccessException extends DomainException {

    public ProcfsAccessException(String message) {
        super(message);
    }

    public ProcfsAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
