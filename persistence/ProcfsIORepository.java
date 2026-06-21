package persistence;

import model.Task;
import model.Kthr;
import model.ProcessIOStat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;


public class ProcfsIORepository implements IORepository {

    private static Map<String, String> readIOKeyValues(Path ioFile) throws IOException {
        Map<String, String> kv = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(ioFile)) {
            try {
                reader.lines().forEach(line -> {
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String val = line.substring(idx + 1).trim();
                        kv.put(key, val);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
        return kv;
    }

    private static long firstLong(String value) {
        if (value == null) return 0;
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 0) return 0;
        return Long.parseLong(parts[0]);
    }

    @Override
    public List<ProcessIOStat> readTaskIO(List<Task> tasks) {
        List<ProcessIOStat> processIO_list = new java.util.ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            ProcessIOStat processIO = new ProcessIOStat();
            if (tasks.get(i).getEUID() >= 1000) {
                Path ioFile = Paths.get("/proc" + "/" + tasks.get(i).getPid() + "/io");
                try {
                    Map<String, String> kv = readIOKeyValues(ioFile);
                    long readBytes  = firstLong(kv.get("read_bytes"));
                    long writeBytes = firstLong(kv.get("write_bytes"));
                    processIO.setPID(tasks.get(i).getPid());
                    processIO.setEUID(tasks.get(i).getEUID());
                    processIO.setRead(readBytes);
                    processIO.setWrite(writeBytes);
                } catch (IOException e) {
                    processIO.setPID(tasks.get(i).getPid());
                    processIO.setRead(-1);
                    processIO.setWrite(-1);
                }
            } else {
                processIO.setPID(tasks.get(i).getPid());
                processIO.setRead(-1);
                processIO.setWrite(-1);
            }
            processIO_list.add(processIO);
        }
        return processIO_list;
    }

    @Override
    public List<ProcessIOStat> readKthrIO(List<Kthr> kthrs) {
        List<ProcessIOStat> processIO_list = new java.util.ArrayList<>();
        for (int i = 0; i < kthrs.size(); i++) {
            ProcessIOStat processIO = new ProcessIOStat();
            if (kthrs.get(i).getEUID() >= 1000) {
                Path ioFile = Paths.get("/proc" + "/" + kthrs.get(i).getPid() + "/io");
                try {
                    Map<String, String> kv = readIOKeyValues(ioFile);
                    long readBytes  = firstLong(kv.get("read_bytes"));
                    long writeBytes = firstLong(kv.get("write_bytes"));
                    processIO.setPID(kthrs.get(i).getPid());
                    processIO.setEUID(kthrs.get(i).getEUID());
                    processIO.setRead(readBytes);
                    processIO.setWrite(writeBytes);
                } catch (IOException e) {
                    processIO.setPID(kthrs.get(i).getPid());
                    processIO.setRead(-1);
                    processIO.setWrite(-1);
                }
            } else {
                processIO.setPID(kthrs.get(i).getPid());
                processIO.setRead(-1);
                processIO.setWrite(-1);
            }
            processIO_list.add(processIO);
        }
        return processIO_list;
    }
}
