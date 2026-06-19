package view;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import model.Processo;
import model.Task;
import model.Kthr;

import java.util.*;

/**
 * Painel de árvore de processos no estilo do htop (F5).
 *
 * Usa um TableView PLANO (não TreeTableView) onde a hierarquia é representada
 * por caracteres Unicode box-drawing na coluna COMMAND, igual ao htop:
 *
 *   systemd
 *   ├─ systemd-journald
 *   ├─ avahi-daemon
 *   │  └─ avahi-daemon (chroot helper)
 *   └─ NetworkManager
 *      ├─ wpa_supplicant
 *      └─ dhclient
 *
 * Vantagens desta abordagem sobre TreeTableView:
 *   - Não há setas de expand/colapso que poluem a visualização.
 *   - A hierarquia fica na coluna COMMAND, mesmo lugar onde o htop mostra.
 *   - A fonte monospace garante que os caracteres │, ├, └, ─ se alinhem.
 *
 * Algoritmo:
 *   1. Constrói um mapa PID → filhos (ordenados por PID) usando PPID.
 *   2. Identifica as raízes (processos cujo pai não está na lista).
 *   3. Percorre em DFS pré-ordem, calculando o prefixo de cada nó.
 *   4. O prefixo acumula "├─ " / "└─ " para o nó atual e "│  " / "   "
 *      para a continuação dos filhos (dependendo se o pai é último ou não).
 */
public class TreePanel extends VBox {

    // -------------------------------------------------------------------------
    // Componentes e estado
    // -------------------------------------------------------------------------

    /**
     * Tabela plana que exibe os processos na ordem DFS da hierarquia.
     * Não usa TreeTableView — a hierarquia é visual, só no texto da coluna COMMAND.
     */
    private final TableView<Processo> table;

    /**
     * Lista observável que alimenta a tabela.
     * Substituída completamente a cada refresh (ordem DFS + prefixos recalculados).
     */
    private final ObservableList<Processo> processList;

    /**
     * Mapa PID → prefixo ASCII calculado na última chamada de update().
     *
     * Exemplos:
     *   PID 1   (raiz única)       → ""
     *   PID 386 (filho de 1, ñ-último) → "├─ "
     *   PID 782 (filho de 721, último)  → "│  └─ "
     *   PID 790 (filho de 789, ñ-último, 789 era último de 1) → "   ├─ "
     *
     * Cada CellFactory da coluna COMMAND consulta este mapa para montar o texto.
     */
    private final Map<Integer, String> prefixMap = new HashMap<>();

    /** Memória total em kB, necessária para calcular MEM% na tabela. */
    private double memTotalKB = 1.0;

    // -------------------------------------------------------------------------
    // Construtor
    // -------------------------------------------------------------------------

    /**
     * Cria o painel com a TableView configurada.
     * Chamado pelo MainFrame durante a inicialização da interface.
     */
    public TreePanel() {
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
     * Reconstrói a lista plana de processos na ordem DFS da hierarquia pai-filho.
     *
     * A cada chamada, o prefixMap e a processList são completamente reconstruídos.
     * Não há estado persistente entre chamadas (não há expand/collapse para preservar).
     *
     * Regras dos prefixos (caracteres Unicode box-drawing):
     *   ├─   U+251C U+2500 : este nó tem irmãos depois dele
     *   └─   U+2514 U+2500 : este nó é o último filho do pai
     *   │    U+2502         : linha de continuação de um ancestral não-último
     *   (espaço)            : continuação de um ancestral que era o último filho
     *
     * @param tasks      lista de processos de usuário
     * @param kthreads   lista de kernel threads
     * @param memTotalKB memória total em kB (para calcular MEM%)
     */
    public void update(List<Processo> tasks, List<Processo> kthreads, double memTotalKB) {
        this.memTotalKB = memTotalKB > 0 ? memTotalKB : 1.0;

        // Combina todos os processos numa lista única.
        List<Processo> all = new ArrayList<>(tasks);
        all.addAll(kthreads);

        // Mapa PID → Processo: usado para verificar se o pai existe na lista.
        Map<Integer, Processo> pidMap = new HashMap<>();
        for (Processo p : all) pidMap.put(p.getPid(), p);

        // Mapa PID → lista de filhos diretos, para percorrer a hierarquia de cima pra baixo.
        Map<Integer, List<Processo>> childMap = new HashMap<>();
        List<Processo> roots = new ArrayList<>();

        for (Processo p : all) {
            // É raiz se o PPID não existe na lista, ou se PPID == PID (caso especial do PID 0).
            boolean temPai = pidMap.containsKey(p.getPpid()) && p.getPpid() != p.getPid();
            if (temPai) {
                childMap.computeIfAbsent(p.getPpid(), k -> new ArrayList<>()).add(p);
            } else {
                roots.add(p);
            }
        }

        // Ordena raízes e filhos por PID para que a ordem seja estável entre refreshes.
        roots.sort(Comparator.comparingInt(Processo::getPid));
        for (List<Processo> children : childMap.values()) {
            children.sort(Comparator.comparingInt(Processo::getPid));
        }

        // DFS pré-ordem: raiz primeiro, depois filhos da esquerda para direita.
        List<Processo> flatList = new ArrayList<>();
        Map<Integer, String> novoPrefixMap = new HashMap<>();

        for (int i = 0; i < roots.size(); i++) {
            boolean isLast = (i == roots.size() - 1);
            // Se há apenas 1 raiz (PID 1), ela fica sem prefixo (igual ao htop).
            // Se há múltiplas raízes, elas recebem ├─ / └─ entre si.
            String branchStr = (roots.size() == 1) ? ""      : (isLast ? "└─ " : "├─ ");
            String contStr   = (roots.size() == 1) ? ""      : (isLast ? "   " : "│  ");
            dfs(roots.get(i), branchStr, contStr, childMap, flatList, novoPrefixMap);
        }

        int pidSelecionado = pidSelecionadoAtual();
        prefixMap.clear();
        prefixMap.putAll(novoPrefixMap);
        processList.setAll(flatList);
        restaurarSelecao(pidSelecionado);
    }

    private int pidSelecionadoAtual() {
        Processo sel = table.getSelectionModel().getSelectedItem();
        return (sel != null) ? sel.getPid() : -1;
    }

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
     * Percorre a sub-árvore com raiz em {@code node} em pré-ordem e preenche
     * {@code result} (ordem de exibição) e {@code prefixes} (prefixo de cada PID).
     *
     * @param node       processo atual
     * @param branchStr  prefixo completo deste nó (ex: "│  ├─ ")
     * @param contStr    prefixo de continuação para os filhos deste nó (ex: "│  │  ")
     *                   — se este nó for não-último: cont = parent_cont + "│  "
     *                   — se este nó for o último:   cont = parent_cont + "   "
     */
    private void dfs(Processo node, String branchStr, String contStr,
                     Map<Integer, List<Processo>> childMap,
                     List<Processo> result, Map<Integer, String> prefixes) {

        prefixes.put(node.getPid(), branchStr);
        result.add(node);

        List<Processo> children = childMap.getOrDefault(node.getPid(), List.of());
        for (int i = 0; i < children.size(); i++) {
            boolean isLast = (i == children.size() - 1);
            // ├─  para não-último filho, └─  para o último.
            // │   continua a linha vertical do pai se ele não era o último;
            //     espaços se o pai era o último (não há linha vertical a continuar).
            String childBranch = contStr + (isLast ? "└─ " : "├─ ");
            String childCont   = contStr + (isLast ? "   " : "│  ");
            dfs(children.get(i), childBranch, childCont, childMap, result, prefixes);
        }
    }

    /**
     * Retorna o processo selecionado na tabela, ou null se nenhum estiver selecionado.
     * Usado pelo MainFrame para o botão Kill no modo árvore.
     */
    public Processo getProcessoSelecionado() {
        return table.getSelectionModel().getSelectedItem();
    }

    /** Pede foco para a tabela (chamado pelo MainFrame ao exibir este painel). */
    public void requestTableFocus() {
        table.requestFocus();
    }

    // -------------------------------------------------------------------------
    // Configuração das colunas
    // -------------------------------------------------------------------------

    /**
     * Cria as colunas da tabela.
     *
     * Igual ao MainPanel, mas sem a coluna PPID (a hierarquia já é visual no COMMAND).
     * A coluna COMMAND usa SimpleObjectProperty<Processo> para que o CellFactory
     * possa consultar prefixMap e verificar instanceof Kthr com segurança, sem
     * depender de getTableRow().getItem() (que pode ser nulo no momento do layout).
     */
    @SuppressWarnings("unchecked")
    private void configurarColunas() {

        // ── PID ──────────────────────────────────────────────────────────────
        TableColumn<Processo, Number> colPid = new TableColumn<>("PID");
        colPid.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getPid()));
        colPid.setPrefWidth(65);

        // ── USER ─────────────────────────────────────────────────────────────
        // Armazena EUID (int); CellFactory resolve para nome de usuário.
        TableColumn<Processo, Number> colUser = new TableColumn<>("USER");
        colUser.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getEUID()));
        colUser.setPrefWidth(90);
        colUser.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); }
                else {
                    setText(resolverUsuario(item.intValue()));
                    setStyle("-fx-text-fill: #ffff87;");
                }
            }
        });

        // ── PRI ──────────────────────────────────────────────────────────────
        TableColumn<Processo, Number> colPri = new TableColumn<>("PRI");
        colPri.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getPri()));
        colPri.setPrefWidth(45);

        // ── NI ───────────────────────────────────────────────────────────────
        TableColumn<Processo, Number> colNi = new TableColumn<>("NI");
        colNi.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getNice()));
        colNi.setPrefWidth(45);

        // ── VIRT ─────────────────────────────────────────────────────────────
        // Kthreads não têm VmSize: retorna 0 para eles.
        TableColumn<Processo, Number> colVirt = new TableColumn<>("VIRT");
        colVirt.setCellValueFactory(cell -> {
            Processo p = cell.getValue();
            return new SimpleLongProperty((p instanceof Task t) ? t.getVirt() : 0L);
        });
        colVirt.setPrefWidth(75);
        colVirt.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : formatarMemoria(item.longValue()));
            }
        });

        // ── RES ──────────────────────────────────────────────────────────────
        TableColumn<Processo, Number> colRes = new TableColumn<>("RES");
        colRes.setCellValueFactory(cell -> {
            Processo p = cell.getValue();
            return new SimpleLongProperty((p instanceof Task t) ? t.getRes() : 0L);
        });
        colRes.setPrefWidth(75);
        colRes.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : formatarMemoria(item.longValue()));
            }
        });

        // ── SHR ──────────────────────────────────────────────────────────────
        TableColumn<Processo, Number> colShr = new TableColumn<>("SHR");
        colShr.setCellValueFactory(cell -> {
            Processo p = cell.getValue();
            return new SimpleLongProperty((p instanceof Task t) ? t.getShr() : 0L);
        });
        colShr.setPrefWidth(75);
        colShr.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : formatarMemoria(item.longValue()));
            }
        });

        // ── S (State) ────────────────────────────────────────────────────────
        // Armazena o code point do char para usar SimpleIntegerProperty.
        TableColumn<Processo, Number> colState = new TableColumn<>("S");
        colState.setCellValueFactory(cell ->
            new SimpleIntegerProperty((int) cell.getValue().getState()));
        colState.setPrefWidth(30);
        colState.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else {
                    char estado = (char) item.intValue();
                    setText(String.valueOf(estado));
                    String cor = switch (estado) {
                        case 'R' -> "#00ff00"; // verde   — rodando
                        case 'D' -> "#ffff00"; // amarelo — aguardando I/O
                        case 'Z' -> "#ff4444"; // vermelho — zombie
                        case 'T' -> "#ff8700"; // laranja  — parado (SIGSTOP)
                        default  -> "#ffffff"; // branco   — sleeping e outros
                    };
                    setStyle("-fx-text-fill: " + cor + ";" +
                             "-fx-alignment: center;" +
                             "-fx-background-color: transparent;");
                }
            }
        });

        // ── CPU% ─────────────────────────────────────────────────────────────
        // Kthreads não têm CPU% calculado: retorna 0.0 para eles.
        TableColumn<Processo, Number> colCpu = new TableColumn<>("CPU%");
        colCpu.setCellValueFactory(cell -> {
            Processo p = cell.getValue();
            return new SimpleDoubleProperty((p instanceof Task t) ? t.getCpuPct() : 0.0);
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

        // ── MEM% ─────────────────────────────────────────────────────────────
        TableColumn<Processo, Number> colMem = new TableColumn<>("MEM%");
        colMem.setCellValueFactory(cell -> {
            Processo p = cell.getValue();
            double pct = (p instanceof Task t) ? (t.getRes() / memTotalKB) * 100.0 : 0.0;
            return new SimpleDoubleProperty(pct);
        });
        colMem.setPrefWidth(60);
        colMem.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null
                    : String.format("%.1f", item.doubleValue()));
            }
        });

        // ── THR ──────────────────────────────────────────────────────────────
        TableColumn<Processo, Number> colThr = new TableColumn<>("THR");
        colThr.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getThreads()));
        colThr.setPrefWidth(45);

        // ── COMMAND ──────────────────────────────────────────────────────────
        // Tipo da coluna: Processo (não String) para que o CellFactory possa:
        //   1. Consultar prefixMap para obter o prefixo ASCII de hierarquia.
        //   2. Verificar instanceof Kthr para aplicar cor verde e formato [comm].
        //   3. Escolher entre cmdline (completo) e comm (fallback) via resolverComando().
        TableColumn<Processo, Processo> colCmd = new TableColumn<>("COMMAND");
        colCmd.setCellValueFactory(cell ->
            new SimpleObjectProperty<>(cell.getValue()));
        colCmd.setPrefWidth(260);
        colCmd.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Processo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    // Prefixo ASCII (ex: "│  ├─ ") + comando resolvido.
                    // A fonte Monospaced garante alinhamento correto dos caracteres │ ├ └.
                    String prefix = prefixMap.getOrDefault(item.getPid(), "");
                    setText(prefix + resolverComando(item));
                    // Kthreads em verde (#00d700), tasks em branco (#ffffff).
                    String cor = (item instanceof Kthr) ? "#00d700" : "#ffffff";
                    setStyle("-fx-text-fill: " + cor + "; -fx-font-family: Monospaced;");
                }
            }
        });

        table.getColumns().addAll(
            colPid, colUser, colPri, colNi,
            colVirt, colRes, colShr, colState,
            colCpu, colMem, colThr, colCmd
        );
    }

    // -------------------------------------------------------------------------
    // Estilo
    // -------------------------------------------------------------------------

    /**
     * Aplica o tema escuro à tabela — idêntico ao MainPanel.
     */
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
            row.selectedProperty().addListener((obs, was, now) -> atualizarCorLinha(row));
            return row;
        });
    }

    private void atualizarCorLinha(TableRow<Processo> row) {
        if (row.isEmpty() || row.getItem() == null) {
            row.setStyle("-fx-background-color: #1a1a2e;");
        } else if (row.isSelected()) {
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
    // Helpers de formatação
    // -------------------------------------------------------------------------

    /**
     * Resolve o texto a exibir na coluna COMMAND, priorizando cmdline sobre comm.
     *
     * Lógica idêntica ao htop:
     *   - Tasks com cmdline     → cmdline completo ("/usr/lib/firefox/firefox ...")
     *   - Tasks sem cmdline     → comm como fallback ("bash")
     *   - Kthreads (sem cmdline)→ "[comm]" entre colchetes (ex: "[kworker/0:0]")
     *
     * O colchete identifica visualmente kthreads quando a cor verde não está disponível
     * (ex: cópia de texto simples), replicando o comportamento do htop.
     *
     * @param p processo a resolver
     * @return string a exibir na coluna COMMAND (sem o prefixo de hierarquia)
     */
    private String resolverComando(Processo p) {
        if (p instanceof Kthr) {
            // Kthreads nunca têm cmdline — exibe [comm] entre colchetes como o htop.
            return "[" + p.getComm() + "]";
        }
        String cmdline = p.getCmdline();
        return cmdline.isEmpty() ? p.getComm() : cmdline;
    }

    /** Formata um valor em kB para string legível (k, M, G). */
    private String formatarMemoria(long kB) {
        if (kB <= 0)          return "0";
        if (kB < 1_000)       return kB + "k";
        if (kB < 1_000_000)   return String.format("%.1fM", kB / 1_000.0);
        return                       String.format("%.2fG", kB / 1_000_000.0);
    }

    /** Resolve um EUID para o nome de usuário do sistema. */
    private String resolverUsuario(int euid) {
        if (euid == 0) return "root";
        try {
            com.sun.security.auth.module.UnixSystem unix =
                new com.sun.security.auth.module.UnixSystem();
            if ((int) unix.getUid() == euid) return unix.getUsername();
        } catch (Exception ignored) {}
        return String.valueOf(euid);
    }
}
