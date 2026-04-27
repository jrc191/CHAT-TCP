package org.pspro.chattcpmultisala;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class AppPrincipal extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Cargar la pantalla de login en lugar del chat directamente
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/org/pspro/chattcpmultisala/login.fxml"));

        Scene scene = new Scene(fxmlLoader.load(), 500, 400);

        stage.setTitle("Chat TCP - Login");
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
