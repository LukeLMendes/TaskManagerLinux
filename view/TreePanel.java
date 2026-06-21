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
import model.ProcessoZumbi;

import java.util.*;

public class TreePanel extends VBox {

    private final TableView<Processo> table;
    private final ObservableList<Processo> processList;
    private final Map<Integer, String> prefixMap = new HashMap<>();
    private Map<Integer, String> anotacoes = new HashMap<>();
    private double memTotalKB = 1.0;
    private String filtro = "";

    public void setFiltro(String f) { this.filtro = f.trim().toLowerCase(); }
    public void setAnotacoes(Map<Integer, String> anotacoes) { this.anotacoes = anotacoes; }
    public void setTableContextMenu(javafx.scene.control.ContextMenu menu) { table.setContextMenu(menu); }

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

    public void update(List<Processo> tasks, List<Processo> kthreads, double memTotalKB) {
        this.memTotalKB = memTotalKB > 0 ? memTotalKB : 1.0;

        List<Processo> all = new ArrayList<>(tasks);
        all.addAll(kthreads);
        if (!filtro.isEmpty()) {
            all.removeIf(p -> {
                String cmd = resolverComando(p).toLowerCase();
                return !cmd.contains(filtro) && !String.valueOf(p.getPid()).contains(filtro);
            });
        }

        Map<Integer, Processo> pidMap = new HashMap<>();
        for (Processo p : all) pidMap.put(p.getPid(), p);

        Map<Integer, List<Processo>> childMap = new HashMap<>();
        List<Processo> roots = new ArrayList<>();

        for (Processo p : all) {
            boolean temPai = pidMap.containsKey(p.getPpid()) && p.getPpid() != p.getPid();
            if (temPai) {
                childMap.computeIfAbsent(p.getPpid(), k -> new ArrayList<>()).add(p);
            } else {
                roots.add(p);
            }
        }

        roots.sort(Comparator.comparingInt(Processo::getPid));
        for (List<Processo> children : childMap.values()) {
            children.sort(Comparator.comparingInt(Processo::getPid));
        }

        List<Processo> flatList = new ArrayList<>();
        Map<Integer, String> novoPrefixMap = new HashMap<>();

        for (Processo root : roots) {
            dfs(root, "", "", childMap, flatList, novoPrefixMap);
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
            boolean isLast = (i == children.size() - 1);
            String childBranch = contStr + (isLast ? "└─ " : "├─ ");
            String childCont   = contStr + (isLast ? "   " : "│  ");
            dfs(children.get(i), childBranch, childCont, childMap, result, prefixes);
        }
    }

    public Processo getProcessoSelecionado() {
        return table.getSelectionModel().getSelectedItem();
    }

    public void requestTableFocus() {
        table.requestFocus();
    }

    @SuppressWarnings("unchecked")
    private void configurarColunas() {

        TableColumn<Processo, Number> colPid = new TableColumn<>("PID");
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

        TableColumn<Processo, Number> colUser = new TableColumn<>("USER");
        colUser.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getEUID()));
        colUser.setPrefWidth(90);
        colUser.setReorderable(false);
        colUser.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); }
                else {
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
            Processo p = cell.getValue();
            return new SimpleLongProperty((p instanceof Task t) ? t.getVirt() : 0L);
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
            Processo p = cell.getValue();
            return new SimpleLongProperty((p instanceof Task t) ? t.getRes() : 0L);
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
            Processo p = cell.getValue();
            return new SimpleLongProperty((p instanceof Task t) ? t.getShr() : 0L);
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
                if (empty || item == null) { setText(null); setStyle(""); }
                else {
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
            Processo p = cell.getValue();
            return new SimpleDoubleProperty((p instanceof Task t) ? t.getCpuPct() : 0.0);
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
            Processo p = cell.getValue();
            double pct = (p instanceof Task t) ? (t.getRes() / memTotalKB) * 100.0 : 0.0;
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

        TableColumn<Processo, Processo> colCmd = new TableColumn<>("COMMAND");
        colCmd.setCellValueFactory(cell ->
            new SimpleObjectProperty<>(cell.getValue()));
        colCmd.setPrefWidth(260);
        colCmd.setReorderable(false);
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

        table.getColumns().addAll(
            colPid, colUser, colPri, colNi,
            colVirt, colRes, colShr, colState,
            colCpu, colMem, colThr, colNote, colCmd
        );

        table.setSortPolicy(tv -> false);
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
        } catch (Exception ignored) {}
        return String.valueOf(euid);
    }
}
