package view;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import model.Processo;
import model.Task;
import model.Kthr;
import model.ProcessoZumbi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class MainPanel extends VBox {

    private final TableView<Processo> table;
    private final ObservableList<Processo> processList;

    private TableColumn<Processo, Number>   colPid;
    private TableColumn<Processo, Number>   colUser;
    private TableColumn<Processo, Processo> colCmd;

    private BiConsumer<String, TableColumn.SortType> sortChangeListener;
    private boolean suppressSortEvent = false;

    private String filtro = "";
    private Map<Integer, String> anotacoes = new HashMap<>();

    public void setFiltro(String f) { this.filtro = f.trim().toLowerCase(); }
    public void setAnotacoes(Map<Integer, String> anotacoes) { this.anotacoes = anotacoes; }
    public void setTableContextMenu(javafx.scene.control.ContextMenu menu) { table.setContextMenu(menu); }

    private double memTotalKB = 1.0;

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

    public void update(List<Processo> tasks, List<Processo> kthreads, double memTotalKB) {
        this.memTotalKB = memTotalKB > 0 ? memTotalKB : 1.0;
        int pidSelecionado = pidSelecionadoAtual();
        List<Processo> todos = new java.util.ArrayList<>(tasks);
        if (kthreads != null) todos.addAll(kthreads);
        if (!filtro.isEmpty()) {
            todos.removeIf(p -> {
                String cmd = resolverComando(p).toLowerCase();
                return !cmd.contains(filtro) && !String.valueOf(p.getPid()).contains(filtro);
            });
        }
        java.util.Comparator<Processo> comp = table.getComparator();
        todos.sort(comp != null ? comp : java.util.Comparator.comparingInt(Processo::getPid));
        processList.setAll(todos);
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

    public Processo getProcessoSelecionado() {
        return table.getSelectionModel().getSelectedItem();
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
            new SimpleIntegerProperty(cell.getValue().getEUID()));
        colUser.setPrefWidth(90);
        colUser.setReorderable(false);
        colUser.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(resolverUsuario(item.intValue()));
                    setStyle("-fx-text-fill: #ffff87; -fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<Processo, Number> colPri = new TableColumn<>("PRI");
        colPri.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getPri()));
        colPri.setPrefWidth(45);
        colPri.setReorderable(false);
        colPri.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-alignment: CENTER;");
                setText(empty || item == null ? null : item.toString());
            }
        });

        TableColumn<Processo, Number> colNi = new TableColumn<>("NI");
        colNi.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getNice()));
        colNi.setPrefWidth(45);
        colNi.setReorderable(false);
        colNi.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-alignment: CENTER;");
                setText(empty || item == null ? null : item.toString());
            }
        });

        TableColumn<Processo, Number> colVirt = new TableColumn<>("VIRT");
        colVirt.setCellValueFactory(cell -> {
            long v = (cell.getValue() instanceof Task t) ? t.getVirt() : 0L;
            return new SimpleLongProperty(v);
        });
        colVirt.setPrefWidth(75);
        colVirt.setReorderable(false);
        colVirt.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-alignment: CENTER;");
                setText((empty || item == null) ? null : formatarMemoria(item.longValue()));
            }
        });

        TableColumn<Processo, Number> colRes = new TableColumn<>("RES");
        colRes.setCellValueFactory(cell -> {
            long v = (cell.getValue() instanceof Task t) ? t.getRes() : 0L;
            return new SimpleLongProperty(v);
        });
        colRes.setPrefWidth(75);
        colRes.setReorderable(false);
        colRes.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-alignment: CENTER;");
                setText((empty || item == null) ? null : formatarMemoria(item.longValue()));
            }
        });

        TableColumn<Processo, Number> colShr = new TableColumn<>("SHR");
        colShr.setCellValueFactory(cell -> {
            long v = (cell.getValue() instanceof Task t) ? t.getShr() : 0L;
            return new SimpleLongProperty(v);
        });
        colShr.setPrefWidth(75);
        colShr.setReorderable(false);
        colShr.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-alignment: CENTER;");
                setText((empty || item == null) ? null : formatarMemoria(item.longValue()));
            }
        });

        TableColumn<Processo, Number> colState = new TableColumn<>("S");
        colState.setCellValueFactory(cell ->
            new SimpleIntegerProperty((int) cell.getValue().getState()));
        colState.setPrefWidth(30);
        colState.setReorderable(false);
        colState.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    char estado = (char) item.intValue();
                    setText(String.valueOf(estado));
                    String cor = switch (estado) {
                        case 'R' -> "#00ff00";
                        case 'D' -> "#ffff00";
                        case 'Z' -> "#ff4444";
                        case 'T' -> "#ff8700";
                        default  -> "#ffffff";
                    };
                    setStyle("-fx-text-fill: " + cor + ";" +
                             "-fx-alignment: center;" +
                             "-fx-background-color: transparent;");
                }
            }
        });

        TableColumn<Processo, Number> colCpu = new TableColumn<>("CPU%");
        colCpu.setCellValueFactory(cell -> {
            double pct = (cell.getValue() instanceof Task t) ? t.getCpuPct() : 0.0;
            return new SimpleDoubleProperty(pct);
        });
        colCpu.setPrefWidth(60);
        colCpu.setReorderable(false);
        colCpu.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-alignment: CENTER;");
                setText((empty || item == null) ? null
                    : String.format("%.1f", item.doubleValue()));
            }
        });

        TableColumn<Processo, Number> colMem = new TableColumn<>("MEM%");
        colMem.setCellValueFactory(cell -> {
            double pct = (cell.getValue() instanceof Task t)
                ? (t.getRes() / memTotalKB) * 100.0
                : 0.0;
            return new SimpleDoubleProperty(pct);
        });
        colMem.setPrefWidth(60);
        colMem.setReorderable(false);
        colMem.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-alignment: CENTER;");
                setText((empty || item == null) ? null
                    : String.format("%.1f", item.doubleValue()));
            }
        });

        TableColumn<Processo, Number> colThr = new TableColumn<>("THR");
        colThr.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getThreads()));
        colThr.setPrefWidth(45);
        colThr.setReorderable(false);
        colThr.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-alignment: CENTER;");
                setText(empty || item == null ? null : item.toString());
            }
        });

        TableColumn<Processo, Number> colPpid = new TableColumn<>("PPID");
        colPpid.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getPpid()));
        colPpid.setPrefWidth(65);
        colPpid.setReorderable(false);
        colPpid.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-alignment: CENTER;");
                setText(empty || item == null ? null : item.toString());
            }
        });

        TableColumn<Processo, String> colNote = new TableColumn<>("NOTE");
        colNote.setCellValueFactory(cell ->
            new SimpleStringProperty(anotacoes.getOrDefault(cell.getValue().getPid(), "")));
        colNote.setPrefWidth(80);
        colNote.setReorderable(false);
        colNote.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #ffaf00; -fx-alignment: CENTER;");
                }
            }
        });

        colCmd = new TableColumn<>("COMMAND");
        colCmd.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleObjectProperty<>(cell.getValue()));
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
                    setText(resolverComando(item));
                    String cor = (item instanceof Kthr)          ? "#00d700"
                               : (item instanceof ProcessoZumbi) ? "#ff5555"
                               : "#ffffff";
                    setStyle("-fx-text-fill: " + cor + ";");
                }
            }
        });

        table.getColumns().addAll(
            colPid, colUser, colPri, colNi,
            colVirt, colRes, colShr, colState,
            colCpu, colMem, colThr, colPpid, colNote, colCmd
        );

        colPid.setSortType(TableColumn.SortType.ASCENDING);
        table.getSortOrder().setAll(colPid);

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

    private String formatarMemoria(long kB) {
        if (kB <= 0)          return "0";
        if (kB < 1_000)       return kB + "k";
        if (kB < 1_000_000)   return String.format("%.1fM", kB / 1_000.0);
        return                       String.format("%.2fG", kB / 1_000_000.0);
    }

    private String resolverUsuario(int euid) {
        if (euid == 0) return "root";
        try {
            com.sun.security.auth.module.UnixSystem unix =
                new com.sun.security.auth.module.UnixSystem();
            if ((int) unix.getUid() == euid) return unix.getUsername();
        } catch (Exception ignored) { }
        return String.valueOf(euid);
    }
}
