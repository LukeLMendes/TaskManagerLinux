package exception;

/**
 * Exceção lançada quando ocorre um erro ao ler um SnapshotData do disco,
 * ou quando nenhum snapshot está disponível para leitura.
 *
 * Causas comuns:
 *   - Nenhum arquivo .dat encontrado no diretório de snapshots.
 *   - Arquivo corrompido ou incompleto.
 *   - A classe SnapshotData foi alterada e o serialVersionUID não bate
 *     com o que foi usado ao gravar (incompatibilidade de versão).
 *   - Permissão negada ao tentar abrir o arquivo.
 *
 * Lançada por FileSnapshotRepository.loadLatest() e loadAll().
 */
public class SnapshotReadException extends DomainException {

    /**
     * Cria a exceção encapsulando a causa original.
     * O parâmetro cause pode ser null quando o erro é "nenhum snapshot encontrado"
     * (situação esperada, não causada por outra exceção).
     *
     * @param message texto descrevendo onde e por que a leitura falhou
     * @param cause   IOException, ClassNotFoundException, ou null se não aplicável
     */
    public SnapshotReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
