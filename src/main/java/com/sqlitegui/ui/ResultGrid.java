package com.sqlitegui.ui;

import com.sqlitegui.db.QueryResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DefaultStringConverter;

import java.util.List;

/**
 * Builds a read-only {@link TableView} from a {@link QueryResult}. Rows are held
 * as observable lists of strings; NULL cells are rendered as a dimmed
 * "(null)" so they are distinguishable from empty strings.
 */
public final class ResultGrid {

    public static final String NULL_DISPLAY = "(null)";

    private ResultGrid() {
    }

    public static TableView<ObservableList<String>> build(QueryResult result) {
        TableView<ObservableList<String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        populate(table, result);
        return table;
    }

    public static void populate(TableView<ObservableList<String>> table, QueryResult result) {
        table.getColumns().clear();
        table.getItems().clear();

        List<String> columns = result.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            final int colIndex = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(columns.get(i));
            col.setCellValueFactory(cell -> {
                ObservableList<String> row = cell.getValue();
                String value = colIndex < row.size() ? row.get(colIndex) : null;
                return new javafx.beans.property.SimpleStringProperty(value);
            });
            col.setCellFactory(tc -> new NullAwareCell());
            col.setPrefWidth(140);
            table.getColumns().add(col);
        }

        ObservableList<ObservableList<String>> items = FXCollections.observableArrayList();
        for (Object[] dataRow : result.getRows()) {
            ObservableList<String> row = FXCollections.observableArrayList();
            for (Object cell : dataRow) {
                row.add(cell == null ? null : cell.toString());
            }
            items.add(row);
        }
        table.setItems(items);
    }

    /** Renders NULL distinctly from empty text. */
    private static final class NullAwareCell extends TextFieldTableCell<ObservableList<String>, String> {
        NullAwareCell() {
            super(new DefaultStringConverter());
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                getStyleClass().remove("null-cell");
            } else if (item == null) {
                setText(NULL_DISPLAY);
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
