package exception;

/**
 * Exceção lançada quando ocorre um erro ao gravar um SnapshotData em disco.
 *
 * Causas comuns:
 *   - Sem espaço em disco.
 *   - Permissão negada no diretório de snapshots.
 *   - Erro de serialização (ex: algum objeto na lista de Processos
 *     não implementa Serializable).
 *
 * Lançada por FileSnapshotRepository.save() e deve ser tratada
 * pela camada de serviço ou pela view (exibindo aviso ao usuário).
 */
public class SnapshotWriteException extends DomainException {

    /**
     * Cria a exceção encapsulando a causa original.
     *
     * @param message texto descrevendo onde e por que a gravação falhou
     * @param cause   IOException ou outra exceção original que causou o erro
     */
    public SnapshotWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
