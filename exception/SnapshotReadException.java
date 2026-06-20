package exception;

public class SnapshotReadException extends DomainException {

    public SnapshotReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
