package view;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import model.Processo;
import model.SystemSnapshot;

import persistence.*;
import service.TaskManagerService;

import java.util.List;

public class MainFrame extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    private static final String COR_FUNDO     = "#1a1a2e";
    private static final String COR_BARRA_CPU = "#00aa00";
    private static final String COR_BARRA_MEM = "#0087ff";
    private static final String COR_BARRA_SWP = "#005fd7";
    private static final String COR_TEXTO     = "#ffffff";
    private static final String COR_LABEL     = "#00d700";
    private static final String COR_ABA_ATIVA = "#005f87";

    private MainPanel mainPanel;
    private TreePanel treePanel;
    private IOPanel   ioPanel;

    private ViewMode viewMode  = ViewMode.MAIN;
    private boolean  modoArvore = false;

    private TaskManagerController controller;

    private List<long[]> previousCpuInfos = null;

    private VBox       cpuBarsBox;
    private ProgressBar memBar;
    private Label       memLabel;
    private ProgressBar swpBar;
    private Label       swpLabel;

    private Button btnMain;
    private Button btnIO;
    private Button btnF5Tree;

    private Label tasksLabel;
    private Label uptimeLabel;
    private Label loadAvgLabel;

    private StackPane painelCentral;

    private Label statusLabel;
    private Label inputPromptLabel;
    private TextField inputField;
    private HBox inputBox;
    private String modoInput = null;
    private int pidInputAtual = -1;
    private boolean killPendente = false;
    private int pidKillPendente = -1;
    private Button btnCloseStatus;
    private javafx.scene.control.TextArea rodapeExpandido;

    @Override
    public void start(Stage stage) {

        ProcfsSystemRepository  sysRepo  = new ProcfsSystemRepository();
        ProcfsProcessRepository procRepo = new ProcfsProcessRepository();
        ProcfsIORepository      ioRepo   = new ProcfsIORepository();
        FileSnapshotRepository  snapRepo = new FileSnapshotRepository();
        LinuxProcessKiller      killer   = new LinuxProcessKiller();

        TaskManagerService service = new TaskManagerService(
            sysRepo, procRepo, ioRepo, snapRepo, killer);

        mainPanel  = new MainPanel();
        treePanel  = new TreePanel();
        ioPanel    = new IOPanel();
        controller = new TaskManagerController(service, this);

        mainPanel.setSortChangeListener((col, type) -> ioPanel.applySortByColumn(col, type));
        ioPanel.setSortChangeListener((col, type)   -> mainPanel.applySortByColumn(col, type));
        mainPanel.setAnotacoes(controller.getAnotacoes());
        treePanel.setAnotacoes(controller.getAnotacoes());
        ioPanel.setAnotacoes(controller.getAnotacoes());

        ioPanel.setModoArvore(modoArvore);

        configurarMenuContexto();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + COR_FUNDO + ";");

        root.setTop(criarCabecalho());
        root.setCenter(criarAreaCentral());
        root.setBottom(criarRodape());

        Scene scene = new Scene(root, 1100, 700);
        scene.setFill(Color.web(COR_FUNDO));
        scene.getStylesheets().add(gerarCSS());

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case TAB    -> { trocarModoTab();      event.consume(); }
                case F5     -> { alternarModoArvore(); event.consume(); }
                case F9     -> { executarKill();       event.consume(); }
                case F10    -> { controller.parar(); Platform.exit(); event.consume(); }
                case ESCAPE -> { if (modoInput != null) { cancelarInput(); event.consume(); } }
                default     -> {}
            }
        });

        stage.setTitle("TaskManager Linux");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.setOnCloseRequest(e -> controller.parar());
        stage.show();

        controller.iniciar();
    }

    private VBox criarCabecalho() {
        VBox cabecalho = new VBox(4);
        cabecalho.setPadding(new Insets(6, 8, 4, 8));
        cabecalho.setStyle("-fx-background-color: " + COR_FUNDO + ";");

        HBox linhaTop = new HBox(20);
        linhaTop.setAlignment(Pos.TOP_LEFT);

        cpuBarsBox = new VBox(2);
        cpuBarsBox.setMinWidth(400);

        VBox infoBox = new VBox(4);
        infoBox.setAlignment(Pos.TOP_LEFT);

        tasksLabel = new Label("Tasks: –");
        tasksLabel.setStyle("-fx-text-fill: " + COR_TEXTO + "; -fx-font-size: 12px;");

        uptimeLabel = new Label("Uptime: –");
        uptimeLabel.setStyle("-fx-text-fill: " + COR_TEXTO + "; -fx-font-size: 12px;");

        loadAvgLabel = new Label("Load average: –");
        loadAvgLabel.setStyle("-fx-text-fill: " + COR_TEXTO + "; -fx-font-size: 12px;");

        infoBox.getChildren().addAll(tasksLabel, uptimeLabel, loadAvgLabel);
        linhaTop.getChildren().addAll(cpuBarsBox, infoBox);

        HBox linhaMem = criarLinhaDeRecurso("Mem", COR_BARRA_MEM);
        memBar   = (ProgressBar) ((HBox) linhaMem.getChildren().get(1)).getChildren().get(0);
        memLabel = (Label)       ((HBox) linhaMem.getChildren().get(1)).getChildren().get(1);

        HBox linhaSwp = criarLinhaDeRecurso("Swp", COR_BARRA_SWP);
        swpBar   = (ProgressBar) ((HBox) linhaSwp.getChildren().get(1)).getChildren().get(0);
        swpLabel = (Label)       ((HBox) linhaSwp.getChildren().get(1)).getChildren().get(1);

        cabecalho.getChildren().addAll(linhaTop, linhaMem, linhaSwp);
        return cabecalho;
    }

    private HBox criarLinhaDeRecurso(String nome, String corBar) {
        HBox linha = new HBox(4);
        linha.setAlignment(Pos.CENTER_LEFT);

        Label lbNome = new Label(nome);
        lbNome.setStyle(
            "-fx-text-fill: " + COR_LABEL + ";" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 12px;" +
            "-fx-min-width: 30px;"
        );

        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(250);
        bar.setPrefHeight(14);
        bar.setStyle(
            "-fx-accent: " + corBar + ";" +
            "-fx-control-inner-background: #1a1a2e;" +
            "-fx-border-color: #555577;" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 0;"
        );

        Label lbValor = new Label("–");
        lbValor.setStyle("-fx-text-fill: " + COR_TEXTO + "; -fx-font-size: 11px;");

        HBox barBox = new HBox(4, bar, lbValor);
        barBox.setAlignment(Pos.CENTER_LEFT);

        linha.getChildren().addAll(lbNome, barBox);
        return linha;
    }

    private VBox criarAreaCentral() {
        VBox area = new VBox(0);

        HBox abas = new HBox(0);
        abas.setStyle("-fx-background-color: #0a0a1e;");

        btnMain = criarBotaoAba("Main", ViewMode.MAIN);
        btnIO   = criarBotaoAba("I/O",  ViewMode.IO);

        destacarAba(btnMain, true);
        destacarAba(btnIO,   false);

        btnMain.setOnAction(e -> {
            trocarModo(ViewMode.MAIN);
            destacarAba(btnMain, true);
            destacarAba(btnIO,   false);
        });
        btnIO.setOnAction(e -> {
            trocarModo(ViewMode.IO);
            destacarAba(btnMain, false);
            destacarAba(btnIO,   true);
        });

        javafx.scene.layout.Region spacerAbas = new javafx.scene.layout.Region();
        HBox.setHgrow(spacerAbas, Priority.ALWAYS);

        javafx.scene.control.TextField campoBusca = new javafx.scene.control.TextField();
        campoBusca.setPromptText("Filtrar processos...");
        campoBusca.setPrefWidth(210);
        campoBusca.setStyle(
            "-fx-background-color: #1e1e38;" +
            "-fx-text-fill: #ffffff;" +
            "-fx-prompt-text-fill: #555577;" +
            "-fx-border-color: #333355;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 3 8 3 8;");
        campoBusca.textProperty().addListener((obs, old, novo) -> {
            String f = novo.trim().toLowerCase();
            mainPanel.setFiltro(f);
            treePanel.setFiltro(f);
            ioPanel.setFiltro(f);
        });

        abas.getChildren().addAll(btnMain, btnIO, spacerAbas, campoBusca);

        painelCentral = new StackPane();
        painelCentral.setStyle("-fx-background-color: " + COR_FUNDO + ";");
        painelCentral.getChildren().addAll(mainPanel, treePanel, ioPanel);

        mainPanel.setVisible(true);
        treePanel.setVisible(false);
        ioPanel.setVisible(false);

        VBox.setVgrow(painelCentral, Priority.ALWAYS);
        area.getChildren().addAll(abas, painelCentral);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    private Button criarBotaoAba(String texto, ViewMode modo) {
        Button btn = new Button(texto);
        btn.setMinWidth(70);
        btn.setPadding(new Insets(4, 16, 4, 16));
        btn.setFont(Font.font("Monospaced", FontWeight.BOLD, 12));
        return btn;
    }

    private void destacarAba(Button btn, boolean ativa) {
        if (ativa) {
            btn.setStyle(
                "-fx-background-color: " + COR_ABA_ATIVA + ";" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-weight: bold;" +
                "-fx-border-color: #005f87 #005f87 " + COR_FUNDO + " #005f87;" +
                "-fx-border-width: 1px;" +
                "-fx-cursor: hand;"
            );
        } else {
            btn.setStyle(
                "-fx-background-color: #0a0a1e;" +
                "-fx-text-fill: #aaaaaa;" +
                "-fx-font-weight: bold;" +
                "-fx-border-color: #333355;" +
                "-fx-border-width: 1px;" +
                "-fx-cursor: hand;"
            );
        }
    }

    private void trocarModo(ViewMode modo) {
        this.viewMode = modo;
        mainPanel.setVisible(modo == ViewMode.MAIN && !modoArvore);
        treePanel.setVisible(modo == ViewMode.MAIN &&  modoArvore);
        ioPanel.setVisible(modo == ViewMode.IO);
        if (modo == ViewMode.MAIN && !modoArvore) mainPanel.requestTableFocus();
        if (modo == ViewMode.MAIN &&  modoArvore) treePanel.requestTableFocus();
        if (modo == ViewMode.IO)                  ioPanel.requestTableFocus();
    }

    private void alternarModoArvore() {
        modoArvore = !modoArvore;
        if (viewMode == ViewMode.MAIN) {
            trocarModo(viewMode);
        }
        ioPanel.setModoArvore(modoArvore);
        btnF5Tree.setText(modoArvore ? "F5List" : "F5Tree");
        btnF5Tree.setStyle(
            "-fx-background-color: " + (modoArvore ? "#005f87" : "#005f00") + ";" +
            "-fx-text-fill: #ffffff;" +
            "-fx-font-weight: bold;" +
            "-fx-border-color: #333355;" +
            "-fx-border-width: 0 1 0 0;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 0;"
        );
    }

    private void trocarModoTab() {
        ViewMode proximo = switch (viewMode) {
            case MAIN -> ViewMode.IO;
            case IO   -> ViewMode.MAIN;
        };
        trocarModo(proximo);
        destacarAba(btnMain, proximo == ViewMode.MAIN);
        destacarAba(btnIO,   proximo == ViewMode.IO);
    }

    private VBox criarRodape() {
        rodapeExpandido = new javafx.scene.control.TextArea();
        rodapeExpandido.setEditable(false);
        rodapeExpandido.setWrapText(true);
        rodapeExpandido.setPrefHeight(80);
        rodapeExpandido.setStyle(
            "-fx-control-inner-background: #0d0d22;" +
            "-fx-text-fill: #ffff87;" +
            "-fx-font-size: 13px;" +
            "-fx-font-family: Monospaced;" +
            "-fx-border-color: #005f87;" +
            "-fx-border-width: 1 0 0 0;"
        );
        rodapeExpandido.setVisible(false);
        rodapeExpandido.setManaged(false);

        HBox rodape = new HBox(0);
        rodape.setStyle("-fx-background-color: #0a0a1e;");
        rodape.setAlignment(Pos.CENTER_LEFT);

        btnF5Tree = criarBotaoRodape("F5", "Tree", "#005f00");
        btnF5Tree.setOnAction(e -> alternarModoArvore());

        Button btnKill = criarBotaoRodape("F9", "Kill", "#870000");
        btnKill.setOnAction(e -> executarKill());

        Button btnSnap = criarBotaoRodape("", "Snapshot", "#005f5f");
        btnSnap.setOnAction(e -> controller.salvarSnapshot());

        Button btnCarregar = criarBotaoRodape("", "Carregar", "#005f5f");
        btnCarregar.setOnAction(e -> abrirDialogoSnapshot());

        Button btnQuit = criarBotaoRodape("F10", "Quit", "#005f00");
        btnQuit.setOnAction(e -> {
            controller.parar();
            Platform.exit();
        });

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel = new Label("");
        statusLabel.setStyle(
            "-fx-text-fill: #ffff87;" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: Monospaced;" +
            "-fx-padding: 0 4 0 8;" +
            "-fx-cursor: hand;"
        );
        statusLabel.setOnMouseClicked(e -> alternarRodapeExpandido());

        btnCloseStatus = new Button("✕");
        btnCloseStatus.setVisible(false);
        btnCloseStatus.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #aaaaaa;" +
            "-fx-font-size: 11px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 2 6 2 6;"
        );
        btnCloseStatus.setOnAction(e -> setStatus(""));

        inputPromptLabel = new Label("");
        inputPromptLabel.setStyle(
            "-fx-text-fill: #00d700;" +
            "-fx-font-size: 11px;" +
            "-fx-padding: 0 4 0 8;"
        );

        inputField = new TextField();
        inputField.setPrefWidth(180);
        inputField.setStyle(
            "-fx-background-color: #1e1e38;" +
            "-fx-text-fill: #ffffff;" +
            "-fx-border-color: #005f87;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 2 6 2 6;"
        );
        inputField.setOnAction(e -> confirmarInput());
        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) cancelarInput();
        });

        Button btnOk = new Button("OK");
        btnOk.setStyle(
            "-fx-background-color: #005f87;" +
            "-fx-text-fill: #ffffff;" +
            "-fx-font-weight: bold;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 3 8 3 8;"
        );
        btnOk.setOnAction(e -> confirmarInput());

        Button btnCancelar = new Button("✕");
        btnCancelar.setStyle(
            "-fx-background-color: #5f0000;" +
            "-fx-text-fill: #ffffff;" +
            "-fx-font-weight: bold;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 3 8 3 8;"
        );
        btnCancelar.setOnAction(e -> cancelarInput());

        inputBox = new HBox(4, inputPromptLabel, inputField, btnOk, btnCancelar);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setVisible(false);

        rodape.getChildren().addAll(btnF5Tree, btnKill, btnSnap, btnCarregar, btnQuit,
                                    spacer, statusLabel, btnCloseStatus, inputBox);

        VBox wrapper = new VBox(0, rodapeExpandido, rodape);
        wrapper.setStyle("-fx-background-color: #0a0a1e;");
        return wrapper;
    }

    private Button criarBotaoRodape(String prefixo, String texto, String corFundo) {
        String label = prefixo.isEmpty() ? texto : prefixo + texto;
        Button btn = new Button(label);
        btn.setMinWidth(90);
        btn.setPadding(new Insets(5, 10, 5, 10));
        btn.setFont(Font.font("Monospaced", FontWeight.BOLD, 12));
        btn.setStyle(
            "-fx-background-color: " + corFundo + ";" +
            "-fx-text-fill: #ffffff;" +
            "-fx-border-color: #333355;" +
            "-fx-border-width: 0 1 0 0;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 0;"
        );
        return btn;
    }

    public void atualizarCabecalho(SystemSnapshot snap) {

        if (snap.getCpuInfos() != null) {
            cpuBarsBox.getChildren().clear();

            GridPane grid = new GridPane();
            grid.setHgap(12);
            grid.setVgap(2);

            List<long[]> cpuInfos = snap.getCpuInfos();
            for (int i = 0; i < cpuInfos.size(); i++) {
                long[] curr = cpuInfos.get(i);
                long[] prev = (previousCpuInfos != null && i < previousCpuInfos.size())
                              ? previousCpuInfos.get(i) : null;

                double pct = calcularUsoCPU(curr, prev);

                Label lbNome = new Label(String.valueOf(i));
                lbNome.setStyle(
                    "-fx-text-fill: " + COR_LABEL + ";" +
                    "-fx-font-size: 11px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-font-family: Monospaced;"
                );
                lbNome.setMinWidth(20);

                ProgressBar bar = new ProgressBar(pct / 100.0);
                bar.setPrefWidth(120);
                bar.setPrefHeight(13);
                bar.setStyle(
                    "-fx-accent: " + COR_BARRA_CPU + ";" +
                    "-fx-control-inner-background: #1a1a2e;" +
                    "-fx-border-color: transparent;"
                );

                Label lbPct = new Label(String.format("%.1f%%", pct));
                lbPct.setStyle(
                    "-fx-text-fill: " + COR_TEXTO + ";" +
                    "-fx-font-size: 11px;" +
                    "-fx-font-family: Monospaced;"
                );

                int col = (i % 2) * 3;
                int row = i / 2;
                grid.add(lbNome, col,     row);
                grid.add(bar,    col + 1, row);
                grid.add(lbPct,  col + 2, row);
            }
            cpuBarsBox.getChildren().add(grid);

            previousCpuInfos = cpuInfos;
        }

        double memTotal = snap.getMemTotal();
        double memUsada = snap.getMemUsed();
        if (memTotal > 0) {
            memBar.setProgress(memUsada / memTotal);
            memLabel.setText(formatarMemGB(memUsada) + "/" + formatarMemGB(memTotal));
        }

        double swapTotal = snap.getSwapTotal();
        double swapUsado = swapTotal - snap.getSwapFree();
        if (swapTotal > 0) {
            swpBar.setProgress(swapUsado / swapTotal);
            swpLabel.setText(formatarMemGB(swapUsado) + "/" + formatarMemGB(swapTotal));
        } else {
            swpBar.setProgress(0);
            swpLabel.setText("0K/" + formatarMemGB(swapTotal));
        }

        long numTasks    = snap.getNumTasks();
        long numThreads  = snap.getNumThreads();
        long numKthreads = snap.getNumKthreads();
        long running     = snap.getNumRunning();

        tasksLabel.setText(String.format(
            "Tasks: %d, %d thr, %d kthr; %d running",
            numTasks, numThreads, numKthreads, running));

        uptimeLabel.setText("Uptime: " + formatarUptime(snap.getUptime()));

        double[] la = snap.getLoadAvg();
        if (la != null && la.length >= 3) {
            loadAvgLabel.setText(String.format("Load average: %.2f %.2f %.2f", la[0], la[1], la[2]));
        }
    }

    public MainPanel getMainPanel() { return mainPanel; }
    public TreePanel getTreePanel() { return treePanel; }
    public IOPanel   getIOPanel()   { return ioPanel;   }

    private void executarKill() {
        model.Processo selecionado = getProcessoSelecionadoAtivo();
        if (selecionado == null) {
            setStatus("Nenhum processo selecionado.");
            killPendente = false;
            return;
        }
        if (!killPendente || pidKillPendente != selecionado.getPid()) {
            killPendente = true;
            pidKillPendente = selecionado.getPid();
            setStatus("Pressione F9 novamente para encerrar PID=" +
                      selecionado.getPid() + " (" + selecionado.getComm() + ")");
        } else {
            killPendente = false;
            setStatus("");
            controller.matarProcesso(selecionado);
        }
    }


    private void configurarMenuContexto() {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();

        javafx.scene.control.MenuItem itemAnotacao =
            new javafx.scene.control.MenuItem("Adicionar/Editar Anotação");
        itemAnotacao.setOnAction(e -> {
            Processo p = getProcessoSelecionadoAtivo();
            if (p == null) { setStatus("Nenhum processo selecionado."); return; }
            String atual = controller.getAnotacoes().getOrDefault(p.getPid(), "");
            mostrarInput("Anotação — PID " + p.getPid() + " (" + p.getComm() + "):", atual, "ANOTACAO", p.getPid());
        });

        javafx.scene.control.MenuItem itemRemoverAnotacao =
            new javafx.scene.control.MenuItem("Remover Anotação");
        itemRemoverAnotacao.setOnAction(e -> {
            Processo p = getProcessoSelecionadoAtivo();
            if (p == null) { setStatus("Nenhum processo selecionado."); return; }
            controller.removerAnotacao(p.getPid());
        });

        javafx.scene.control.MenuItem itemRenice =
            new javafx.scene.control.MenuItem("Renice (Editar Prioridade)");
        itemRenice.setOnAction(e -> {
            Processo p = getProcessoSelecionadoAtivo();
            if (p == null) { setStatus("Nenhum processo selecionado."); return; }
            mostrarInput("Nice (-20 a +19) — PID " + p.getPid() + " (" + p.getComm() + "):",
                         String.valueOf(p.getNice()), "RENICE", p.getPid());
        });

        javafx.scene.control.SeparatorMenuItem sep1 =
            new javafx.scene.control.SeparatorMenuItem();
        javafx.scene.control.SeparatorMenuItem sep2 =
            new javafx.scene.control.SeparatorMenuItem();

        javafx.scene.control.MenuItem itemKill =
            new javafx.scene.control.MenuItem("Kill Processo (SIGTERM)");
        itemKill.setOnAction(e -> executarKill());

        menu.getItems().addAll(itemAnotacao, itemRemoverAnotacao, sep1, itemRenice, sep2, itemKill);

        mainPanel.setTableContextMenu(menu);
        treePanel.setTableContextMenu(menu);
        ioPanel.setTableContextMenu(menu);
    }

    private Processo getProcessoSelecionadoAtivo() {
        if (viewMode == ViewMode.MAIN && !modoArvore) return mainPanel.getProcessoSelecionado();
        if (viewMode == ViewMode.MAIN &&  modoArvore) return treePanel.getProcessoSelecionado();
        if (viewMode == ViewMode.IO)                  return ioPanel.getProcessoSelecionado();
        return null;
    }

    private void abrirDialogoSnapshot() {
        model.SnapshotData snap = controller.carregarUltimoSnapshot();
        if (snap == null) return;

        java.util.List<Processo> processos = new java.util.ArrayList<>();
        if (snap.getTasks()    != null) processos.addAll(snap.getTasks());
        if (snap.getKthreads() != null) processos.addAll(snap.getKthreads());

        javafx.stage.Stage dialogo = new javafx.stage.Stage();
        dialogo.setTitle("Snapshot — " + new java.util.Date(snap.getTimestamp()));

        javafx.collections.ObservableList<Processo> lista =
            javafx.collections.FXCollections.observableArrayList(processos);

        TableView<Processo> tabela = new TableView<>(lista);
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Processo, String> cTipo = new TableColumn<>("Tipo");
        cTipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTipo()));
        cTipo.setPrefWidth(65);

        TableColumn<Processo, Number> cPid = new TableColumn<>("PID");
        cPid.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getPid()));
        cPid.setPrefWidth(65);

        TableColumn<Processo, String> cCmd = new TableColumn<>("COMMAND");
        cCmd.setCellValueFactory(c -> {
            Processo p = c.getValue();
            String txt = p.getCmdline().isEmpty() ? p.getComm() : p.getCmdline();
            return new javafx.beans.property.SimpleStringProperty(txt);
        });

        tabela.getColumns().addAll(cTipo, cPid, cCmd);

        Button fechar = new Button("Fechar");
        fechar.setOnAction(e -> dialogo.close());

        VBox layout = new VBox(8,
            new javafx.scene.control.Label("Processos: " + processos.size() +
                "   |   Salvo em: " + new java.util.Date(snap.getTimestamp())),
            tabela,
            new HBox(fechar));
        layout.setPadding(new Insets(10));
        VBox.setVgrow(tabela, Priority.ALWAYS);

        dialogo.setScene(new javafx.scene.Scene(layout, 700, 460));
        dialogo.show();
    }

    private double calcularUsoCPU(long[] curr, long[] prev) {
        if (curr == null || curr.length < 4) return 0.0;

        long total, idle;

        if (prev == null || prev.length < 4) {
            total = 0;
            for (long v : curr) total += v;
            idle = curr[3] + (curr.length > 4 ? curr[4] : 0);
        } else {
            total = 0;
            for (int j = 0; j < Math.min(curr.length, prev.length); j++) {
                total += curr[j] - prev[j];
            }
            long currIdle = curr[3] + (curr.length > 4 ? curr[4] : 0);
            long prevIdle = prev[3] + (prev.length > 4 ? prev[4] : 0);
            idle = currIdle - prevIdle;
        }

        if (total <= 0) return 0.0;
        return Math.max(0, Math.min(100, (total - idle) * 100.0 / total));
    }

    private String formatarMemGB(double kB) {
        if (kB >= 1_048_576) return String.format("%.1fG", kB / 1_048_576.0);
        if (kB >= 1_024)     return String.format("%.1fM", kB / 1_024.0);
        return                      String.format("%.0fK", kB);
    }

    private String formatarUptime(double segundos) {
        long total = (long) segundos;
        long hh    = total / 3600;
        long mm    = (total % 3600) / 60;
        long ss    = total % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }

    public void setStatus(String msg) {
        statusLabel.setText(msg);
        btnCloseStatus.setVisible(!msg.isEmpty());
        rodapeExpandido.setText(msg);
        if (msg.isEmpty()) {
            killPendente = false;
            rodapeExpandido.setVisible(false);
            rodapeExpandido.setManaged(false);
        }
    }

    private void mostrarInput(String prompt, String valorAtual, String modo, int pid) {
        modoInput = modo;
        pidInputAtual = pid;
        inputPromptLabel.setText(prompt);
        inputField.setText(valorAtual);
        statusLabel.setText("");
        inputBox.setVisible(true);
        inputField.requestFocus();
        inputField.selectAll();
    }

    private void confirmarInput() {
        String valor = inputField.getText().trim();
        String modo  = modoInput;
        int pid      = pidInputAtual;
        cancelarInput();
        if ("ANOTACAO".equals(modo)) {
            if (!valor.isBlank()) controller.adicionarAnotacao(pid, valor);
        } else if ("RENICE".equals(modo)) {
            try {
                controller.reniceProceso(pid, Integer.parseInt(valor));
            } catch (NumberFormatException ex) {
                setStatus("Valor inválido: " + valor);
            }
        }
    }

    private void alternarRodapeExpandido() {
        if (statusLabel.getText().isEmpty()) return;
        boolean abrir = !rodapeExpandido.isVisible();
        rodapeExpandido.setVisible(abrir);
        rodapeExpandido.setManaged(abrir);
    }

    private void cancelarInput() {
        modoInput = null;
        pidInputAtual = -1;
        inputField.clear();
        inputBox.setVisible(false);
    }

    private String gerarCSS() {
        String css =
            ".table-view .column-header-background," +
            ".tree-table-view .column-header-background {" +
            "  -fx-background-color: #003300;" +
            "}" +
            ".table-view .column-header, .table-view .filler," +
            ".tree-table-view .column-header, .tree-table-view .filler {" +
            "  -fx-background-color: #004400;" +
            "  -fx-border-color: #005500;" +
            "  -fx-border-width: 0 1 1 0;" +
            "}" +
            ".table-view .column-header .label," +
            ".tree-table-view .column-header .label {" +
            "  -fx-text-fill: #00ff00;" +
            "  -fx-font-weight: bold;" +
            "  -fx-font-size: 12px;" +
            "  -fx-font-family: 'Monospaced';" +
            "}" +
            ".table-view .table-cell," +
            ".tree-table-view .tree-table-cell {" +
            "  -fx-text-fill: #ffffff;" +
            "  -fx-font-size: 12px;" +
            "  -fx-font-family: 'Monospaced';" +
            "  -fx-border-color: transparent;" +
            "  -fx-padding: 2 4 2 4;" +
            "}" +
            ".table-view .scroll-bar," +
            ".tree-table-view .scroll-bar {" +
            "  -fx-background-color: #0a0a1e;" +
            "}" +
            ".table-view .scroll-bar .thumb," +
            ".tree-table-view .scroll-bar .thumb {" +
            "  -fx-background-color: #005f87;" +
            "}" +
            ".table-view:focused .table-row-cell:selected," +
            ".table-view:focused .table-row-cell:selected .table-cell," +
            ".tree-table-view:focused .tree-table-row-cell:selected," +
            ".tree-table-view:focused .tree-table-row-cell:selected .tree-table-cell {" +
            "  -fx-background-color: #005f87;" +
            "  -fx-text-fill: #ffffff;" +
            "}" +
            ".table-row-cell:selected .table-cell," +
            ".tree-table-row-cell:selected .tree-table-cell {" +
            "  -fx-background-color: transparent;" +
            "}" +
            ".tree-table-row-cell .tree-disclosure-node .arrow {" +
            "  -fx-background-color: #aaaaaa;" +
            "}";

        return "data:text/css," + css.replace(" ", "%20")
                                     .replace("{", "%7B")
                                     .replace("}", "%7D")
                                     .replace(":", "%3A")
                                     .replace(";", "%3B")
                                     .replace("#", "%23")
                                     .replace("'", "%27")
                                     .replace("\n", "");
    }
}
