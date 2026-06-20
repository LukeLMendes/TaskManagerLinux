package persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import model.Processo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.io.BufferedReader;
import java.io.IOException;

public class ProcfsSystemRepository implements SystemRepository {

    @Override
    public int countTasks(List<Processo> tasks) {
        return tasks.size();
    }

    @Override
    public int countThreads(List<Processo> tasks, List<Processo> kthrs) {
        int threads = 0;
        for (Processo i : tasks) {
            threads = threads + i.getThreads();
        }
        for (Processo i : kthrs) {
            threads = threads + i.getThreads();
        }
        return threads;
    }

    @Override
    public int countKthr(List<Processo> kthrs) {
        return kthrs.size();
    }

    @Override
    public int running() {
        Path statFile = Paths.get("/proc/stat");
        try (BufferedReader reader = Files.newBufferedReader(statFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("procs_running")) {
                    return Integer.parseInt(line.split("\\s+")[1]);
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public double uptime() {
        Path uptimeFile = Paths.get("/proc/uptime");
        double uptimeSec = 0;
        try (BufferedReader reader = Files.newBufferedReader(uptimeFile)) {
            String line = reader.readLine();
            uptimeSec = Double.parseDouble(line.split("\\s+")[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return uptimeSec;
    }

    private static Map<String, String> readStatKeyValues(Path statFile) throws IOException {
        Map<String, String> kv = new java.util.TreeMap<>((a, b) -> {
            int na = Integer.parseInt(a.replaceAll("\\D+", ""));
            int nb = Integer.parseInt(b.replaceAll("\\D+", ""));
            return Integer.compare(na, nb);
        });

        try (BufferedReader reader = Files.newBufferedReader(statFile)) {
            reader.lines().filter(line -> line.matches("^\\p{Lower}{3}+\\d+.*"))
            .forEach(new Consumer<String>() {
                @Override
                public void accept(String line) {
                    String[] line_parts = line.split("\\s+", 2);
                    kv.put(line_parts[0], line_parts[1]);
                }
            });
        }

        return kv;
    }

    @Override
    public Map<String, Long> memInfo() {
        Path memFile = Paths.get("/proc/meminfo");
        Map<String, Long> map = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(memFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String val = line.substring(idx + 1).trim().split("\\s+")[0];
                    map.put(key, Long.parseLong(val));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return map;
    }

    @Override
    public double[] loadAvg() {
        Path loadFile = Paths.get("/proc/loadavg");
        try (BufferedReader reader = Files.newBufferedReader(loadFile)) {
            String[] parts = reader.readLine().trim().split("\\s+");
            return new double[]{
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2])
            };
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            return new double[]{0, 0, 0};
        }
    }

    @Override
    public List<long[]> cpu_infos() {
        Path statFile = Paths.get("/proc/stat");
        Map<String, String> kv;
        try {
            kv = readStatKeyValues(statFile);
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }

        List<long[]> cpu_infoList = new ArrayList<>();

        kv.forEach((chave, valor) -> {
            String[] valor_parts = valor.split("\\s+");
            long[] infos = new long[valor_parts.length];
            for (int i = 0; i < valor_parts.length; i++) {
                infos[i] = Long.parseLong(valor_parts[i]);
            }
            cpu_infoList.add(infos);
        });

        return cpu_infoList;
    }
}
