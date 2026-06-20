package persistence;

import model.SnapshotData;
import exception.SnapshotReadException;
import exception.SnapshotWriteException;

import java.util.List;

public interface SnapshotRepository {
    void save(SnapshotData data) throws SnapshotWriteException;
    SnapshotData loadLatest() throws SnapshotReadException;
    List<SnapshotData> loadAll() throws SnapshotReadException;
}
