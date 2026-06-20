package exception;

public class ProcessoNaoEncontradoException extends DomainException {

    public ProcessoNaoEncontradoException(int pid) {
        super("Processo com PID " + pid + " não encontrado no relatório.");
    }
}
