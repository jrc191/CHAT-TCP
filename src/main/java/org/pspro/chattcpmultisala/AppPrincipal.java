package org.pspro.chattcpmultisala;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class AppPrincipal extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(AppPrincipal.class.getResource("principal.fxml"));

        Scene scene = new Scene(fxmlLoader.load(), 800, 500);

        stage.setTitle("Cliente Chat TCP");
        stage.setScene(scene);

        stage.setOnCloseRequest(event -> {
            System.exit(0);
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}