package com.sqlitegui;

import com.sqlitegui.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX application entry point: wires the main view into the primary stage. */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        MainView mainView = new MainView(stage);
        Scene scene = new Scene(mainView.getRoot(), 1100, 720);
        scene.getStylesheets().add(
                App.class.getResource("/styles.css").toExternalForm());
        stage.setTitle("SQLite GUI");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> mainView.shutdown());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
