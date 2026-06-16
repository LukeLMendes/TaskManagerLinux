package exception;

/**
 * Exceção lançada quando ocorre um erro ao acessar o sistema de arquivos /proc.
 *
 * O /proc é um sistema de arquivos virtual do Linux que expõe informações
 * do kernel em tempo real. Erros ao acessá-lo podem ocorrer por:
 *   - Permissão negada: tentar ler /proc/<pid>/io de um processo de outro usuário
 *     sem ser root (arquivo io exige CAP_SYS_PTRACE ou ser o dono do processo).
 *   - Processo encerrado durante a leitura: entre listar /proc e ler /proc/<pid>/status,
 *     o processo pode ter morrido — o arquivo desaparece (race condition esperada).
 *   - Arquivo inexistente: o campo solicitado não existe nesta versão do kernel.
 *
 * Lançada pelas implementações Procfs* do pacote persistence
 * (ProcfsProcessRepository, ProcfsIORepository, ProcfsSystemRepository)
 * quando um IOException não pode ser simplesmente ignorado.
 *
 * Estende DomainException para que a camada de serviço possa capturá-la
 * junto com as outras exceções de domínio com um único bloco catch(DomainException e).
 */
public class ProcfsAccessException extends DomainException {

    /**
     * Cria a exceção com uma mensagem descritiva.
     * Use quando o erro não foi causado por outra exceção.
     *
     * @param message texto explicando qual arquivo ou campo do /proc não pôde ser lido
     */
    public ProcfsAccessException(String message) {
        super(message);
    }

    /**
     * Cria a exceção encapsulando a causa original (normalmente um IOException).
     * Versão preferida porque preserva o stack trace original para depuração.
     *
     * @param message texto explicando qual arquivo do /proc gerou o erro
     * @param cause   IOException original lançado pela operação de leitura
     */
    public ProcfsAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
