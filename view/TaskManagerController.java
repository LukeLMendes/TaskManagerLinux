package view;

import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.util.Duration;

import model.Processo;
import model.ProcessIOStat;
import model.SystemSnapshot;

import service.TaskManagerService;

import exception.KillProcessException;
import exception.InvalidPidException;
import exception.SnapshotWriteException;

import java.util.List;

/**
 * Controlador da interface gráfica — intermediário entre a view e o serviço.
 *
 * No diagrama UML, TaskManagerController recebe uma referência ao
 * TaskManagerService e envia comandos a ele. A view (MainFrame) possui
 * o controller por composição (diamond fechado no diagrama).
 *
 * Responsabilidades:
 *   1. Iniciar e parar o ciclo de atualização automática a cada 1 segundo.
 *   2. Invocar TaskManagerService.refresh() numa thread de background (para
 *      não travar a UI durante a leitura do /proc).
 *   3. Após o refresh, enviar os dados para os painéis via Platform.runLater()
 *      (que garante execução na JavaFX Application Thread).
 *   4. Processar ações do usuário: matar processo, salvar snapshot.
 *
 * O ScheduledService do JavaFX é a ferramenta certa aqui: ele executa uma
 * Task em background em intervalos fixos e respeita o ciclo de vida da
 * aplicação JavaFX (start/cancel automáticos com a janela).
 */
public class TaskManagerController {

    // -------------------------------------------------------------------------
    // Dependências
    // -------------------------------------------------------------------------

    /** Serviço que lê o /proc e expõe os dados do sistema. */
    private final TaskManagerService service;

    /**
     * Referência ao MainFrame para que o controller possa atualizar
     * o cabeçalho (uptime, contagens, barras de memória/cpu).
     */
    private final MainFrame frame;

    // -------------------------------------------------------------------------
    // Timer de atualização
    // -------------------------------------------------------------------------

    /**
     * Serviço agendado do JavaFX que dispara o refresh a cada 1 segundo.
     *
     * ScheduledService<Void> é preferível a um Timer/Thread manual porque:
     *   - É integrado ao JavaFX e para automaticamente quando a aplicação encerra.
     *   - Permite usar Platform.runLater() de forma segura no succeeded().
     *   - Suporta restart/cancel sem race conditions.
     */
    private final ScheduledService<Void> refreshService;

    // -------------------------------------------------------------------------
    // Construtor
    // -------------------------------------------------------------------------

    /**
     * Cria o controller e configura o timer de atualização de 1 segundo.
     *
     * @param service serviço de backend já instanciado com todas as dependências
     * @param frame   janela principal que será atualizada a cada ciclo
     */
    public TaskManagerController(TaskManagerService service, MainFrame frame) {
        this.service = service;
        this.frame   = frame;

        // Configura o ScheduledService: executa createTask() a cada 1 segundo.
        this.refreshService = new ScheduledService<>() {

            /**
             * Chamado pelo JavaFX a cada intervalo para criar a tarefa de background.
             * A task é executada numa thread separada (não a Application Thread),
             * portanto pode fazer I/O de disco (leitura de /proc) sem travar a UI.
             */
            @Override
            protected Task<Void> createTask() {
                return new Task<>() {
                    @Override
                    protected Void call() {
                        // Esta execução acontece fora da JavaFX Application Thread.
                        // Lê todos os dados do /proc — operação potencialmente lenta.
                        service.refresh();
                        return null;
                    }
                };
            }
        };

        // Define o intervalo entre o FIM de uma execução e o INÍCIO da próxima.
        // Usar Period (não Delay) garante que travamentos ocasionais de /proc
        // não acumulem execuções pendentes.
        refreshService.setPeriod(Duration.seconds(1));

        // Quando o refresh em background termina com sucesso, atualiza a UI.
        // succeeded() é chamado automaticamente na JavaFX Application Thread — seguro.
        refreshService.setOnSucceeded(event -> atualizarUI());

        // Se o refresh falhar (ex: IOException inesperado), loga mas não para o timer.
        refreshService.setOnFailed(event -> {
            Throwable erro = refreshService.getException();
            if (erro != null) {
                System.err.println("[TaskManagerController] Erro no refresh: "
                                   + erro.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Ciclo de vida
    // -------------------------------------------------------------------------

    /**
     * Inicia o ciclo de atualização automática.
     * Deve ser chamado pelo MainFrame após a janela estar pronta.
     *
     * Faz um refresh imediato antes de iniciar o timer para que a tabela
     * não fique vazia durante o primeiro segundo de execução.
     */
    public void iniciar() {
        // Faz um primeiro refresh síncrono para popular a UI imediatamente.
        service.refresh();
        atualizarUI();

        // Inicia o timer que vai chamar refresh() a cada 1 segundo a partir de agora.
        refreshService.start();
    }

    /**
     * Para o ciclo de atualização automática.
     * Deve ser chamado ao fechar a janela para liberar a thread de background.
     */
    public void parar() {
        refreshService.cancel();
    }

    // -------------------------------------------------------------------------
    // Ações do usuário
    // -------------------------------------------------------------------------

    /**
     * Encerra o processo selecionado na tabela atualmente visível.
     *
     * O processo a matar é obtido do MainPanel (único painel que lista processos
     * de usuário com PID). O IOPanel não expõe seleção de processo diretamente.
     *
     * Exibe um Alert de confirmação antes de enviar o sinal, igual ao htop
     * que pede confirmação ao pressionar F9.
     *
     * @param processo processo a encerrar (não pode ser null)
     */
    public void matarProcesso(Processo processo) {
        if (processo == null) return;

        try {
            // Envia SIGTERM via service → processKiller.killProcess().
            service.killProcess(processo.getPid());
            System.out.println("[Kill] SIGTERM enviado para PID=" + processo.getPid());
        } catch (InvalidPidException e) {
            mostrarErro("PID inválido", e.getMessage());
        } catch (KillProcessException e) {
            mostrarErro("Falha ao encerrar processo",
                "PID=" + processo.getPid() + ": " + e.getMessage());
        }
    }

    /**
     * Salva um snapshot do estado atual do sistema em disco.
     *
     * Chamado pelo botão "Snapshot" no rodapé da janela.
     * Exibe um Alert de sucesso ou erro após a operação.
     */
    public void salvarSnapshot() {
        try {
            service.saveSnapshot();
            mostrarInfo("Snapshot salvo",
                "Estado do sistema salvo com sucesso.");
        } catch (SnapshotWriteException e) {
            mostrarErro("Falha ao salvar snapshot", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Atualização da UI (chamado após cada refresh bem-sucedido)
    // -------------------------------------------------------------------------

    /**
     * Envia os dados mais recentes do serviço para os componentes visuais.
     *
     * Este método é sempre chamado na JavaFX Application Thread (diretamente
     * pelo setOnSucceeded do ScheduledService ou via Platform.runLater).
     * Portanto é seguro alterar componentes JavaFX aqui sem usar runLater.
     */
    /**
     * Envia os dados mais recentes do serviço para todos os painéis visíveis.
     *
     * Atualiza sempre todos os três painéis (Main, Tree, IO), mesmo que nem todos
     * estejam visíveis no momento. Isso garante que ao trocar de aba o conteúdo
     * já esteja atualizado sem precisar esperar o próximo ciclo de refresh.
     *
     * Este método é sempre chamado na JavaFX Application Thread (via setOnSucceeded
     * do ScheduledService, ou diretamente no iniciar()). É seguro alterar
     * componentes JavaFX aqui sem usar Platform.runLater().
     */
    private void atualizarUI() {
        List<Processo>      tasks    = service.getTasks();
        List<Processo>      kthreads = service.getKthreads();
        List<ProcessIOStat> ioStats  = service.getTaskIOStats();
        SystemSnapshot      snapshot = service.getSystemSnapshot();

        double memTotal = (snapshot != null) ? snapshot.getMemTotal() : 1.0;

        // ── MainPanel (aba "Main") ─────────────────────────────────────────
        // Lista plana com tasks + kthreads ordenados por PID.
        // Kthreads aparecem em verde na coluna COMMAND (mesmo visual do TreePanel).
        if (tasks != null) {
            frame.getMainPanel().update(tasks, kthreads, memTotal);
        }

        // ── TreePanel (aba "Tree") ─────────────────────────────────────────
        // Árvore hierárquica com tasks + kthreads, organizada por PPID.
        if (tasks != null && kthreads != null) {
            frame.getTreePanel().update(tasks, kthreads, memTotal);
        }

        // ── IOPanel (aba "I/O") ───────────────────────────────────────────
        // Passa tasks e kthreads para que o painel construa a árvore hierárquica
        // e exiba as colunas USER e COMMAND da mesma forma que o TreePanel.
        // ioStats pode ser null para processos sem permissão de leitura de /proc/io.
        frame.getIOPanel().update(ioStats, tasks, kthreads);

        // ── Cabeçalho (barras de CPU/mem, uptime, load avg) ───────────────
        if (snapshot != null) {
            frame.atualizarCabecalho(snapshot, tasks, kthreads);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers de diálogo
    // -------------------------------------------------------------------------

    /**
     * Exibe um Alert de erro na tela, com título e mensagem.
     * Deve ser chamado na Application Thread.
     *
     * @param titulo   título do diálogo
     * @param mensagem texto da mensagem de erro
     */
    private void mostrarErro(String titulo, String mensagem) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(titulo);
            alert.setHeaderText(null);
            alert.setContentText(mensagem);
            alert.showAndWait();
        });
    }

    /**
     * Exibe um Alert informativo (para confirmar ações bem-sucedidas).
     *
     * @param titulo   título do diálogo
     * @param mensagem texto da mensagem
     */
    private void mostrarInfo(String titulo, String mensagem) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle(titulo);
            alert.setHeaderText(null);
            alert.setContentText(mensagem);
            alert.showAndWait();
        });
    }
}
