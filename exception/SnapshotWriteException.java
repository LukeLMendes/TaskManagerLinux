package exception;

public class SnapshotWriteException extends DomainException {

    public SnapshotWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
