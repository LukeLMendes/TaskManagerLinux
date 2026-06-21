package persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import model.Processo;
import model.Task;
import model.Kthr;
import model.ProcessoZumbi;

public class ProcfsProcessRepository implements ProcessRepository {

    private static final long CLK_TCK = 100;

    private final Map<Integer, long[]> prevTicks = new HashMap<>();

    private static Map<String, String> readStatusKeyValues(Path statusFile) throws IOException {
        Map<String, String> kv = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(statusFile)) {
            reader.lines().forEach(line -> {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String val = line.substring(idx + 1).trim();
                    kv.put(key, val);
                }
            });
        }
        return kv;
    }

    private static int firstInt(String value) {
        if (value == null) return 0;
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 0) return 0;
        return Integer.parseInt(parts[0]);
    }

    private static long firstLong(String value) {
        if (value == null) return 0;
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 0) return 0;
        return Long.parseLong(parts[0]);
    }

    private static String parseComm(String line) {
        int firstParen = line.indexOf('(');
        int lastParen  = line.lastIndexOf(')');
        if (firstParen < 0 || lastParen <= firstParen) return "";
        return line.substring(firstParen + 1, lastParen);
    }

    private static String[] parseStatLine(String line) {
        int lastParen = line.lastIndexOf(')');
        if (lastParen < 0 || lastParen + 2 >= line.length()) return new String[0];
        return line.substring(lastParen + 2).trim().split("\\s+");
    }

    private static boolean isKthreadProcess(Path procDir) {
        Path statusFile = procDir.resolve("status");
        try {
            Map<String, String> kv = readStatusKeyValues(statusFile);
            int kthread = firstInt(kv.get("Kthread"));
            return kthread != 0;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<Processo> readTask() {
        Path pathDir = Paths.get("/proc");
        List<Processo> list = new ArrayList<>();

        try (Stream<Path> procs = Files.list(pathDir)) {
            procs
                .filter(path -> Files.isDirectory(path))
                .filter(path -> path.getFileName().toString().matches("\\d+"))
                .filter(path -> !isKthreadProcess(path))
                .forEach(new Consumer<Path>() {
                    @Override
                    public void accept(Path path) {

                        Path statusFile = path.resolve("status");
                        Processo task;
                        try {
                            Map<String, String> kv = readStatusKeyValues(statusFile);

                            int pid     = firstInt(kv.get("Pid"));
                            int ppid    = firstInt(kv.get("PPid"));
                            int threads = firstInt(kv.get("Threads"));
                            char state  = kv.get("State").charAt(0);

                            String uids = kv.get("Uid");
                            int euid = Integer.parseInt(uids.split("\\s+")[1]);

                            task = (state == 'Z') ? new ProcessoZumbi() : new Task();

                            task.setPid(pid);
                            task.setPpid(ppid);
                            task.setThreads(threads);
                            task.setEUID(euid);
                            task.setState(state);

                            if (task instanceof Task t) {
                                long virt     = firstLong(kv.get("VmSize"));
                                long res      = firstLong(kv.get("VmRSS"));
                                long rssFile  = firstLong(kv.get("RssFile"));
                                long rssShmem = firstLong(kv.get("RssShmem"));
                                t.setVirt(virt);
                                t.setRes(res);
                                t.setShr(rssFile + rssShmem);
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }

                        Path statFile = path.resolve("stat");
                        try (BufferedReader reader = Files.newBufferedReader(statFile)) {
                            String line = reader.readLine();
                            if (line != null) {
                                task.setComm(parseComm(line));
                                String[] f = parseStatLine(line);
                                if (f.length > 16) {
                                    task.setPri(Integer.parseInt(f[15]));
                                    task.setNice(Integer.parseInt(f[16]));

                                    if (task instanceof Task t) {
                                        long utime = Long.parseLong(f[11]);
                                        long stime = Long.parseLong(f[12]);
                                        long currTicks = utime + stime;
                                        long[] prev = prevTicks.get(task.getPid());
                                        if (prev != null) {
                                            long delta = currTicks - prev[0];
                                            t.setCpuPct(Math.max(0, delta * 100.0 / CLK_TCK));
                                        }
                                        prevTicks.put(task.getPid(), new long[]{currTicks});
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        Path cmdlineFile = path.resolve("cmdline");
                        try {
                            byte[] bytes = Files.readAllBytes(cmdlineFile);
                            if (bytes.length > 0) {
                                task.setCmdline(new String(bytes).replace('\0', ' ').trim());
                            }
                        } catch (IOException e) {
                        }

                        Path childrenFile = path.resolve("task/" + task.getPid() + "/children");
                        try (BufferedReader reader = Files.newBufferedReader(childrenFile)) {
                            if (reader.read() != -1) {
                                String[] lines = reader.lines().toArray(String[]::new);
                                if (lines.length > 0) {
                                    String[] pids = lines[0].trim().split("\\s+");
                                    for (String pid : pids) {
                                        task.addChild(Integer.parseInt(pid));
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        list.add(task);
                    }
                });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return list;
    }

    public List<Processo> readKthr() {
        Path pathDir = Paths.get("/proc");
        List<Processo> list = new ArrayList<>();

        try (Stream<Path> procs = Files.list(pathDir)) {
            procs
                .filter(path -> Files.isDirectory(path))
                .filter(path -> path.getFileName().toString().matches("\\d+"))
                .filter(path -> isKthreadProcess(path))
                .forEach(new Consumer<Path>() {
                    @Override
                    public void accept(Path path) {

                        Kthr kthr = new Kthr();

                        Path statusFile = path.resolve("status");
                        try {
                            Map<String, String> kv = readStatusKeyValues(statusFile);

                            int pid      = firstInt(kv.get("Pid"));
                            int ppid     = firstInt(kv.get("PPid"));
                            int threads  = firstInt(kv.get("Threads"));
                            char state   = kv.get("State").charAt(0);

                            String uids = kv.get("Uid");
                            int euid = Integer.parseInt(uids.split("\\s+")[1]);

                            int kthreads = firstInt(kv.get("Kthreads"));

                            kthr.setPid(pid);
                            kthr.setPpid(ppid);
                            kthr.setThreads(threads);
                            kthr.setEUID(euid);
                            kthr.setState(state);
                            kthr.setKthreads(kthreads);

                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }

                        Path statFile = path.resolve("stat");
                        try (BufferedReader reader = Files.newBufferedReader(statFile)) {
                            String line = reader.readLine();
                            if (line != null) {
                                kthr.setComm(parseComm(line));
                                String[] f = parseStatLine(line);
                                if (f.length > 16) {
                                    kthr.setPri(Integer.parseInt(f[15]));
                                    kthr.setNice(Integer.parseInt(f[16]));
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        Path childrenFile = path.resolve("task/" + kthr.getPid() + "/children");
                        try (BufferedReader reader = Files.newBufferedReader(childrenFile)) {
                            if (reader.read() != -1) {
                                String[] lines = reader.lines().toArray(String[]::new);
                                if (lines.length > 0) {
                                    String[] pids = lines[0].trim().split("\\s+");
                                    for (String pid : pids) {
                                        kthr.addChild(Integer.parseInt(pid));
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        list.add(kthr);
                    }
                });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return list;
    }
}
