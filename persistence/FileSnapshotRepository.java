package persistence;

import model.SnapshotData;
import exception.SnapshotReadException;
import exception.SnapshotWriteException;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementação de SnapshotRepository que persiste SnapshotData em arquivos
 * binários no disco usando serialização nativa do Java (ObjectOutputStream).
 *
 * Cada snapshot é salvo como um arquivo separado com nome no formato:
 *   snapshot_<timestamp>.dat
 * onde <timestamp> é o valor em milissegundos retornado por SnapshotData.getTimestamp().
 *
 * Os arquivos ficam no diretório configurado no construtor (padrão: "snapshots/").
 * A ordenação alfabética dos nomes coincide com a cronológica porque o timestamp
 * é sempre um número crescente, o que simplifica a busca pelo mais recente.
 */
public class FileSnapshotRepository implements SnapshotRepository {

    // Caminho do diretório onde os arquivos .dat serão salvos.
    // Declarado como final porque não muda após a construção do objeto.
    private final Path snapshotDir;

    /**
     * Construtor padrão: usa o subdiretório "snapshots/" relativo ao diretório
     * de trabalho atual (onde o programa foi iniciado).
     */
    public FileSnapshotRepository() {
        this.snapshotDir = Paths.get("snapshots");
    }

    /**
     * Construtor que permite configurar um diretório personalizado.
     *
     * @param dirPath caminho absoluto ou relativo do diretório de snapshots
     */
    public FileSnapshotRepository(String dirPath) {
        this.snapshotDir = Paths.get(dirPath);
    }

    /**
     * Serializa o SnapshotData e grava no disco como arquivo binário.
     *
     * O diretório é criado automaticamente se não existir.
     * O nome do arquivo inclui o timestamp para garantir unicidade
     * e facilitar a busca pelo snapshot mais recente.
     *
     * @param data snapshot a ser salvo
     * @throws SnapshotWriteException se falhar ao criar o diretório ou gravar o arquivo
     */
    @Override
    public void save(SnapshotData data) throws SnapshotWriteException {
        try {
            // Cria o diretório (e os pais necessários) caso ainda não existam.
            // Se já existir, não faz nada e não lança exceção.
            Files.createDirectories(snapshotDir);

            // Monta o nome do arquivo usando o timestamp do snapshot.
            // Ex: snapshot_1718400000000.dat
            String fileName = "snapshot_" + data.getTimestamp() + ".dat";
            Path filePath = snapshotDir.resolve(fileName);

            // ObjectOutputStream converte o objeto Java em bytes (serialização).
            // O bloco try-with-resources garante que o stream seja fechado mesmo
            // se ocorrer exceção durante a escrita.
            try (ObjectOutputStream oos =
                     new ObjectOutputStream(Files.newOutputStream(filePath))) {
                oos.writeObject(data);
            }

        } catch (IOException e) {
            // Encapsula o IOException em SnapshotWriteException para não vazar
            // detalhes de I/O para a camada de serviço.
            throw new SnapshotWriteException(
                    "Falha ao salvar snapshot em: " + snapshotDir, e);
        }
    }

    /**
     * Lê e retorna o snapshot salvo mais recentemente no diretório configurado.
     *
     * "Mais recente" é determinado pela ordenação alfabética dos nomes dos arquivos,
     * que coincide com a ordem cronológica porque o nome contém o timestamp numérico.
     *
     * @return o SnapshotData do arquivo com o maior timestamp
     * @throws SnapshotReadException se não houver nenhum arquivo ou falhar a leitura
     */
    @Override
    public SnapshotData loadLatest() throws SnapshotReadException {
        List<Path> files = listSnapshotFiles();

        // Se não há arquivos salvos, lança exceção imediatamente.
        if (files.isEmpty()) {
            throw new SnapshotReadException(
                    "Nenhum snapshot encontrado em: " + snapshotDir, null);
        }

        // listSnapshotFiles() retorna a lista ordenada do mais antigo ao mais recente.
        // O último elemento é, portanto, o mais recente.
        Path latest = files.get(files.size() - 1);
        return readFromFile(latest);
    }

    /**
     * Lê e retorna todos os snapshots salvos, do mais antigo ao mais recente.
     *
     * Útil para exibir um histórico de capturas ou comparar estados do sistema
     * em diferentes momentos.
     *
     * @return lista de SnapshotData; lista vazia se nenhum snapshot existir
     * @throws SnapshotReadException se falhar ao listar ou deserializar algum arquivo
     */
    @Override
    public List<SnapshotData> loadAll() throws SnapshotReadException {
        List<Path> files = listSnapshotFiles();
        List<SnapshotData> result = new ArrayList<>();

        // Itera sobre todos os arquivos encontrados e deserializa cada um.
        for (Path file : files) {
            result.add(readFromFile(file));
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Métodos auxiliares privados — usados internamente por save/load.
    // -------------------------------------------------------------------------

    /**
     * Lista todos os arquivos de snapshot no diretório, ordenados pelo nome
     * (que equivale à ordem cronológica, já que o nome contém o timestamp).
     *
     * Filtra apenas arquivos que comecem com "snapshot_" e terminem com ".dat"
     * para ignorar quaisquer outros arquivos que estejam no mesmo diretório.
     *
     * @return lista de Paths ordenada; lista vazia se o diretório não existir
     * @throws SnapshotReadException se ocorrer erro ao listar o diretório
     */
    private List<Path> listSnapshotFiles() throws SnapshotReadException {
        // Se o diretório não existe ainda, não há snapshots — retorna lista vazia.
        if (!Files.exists(snapshotDir)) {
            return new ArrayList<>();
        }

        try {
            return Files.list(snapshotDir)
                    // Mantém apenas arquivos com o padrão de nome esperado.
                    .filter(p -> p.getFileName().toString().startsWith("snapshot_")
                              && p.getFileName().toString().endsWith(".dat"))
                    // Ordena pelo nome (string), que equivale à ordem por timestamp.
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            throw new SnapshotReadException(
                    "Erro ao listar snapshots em: " + snapshotDir, e);
        }
    }

    /**
     * Abre o arquivo no caminho indicado e deserializa o objeto SnapshotData contido nele.
     *
     * ObjectInputStream faz o processo inverso ao ObjectOutputStream:
     * lê os bytes do arquivo e reconstrói o objeto Java original.
     *
     * @param path caminho do arquivo .dat a ser lido
     * @return o SnapshotData deserializado
     * @throws SnapshotReadException se o arquivo não existir, estiver corrompido
     *         ou a classe SnapshotData tiver mudado incompativelmente (serialVersionUID)
     */
    private SnapshotData readFromFile(Path path) throws SnapshotReadException {
        try (ObjectInputStream ois =
                 new ObjectInputStream(Files.newInputStream(path))) {
            // O cast para SnapshotData é seguro porque só salvamos objetos desse tipo.
            return (SnapshotData) ois.readObject();

        } catch (IOException | ClassNotFoundException e) {
            throw new SnapshotReadException(
                    "Falha ao ler snapshot: " + path, e);
        }
    }
}
