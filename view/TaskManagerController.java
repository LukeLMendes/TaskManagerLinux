package view;

import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.util.Duration;

import model.Processo;
import model.SnapshotData;
import model.ProcessIOStat;
import model.SystemSnapshot;

import service.TaskManagerService;

import exception.KillProcessException;
import exception.InvalidPidException;
import exception.SnapshotWriteException;
import exception.SnapshotReadException;
import exception.DomainException;

import java.util.List;
import java.util.Map;

public class TaskManagerController {

    private final TaskManagerService service;
    private final MainFrame frame;
    private final ScheduledService<Void> refreshService;

    public TaskManagerController(TaskManagerService service, MainFrame frame) {
        this.service = service;
        this.frame   = frame;

        this.refreshService = new ScheduledService<>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<>() {
                    @Override
                    protected Void call() {
                        service.refresh();
                        return null;
                    }
                };
            }
        };

        refreshService.setPeriod(Duration.seconds(1));
        refreshService.setOnSucceeded(event -> atualizarUI());
        refreshService.setOnFailed(event -> {
            Throwable erro = refreshService.getException();
            if (erro != null) {
                mostrarErro("Erro no refresh",
                    "Falha ao ler dados do sistema: " + erro.getMessage());
            }
        });
    }

    public void iniciar() {
        try {
            service.refresh();
        } catch (Exception e) {
            mostrarErro("Erro na inicialização",
                "Falha ao ler dados do sistema: " + e.getMessage());
        }
        atualizarUI();
        refreshService.start();
    }

    public void parar() {
        refreshService.cancel();
    }

    public void matarProcesso(Processo processo) {
        if (processo == null) return;
        try {
            service.killProcess(processo.getPid());
            System.out.println("[Kill] SIGTERM enviado para PID=" + processo.getPid());
        } catch (InvalidPidException e) {
            mostrarErro("PID inválido", e.getMessage());
        } catch (KillProcessException e) {
            mostrarErro("Falha ao encerrar processo",
                "PID=" + processo.getPid() + ": " + e.getMessage());
        }
    }

    public void salvarSnapshot() {
        try {
            service.saveSnapshot();
            mostrarInfo("Snapshot salvo", "Estado do sistema salvo com sucesso.");
        } catch (SnapshotWriteException e) {
            mostrarErro("Falha ao salvar snapshot", e.getMessage());
        }
    }

    public model.SnapshotData carregarUltimoSnapshot() {
        try {
            return service.loadLatestSnapshot();
        } catch (SnapshotReadException e) {
            mostrarErro("Falha ao carregar snapshot", e.getMessage());
            return null;
        }
    }

    public void adicionarAnotacao(int pid, String texto) {
        try {
            service.adicionarAnotacao(pid, texto);
        } catch (SnapshotWriteException e) {
            mostrarErro("Anotação", "Falha ao salvar anotação: " + e.getMessage());
        }
    }

    public void removerAnotacao(int pid) {
        try {
            service.removerAnotacao(pid);
        } catch (SnapshotWriteException e) {
            mostrarErro("Anotação", "Falha ao salvar anotação: " + e.getMessage());
        }
    }

    public Map<Integer, String> getAnotacoes() {
        return service.getAnnotationService().getAll();
    }

    public void reniceProceso(int pid, int novoNice) {
        try {
            service.reniceProceso(pid, novoNice);
        } catch (DomainException e) {
            mostrarErro("Renice", e.getMessage());
        }
    }

    private void atualizarUI() {
        List<Processo>      tasks    = service.getTasks();
        List<Processo>      kthreads = service.getKthreads();
        List<ProcessIOStat> ioStats  = service.getTaskIOStats();
        SystemSnapshot      snapshot = service.getSystemSnapshot();

        double memTotal = (snapshot != null) ? snapshot.getMemTotal() : 1.0;

        if (tasks != null) {
            frame.getMainPanel().update(tasks, kthreads, memTotal);
        }

        if (tasks != null && kthreads != null) {
            frame.getTreePanel().update(tasks, kthreads, memTotal);
        }

        frame.getIOPanel().update(ioStats, tasks, kthreads);

        if (snapshot != null) {
            frame.atualizarCabecalho(snapshot);
        }
    }

    private void mostrarErro(String titulo, String mensagem) {
        Platform.runLater(() -> frame.setStatus("Erro — " + titulo + ": " + mensagem));
    }

    private void mostrarInfo(String titulo, String mensagem) {
        Platform.runLater(() -> frame.setStatus(titulo + ": " + mensagem));
    }
}
