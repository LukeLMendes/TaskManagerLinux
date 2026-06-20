package exception;

public class InvalidPidException extends DomainException {

    private final int pid;

    public InvalidPidException(String message) {
        super(message);
        this.pid = -1;
    }

    public InvalidPidException(String message, int pid) {
        super(message);
        this.pid = pid;
    }

    public InvalidPidException(String message, Throwable cause) {
        super(message, cause);
        this.pid = -1;
    }

    public int getPid() {
        return pid;
    }
}
