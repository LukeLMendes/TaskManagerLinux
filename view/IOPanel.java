package view;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import model.Processo;
import model.Kthr;
import model.ProcessIOStat;

import java.util.*;

/**
 * Painel de I/O no estilo htop, com hierarquia ASCII na coluna COMMAND.
 *
 * Exibe TODOS os processos (tasks + kthreads) em ordem de árvore (DFS por PPID),
 * usando os mesmos prefixos ├─ / └─ / │ do TreePanel para mostrar a hierarquia.
 * As colunas READ e WRITE vêm de /proc/<pid>/io; processos sem permissão ou
 * sem dado mostram "N/A".
 *
 * Colunas: PID | USER | READ | WRITE | COMMAND
 *
 * Regra de tipagem (mesma dos outros painéis):
 *   CellValueFactory → tipo nativo (int, double, Processo) para ordenação correta.
 *   CellFactory      → exclusivamente formatação e cor (nunca lógica de dados).
 *
 * A árvore é recalculada inteiramente a cada update() — sem estado persistente.
 */
public class IOPanel extends VBox {

    // -------------------------------------------------------------------------
    // Componentes e estado
    // -------------------------------------------------------------------------

    /**
     * Tabela plana cujo conteúdo é a listagem DFS da árvore de processos.
     * O tipo de item é Processo — READ/WRITE são obtidos via ioByPid.
     */
    private final TableView<Processo> table;

    /**
     * Lista observável que alimenta a tabela.
     * Substituída a cada update() com a nova ordem DFS.
     */
    private final ObservableList<Processo> processList;

    /**
     * Mapa PID → prefixo ASCII calculado no último update().
     * Consultado pelo CellFactory da coluna COMMAND.
     */
    private final Map<Integer, String> prefixMap = new HashMap<>();

    /**
     * Mapa PID → dados de I/O do último update().
     * Processos ausentes não têm dado de I/O (exibição: "N/A").
     */
    private final Map<Integer, ProcessIOStat> ioByPid = new HashMap<>();

    /**
     * Controla se o painel exibe a hierarquia pai-filho (true) ou uma lista
     * plana ordenada por PID (false). Alternado pela tecla F5 via MainFrame.
     * Inicia como true (árvore), igual ao TreePanel no modo MAIN.
     */
    private boolean modoArvore = true;

    // -------------------------------------------------------------------------
    // Construtor
    // -------------------------------------------------------------------------

    /**
     * Cria o painel de I/O com a tabela configurada.
     * Chamado pelo MainFrame durante a inicialização da interface.
     */
    public IOPanel() {
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
     * Reconstrói a lista de processos em ordem DFS e atualiza os dados de I/O.
     *
     * Recebe todos os processos para construir a árvore completa (mesmo que um
     * processo não tenha dado de I/O — aparece com "N/A"). Os dados de I/O
     * são indexados por PID para acesso O(1) dentro dos CellFactories.
     *
     * @param stats    dados de leitura/escrita por PID (pode ter menos entradas que processos)
     * @param tasks    lista de processos de usuário (com PPID e comm populados)
     * @param kthreads lista de kernel threads (com PPID e comm populados)
     */
    public void update(List<ProcessIOStat> stats,
                       List<Processo> tasks,
                       List<Processo> kthreads) {

        // Reconstrói o índice PID → IOStat a cada refresh.
        ioByPid.clear();
        if (stats != null) {
            for (ProcessIOStat s : stats) {
                ioByPid.put(s.getPID(), s);
            }
        }

        if (tasks == null && kthreads == null) return;

        // Combina tasks e kthreads numa lista única.
        List<Processo> all = new ArrayList<>();
        if (tasks    != null) all.addAll(tasks);
        if (kthreads != null) all.addAll(kthreads);

        int pidSelecionado = pidSelecionadoAtual();
        Map<Integer, String> novoPrefixMap = new HashMap<>();
        List<Processo> flatList;

        if (modoArvore) {
            // ── Modo árvore: DFS pré-ordem com prefixos ├─ / └─ / │ ──────────
            Map<Integer, Processo> pidMap = new HashMap<>();
            for (Processo p : all) pidMap.put(p.getPid(), p);

            Map<Integer, List<Processo>> childMap = new HashMap<>();
            List<Processo> roots = new ArrayList<>();
            for (Processo p : all) {
                boolean temPai = pidMap.containsKey(p.getPpid()) && p.getPpid() != p.getPid();
                if (temPai) childMap.computeIfAbsent(p.getPpid(), k -> new ArrayList<>()).add(p);
                else        roots.add(p);
            }
            roots.sort(Comparator.comparingInt(Processo::getPid));
            for (List<Processo> ch : childMap.values()) ch.sort(Comparator.comparingInt(Processo::getPid));

            flatList = new ArrayList<>();
            for (int i = 0; i < roots.size(); i++) {
                boolean isLast   = (i == roots.size() - 1);
                String branchStr = (roots.size() == 1) ? ""    : (isLast ? "└─ " : "├─ ");
                String contStr   = (roots.size() == 1) ? ""    : (isLast ? "   " : "│  ");
                dfs(roots.get(i), branchStr, contStr, childMap, flatList, novoPrefixMap);
            }
        } else {
            // ── Modo lista: ordenação simples por PID, sem prefixos ───────────
            // prefixMap fica vazio; o CellFactory de COMMAND não adiciona prefixo.
            flatList = new ArrayList<>(all);
            flatList.sort(Comparator.comparingInt(Processo::getPid));
        }

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
     * Percorre a sub-árvore de {@code node} em pré-ordem, preenchendo a lista
     * plana e calculando o prefixo de hierarquia ASCII para cada processo.
     *
     * A lógica de prefixo é idêntica ao TreePanel para manter consistência visual:
     *   "├─ " → nó com irmãos depois dele   |   cont. = "│  " (linha vertical)
     *   "└─ " → último filho do pai          |   cont. = "   " (espaços)
     */
    private void dfs(Processo node, String branchStr, String contStr,
                     Map<Integer, List<Processo>> childMap,
                     List<Processo> result, Map<Integer, String> prefixes) {

        prefixes.put(node.getPid(), branchStr);
        result.add(node);

        List<Processo> children = childMap.getOrDefault(node.getPid(), List.of());
        for (int i = 0; i < children.size(); i++) {
            boolean isLast   = (i == children.size() - 1);
            String childBranch = contStr + (isLast ? "└─ " : "├─ ");
            String childCont   = contStr + (isLast ? "   " : "│  ");
            dfs(children.get(i), childBranch, childCont, childMap, result, prefixes);
        }
    }

    // -------------------------------------------------------------------------
    // Configuração das colunas
    // -------------------------------------------------------------------------

    /**
     * Cria as colunas da tabela: PID | USER | READ | WRITE | COMMAND.
     *
     * USER e COMMAND usam o objeto Processo diretamente (SimpleObjectProperty<Processo>)
     * para evitar o problema de timing com getTableRow().getItem() durante o layout.
     *
     * READ e WRITE consultam ioByPid em tempo de renderização. Quando o PID não
     * está no mapa (sem dado de I/O), exibem "N/A" em cinza.
     */
    @SuppressWarnings("unchecked")
    private void configurarColunas() {

        // ── PID ──────────────────────────────────────────────────────────────
        TableColumn<Processo, Number> colPid = new TableColumn<>("PID");
        colPid.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getPid()));
        colPid.setPrefWidth(65);

        // ── USER ─────────────────────────────────────────────────────────────
        // Passa o Processo completo para que o CellFactory resolva EUID → nome.
        TableColumn<Processo, Processo> colUser = new TableColumn<>("USER");
        colUser.setCellValueFactory(cell ->
            new SimpleObjectProperty<>(cell.getValue()));
        colUser.setPrefWidth(90);
        colUser.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Processo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(resolverUsuario(item.getEUID()));
                    setStyle("-fx-text-fill: #ffff87;");
                }
            }
        });

        // ── READ ─────────────────────────────────────────────────────────────
        // Valor de /proc/<pid>/io "read_bytes". Ciano quando disponível, cinza se N/A.
        // Passa o Processo (não Number) para consultar ioByPid e distinguir "sem dado"
        // de "leu zero bytes" — ambos seriam 0.0 se usássemos Number direto.
        TableColumn<Processo, Processo> colRead = new TableColumn<>("READ");
        colRead.setCellValueFactory(cell ->
            new SimpleObjectProperty<>(cell.getValue()));
        colRead.setPrefWidth(110);
        colRead.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Processo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                ProcessIOStat stat = ioByPid.get(item.getPid());
                if (stat == null || stat.getRead() < 0) {
                    setText("N/A");
                    setStyle("-fx-text-fill: #888888;" +
                             "-fx-alignment: CENTER-RIGHT;" +
                             "-fx-background-color: transparent;");
                } else {
                    setText(formatarBytes(stat.getRead()));
                    setStyle("-fx-text-fill: #00d7ff;" +
                             "-fx-alignment: CENTER-RIGHT;" +
                             "-fx-background-color: transparent;");
                }
            }
        });

        // ── WRITE ────────────────────────────────────────────────────────────
        // Valor de /proc/<pid>/io "write_bytes". Laranja quando disponível.
        TableColumn<Processo, Processo> colWrite = new TableColumn<>("WRITE");
        colWrite.setCellValueFactory(cell ->
            new SimpleObjectProperty<>(cell.getValue()));
        colWrite.setPrefWidth(110);
        colWrite.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Processo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                ProcessIOStat stat = ioByPid.get(item.getPid());
                if (stat == null || stat.getWrite() < 0) {
                    setText("N/A");
                    setStyle("-fx-text-fill: #888888;" +
                             "-fx-alignment: CENTER-RIGHT;" +
                             "-fx-background-color: transparent;");
                } else {
                    setText(formatarBytes(stat.getWrite()));
                    setStyle("-fx-text-fill: #ffaf00;" +
                             "-fx-alignment: CENTER-RIGHT;" +
                             "-fx-background-color: transparent;");
                }
            }
        });

        // ── COMMAND ──────────────────────────────────────────────────────────
        // Prefixo ASCII de hierarquia + comando resolvido via resolverComando().
        // Kthreads em verde, tasks em branco; fonte Monospaced para alinhamento.
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
                    String prefix = prefixMap.getOrDefault(item.getPid(), "");
                    setText(prefix + resolverComando(item));
                    String cor = (item instanceof Kthr) ? "#00d700" : "#ffffff";
                    setStyle("-fx-text-fill: " + cor + "; -fx-font-family: Monospaced;");
                }
            }
        });

        table.getColumns().addAll(colPid, colUser, colRead, colWrite, colCmd);
    }

    // -------------------------------------------------------------------------
    // Estilo
    // -------------------------------------------------------------------------

    /**
     * Retorna o processo selecionado na tabela, ou null se nenhum estiver selecionado.
     * Usado pelo MainFrame.executarKill() para encerrar processos no modo IO.
     */
    public Processo getProcessoSelecionado() {
        return table.getSelectionModel().getSelectedItem();
    }

    /**
     * Define o modo de exibição: true = árvore hierárquica (DFS), false = lista por PID.
     * Chamado pelo MainFrame quando o usuário pressiona F5 no modo IO.
     * A mudança entra em vigor no próximo ciclo de update() (até 1 segundo).
     */
    public void setModoArvore(boolean modoArvore) {
        this.modoArvore = modoArvore;
    }

    /** Pede foco para a tabela (chamado pelo MainFrame ao exibir este painel). */
    public void requestTableFocus() {
        table.requestFocus();
    }

    /** Aplica o tema escuro à tabela, idêntico aos outros painéis. */
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
     * Comportamento (idêntico ao htop e ao TreePanel):
     *   - Tasks com cmdline      → cmdline completo ("/usr/bin/python3 script.py")
     *   - Tasks sem cmdline      → comm como fallback ("bash")
     *   - Kthreads (sem cmdline) → "[comm]" entre colchetes (ex: "[kworker/0:0]")
     */
    private String resolverComando(Processo p) {
        if (p instanceof Kthr) {
            return "[" + p.getComm() + "]";
        }
        String cmdline = p.getCmdline();
        return cmdline.isEmpty() ? p.getComm() : cmdline;
    }

    /** Formata um valor em bytes para string legível (B, k, M, G). */
    private String formatarBytes(double bytes) {
        if (bytes < 1_000)           return String.format("%.0f B",  bytes);
        if (bytes < 1_000_000)       return String.format("%.1f k",  bytes / 1_000.0);
        if (bytes < 1_000_000_000)   return String.format("%.1f M",  bytes / 1_000_000.0);
        return                              String.format("%.2f G",   bytes / 1_000_000_000.0);
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
