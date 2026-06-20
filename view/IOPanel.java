package view;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import model.Processo;
import model.Kthr;
import model.ProcessIOStat;
import model.ProcessoZumbi;

import java.util.*;
import java.util.function.BiConsumer;

public class IOPanel extends VBox {

    private final TableView<Processo> table;
    private final ObservableList<Processo> processList;

    private TableColumn<Processo, Number>   colPid;
    private TableColumn<Processo, Processo> colUser;
    private TableColumn<Processo, Processo> colCmd;

    private BiConsumer<String, TableColumn.SortType> sortChangeListener;
    private boolean suppressSortEvent = false;

    private final Map<Integer, String>      prefixMap = new HashMap<>();
    private final Map<Integer, ProcessIOStat> ioByPid = new HashMap<>();

    private boolean modoArvore = true;
    private String filtro = "";

    public void setFiltro(String f) { this.filtro = f.trim().toLowerCase(); }
    public void setTableContextMenu(javafx.scene.control.ContextMenu menu) { table.setContextMenu(menu); }

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

    public void update(List<ProcessIOStat> stats,
                       List<Processo> tasks,
                       List<Processo> kthreads) {

        ioByPid.clear();
        if (stats != null) {
            for (ProcessIOStat s : stats) {
                ioByPid.put(s.getPID(), s);
            }
        }

        if (tasks == null && kthreads == null) return;

        List<Processo> all = new ArrayList<>();
        if (tasks    != null) all.addAll(tasks);
        if (kthreads != null) all.addAll(kthreads);
        if (!filtro.isEmpty()) {
            all.removeIf(p -> {
                String cmd = (p.getCmdline().isEmpty() ? p.getComm() : p.getCmdline()).toLowerCase();
                return !cmd.contains(filtro) && !String.valueOf(p.getPid()).contains(filtro);
            });
        }

        int pidSelecionado = pidSelecionadoAtual();
        Map<Integer, String> novoPrefixMap = new HashMap<>();
        List<Processo> flatList;

        if (modoArvore) {
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
            for (Processo root : roots) {
                dfs(root, "", "", childMap, flatList, novoPrefixMap);
            }
        } else {
            flatList = new ArrayList<>(all);
            java.util.Comparator<Processo> comp = table.getComparator();
            flatList.sort(comp != null ? comp : Comparator.comparingInt(Processo::getPid));
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
        for (Processo p : processList) {
            if (p.getPid() == pid) {
                table.getSelectionModel().select(p);
                return;
            }
        }
    }

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

    @SuppressWarnings("unchecked")
    private void configurarColunas() {

        colPid = new TableColumn<>("PID");
        colPid.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getPid()));
        colPid.setPrefWidth(65);
        colPid.setReorderable(false);
        colPid.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-alignment: CENTER;");
                setText(empty || item == null ? null : item.toString());
            }
        });

        colUser = new TableColumn<>("USER");
        colUser.setCellValueFactory(cell ->
            new SimpleObjectProperty<>(cell.getValue()));
        colUser.setPrefWidth(90);
        colUser.setReorderable(false);
        colUser.setComparator(Comparator.comparingInt(Processo::getEUID));
        colUser.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Processo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(resolverUsuario(item.getEUID()));
                    setStyle("-fx-text-fill: #ffff87; -fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<Processo, Processo> colRead = new TableColumn<>("READ");
        colRead.setCellValueFactory(cell ->
            new SimpleObjectProperty<>(cell.getValue()));
        colRead.setPrefWidth(85);
        colRead.setReorderable(false);
        colRead.setComparator((a, b) -> {
            ProcessIOStat sa = ioByPid.get(a.getPid());
            ProcessIOStat sb = ioByPid.get(b.getPid());
            double ra = (sa == null || sa.getRead() < 0) ? -1 : sa.getRead();
            double rb = (sb == null || sb.getRead() < 0) ? -1 : sb.getRead();
            return Double.compare(ra, rb);
        });
        colRead.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Processo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                ProcessIOStat stat = ioByPid.get(item.getPid());
                if (stat == null || stat.getRead() < 0) {
                    setText("N/A");
                    setStyle("-fx-text-fill: #555577; -fx-alignment: CENTER; -fx-background-color: transparent;");
                } else {
                    setText(formatarBytes(stat.getRead()));
                    setStyle("-fx-text-fill: #00d7ff; -fx-alignment: CENTER; -fx-background-color: transparent;");
                }
            }
        });

        TableColumn<Processo, Processo> colWrite = new TableColumn<>("WRITE");
        colWrite.setCellValueFactory(cell ->
            new SimpleObjectProperty<>(cell.getValue()));
        colWrite.setPrefWidth(85);
        colWrite.setReorderable(false);
        colWrite.setComparator((a, b) -> {
            ProcessIOStat sa = ioByPid.get(a.getPid());
            ProcessIOStat sb = ioByPid.get(b.getPid());
            double wa = (sa == null || sa.getWrite() < 0) ? -1 : sa.getWrite();
            double wb = (sb == null || sb.getWrite() < 0) ? -1 : sb.getWrite();
            return Double.compare(wa, wb);
        });
        colWrite.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Processo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                ProcessIOStat stat = ioByPid.get(item.getPid());
                if (stat == null || stat.getWrite() < 0) {
                    setText("N/A");
                    setStyle("-fx-text-fill: #555577; -fx-alignment: CENTER; -fx-background-color: transparent;");
                } else {
                    setText(formatarBytes(stat.getWrite()));
                    setStyle("-fx-text-fill: #ffaf00; -fx-alignment: CENTER; -fx-background-color: transparent;");
                }
            }
        });

        colCmd = new TableColumn<>("COMMAND");
        colCmd.setCellValueFactory(cell ->
            new SimpleObjectProperty<>(cell.getValue()));
        colCmd.setPrefWidth(260);
        colCmd.setReorderable(false);
        colCmd.setComparator((a, b) ->
            resolverComando(a).compareToIgnoreCase(resolverComando(b)));
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
                    String cor = (item instanceof Kthr)          ? "#00d700"
                               : (item instanceof ProcessoZumbi) ? "#ff5555"
                               : "#ffffff";
                    setStyle("-fx-text-fill: " + cor + "; -fx-font-family: Monospaced;");
                }
            }
        });

        table.getColumns().addAll(colPid, colUser, colRead, colWrite, colCmd);

        table.setSortPolicy(tv -> false);

        table.getSortOrder().addListener((ListChangeListener<TableColumn<Processo, ?>>) change -> {
            if (suppressSortEvent || sortChangeListener == null) return;
            if (table.getSortOrder().isEmpty()) return;
            TableColumn<Processo, ?> sorted = table.getSortOrder().get(0);
            String colName = null;
            if (sorted == colPid)  colName = "PID";
            if (sorted == colUser) colName = "USER";
            if (sorted == colCmd)  colName = "COMMAND";
            if (colName != null) sortChangeListener.accept(colName, sorted.getSortType());
        });

        colPid.sortTypeProperty().addListener((obs, old, newType) -> {
            if (suppressSortEvent || sortChangeListener == null) return;
            if (table.getSortOrder().isEmpty() || table.getSortOrder().get(0) != colPid) return;
            sortChangeListener.accept("PID", newType);
        });
        colUser.sortTypeProperty().addListener((obs, old, newType) -> {
            if (suppressSortEvent || sortChangeListener == null) return;
            if (table.getSortOrder().isEmpty() || table.getSortOrder().get(0) != colUser) return;
            sortChangeListener.accept("USER", newType);
        });
        colCmd.sortTypeProperty().addListener((obs, old, newType) -> {
            if (suppressSortEvent || sortChangeListener == null) return;
            if (table.getSortOrder().isEmpty() || table.getSortOrder().get(0) != colCmd) return;
            sortChangeListener.accept("COMMAND", newType);
        });
    }

    public void setSortChangeListener(BiConsumer<String, TableColumn.SortType> listener) {
        this.sortChangeListener = listener;
    }

    public void applySortByColumn(String column, TableColumn.SortType type) {
        suppressSortEvent = true;
        try {
            if      ("PID"    .equals(column)) { colPid .setSortType(type); table.getSortOrder().setAll(colPid);  }
            else if ("USER"   .equals(column)) { colUser.setSortType(type); table.getSortOrder().setAll(colUser); }
            else if ("COMMAND".equals(column)) { colCmd .setSortType(type); table.getSortOrder().setAll(colCmd);  }
        } finally {
            suppressSortEvent = false;
        }
    }

    public Processo getProcessoSelecionado() {
        return table.getSelectionModel().getSelectedItem();
    }

    public void setModoArvore(boolean modoArvore) {
        this.modoArvore = modoArvore;
        if (modoArvore) {
            table.getSortOrder().clear();
            table.setSortPolicy(tv -> false);
        } else {
            table.setSortPolicy(null);
        }
    }

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

    private String resolverComando(Processo p) {
        if (p instanceof Kthr) {
            return p.getComm();
        }
        String cmdline = p.getCmdline();
        return cmdline.isEmpty() ? p.getComm() : cmdline;
    }

    private String formatarBytes(double bytes) {
        if (bytes < 1_000)           return String.format("%.0f B",  bytes);
        if (bytes < 1_000_000)       return String.format("%.1f k",  bytes / 1_000.0);
        if (bytes < 1_000_000_000)   return String.format("%.1f M",  bytes / 1_000_000.0);
        return                              String.format("%.2f G",   bytes / 1_000_000_000.0);
    }

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
