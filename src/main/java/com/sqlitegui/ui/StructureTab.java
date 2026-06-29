package com.sqlitegui.ui;

import com.sqlitegui.db.ColumnInfo;
import com.sqlitegui.db.Database;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** Shows the column metadata and the CREATE statement for the selected object. */
public final class StructureTab {

    private final VBox root = new VBox(8);
    private final Label title = new Label("No table selected.");
    private final TableView<ColumnInfo> columnsTable = new TableView<>();
    private final TextArea ddlArea = new TextArea();

    public StructureTab() {
        build();
    }

    private void build() {
        root.setPadding(new Insets(10));
        title.getStyleClass().add("section-title");

        columnsTable.getColumns().add(textColumn("Name", ColumnInfo::getName, 180));
        columnsTable.getColumns().add(textColumn("Type", ColumnInfo::getType, 120));
        columnsTable.getColumns().add(textColumn("Not Null",
                c -> c.isNotNull() ? "YES" : "", 80));
        columnsTable.getColumns().add(textColumn("Primary Key",
                c -> c.isPrimaryKey() ? "YES" : "", 100));
        columnsTable.getColumns().add(textColumn("Default",
                ColumnInfo::getDefaultValue, 140));
        columnsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        columnsTable.setPlaceholder(new Label("Select a table or view in the sidebar."));

        Label ddlLabel = new Label("CREATE statement");
        ddlLabel.getStyleClass().add("section-title");
        ddlArea.setEditable(false);
        ddlArea.getStyleClass().add("sql-editor");
        ddlArea.setPrefRowCount(8);

        VBox.setVgrow(columnsTable, Priority.ALWAYS);
        root.getChildren().addAll(title, columnsTable, ddlLabel, ddlArea);
    }

    private TableColumn<ColumnInfo, String> textColumn(
            String header, java.util.function.Function<ColumnInfo, String> getter, double width) {
        TableColumn<ColumnInfo, String> col = new TableColumn<>(header);
        col.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(getter.apply(cell.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    public void show(Database db, String objectName) {
        try {
            title.setText("Structure of \"" + objectName + "\"");
            List<ColumnInfo> cols = db.getColumns(objectName);
            columnsTable.setItems(FXCollections.observableArrayList(cols));
            ddlArea.setText(db.getDdl(objectName));
        } catch (Exception ex) {
            Dialogs.error("Could not load structure", ex);
        }
    }

    public void clear() {
        title.setText("No table selected.");
        columnsTable.getItems().clear();
        ddlArea.clear();
    }

    public VBox getRoot() {
        return root;
    }
}
