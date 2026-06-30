package com.sqlitegui.ui;

import com.sqlitegui.db.CsvSupport;
import com.sqlitegui.db.Database;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** Top-level application view: menus, schema sidebar, and the feature tabs. */
public final class MainView {

    private final Stage stage;
    private final BorderPane root = new BorderPane();
    private final TreeView<SchemaNode> schemaTree = new TreeView<>();
    private final Label statusBar = new Label("No database open.");

    private final DataTab dataTab = new DataTab();
    private final StructureTab structureTab = new StructureTab();
    private final SqlTab sqlTab;
    private final TabPane tabPane = new TabPane();

    private Database db;

    public MainView(Stage stage) {
        this.stage = stage;
        this.sqlTab = new SqlTab(() -> db, this::refreshSchema);
        build();
    }

    private void build() {
        root.setTop(buildMenuBar());

        schemaTree.setShowRoot(false);
        schemaTree.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> onSchemaSelect(selected));

        Tab data = new Tab("Data", dataTab.getRoot());
        Tab structure = new Tab("Structure", structureTab.getRoot());
        Tab sql = new Tab("SQL", sqlTab.getRoot());
        tabPane.getTabs().addAll(data, structure, sql);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        SplitPane split = new SplitPane(schemaTree, tabPane);
        split.setDividerPositions(0.22);
        SplitPane.setResizableWithParent(schemaTree, false);

        root.setCenter(split);

        HBox statusWrap = new HBox(statusBar);
        statusWrap.getStyleClass().add("status-bar");
        root.setBottom(statusWrap);
    }

    private MenuBar buildMenuBar() {
        Menu fileMenu = new Menu("File");

        MenuItem open = new MenuItem("Open Database…");
        open.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        open.setOnAction(e -> openDatabase());

        MenuItem create = new MenuItem("New Database…");
        create.setOnAction(e -> newDatabase());

        MenuItem close = new MenuItem("Close Database");
        close.setOnAction(e -> closeDatabase());

        MenuItem importCsv = new MenuItem("Import CSV into new table…");
        importCsv.setOnAction(e -> importCsv());

        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> {
            shutdown();
            stage.close();
        });

        fileMenu.getItems().addAll(open, create, close,
                new SeparatorMenuItem(), importCsv,
                new SeparatorMenuItem(), exit);

        Menu helpMenu = new Menu("Help");
        MenuItem about = new MenuItem("About");
        about.setOnAction(e -> Dialogs.info("SQLite GUI",
                "A lightweight SQLite browser built with JavaFX.\nVersion 0.1.0"));
        helpMenu.getItems().add(about);

        return new MenuBar(fileMenu, helpMenu);
    }

    // ------------------------------------------------------------------
    // Database lifecycle
    // ------------------------------------------------------------------

    private void openDatabase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open SQLite Database");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("SQLite databases",
                        "*.db", "*.sqlite", "*.sqlite3", "*.db3"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            openPath(file.getAbsolutePath());
        }
    }

    private void newDatabase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Create SQLite Database");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SQLite database", "*.db"));
        chooser.setInitialFileName("new.db");
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            openPath(file.getAbsolutePath());
        }
    }

    private void openPath(String path) {
        closeDatabase();
        try {
            db = Database.open(path);
            stage.setTitle("SQLite GUI — " + path);
            statusBar.setText("Opened " + path);
            refreshSchema();
        } catch (Exception ex) {
            Dialogs.error("Could not open database", ex);
            statusBar.setText("Failed to open database.");
        }
    }

    private void closeDatabase() {
        if (db != null) {
            try {
                db.close();
            } catch (Exception ignored) {
                // closing a connection rarely fails; nothing actionable for the user
            }
            db = null;
        }
        schemaTree.setRoot(null);
        dataTab.clear();
        structureTab.clear();
        stage.setTitle("SQLite GUI");
        statusBar.setText("No database open.");
    }

    // ------------------------------------------------------------------
    // Schema sidebar
    // ------------------------------------------------------------------

    private void refreshSchema() {
        if (db == null) {
            schemaTree.setRoot(null);
            return;
        }
        try {
            TreeItem<SchemaNode> rootItem = new TreeItem<>(SchemaNode.group("Database"));
            rootItem.setExpanded(true);

            TreeItem<SchemaNode> tables = new TreeItem<>(SchemaNode.group("Tables"));
            tables.setExpanded(true);
            for (String t : db.getTables()) {
                tables.getChildren().add(new TreeItem<>(SchemaNode.table(t)));
            }

            TreeItem<SchemaNode> views = new TreeItem<>(SchemaNode.group("Views"));
            for (String v : db.getViews()) {
                views.getChildren().add(new TreeItem<>(SchemaNode.view(v)));
            }

            rootItem.getChildren().addAll(tables, views);
            schemaTree.setRoot(rootItem);
            statusBar.setText(tables.getChildren().size() + " table(s), "
                    + views.getChildren().size() + " view(s).");
        } catch (Exception ex) {
            Dialogs.error("Could not read schema", ex);
        }
    }

    private void onSchemaSelect(TreeItem<SchemaNode> item) {
        if (item == null || db == null) {
            return;
        }
        SchemaNode node = item.getValue();
        if (node == null || node.isGroup()) {
            return;
        }
        structureTab.show(db, node.name());
        if (node.isTable()) {
            dataTab.load(db, node.name());
        } else {
            // Views are browsable read-only via SQL; show structure only.
            dataTab.clear();
        }
        statusBar.setText("Selected " + node.name());
    }

    // ------------------------------------------------------------------
    // CSV import
    // ------------------------------------------------------------------

    private void importCsv() {
        if (db == null) {
            Dialogs.info("Import CSV", "Open or create a database first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import CSV");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        String suggested = sanitizeTableName(file.getName());
        TextInputDialog nameDialog = new TextInputDialog(suggested);
        nameDialog.setTitle("Import CSV");
        nameDialog.setHeaderText("Name the new table");
        nameDialog.setContentText("Table name:");
        Optional<String> tableName = nameDialog.showAndWait();
        if (tableName.isEmpty() || tableName.get().isBlank()) {
            return;
        }

        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            int rows = CsvSupport.importToNewTable(db, reader, tableName.get().trim(), true);
            refreshSchema();
            Dialogs.info("Import complete",
                    "Imported " + rows + " row(s) into \"" + tableName.get().trim() + "\".");
        } catch (Exception ex) {
            Dialogs.error("CSV import failed", ex);
        }
    }

    private String sanitizeTableName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String cleaned = base.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isEmpty() || Character.isDigit(cleaned.charAt(0))) {
            cleaned = "t_" + cleaned;
        }
        return cleaned;
    }

    public void shutdown() {
        closeDatabase();
    }

    public BorderPane getRoot() {
        return root;
    }

    // ------------------------------------------------------------------
    // Sidebar node model
    // ------------------------------------------------------------------

    /** Value type for the schema tree: a named table/view or a grouping header. */
    public record SchemaNode(String name, Kind kind) {
        public enum Kind { GROUP, TABLE, VIEW }

        public static SchemaNode group(String name) { return new SchemaNode(name, Kind.GROUP); }
        public static SchemaNode table(String name) { return new SchemaNode(name, Kind.TABLE); }
        public static SchemaNode view(String name) { return new SchemaNode(name, Kind.VIEW); }

        public boolean isGroup() { return kind == Kind.GROUP; }
        public boolean isTable() { return kind == Kind.TABLE; }

        @Override
        public String toString() {
            return switch (kind) {
                case TABLE -> "📄 " + name;   // 📄
                case VIEW -> "👁 " + name;     // 👁
                case GROUP -> name;
            };
        }
    }
}
