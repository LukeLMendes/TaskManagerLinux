import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;

//import java.util.List;
//import java.util.ArrayList;

class CPU {
	String model_name;
}

class MEM {
	int memTotal;
	int memFree;
}

public class TaskManager {
	public static void main(String[] args) {

		// dados da CPU
		try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("model name")) {
					CPU cpu = new CPU();
					cpu.model_name = line.split(":")[1].trim();
					System.out.println("CPU Model: " + cpu.model_name);
					break; // Para ler apenas o modelo da CPU, podemos sair do loop após encontrar a
									// primeira ocorrência
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// dados da memoria
		try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
			String line;
			while ((line = br.readLine()) != null) {
				if ((line.startsWith("MemTotal:"))) {
					MEM mem = new MEM();
					mem.memTotal = Integer.parseInt(line.split(":")[1].trim().split(" ")[0]);
					System.out.println("Mem Total: " + mem.memTotal + " kB");

				} else if ((line.startsWith("MemFree:"))) {
					MEM mem = new MEM();
					mem.memFree = Integer.parseInt(line.split(":")[1].trim().split(" ")[0]);
					System.out.println("Mem Free: " + mem.memFree + " kB");
					break; // Para ler apenas o valor de memória livre, podemos sair do loop após encontrar
									// a primeira ocorrência
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// dados dos processos

		Path procDir = Paths.get("/proc");

		try (Stream<Path> procFile = Files.list(procDir)) {
			long numTasks = procFile.filter(path -> Files.isDirectory(path)) // somente os diretorios
					.filter(path -> path.getFileName().toString().matches("\\d+")) // so os diretorios com num inteiro no nome
					.filter(new Predicate<Path>() { // somente as tasks (exclui os kthr)
						@Override
						public boolean test(Path path) {
							Path statusFile = path.resolve("status");
							try (BufferedReader reader = Files.newBufferedReader(statusFile)) {
								String line = reader.lines()
										.filter(string -> string.startsWith("Kthread:"))
										.findFirst().toString();

								return line.contains("0");
							} catch (IOException e) {
								e.printStackTrace();
								return false;
							}
						}
					})
					// .forEach(path -> System.out.println(path.getFileName().toString()));
					.count(); // numero de tasks

			System.out.println("Tasks: " + numTasks);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
