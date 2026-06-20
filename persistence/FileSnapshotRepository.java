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

public class FileSnapshotRepository implements SnapshotRepository {

    private final Path snapshotDir;

    public FileSnapshotRepository() {
        this.snapshotDir = Paths.get("snapshots");
    }

    public FileSnapshotRepository(String dirPath) {
        this.snapshotDir = Paths.get(dirPath);
    }

    @Override
    public void save(SnapshotData data) throws SnapshotWriteException {
        try {
            Files.createDirectories(snapshotDir);
            String fileName = "snapshot_" + data.getTimestamp() + ".dat";
            Path filePath = snapshotDir.resolve(fileName);
            try (ObjectOutputStream oos =
                     new ObjectOutputStream(Files.newOutputStream(filePath))) {
                oos.writeObject(data);
            }
        } catch (IOException e) {
            throw new SnapshotWriteException(
                    "Falha ao salvar snapshot em: " + snapshotDir, e);
        }
    }

    @Override
    public SnapshotData loadLatest() throws SnapshotReadException {
        List<Path> files = listSnapshotFiles();
        if (files.isEmpty()) {
            throw new SnapshotReadException(
                    "Nenhum snapshot encontrado em: " + snapshotDir, null);
        }
        Path latest = files.get(files.size() - 1);
        return readFromFile(latest);
    }

    @Override
    public List<SnapshotData> loadAll() throws SnapshotReadException {
        List<Path> files = listSnapshotFiles();
        List<SnapshotData> result = new ArrayList<>();
        for (Path file : files) {
            result.add(readFromFile(file));
        }
        return result;
    }

    private List<Path> listSnapshotFiles() throws SnapshotReadException {
        if (!Files.exists(snapshotDir)) {
            return new ArrayList<>();
        }
        try {
            return Files.list(snapshotDir)
                    .filter(p -> p.getFileName().toString().startsWith("snapshot_")
                              && p.getFileName().toString().endsWith(".dat"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new SnapshotReadException(
                    "Erro ao listar snapshots em: " + snapshotDir, e);
        }
    }

    private SnapshotData readFromFile(Path path) throws SnapshotReadException {
        try (ObjectInputStream ois =
                 new ObjectInputStream(Files.newInputStream(path))) {
            return (SnapshotData) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new SnapshotReadException(
                    "Falha ao ler snapshot: " + path, e);
        }
    }
}
