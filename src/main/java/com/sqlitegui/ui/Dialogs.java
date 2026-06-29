package com.sqlitegui.ui;

import com.sqlitegui.db.CsvSupport;
import com.sqlitegui.db.QueryResult;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Small collection of reusable dialog helpers. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void error(String header, Throwable t) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(t.getMessage());

        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        TextArea details = new TextArea(sw.toString());
        details.setEditable(false);
        details.setWrapText(false);
        alert.getDialogPane().setExpandableContent(details);
        alert.showAndWait();
    }

    public static void info(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("SQLite GUI");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm");
        alert.setHeaderText(header);
        alert.setContentText(message);
        return alert.showAndWait()
                .filter(b -> b == javafx.scene.control.ButtonType.OK)
                .isPresent();
    }

    /** Prompts for a destination file and writes the result set as CSV. */
    public static void exportCsv(Window owner, QueryResult result, Consumer<String> status) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export as CSV");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName("export.csv");
        java.io.File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }
        try (Writer w = new FileWriter(file, StandardCharsets.UTF_8)) {
            CsvSupport.export(result, w);
            status.accept("Exported " + result.getRowCount() + " row(s) to " + file.getName());
        } catch (IOException ex) {
            error("Export failed", ex);
        }
    }
}
