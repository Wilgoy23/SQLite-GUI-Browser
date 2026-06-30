package com.sqlitegui.ui;

import com.sqlitegui.db.Database;
import com.sqlitegui.db.QueryResult;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.converter.DefaultStringConverter;

/**
 * Editable, paginated browser for a single table. The hidden first column holds
 * each row's rowid, which is used to commit edits, inserts, and deletes back to
 * the database.
 */
public final class DataTab {

    private static final int PAGE_SIZE = 200;

    private final BorderPane root = new BorderPane();
    private final TableView<ObservableList<String>> table = new TableView<>();
    private final Label pageLabel = new Label();
    private final Button prevButton = new Button("◀ Prev");
    private final Button nextButton = new Button("Next ▶");
    private final Button addRowButton = new Button("Add row");
    private final Button deleteRowButton = new Button("Delete row");
    private final Button refreshButton = new Button("Refresh");
    private final Button exportButton = new Button("Export CSV…");

    private Database db;
    private String tableName;
    private boolean editable;
    private long totalRows;
    private int offset;

    public DataTab() {
        build();
    }

    private void build() {
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Select a table in the sidebar to browse its data."));

        addRowButton.setOnAction(e -> addRow());
        deleteRowButton.setOnAction(e -> deleteSelectedRow());
        refreshButton.setOnAction(e -> reload());
        exportButton.setOnAction(e -> exportCurrentPage());
        prevButton.setOnAction(e -> {
            if (offset - PAGE_SIZE >= 0) {
                offset -= PAGE_SIZE;
                reload();
            }
        });
        nextButton.setOnAction(e -> {
            if (offset + PAGE_SIZE < totalRows) {
                offset += PAGE_SIZE;
                reload();
            }
        });

        ToolBar toolBar = new ToolBar(addRowButton, deleteRowButton, refreshButton, exportButton);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox pager = new HBox(8, prevButton, pageLabel, nextButton);
        pager.setAlignment(Pos.CENTER_LEFT);
        pager.setPadding(new Insets(6, 8, 6, 8));

        root.setTop(toolBar);
        root.setCenter(table);
        root.setBottom(pager);
        updatePagerState();
    }

    public void load(Database db, String tableName) {
        this.db = db;
        this.tableName = tableName;
        this.offset = 0;
        try {
            this.totalRows = db.getRowCount(tableName);
            // Editing requires a usable rowid. WITHOUT ROWID tables throw here;
            // fall back to read-only browsing in that case.
            this.editable = probeRowid(db, tableName);
        } catch (Exception ex) {
            this.totalRows = 0;
            this.editable = false;
        }
        reload();
    }

    private boolean probeRowid(Database db, String tableName) {
        try {
            db.getTableData(tableName, 1, 0);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void reload() {
        if (db == null || tableName == null) {
            return;
        }
        try {
            QueryResult result = db.getTableData(tableName, PAGE_SIZE, offset);
            rebuildColumns(result);
            ObservableList<ObservableList<String>> items = FXCollections.observableArrayList();
            for (Object[] dataRow : result.getRows()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                for (Object cell : dataRow) {
                    row.add(cell == null ? null : cell.toString());
                }
                items.add(row);
            }
            table.setItems(items);
            updatePagerState();
        } catch (Exception ex) {
            Dialogs.error("Could not load table data", ex);
        }
    }

    /** Rebuilds columns from the result. Column 0 (rowid) is intentionally skipped. */
    private void rebuildColumns(QueryResult result) {
        table.getColumns().clear();
        java.util.List<String> cols = result.getColumns();
        for (int i = 1; i < cols.size(); i++) {
            final int rowIndex = i; // index into the row list (includes rowid at 0)
            final String columnName = cols.get(i);
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(columnName);
            col.setCellValueFactory(cell -> {
                ObservableList<String> row = cell.getValue();
                return new SimpleStringProperty(rowIndex < row.size() ? row.get(rowIndex) : null);
            });
            col.setCellFactory(tc -> new NullAwareEditingCell());
            col.setEditable(editable);
            col.setPrefWidth(140);
            col.setOnEditCommit(ev -> commitEdit(ev.getRowValue(), rowIndex, columnName, ev.getNewValue()));
            table.getColumns().add(col);
        }
    }

    private void commitEdit(ObservableList<String> row, int rowIndex, String columnName, String newValue) {
        String rowId = row.get(0);
        try {
            // Treat the literal "(null)" placeholder and empty input as SQL NULL.
            Object stored = (newValue == null || newValue.isEmpty()
                    || ResultGrid.NULL_DISPLAY.equals(newValue)) ? null : newValue;
            db.updateCell(tableName, rowId, columnName, stored);
            row.set(rowIndex, stored == null ? null : newValue);
            table.refresh();
        } catch (Exception ex) {
            Dialogs.error("Update failed", ex);
            reload();
        }
    }

    private void addRow() {
        if (!ensureEditable()) {
            return;
        }
        try {
            db.insertEmptyRow(tableName);
            totalRows++;
            // Jump to the last page so the new row is visible.
            offset = (int) ((Math.max(totalRows - 1, 0) / PAGE_SIZE) * PAGE_SIZE);
            reload();
        } catch (Exception ex) {
            Dialogs.error("Could not add row", ex);
        }
    }

    private void deleteSelectedRow() {
        if (!ensureEditable()) {
            return;
        }
        ObservableList<String> selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.info("Delete row", "Select a row to delete first.");
            return;
        }
        if (!Dialogs.confirm("Delete row", "Permanently delete the selected row?")) {
            return;
        }
        try {
            db.deleteRow(tableName, selected.get(0));
            totalRows--;
            if (offset >= totalRows && offset >= PAGE_SIZE) {
                offset -= PAGE_SIZE;
            }
            reload();
        } catch (Exception ex) {
            Dialogs.error("Could not delete row", ex);
        }
    }

    private boolean ensureEditable() {
        if (!editable) {
            Dialogs.info("Read-only", "This table has no usable rowid and cannot be edited here.");
            return false;
        }
        return true;
    }

    private void exportCurrentPage() {
        if (db == null || tableName == null) {
            return;
        }
        try {
            QueryResult page = db.getTableData(tableName, PAGE_SIZE, offset);
            // Drop the rowid column from the export.
            java.util.List<String> cols = new java.util.ArrayList<>(page.getColumns());
            java.util.List<Object[]> rows = new java.util.ArrayList<>();
            for (Object[] r : page.getRows()) {
                rows.add(java.util.Arrays.copyOfRange(r, 1, r.length));
            }
            QueryResult trimmed = QueryResult.ofRows(
                    cols.subList(1, cols.size()), rows);
            Dialogs.exportCsv(root.getScene().getWindow(), trimmed,
                    msg -> { /* status surfaced via dialog */ });
        } catch (Exception ex) {
            Dialogs.error("Export failed", ex);
        }
    }

    private void updatePagerState() {
        long from = totalRows == 0 ? 0 : offset + 1;
        long to = Math.min(offset + PAGE_SIZE, totalRows);
        String editLabel = editable ? "" : "  (read-only)";
        pageLabel.setText("Rows " + from + "–" + to + " of " + totalRows + editLabel);
        prevButton.setDisable(offset == 0);
        nextButton.setDisable(offset + PAGE_SIZE >= totalRows);
    }

    public void clear() {
        db = null;
        tableName = null;
        table.getColumns().clear();
        table.getItems().clear();
        totalRows = 0;
        offset = 0;
        updatePagerState();
    }

    public BorderPane getRoot() {
        return root;
    }

    /** Editable text cell that shows NULL distinctly. */
    private static final class NullAwareEditingCell
            extends TextFieldTableCell<ObservableList<String>, String> {
        NullAwareEditingCell() {
            super(new DefaultStringConverter());
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (isEditing()) {
                return;
            }
            if (empty) {
                setText(null);
                getStyleClass().remove("null-cell");
            } else if (item == null) {
                setText(ResultGrid.NULL_DISPLAY);
                if (!getStyleClass().contains("null-cell")) {
                    getStyleClass().add("null-cell");
                }
            } else {
                setText(item);
                getStyleClass().remove("null-cell");
            }
        }
    }
}
