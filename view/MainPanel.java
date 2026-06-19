package view;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import model.Processo;
import model.Task;
import model.Kthr;

import java.util.List;

/**
 * Painel principal da interface — equivale à tela padrão do htop (ViewMode.MAIN).
 *
 * Exibe uma TableView com as colunas:
 *   PID | USER | PRI | NI | VIRT | RES | SHR | S | CPU% | MEM% | THR | PPID | COMMAND
 *
 * REGRA DE TIPAGEM das colunas:
 *   - CellValueFactory → retorna o tipo nativo do dado (int, long, double, String).
 *     Nunca converte para String aqui. Isso preserva a ordenação numérica correta
 *     ao clicar no cabeçalho da coluna (ex: 1024 kB ordena como número, não como texto).
 *   - CellFactory → responsável exclusivamente pela formatação visual e cor.
 *     Só é adicionado quando a célula precisa de algo além do valor bruto
 *     (ex: long kB → "1.0M", char estado → cor verde/vermelha).
 *     Células de número inteiro simples (PID, NI, THR...) não precisam de CellFactory.
 *
 * No diagrama UML, MainPanel tem agregação com MainFrame (diamond aberto).
 * A tabela é atualizada pelo TaskManagerController a cada 1 segundo.
 */
public class MainPanel extends VBox {

    // -------------------------------------------------------------------------
    // Componentes JavaFX
    // -------------------------------------------------------------------------

    /** Tabela que lista todos os processos de usuário (Tasks). */
    private final TableView<Processo> table;

    /**
     * Lista observável — modelo da tabela.
     * Quando o controller chama setAll(), a TableView redesenha automaticamente.
     */
    private final ObservableList<Processo> processList;

    // -------------------------------------------------------------------------
    // Estado interno
    // -------------------------------------------------------------------------

    /**
     * Memória total do sistema em kB, usada para calcular MEM%.
     * Atualizada junto com a lista de processos via update().
     */
    private double memTotalKB = 1.0; // evita divisão por zero antes do 1º refresh

    // -------------------------------------------------------------------------
    // Construtor
    // -------------------------------------------------------------------------

    /**
     * Cria o painel e configura a tabela de processos.
     * Chamado pelo MainFrame durante a inicialização da interface.
     */
    public MainPanel() {
        setPadding(new Insets(0));
        setSpacing(0);

        processList = FXCollections.observableArrayList();

        table = new TableView<>(processList);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        configurarColunas();
        aplicarEstilo();

        getChildren().add(table);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
    }

    // -------------------------------------------------------------------------
    // API pública — chamada pelo TaskManagerController
    // -------------------------------------------------------------------------

    /**
     * Atualiza o conteúdo da tabela com os dados mais recentes do sistema.
     * Deve ser chamado na JavaFX Application Thread.
     *
     * Tasks e kthreads são combinados e ordenados por PID para o modo lista plana.
     * No modo árvore (TreePanel) a ordem segue a hierarquia DFS; aqui é por PID.
     *
     * @param tasks      lista de processos de usuário retornada pelo serviço
     * @param kthreads   lista de kernel threads (exibidas em verde na coluna COMMAND)
     * @param memTotalKB memória total do sistema em kB (para calcular MEM%)
     */
    public void update(List<Processo> tasks, List<Processo> kthreads, double memTotalKB) {
        this.memTotalKB = memTotalKB > 0 ? memTotalKB : 1.0;
        // Salva o PID selecionado antes de substituir a lista.
        int pidSelecionado = pidSelecionadoAtual();
        // Combina tasks e kthreads numa lista única ordenada por PID.
        List<Processo> todos = new java.util.ArrayList<>(tasks);
        if (kthreads != null) todos.addAll(kthreads);
        todos.sort(java.util.Comparator.comparingInt(Processo::getPid));
        processList.setAll(todos);
        restaurarSelecao(pidSelecionado);
    }

    // Retorna o PID do item selecionado, ou -1 se nada estiver selecionado.
    private int pidSelecionadoAtual() {
        Processo sel = table.getSelectionModel().getSelectedItem();
        return (sel != null) ? sel.getPid() : -1;
    }

    // Após setAll(), re-seleciona o item com o PID salvo (busca por PID, não por índice,
    // pois a posição pode ter mudado com a chegada/saída de processos).
    private void restaurarSelecao(int pid) {
        if (pid == -1) return;
        for (int i = 0; i < processList.size(); i++) {
            if (processList.get(i).getPid() == pid) {
                table.getSelectionModel().select(i);
                return;
            }
        }
    }

    /**
     * Retorna o processo selecionado na tabela, ou null se nenhum estiver selecionado.
     * Usado pelo controller ao executar Kill.
     */
    public Processo getProcessoSelecionado() {
        return table.getSelectionModel().getSelectedItem();
    }

    // -------------------------------------------------------------------------
    // Configuração das colunas
    // -------------------------------------------------------------------------

    /**
     * Cria todas as colunas e as adiciona à tabela.
     *
     * Padrão adotado em cada coluna:
     *   1. CellValueFactory → extrai o valor nativo do modelo (int, long, double, char).
     *      Isso permite que o JavaFX ordene numericamente ao clicar no cabeçalho.
     *   2. CellFactory      → só adicionado quando a célula precisa de formatação
     *      especial (ex: kB → "1.2M") ou cor (ex: estado 'R' em verde).
     *      Células simples de número inteiro não precisam de CellFactory —
     *      o JavaFX já renderiza o valor nativo corretamente.
     */
    @SuppressWarnings("unchecked")
    private void configurarColunas() {

        // PID
        // Tipo nativo: int. Sem CellFactory — o JavaFX exibe o int diretamente.
        TableColumn<Processo, Number> colPid = new TableColumn<>("PID");
        colPid.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getPid()));
        colPid.setPrefWidth(65);

        // USER
        // EUID é int, mas o valor exibido é o nome textual do usuário.
        // Usamos Integer no value factory e CellFactory para resolver o nome.
        // Desta forma a ordenação é por EUID numérico, não alfabética.
        TableColumn<Processo, Number> colUser = new TableColumn<>("USER");
        colUser.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getEUID()));
        colUser.setPrefWidth(90);
        // CellFactory: converte EUID → nome para exibição, sem alterar o valor subjacente.
        colUser.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    // Resolve o EUID para nome sem modificar o tipo armazenado.
                    setText(resolverUsuario(item.intValue()));
                    setStyle("-fx-text-fill: #ffff87;"); // amarelo claro, como o htop
                }
            }
        });

        // PRI
        // Tipo nativo: int. Campo [17] de /proc/<pid>/stat. Sem CellFactory.
        TableColumn<Processo, Number> colPri = new TableColumn<>("PRI");
        colPri.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getPri()));
        colPri.setPrefWidth(45);

        // NI
        // Tipo nativo: int. Niceness de -20 a +19. Sem CellFactory.
        TableColumn<Processo, Number> colNi = new TableColumn<>("NI");
        colNi.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getNice()));
        colNi.setPrefWidth(45);

        // VIRT
        // Tipo nativo: long (kB de VmSize). CellFactory formata para "X.XM"/"X.XG".
        // A ordenação ao clicar no cabeçalho usa o long subjacente — correta.
        TableColumn<Processo, Number> colVirt = new TableColumn<>("VIRT");
        colVirt.setCellValueFactory(cell -> {
            long v = (cell.getValue() instanceof Task t) ? t.getVirt() : 0L;
            return new SimpleLongProperty(v);
        });
        colVirt.setPrefWidth(75);
        // CellFactory: recebe o long e formata para exibição legível.
        colVirt.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : formatarMemoria(item.longValue()));
            }
        });

        // RES
        // Tipo nativo: long (kB de VmRSS — RAM física realmente usada).
        TableColumn<Processo, Number> colRes = new TableColumn<>("RES");
        colRes.setCellValueFactory(cell -> {
            long v = (cell.getValue() instanceof Task t) ? t.getRes() : 0L;
            return new SimpleLongProperty(v);
        });
        colRes.setPrefWidth(75);
        colRes.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : formatarMemoria(item.longValue()));
            }
        });

        // SHR
        // Tipo nativo: long (kB de RssFile + RssShmem — páginas compartilhadas).
        TableColumn<Processo, Number> colShr = new TableColumn<>("SHR");
        colShr.setCellValueFactory(cell -> {
            long v = (cell.getValue() instanceof Task t) ? t.getShr() : 0L;
            return new SimpleLongProperty(v);
        });
        colShr.setPrefWidth(75);
        colShr.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : formatarMemoria(item.longValue()));
            }
        });

        // S (State)
        // Tipo nativo: char (um único caractere do campo "State" em /proc/<pid>/status).
        // Armazenamos como Integer (code point do char) para usar SimpleIntegerProperty.
        // CellFactory converte de volta para char e aplica cor.
        TableColumn<Processo, Number> colState = new TableColumn<>("S");
        colState.setCellValueFactory(cell ->
            // Armazena o code point do char (ex: 'S' → 83, 'R' → 82).
            new SimpleIntegerProperty((int) cell.getValue().getState()));
        colState.setPrefWidth(30);
        // CellFactory: reconverte para char e aplica coloração por estado.
        colState.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    // Reconverte code point → char para exibição.
                    char estado = (char) item.intValue();
                    setText(String.valueOf(estado));
                    // Cor conforme o estado: igual ao htop.
                    String cor = switch (estado) {
                        case 'R' -> "#00ff00"; // verde  — em execução
                        case 'D' -> "#ffff00"; // amarelo — esperando I/O (uninterruptible)
                        case 'Z' -> "#ff4444"; // vermelho — zombie
                        case 'T' -> "#ff8700"; // laranja — parado (SIGSTOP)
                        default  -> "#ffffff"; // branco   — sleeping e outros
                    };
                    setStyle("-fx-text-fill: " + cor + ";" +
                             "-fx-alignment: center;" +
                             "-fx-background-color: transparent;");
                }
            }
        });

        // CPU%
        // Tipo nativo: double. Percentual de uso de CPU calculado em ProcfsProcessRepository
        // como: (utime_agora + stime_agora - utime_antes - stime_antes) / CLK_TCK * 100.
        // Representa o uso de UM núcleo: 100% = um núcleo ocupado por 1 segundo.
        // Processos multi-thread podem superar 100% (igual ao htop por padrão).
        // No primeiro ciclo sempre aparece 0.0 — não há leitura anterior para comparar.
        // A ordenação ao clicar no cabeçalho usa o double subjacente (ordem numérica correta).
        TableColumn<Processo, Number> colCpu = new TableColumn<>("CPU%");
        colCpu.setCellValueFactory(cell -> {
            double pct = (cell.getValue() instanceof Task t) ? t.getCpuPct() : 0.0;
            return new SimpleDoubleProperty(pct);
        });
        colCpu.setPrefWidth(60);
        colCpu.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null
                    : String.format("%.1f", item.doubleValue()));
            }
        });

        // MEM%
        // Tipo nativo: double (percentual calculado como RES/memTotal*100).
        // CellFactory formata para "X.X" sem símbolo % (como o htop).
        // A ordenação numérica ao clicar é preservada pelo double subjacente.
        TableColumn<Processo, Number> colMem = new TableColumn<>("MEM%");
        colMem.setCellValueFactory(cell -> {
            // Calcula o percentual aqui para que o valor nativo armazenado seja double.
            double pct = (cell.getValue() instanceof Task t)
                ? (t.getRes() / memTotalKB) * 100.0
                : 0.0;
            return new SimpleDoubleProperty(pct);
        });
        colMem.setPrefWidth(60);
        // CellFactory: formata o double com 1 casa decimal.
        colMem.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null
                    : String.format("%.1f", item.doubleValue()));
            }
        });

        // THR (Threads)
        // Tipo nativo: int. Número de threads do processo. Sem CellFactory.
        TableColumn<Processo, Number> colThr = new TableColumn<>("THR");
        colThr.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getThreads()));
        colThr.setPrefWidth(45);

        // PPID
        // Tipo nativo: int. PID do processo pai. Sem CellFactory.
        TableColumn<Processo, Number> colPpid = new TableColumn<>("PPID");
        colPpid.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getPpid()));
        colPpid.setPrefWidth(65);

        // COMMAND
        // Exibe o cmdline completo de /proc/<pid>/cmdline (ex: "/usr/lib/firefox/firefox"),
        // igual ao htop. Quando o cmdline está vazio (processo zumbi), usa o comm como
        // fallback. A coluna usa Processo como tipo para que o CellFactory possa
        // chamar resolverComando(), que encapsula essa lógica de prioridade.
        TableColumn<Processo, Processo> colCmd = new TableColumn<>("COMMAND");
        colCmd.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleObjectProperty<>(cell.getValue()));
        colCmd.setPrefWidth(260);
        colCmd.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Processo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(resolverComando(item));
                    // Kthreads em verde (#00d700), tasks em branco (#ffffff),
                    // igual ao TreePanel para manter consistência visual entre modos.
                    String cor = (item instanceof Kthr) ? "#00d700" : "#ffffff";
                    setStyle("-fx-text-fill: " + cor + ";");
                }
            }
        });

        table.getColumns().addAll(
            colPid, colUser, colPri, colNi,
            colVirt, colRes, colShr, colState,
            colCpu, colMem, colThr, colPpid, colCmd
        );
    }

    // -------------------------------------------------------------------------
    // Estilos
    // -------------------------------------------------------------------------

    /**
     * Aplica estilo CSS geral à tabela: fundo escuro e linhas alternadas.
     */
    /** Pede foco para a tabela (chamado pelo MainFrame ao exibir este painel). */
    public void requestTableFocus() {
        table.requestFocus();
    }

    private void aplicarEstilo() {
        table.setStyle(
            "-fx-background-color: #1a1a2e;" +
            "-fx-table-cell-border-color: #2a2a4a;" +
            "-fx-border-color: transparent;"
        );

        table.setRowFactory(tv -> {
            TableRow<Processo> row = new TableRow<>() {
                @Override
                protected void updateItem(Processo item, boolean empty) {
                    super.updateItem(item, empty);
                    atualizarCorLinha(this);
                }
            };
            // Atualiza a cor diretamente quando a seleção muda (seta cima/baixo,
            // clique do mouse). Sem isso o estilo só é refeito no próximo updateItem().
            row.selectedProperty().addListener((obs, was, now) -> atualizarCorLinha(row));
            return row;
        });
    }

    private void atualizarCorLinha(TableRow<Processo> row) {
        if (row.isEmpty() || row.getItem() == null) {
            row.setStyle("-fx-background-color: #1a1a2e;");
        } else if (row.isSelected()) {
            // Azul brilhante + texto em negrito para o processo selecionado.
            row.setStyle(
                "-fx-background-color: #0087af;" +
                "-fx-font-weight: bold;"
            );
        } else {
            String bg = row.getIndex() % 2 == 0 ? "#1a1a2e" : "#1e1e38";
            row.setStyle("-fx-background-color: " + bg + "; -fx-font-weight: normal;");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers de formatação — usados apenas nos CellFactories acima
    // -------------------------------------------------------------------------

    /**
     * Resolve o texto a exibir na coluna COMMAND, priorizando cmdline sobre comm.
     *
     * Lógica idêntica ao htop, TreePanel e IOPanel:
     *   - Tasks com cmdline      → cmdline completo ("/usr/lib/firefox/firefox ...")
     *   - Tasks sem cmdline      → comm como fallback ("bash")
     *   - Kthreads (sem cmdline) → "[comm]" entre colchetes (ex: "[kworker/0:0]")
     *
     * @param p processo a resolver
     * @return string a exibir na coluna COMMAND
     */
    private String resolverComando(Processo p) {
        if (p instanceof Kthr) {
            return "[" + p.getComm() + "]";
        }
        String cmdline = p.getCmdline();
        return cmdline.isEmpty() ? p.getComm() : cmdline;
    }

    /**
     * Formata um valor em kB para string legível, idêntico ao htop:
     *   < 1 000 kB      → "NNNk"
     *   < 1 000 000 kB  → "N.NM"
     *   else            → "N.NG"
     *
     * Este método é chamado SOMENTE dentro de CellFactory (camada de exibição).
     * O valor nativo (long) já foi entregue ao JavaFX pelo CellValueFactory.
     *
     * @param kB valor em kilobytes
     * @return string formatada para exibição
     */
    private String formatarMemoria(long kB) {
        if (kB <= 0)          return "0";
        if (kB < 1_000)       return kB + "k";
        if (kB < 1_000_000)   return String.format("%.1fM", kB / 1_000.0);
        return                       String.format("%.2fG", kB / 1_000_000.0);
    }

    /**
     * Resolve um EUID para o nome de usuário do sistema.
     * Chamado SOMENTE dentro do CellFactory de USER (camada de exibição).
     * O valor subjacente da célula continua sendo o int EUID.
     *
     * @param euid effective user ID
     * @return nome do usuário, ou o EUID como string se não for possível resolver
     */
    private String resolverUsuario(int euid) {
        if (euid == 0) return "root";
        try {
            com.sun.security.auth.module.UnixSystem unix =
                new com.sun.security.auth.module.UnixSystem();
            if ((int) unix.getUid() == euid) return unix.getUsername();
        } catch (Exception ignored) { /* módulo não disponível */ }
        return String.valueOf(euid);
    }
}
