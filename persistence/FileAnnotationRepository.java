package persistence;

import exception.SnapshotReadException;
import exception.SnapshotWriteException;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class FileAnnotationRepository implements AnnotationRepository {

    private static final String FILE_PATH = "annotations.dat";

    @Override
    @SuppressWarnings("unchecked")
    public Map<Integer, String> load() throws SnapshotReadException {
        Path path = Paths.get(FILE_PATH);
        if (!Files.exists(path)) return new HashMap<>();
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(path))) {
            return (Map<Integer, String>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new SnapshotReadException("Falha ao carregar anotações de " + FILE_PATH, e);
        }
    }

    @Override
    public void save(Map<Integer, String> annotations) throws SnapshotWriteException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                Files.newOutputStream(Paths.get(FILE_PATH)))) {
            oos.writeObject(new HashMap<>(annotations));
        } catch (IOException e) {
            throw new SnapshotWriteException("Falha ao salvar anotações em " + FILE_PATH, e);
        }
    }
}
