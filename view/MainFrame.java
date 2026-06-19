package view;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import model.Processo;
import model.SystemSnapshot;

import persistence.*;
import service.TaskManagerService;

import java.util.List;

/**
 * Janela principal do TaskManager — ponto de entrada da interface gráfica JavaFX.
 *
 * No diagrama UML, MainFrame possui composição com:
 *   - TaskManagerController (diamond fechado: o controller só existe dentro do frame)
 *   - MainPanel  (diamond aberto: agregação)
 *   - IOPanel    (diamond aberto: agregação)
 * E usa o enum ViewMode para controlar qual painel está visível.
 *
 * Layout inspirado no htop, de cima para baixo:
 * ┌─────────────────────────────────────────────────────────────┐
 * │  BARRAS DE CPU  (uma por núcleo)        │  CONTADORES      │
 * │  BARRA DE MEM   [||||||||  7.0G/14.4G]  │  Tasks: N, M thr │
 * │  BARRA DE SWP   [  0K/2.0G]             │  Uptime: HH:MM:SS│
 * ├────────────────────────────────────────────────────────────-│
 * │  [Main]  [I/O]   ← abas de modo                            │
 * ├─────────────────────────────────────────────────────────────│
 * │  Tabela de processos (MainPanel ou IOPanel)                  │
 * ├─────────────────────────────────────────────────────────────│
 * │  [Kill] [Snapshot] [Quit]   ← rodapé de ações              │
 * └─────────────────────────────────────────────────────────────┘
 *
 * Cores gerais (paleta htop-like):
 *   Fundo:       #1a1a2e  (azul muito escuro)
 *   Cabeçalho:   #005f00  (verde escuro para labels de barra)
 *   Barra CPU:   #00aa00  (verde)
 *   Barra MEM:   #0087ff  (azul)
 *   Texto info:  #ffffff  (branco)
 *   Aba ativa:   #005f87  (azul ciano)
 *   Rodapé btn:  #005f00  / #005f87 / #870000
 */
public class MainFrame extends Application {

    /**
     * Ponto de entrada da JVM — chama o runtime do JavaFX diretamente.
     * Como MainFrame já estende Application, pode conter o main() aqui mesmo,
     * eliminando a necessidade de uma classe TaskManager separada.
     *
     * @param args argumentos de linha de comando (não utilizados neste MVP)
     */
    public static void main(String[] args) {
        launch(args);
    }

    // -------------------------------------------------------------------------
    // Constantes visuais
    // -------------------------------------------------------------------------
    private static final String COR_FUNDO      = "#1a1a2e";
    private static final String COR_BARRA_CPU  = "#00aa00";
    private static final String COR_BARRA_MEM  = "#0087ff";
    private static final String COR_BARRA_SWP  = "#005fd7";
    private static final String COR_TEXTO      = "#ffffff";
    private static final String COR_LABEL      = "#00d700";
    private static final String COR_ABA_ATIVA  = "#005f87";
    private static final int    REFRESH_MS     = 1000;

    // -------------------------------------------------------------------------
    // Componentes da interface
    // -------------------------------------------------------------------------

    /** Painel de processos de usuário (aba "Main"). */
    private MainPanel mainPanel;

    /** Painel de árvore hierárquica de processos (alternado por F5 dentro do modo MAIN). */
    private TreePanel treePanel;

    /** Painel de estatísticas de I/O (aba "I/O"). */
    private IOPanel ioPanel;

    /** Modo de exibição atual (MAIN ou IO). */
    private ViewMode viewMode = ViewMode.MAIN;

    /**
     * Controla se o modo Main exibe a lista plana (false) ou a árvore (true).
     * Alternado pelo botão F5 Tree no rodapé — igual ao htop.
     */
    private boolean modoArvore = false;

    /** Controlador: intermediário entre a view e o serviço. */
    private TaskManagerController controller;

    /**
     * Contadores de CPU do ciclo anterior, usados para calcular o delta.
     * O uso REAL de CPU entre dois instantes = (delta_ativo) / (delta_total).
     * Sem guardar o ciclo anterior, só seria possível calcular a média desde o boot.
     */
    private List<long[]> previousCpuInfos = null;

    // ── Elementos do cabeçalho ──────────────────────────────────────────────
    /** Contêiner das barras de CPU (uma ProgressBar por núcleo). */
    private VBox cpuBarsBox;

    /** Barra de uso de memória RAM. */
    private ProgressBar memBar;

    /** Label ao lado da barra de memória (ex: "7.0G/14.4G"). */
    private Label memLabel;

    /** Barra de uso de swap. */
    private ProgressBar swpBar;

    /** Label ao lado da barra de swap. */
    private Label swpLabel;

    // Referências aos botões de aba — necessárias para trocarModoTab() atualizar
    // o destaque visual ao trocar via teclado sem clicar nos botões.
    private Button btnMain;
    private Button btnIO;

    // Botão F5 Tree no rodapé: muda de estilo quando o modo árvore está ativo.
    private Button btnF5Tree;

    /** Label de contagens: "Tasks: 150, 1214 thr, 384 kthr; 3 running" */
    private Label tasksLabel;

    /** Label de uptime: "Uptime: 01:42:02" */
    private Label uptimeLabel;

    /** Label de load average: "Load average: 0.62 0.81 0.94" */
    private Label loadAvgLabel;

    /** Contêiner central onde MainPanel ou IOPanel são exibidos. */
    private StackPane painelCentral;

    // -------------------------------------------------------------------------
    // Ponto de entrada JavaFX
    // -------------------------------------------------------------------------

    /**
     * Ponto de entrada JavaFX. Chamado pelo Application.launch() no TaskManager.java.
     * Monta toda a interface e inicia o controller.
     *
     * @param stage janela principal fornecida pelo JavaFX runtime
     */
    @Override
    public void start(Stage stage) {

        // ── 1. Instancia as dependências de persistence (implementações concretas) ──
        // Esta é a única vez que as classes concretas aparecem — depois disso
        // tudo depende de interfaces, conforme o diagrama UML.
        ProcfsSystemRepository  sysRepo      = new ProcfsSystemRepository();
        ProcfsProcessRepository procRepo     = new ProcfsProcessRepository();
        ProcfsIORepository      ioRepo       = new ProcfsIORepository();
        FileSnapshotRepository  snapRepo     = new FileSnapshotRepository();
        LinuxProcessKiller      killer       = new LinuxProcessKiller();

        // ── 2. Instancia o serviço de negócio ──────────────────────────────
        TaskManagerService service = new TaskManagerService(
            sysRepo, procRepo, ioRepo, snapRepo, killer);

        // ── 3. Cria os painéis e o controller ─────────────────────────────
        mainPanel  = new MainPanel();
        treePanel  = new TreePanel();
        ioPanel    = new IOPanel();
        controller = new TaskManagerController(service, this);

        // ── 4. Monta o layout completo ─────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + COR_FUNDO + ";");

        // Cabeçalho: barras de CPU/MEM/SWP + contadores de info.
        root.setTop(criarCabecalho());

        // Abas "Main" e "I/O" + painel central alternável.
        root.setCenter(criarAreaCentral());

        // Rodapé: botões de ação.
        root.setBottom(criarRodape());

        // ── 5. Configura e exibe a janela ──────────────────────────────────
        Scene scene = new Scene(root, 1100, 700);
        scene.setFill(Color.web(COR_FUNDO));

        // CSS global: cabeçalho de tabela verde escuro, texto branco.
        scene.getStylesheets().add(gerarCSS());

        // Atalhos de teclado globais — interceptados antes de qualquer controle.
        // addEventFilter captura na fase de "descida" do evento, antes que a tabela
        // possa consumir Tab (navegação de células) ou F5 (refresh do browser).
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case TAB -> { trocarModoTab();      event.consume(); }
                case F5  -> { alternarModoArvore(); event.consume(); }
                // F9: atalho global de Kill, igual ao htop.
                case F9  -> { executarKill();       event.consume(); }
                default  -> {}
            }
        });

        stage.setTitle("TaskManager Linux");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(500);

        // Para o timer de atualização quando a janela for fechada.
        stage.setOnCloseRequest(e -> controller.parar());

        stage.show();

        // ── 6. Inicia o ciclo de atualização (1 segundo) ───────────────────
        controller.iniciar();
    }

    // -------------------------------------------------------------------------
    // Construtores de seções do layout
    // -------------------------------------------------------------------------

    /**
     * Monta o cabeçalho da janela: barras de CPU, barra de memória, barra de swap
     * e os contadores de tasks/threads/uptime — exatamente como o htop.
     *
     * Layout:
     *   [CPU 0: ████░░ 6.1%]  [CPU 6: ████ 11.5%]  │  Tasks: N, M thr...
     *   [CPU 1: ██░░░░ 1.5%]  [CPU 7: ███░ 5.3%]   │  Uptime: HH:MM:SS
     *   [Mem: █████████ 7.0G/14.4G]
     *   [Swp: ░░░░░░░░ 0K/2.0G]
     *
     * @return VBox com todo o cabeçalho
     */
    private VBox criarCabecalho() {
        VBox cabecalho = new VBox(4);
        cabecalho.setPadding(new Insets(6, 8, 4, 8));
        cabecalho.setStyle("-fx-background-color: " + COR_FUNDO + ";");

        // ── Linha superior: barras CPU + bloco de info ──────────────────────
        HBox linhaTop = new HBox(20);
        linhaTop.setAlignment(Pos.TOP_LEFT);

        // Caixa das barras de CPU (será populada pelo atualizarCabecalho()).
        cpuBarsBox = new VBox(2);
        cpuBarsBox.setMinWidth(400);

        // Bloco de informações do lado direito.
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

        // ── Linha de Memória ─────────────────────────────────────────────────
        HBox linhaMem = criarLinhaDeRecurso("Mem", COR_BARRA_MEM);
        memBar   = (ProgressBar) ((HBox) linhaMem.getChildren().get(1)).getChildren().get(0);
        memLabel = (Label)       ((HBox) linhaMem.getChildren().get(1)).getChildren().get(1);

        // ── Linha de Swap ────────────────────────────────────────────────────
        HBox linhaSwp = criarLinhaDeRecurso("Swp", COR_BARRA_SWP);
        swpBar   = (ProgressBar) ((HBox) linhaSwp.getChildren().get(1)).getChildren().get(0);
        swpLabel = (Label)       ((HBox) linhaSwp.getChildren().get(1)).getChildren().get(1);

        cabecalho.getChildren().addAll(linhaTop, linhaMem, linhaSwp);
        return cabecalho;
    }

    /**
     * Cria uma linha de recurso (Mem ou Swp) com label + ProgressBar + label de valor.
     *
     * Exemplo visual:  Mem [████████████░░░░░░  7.0G/14.4G]
     *
     * @param nome   "Mem" ou "Swp"
     * @param corBar cor hexadecimal da barra preenchida
     * @return HBox pronto para ser adicionado ao cabeçalho
     */
    private HBox criarLinhaDeRecurso(String nome, String corBar) {
        HBox linha = new HBox(4);
        linha.setAlignment(Pos.CENTER_LEFT);

        // Label do nome do recurso, em verde como no htop.
        Label lbNome = new Label(nome);
        lbNome.setStyle(
            "-fx-text-fill: " + COR_LABEL + ";" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 12px;" +
            "-fx-min-width: 30px;"
        );

        // Barra de progresso + label de valor numa HBox interna.
        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(250);
        bar.setPrefHeight(14);
        // -fx-control-inner-background afeta o track interno do ProgressBar.
        // -fx-background-color no nó externo apenas pintaria o contêiner, não o track.
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

    /**
     * Monta a área central: abas "Main" / "I/O" (como o htop) + painel de conteúdo.
     *
     * O StackPane painelCentral alterna entre mainPanel e ioPanel conforme o modo.
     *
     * @return VBox com as abas e o painel de conteúdo
     */
    private VBox criarAreaCentral() {
        VBox area = new VBox(0);

        // ── Barra de abas ──────────────────────────────────────────────────
        HBox abas = new HBox(0);
        abas.setStyle("-fx-background-color: #0a0a1e;");

        // Guarda referências para que trocarModoTab() possa atualizar os estilos
        // ao trocar via teclado (Tab), sem que nenhum botão tenha sido clicado.
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

        abas.getChildren().addAll(btnMain, btnIO);

        // ── Painel central alternável ──────────────────────────────────────
        // StackPane sobrepõe os três painéis; trocarModo()/alternarModoArvore()
        // controlam qual fica visível em cada momento.
        painelCentral = new StackPane();
        painelCentral.setStyle("-fx-background-color: " + COR_FUNDO + ";");
        painelCentral.getChildren().addAll(mainPanel, treePanel, ioPanel);

        // Início: lista plana visível, os outros ocultos.
        mainPanel.setVisible(true);
        treePanel.setVisible(false);
        ioPanel.setVisible(false);

        VBox.setVgrow(painelCentral, Priority.ALWAYS);
        area.getChildren().addAll(abas, painelCentral);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    /**
     * Cria um botão de aba com estilo parecido com o htop.
     *
     * @param texto texto exibido no botão
     * @param modo  modo que este botão ativa
     * @return Button configurado
     */
    private Button criarBotaoAba(String texto, ViewMode modo) {
        Button btn = new Button(texto);
        btn.setMinWidth(70);
        btn.setPadding(new Insets(4, 16, 4, 16));
        btn.setFont(Font.font("Monospaced", FontWeight.BOLD, 12));
        return btn;
    }

    /**
     * Aplica ou remove o estilo "ativa" de uma aba.
     * Ativa: fundo azul #005f87, texto branco.
     * Inativa: fundo escuro #0a0a1e, texto cinza.
     *
     * @param btn    botão da aba
     * @param ativa  true se esta aba está selecionada
     */
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

    /**
     * Alterna o painel central para o modo especificado (MAIN ou IO).
     *
     * Quando modo == MAIN, exibe MainPanel (lista) ou TreePanel (árvore)
     * dependendo do flag modoArvore — comportamento idêntico ao htop onde
     * F5 alterna a vista dentro da aba Main.
     *
     * @param modo novo modo de exibição
     */
    private void trocarModo(ViewMode modo) {
        this.viewMode = modo;
        // No modo MAIN: exibe lista plana OU árvore conforme modoArvore.
        mainPanel.setVisible(modo == ViewMode.MAIN && !modoArvore);
        treePanel.setVisible(modo == ViewMode.MAIN &&  modoArvore);
        ioPanel.setVisible(modo == ViewMode.IO);
        // Entrega o foco à tabela do painel que ficou visível, garantindo
        // que as teclas de seta navegam imediatamente sem precisar de clique.
        if (modo == ViewMode.MAIN && !modoArvore) mainPanel.requestTableFocus();
        if (modo == ViewMode.MAIN &&  modoArvore) treePanel.requestTableFocus();
        if (modo == ViewMode.IO)                  ioPanel.requestTableFocus();
    }

    /**
     * Alterna entre lista plana e árvore hierárquica (tecla F5 ou botão).
     * Funciona em ambos os modos: MAIN (alterna MainPanel/TreePanel) e IO
     * (alterna entre lista plana e árvore dentro do próprio IOPanel).
     *
     * O flag modoArvore é compartilhado: mudar no modo MAIN preserva o estado
     * ao voltar para IO, e vice-versa — um único toggle para toda a aplicação.
     *
     * Atualiza texto e cor do botão para refletir a ação que será executada
     * no próximo clique (não o estado atual):
     *   - lista ativa  → "F5Tree"  (verde)
     *   - árvore ativa → "F5List"  (azul)
     */
    private void alternarModoArvore() {
        modoArvore = !modoArvore;
        if (viewMode == ViewMode.MAIN) {
            // No modo MAIN troca o painel visível entre mainPanel e treePanel.
            trocarModo(viewMode);
        } else {
            // No modo IO notifica o IOPanel para que o próximo update() use
            // lista plana ou árvore conforme o novo estado do flag.
            ioPanel.setModoArvore(modoArvore);
        }
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

    /**
     * Cicla entre MAIN e IO ao pressionar Tab: MAIN → IO → MAIN.
     * O flag modoArvore é preservado ao trocar de aba — se a árvore estava
     * ativa no modo MAIN e o usuário voltar de IO, a árvore aparece novamente.
     */
    private void trocarModoTab() {
        ViewMode proximo = switch (viewMode) {
            case MAIN -> ViewMode.IO;
            case IO   -> ViewMode.MAIN;
        };
        trocarModo(proximo);
        destacarAba(btnMain, proximo == ViewMode.MAIN);
        destacarAba(btnIO,   proximo == ViewMode.IO);
    }

    /**
     * Monta o rodapé com os botões de ação, inspirado na barra F1–F10 do htop.
     *
     * [F5Tree]  [F9Kill]  [Snapshot]  [F10Quit]
     *
     * @return HBox com os botões de ação
     */
    private HBox criarRodape() {
        HBox rodape = new HBox(0);
        rodape.setStyle("-fx-background-color: #0a0a1e;");
        rodape.setAlignment(Pos.CENTER_LEFT);

        // ── F5 Tree ──────────────────────────────────────────────────────────
        // Alterna entre lista plana e árvore hierárquica dentro do modo Main.
        // Quando ativo (modoArvore=true), o fundo fica azul para indicar o estado.
        // A referência é guardada em btnF5Tree para que alternarModoArvore()
        // possa atualizar o estilo sem precisar recriar o botão.
        btnF5Tree = criarBotaoRodape("F5", "Tree", "#005f00");
        btnF5Tree.setOnAction(e -> alternarModoArvore());

        // ── F9 Kill ──────────────────────────────────────────────────────────
        // Encerra o processo selecionado no painel ativo.
        // O botão delega para executarKill(), o mesmo método chamado pela tecla F9.
        Button btnKill = criarBotaoRodape("F9", "Kill", "#870000");
        btnKill.setOnAction(e -> executarKill());

        // ── Snapshot ─────────────────────────────────────────────────────────
        Button btnSnap = criarBotaoRodape("", "Snapshot", "#005f5f");
        btnSnap.setOnAction(e -> controller.salvarSnapshot());

        // ── F10 Quit ─────────────────────────────────────────────────────────
        Button btnQuit = criarBotaoRodape("F10", "Quit", "#005f00");
        btnQuit.setOnAction(e -> {
            controller.parar();
            Platform.exit();
        });

        rodape.getChildren().addAll(btnF5Tree, btnKill, btnSnap, btnQuit);
        return rodape;
    }

    /**
     * Cria um botão de rodapé no estilo htop: prefixo Fn em azul + texto em branco.
     *
     * @param prefixo texto da tecla de função ("F9", "F10", ou "" para sem prefixo)
     * @param texto   nome da ação ("Kill", "Quit", "Snapshot")
     * @param corFundo cor hexadecimal do fundo do botão
     * @return Button estilizado
     */
    private Button criarBotaoRodape(String prefixo, String texto, String corFundo) {
        // Monta o texto do botão: "F9Kill" → exibido como "F9Kill" em estilo htop.
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

    // -------------------------------------------------------------------------
    // Atualização do cabeçalho (chamada pelo controller após cada refresh)
    // -------------------------------------------------------------------------

    /**
     * Atualiza todos os elementos do cabeçalho com os dados mais recentes:
     *   - Barras de CPU (uma por núcleo, calculada pela variação de contadores)
     *   - Barra e label de memória RAM
     *   - Barra e label de swap
     *   - Label de contagem de tasks/threads/kthreads e processos rodando
     *   - Label de uptime
     *   - Label de load average (1min, 5min, 15min)
     *
     * Deve ser chamado na JavaFX Application Thread (o controller garante isso).
     *
     * @param snap     SystemSnapshot com os dados gerais do sistema
     * @param tasks    lista de processos de usuário (usada para contar os "running")
     * @param kthreads lista de kernel threads (não usada diretamente aqui, recebida por consistência)
     */
    public void atualizarCabecalho(SystemSnapshot snap,
                                    List<Processo> tasks,
                                    List<Processo> kthreads) {

        // ── Barras de CPU ──────────────────────────────────────────────────
        // cpu_infos: lista ordenada (cpu0, cpu1, cpu2...) de arrays com os
        // contadores acumulados de /proc/stat desde o boot.
        // Formato de cada array: [user, nice, system, idle, iowait, irq, softirq, ...]
        //
        // Para obter o uso ATUAL (igual ao htop), calculamos o delta entre
        // a leitura atual e a leitura do ciclo anterior (1 segundo atrás):
        //   uso% = (deltaTotal - deltaIdle) / deltaTotal * 100
        //
        // Na primeira execução previousCpuInfos é null — usamos valores absolutos
        // como fallback (só nesse primeiro ciclo o valor será menos preciso).
        if (snap.getCpuInfos() != null) {
            cpuBarsBox.getChildren().clear();

            GridPane grid = new GridPane();
            grid.setHgap(12);
            grid.setVgap(2);

            List<long[]> cpuInfos = snap.getCpuInfos();
            for (int i = 0; i < cpuInfos.size(); i++) {
                long[] curr = cpuInfos.get(i);
                // Recupera o array do ciclo anterior para o mesmo núcleo, se existir.
                long[] prev = (previousCpuInfos != null && i < previousCpuInfos.size())
                              ? previousCpuInfos.get(i) : null;

                double pct = calcularUsoCPU(curr, prev);

                // Label do núcleo: "0", "1", ...
                Label lbNome = new Label(String.valueOf(i));
                lbNome.setStyle(
                    "-fx-text-fill: " + COR_LABEL + ";" +
                    "-fx-font-size: 11px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-font-family: Monospaced;"
                );
                lbNome.setMinWidth(20);

                // Barra do núcleo.
                ProgressBar bar = new ProgressBar(pct / 100.0);
                bar.setPrefWidth(120);
                bar.setPrefHeight(13);
                bar.setStyle(
                    "-fx-accent: " + COR_BARRA_CPU + ";" +
                    "-fx-control-inner-background: #1a1a2e;" +
                    "-fx-border-color: transparent;"
                );

                // Label do percentual ao lado da barra.
                Label lbPct = new Label(String.format("%.1f%%", pct));
                lbPct.setStyle(
                    "-fx-text-fill: " + COR_TEXTO + ";" +
                    "-fx-font-size: 11px;" +
                    "-fx-font-family: Monospaced;"
                );

                // Distribui em 2 colunas (col 0 e col 3).
                int col = (i % 2) * 3;
                int row = i / 2;
                grid.add(lbNome, col,     row);
                grid.add(bar,    col + 1, row);
                grid.add(lbPct,  col + 2, row);
            }
            cpuBarsBox.getChildren().add(grid);

            // Guarda a leitura atual para servir de "anterior" no próximo ciclo.
            previousCpuInfos = cpuInfos;
        }

        // ── Barra de Memória ───────────────────────────────────────────────
        // Usa mem_used pré-calculado no serviço com a mesma fórmula do htop:
        //   used = MemTotal - MemFree - Buffers - Cached - SReclaimable
        double memTotal = snap.getMemTotal();
        double memUsada = snap.getMemUsed();
        if (memTotal > 0) {
            memBar.setProgress(memUsada / memTotal);
            memLabel.setText(formatarMemGB(memUsada) + "/" + formatarMemGB(memTotal));
        }

        // ── Barra de Swap ─────────────────────────────────────────────────
        double swapTotal = snap.getSwapTotal();
        double swapUsado = swapTotal - snap.getSwapFree();
        if (swapTotal > 0) {
            swpBar.setProgress(swapUsado / swapTotal);
            swpLabel.setText(formatarMemGB(swapUsado) + "/" + formatarMemGB(swapTotal));
        } else {
            swpBar.setProgress(0);
            swpLabel.setText("0K/" + formatarMemGB(swapTotal));
        }

        // ── Contadores de tasks/threads ────────────────────────────────────
        long numTasks    = snap.getNumTasks();
        long numThreads  = snap.getNumThreads();
        long numKthreads = snap.getNumKthreads();

        // Conta quantos processos estão no estado 'R' (running).
        long running = (tasks != null)
            ? tasks.stream().filter(p -> p.getState() == 'R').count() : 0;

        tasksLabel.setText(String.format(
            "Tasks: %d, %d thr, %d kthr; %d running",
            numTasks, numThreads, numKthreads, running));

        // ── Uptime ────────────────────────────────────────────────────────
        uptimeLabel.setText("Uptime: " + formatarUptime(snap.getUptime()));

        // ── Load average ──────────────────────────────────────────────────
        // la[0] = média do último 1 minuto, la[1] = 5 minutos, la[2] = 15 minutos.
        // Um valor de 1.0 em um sistema com 4 núcleos significa 25% de carga;
        // um valor de 4.0 significa que todos os núcleos estão completamente ocupados.
        // Valores acima do número de núcleos indicam fila de espera pela CPU.
        double[] la = snap.getLoadAvg();
        loadAvgLabel.setText(String.format("Load average: %.2f %.2f %.2f", la[0], la[1], la[2]));
    }

    // -------------------------------------------------------------------------
    // Getters — usados pelo TaskManagerController para acessar os painéis
    // -------------------------------------------------------------------------

    /**
     * Retorna o painel principal de processos (lista plana).
     * Usado pelo controller para chamar update() e getProcessoSelecionado().
     */
    public MainPanel getMainPanel() { return mainPanel; }

    /**
     * Retorna o painel de árvore hierárquica (tasks + kthreads).
     * Usado pelo controller para chamar update() a cada refresh.
     */
    public TreePanel getTreePanel() { return treePanel; }

    /**
     * Retorna o painel de I/O.
     * Usado pelo controller para chamar update().
     */
    public IOPanel getIOPanel() { return ioPanel; }

    // -------------------------------------------------------------------------
    // Ações de Kill (botão + tecla F9)
    // -------------------------------------------------------------------------

    /**
     * Encerra o processo selecionado no painel atualmente visível.
     *
     * Funciona em todos os modos:
     *   - MAIN lista  → mainPanel.getProcessoSelecionado()
     *   - MAIN árvore → treePanel.getProcessoSelecionado()
     *   - IO          → ioPanel.getProcessoSelecionado()
     *
     * Chamado tanto pelo botão F9Kill quanto pela tecla F9 (scene event filter).
     */
    private void executarKill() {
        model.Processo selecionado = switch (viewMode) {
            case MAIN -> modoArvore
                ? treePanel.getProcessoSelecionado()
                : mainPanel.getProcessoSelecionado();
            case IO   -> ioPanel.getProcessoSelecionado();
        };
        if (selecionado == null) {
            mostrarAlertaSemSelecao();
        } else {
            if (confirmarKill(selecionado.getPid())) {
                controller.matarProcesso(selecionado);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers de diálogos de confirmação
    // -------------------------------------------------------------------------

    /**
     * Exibe um diálogo de confirmação antes de encerrar um processo.
     * Igual ao htop que pede confirmação ao pressionar F9.
     *
     * @param pid PID do processo que será encerrado
     * @return true se o usuário confirmou, false caso contrário
     */
    private boolean confirmarKill(int pid) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Encerrar processo");
        alert.setHeaderText("Encerrar PID=" + pid + "?");
        alert.setContentText(
            "Um sinal SIGTERM será enviado ao processo.\n" +
            "O processo pode não encerrar imediatamente.");
        return alert.showAndWait()
                    .filter(r -> r == ButtonType.OK)
                    .isPresent();
    }

    /**
     * Exibe um alerta informando que nenhum processo está selecionado.
     * Chamado quando o usuário clica em "Kill" sem selecionar uma linha.
     */
    private void mostrarAlertaSemSelecao() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Nenhum processo selecionado");
        alert.setHeaderText(null);
        alert.setContentText("Selecione um processo na tabela antes de usar Kill.");
        alert.showAndWait();
    }

    // -------------------------------------------------------------------------
    // Helpers de formatação
    // -------------------------------------------------------------------------

    /**
     * Calcula o percentual de uso de um núcleo de CPU usando o delta entre
     * duas leituras consecutivas de /proc/stat — exatamente como o htop faz.
     *
     * Cada array tem a forma: [user, nice, system, idle, iowait, irq, softirq, ...]
     * Os contadores são acumulados desde o boot, então o uso no intervalo é:
     *
     *   deltaTotal = sum(curr) - sum(prev)
     *   deltaIdle  = (curr[3] + curr[4]) - (prev[3] + prev[4])   ← idle + iowait
     *   uso%       = (deltaTotal - deltaIdle) / deltaTotal * 100
     *
     * Se prev for null (primeiro ciclo), usa os valores absolutos como fallback —
     * será impreciso apenas nesse primeiro segundo.
     *
     * @param curr contadores da leitura atual
     * @param prev contadores da leitura anterior (null no primeiro ciclo)
     * @return percentual de uso entre 0.0 e 100.0
     */
    private double calcularUsoCPU(long[] curr, long[] prev) {
        if (curr == null || curr.length < 4) return 0.0;

        long total, idle;

        if (prev == null || prev.length < 4) {
            // Fallback para o primeiro ciclo: usa totais absolutos desde o boot.
            total = 0;
            for (long v : curr) total += v;
            idle = curr[3] + (curr.length > 4 ? curr[4] : 0);
        } else {
            // Cálculo delta: diferença entre a leitura atual e a anterior.
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

    /**
     * Formata um valor de memória em kB para string com unidade.
     * Exemplos: 7012345 kB → "6.7G", 512000 kB → "500.0M".
     *
     * @param kB valor em kilobytes
     * @return string formatada com unidade (K, M, G)
     */
    private String formatarMemGB(double kB) {
        // htop usa prefixos binários: 1G = 1.048.576 kB (2^20), 1M = 1.024 kB (2^10).
        // Usar decimal (1G = 1.000.000 kB) daria valores ~7% maiores que o htop.
        if (kB >= 1_048_576) return String.format("%.1fG", kB / 1_048_576.0);
        if (kB >= 1_024)     return String.format("%.1fM", kB / 1_024.0);
        return                      String.format("%.0fK", kB);
    }

    /**
     * Formata o uptime em segundos para o formato "HH:MM:SS" exibido no cabeçalho.
     * Exemplo: 6122.5 → "01:42:02".
     *
     * @param segundos uptime em segundos (valor de /proc/uptime)
     * @return string no formato HH:MM:SS
     */
    private String formatarUptime(double segundos) {
        long total = (long) segundos;
        long hh    = total / 3600;
        long mm    = (total % 3600) / 60;
        long ss    = total % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }

    /**
     * Gera uma URI de CSS de dados inline para estilizar o cabeçalho das colunas
     * da TableView (fundo verde escuro + texto branco), já que o JavaFX não permite
     * estilizar o cabeçalho via CSS inline direto em setStyle() da TableColumn.
     *
     * @return URI "data:text/css,..." com o CSS de aplicação
     */
    private String gerarCSS() {
        // O CSS abaixo estiliza tanto TableView (MainPanel/IOPanel) quanto
        // TreeTableView (TreePanel). As classes CSS do JavaFX são diferentes:
        //   TableView     → .table-view, .table-cell, .table-row-cell
        //   TreeTableView → .tree-table-view, .tree-table-cell, .tree-table-row-cell
        // Por isso cada regra precisa ter os dois seletores.
        String css =
            // ── Fundo do cabeçalho das colunas ───────────────────────────
            ".table-view .column-header-background," +
            ".tree-table-view .column-header-background {" +
            "  -fx-background-color: #003300;" +
            "}" +
            // ── Cada célula do cabeçalho ──────────────────────────────────
            ".table-view .column-header, .table-view .filler," +
            ".tree-table-view .column-header, .tree-table-view .filler {" +
            "  -fx-background-color: #004400;" +
            "  -fx-border-color: #005500;" +
            "  -fx-border-width: 0 1 1 0;" +
            "}" +
            // ── Texto do cabeçalho (verde, monospace, bold) ───────────────
            ".table-view .column-header .label," +
            ".tree-table-view .column-header .label {" +
            "  -fx-text-fill: #00ff00;" +
            "  -fx-font-weight: bold;" +
            "  -fx-font-size: 12px;" +
            "  -fx-font-family: 'Monospaced';" +
            "}" +
            // ── Células de dados ─────────────────────────────────────────
            ".table-view .table-cell," +
            ".tree-table-view .tree-table-cell {" +
            "  -fx-text-fill: #ffffff;" +
            "  -fx-font-size: 12px;" +
            "  -fx-font-family: 'Monospaced';" +
            "  -fx-border-color: transparent;" +
            "  -fx-padding: 2 4 2 4;" +
            "}" +
            // ── Scrollbar ────────────────────────────────────────────────
            ".table-view .scroll-bar," +
            ".tree-table-view .scroll-bar {" +
            "  -fx-background-color: #0a0a1e;" +
            "}" +
            ".table-view .scroll-bar .thumb," +
            ".tree-table-view .scroll-bar .thumb {" +
            "  -fx-background-color: #005f87;" +
            "}" +
            // ── Linha selecionada (azul) ──────────────────────────────────
            ".table-view:focused .table-row-cell:selected," +
            ".table-view:focused .table-row-cell:selected .table-cell," +
            ".tree-table-view:focused .tree-table-row-cell:selected," +
            ".tree-table-view:focused .tree-table-row-cell:selected .tree-table-cell {" +
            "  -fx-background-color: #005f87;" +
            "  -fx-text-fill: #ffffff;" +
            "}" +
            // ── Remove azul padrão do JavaFX em células selecionadas ──────
            ".table-row-cell:selected .table-cell," +
            ".tree-table-row-cell:selected .tree-table-cell {" +
            "  -fx-background-color: transparent;" +
            "}" +
            // ── Seta de expansão (triângulo) da TreeTableView ─────────────
            // Por padrão é preta; trocamos para branco para aparecer no fundo escuro.
            ".tree-table-row-cell .tree-disclosure-node .arrow {" +
            "  -fx-background-color: #aaaaaa;" +
            "}";

        // Codifica o CSS como URI de dados para passar ao Scene.getStylesheets().add().
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
