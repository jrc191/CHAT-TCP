package org.pspro.chattcpmultisala;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class AppPrincipal extends Application {

    // ── Tamaños mínimos de la ventana ──────────────────────────────────────
    private static final double MIN_WIDTH  = 800;
    private static final double MIN_HEIGHT = 560;

    // ── Tamaño inicial cómodo ──────────────────────────────────────────────
    private static final double INIT_WIDTH  = 1100;
    private static final double INIT_HEIGHT = 680;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                getClass().getResource("/org/pspro/chattcpmultisala/login.fxml"));

        Scene scene = new Scene(fxmlLoader.load(), 420, 520);

        stage.setTitle("ChatTCP");
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setOnCloseRequest(event -> System.exit(0));

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
