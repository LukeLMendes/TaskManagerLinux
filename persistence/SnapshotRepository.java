package persistence;

import model.SnapshotData;
import exception.SnapshotReadException;
import exception.SnapshotWriteException;

import java.util.List;

/**
 * Interface que define o contrato para salvar e recuperar snapshots do sistema.
 *
 * No diagrama UML, TaskManagerService depende desta interface (não da implementação
 * concreta), o que permite trocar o mecanismo de persistência sem alterar a camada
 * de serviço — por exemplo, substituir FileSnapshotRepository por um que grave
 * em banco de dados, sem mudar nenhuma outra classe.
 *
 * A implementação concreta atualmente usada é FileSnapshotRepository.
 */
public interface SnapshotRepository {

    /**
     * Persiste um snapshot do estado atual do sistema em algum meio de armazenamento.
     *
     * @param data objeto SnapshotData contendo tasks, kthreads, SystemInfos e IOStats
     * @throws SnapshotWriteException se ocorrer qualquer erro ao gravar
     *         (ex: sem espaço em disco, permissão negada no diretório)
     */
    void save(SnapshotData data) throws SnapshotWriteException;

    /**
     * Recupera o snapshot mais recente disponível no armazenamento.
     *
     * @return o SnapshotData com o maior timestamp encontrado
     * @throws SnapshotReadException se não houver nenhum snapshot salvo,
     *         ou se ocorrer erro ao ler o arquivo (corrompido, permissão, etc.)
     */
    SnapshotData loadLatest() throws SnapshotReadException;

    /**
     * Recupera todos os snapshots salvos, ordenados do mais antigo ao mais recente.
     *
     * @return lista de SnapshotData; pode ser vazia se nenhum snapshot existir ainda
     * @throws SnapshotReadException se ocorrer erro ao listar ou deserializar os arquivos
     */
    List<SnapshotData> loadAll() throws SnapshotReadException;
}
