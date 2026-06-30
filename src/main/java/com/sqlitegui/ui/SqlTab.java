package com.sqlitegui.ui;

import com.sqlitegui.db.Database;
import com.sqlitegui.db.QueryResult;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Supplier;

/** A SQL editor pane: type a statement, run it, and view results or row counts. */
public final class SqlTab {

    private final BorderPane root = new BorderPane();
    private final TextArea editor = new TextArea();
    private final TableView<ObservableList<String>> resultTable = new TableView<>();
    private final Label statusLabel = new Label("Ready.");
    private final Supplier<Database> dbSupplier;
    private final Runnable onSchemaChanged;

    private QueryResult lastResult;

    public SqlTab(Supplier<Database> dbSupplier, Runnable onSchemaChanged) {
        this.dbSupplier = dbSupplier;
        this.onSchemaChanged = onSchemaChanged;
        build();
    }

    private void build() {
        editor.setPromptText("Write SQL here, e.g.  SELECT * FROM my_table;\nPress Ctrl+Enter to run.");
        editor.getStyleClass().add("sql-editor");
        editor.setOnKeyPressed(e -> {
            if (new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN).match(e)) {
                run();
            }
        });

        Button runButton = new Button("Run  (Ctrl+Enter)");
        runButton.setDefaultButton(false);
        runButton.setOnAction(e -> run());

        Button exportButton = new Button("Export results as CSV…");
        exportButton.setOnAction(e -> exportResults());

        ToolBar toolBar = new ToolBar(runButton, exportButton);

        VBox editorBox = new VBox(toolBar, editor);
        VBox.setVgrow(editor, Priority.ALWAYS);

        resultTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        resultTable.setPlaceholder(new Label("Run a query to see results."));

        SplitPane split = new SplitPane(editorBox, resultTable);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.4);

        statusLabel.setPadding(new Insets(4, 8, 4, 8));

        root.setCenter(split);
        root.setBottom(statusLabel);
    }

    private void run() {
        Database db = dbSupplier.get();
        if (db == null) {
            statusLabel.setText("Open a database first.");
            return;
        }
        String sql = selectedOrAllText();
        if (sql.isBlank()) {
            statusLabel.setText("Nothing to run.");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            QueryResult result = db.execute(sql);
            long elapsed = System.currentTimeMillis() - start;
            if (result.isUpdate()) {
                resultTable.getColumns().clear();
                resultTable.getItems().clear();
                statusLabel.setText(result.getUpdateCount() + " row(s) affected.  (" + elapsed + " ms)");
                lastResult = null;
                if (onSchemaChanged != null) {
                    onSchemaChanged.run();
                }
            } else {
                ResultGrid.populate(resultTable, result);
                lastResult = result;
                statusLabel.setText(result.getRowCount() + " row(s) returned.  (" + elapsed + " ms)");
            }
        } catch (Exception ex) {
            statusLabel.setText("Error: " + ex.getMessage());
            Dialogs.error("Query failed", ex);
        }
    }

    private String selectedOrAllText() {
        String selected = editor.getSelectedText();
        return (selected != null && !selected.isBlank()) ? selected : editor.getText();
    }

    private void exportResults() {
        if (lastResult == null || lastResult.getColumns().isEmpty()) {
            statusLabel.setText("No query results to export.");
            return;
        }
        Dialogs.exportCsv(root.getScene().getWindow(), lastResult,
                msg -> statusLabel.setText(msg));
    }

    public BorderPane getRoot() {
        return root;
    }
}
