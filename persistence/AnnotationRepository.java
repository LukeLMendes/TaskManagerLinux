package persistence;

import exception.SnapshotReadException;
import exception.SnapshotWriteException;

import java.util.Map;

public interface AnnotationRepository {
    Map<Integer, String> load() throws SnapshotReadException;
    void save(Map<Integer, String> annotations) throws SnapshotWriteException;
}
