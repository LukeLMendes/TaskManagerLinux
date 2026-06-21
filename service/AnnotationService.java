package service;

import persistence.AnnotationRepository;
import exception.SnapshotReadException;
import exception.SnapshotWriteException;

import java.util.HashMap;
import java.util.Map;

public class AnnotationService {

    private final Map<Integer, String> annotations = new HashMap<>();
    private final AnnotationRepository repository;

    public AnnotationService(AnnotationRepository repository) {
        this.repository = repository;
    }

    public void add(int pid, String text) {
        annotations.put(pid, text.trim());
    }

    public void remove(int pid) {
        annotations.remove(pid);
    }

    public String get(int pid) {
        return annotations.getOrDefault(pid, "");
    }

    public boolean has(int pid) {
        return annotations.containsKey(pid);
    }

    public Map<Integer, String> getAll() {
        return annotations;
    }

    public void load() throws SnapshotReadException {
        Map<Integer, String> loaded = repository.load();
        annotations.clear();
        annotations.putAll(loaded);
    }

    public void save() throws SnapshotWriteException {
        repository.save(annotations);
    }
}
