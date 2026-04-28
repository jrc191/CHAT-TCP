package org.pspro.chattcpmultisala;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class AppPrincipal extends Application {

    // ── Tamaños mínimos de la ventana ──────────────────────────────────────
    // Por debajo de estos valores el layout se vería apretado.
    // Con 3 paneles (sidebar 220 + chat 340 + perfil 240) el mínimo útil
    // es 800 × 560, pero se puede ajustar libremente.
    private static final double MIN_WIDTH  = 800;
    private static final double MIN_HEIGHT = 560;

    // ── Tamaño inicial cómodo ──────────────────────────────────────────────
    private static final double INIT_WIDTH  = 1100;
    private static final double INIT_HEIGHT = 680;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                getClass().getResource("/org/pspro/chattcpmultisala/login.fxml"));

        // La escena de login NO necesita ser grande
        Scene scene = new Scene(fxmlLoader.load(), 420, 520);

        stage.setTitle("Telegram");
        stage.setScene(scene);

        // ── Restricciones de tamaño ────────────────────────────────────────
        // Se aplican al Stage para que afecten a cualquier escena que se cargue
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        // Cuando se abra la ventana del chat (desde LoginController) el Stage
        // ya tendrá estos mínimos; el FXML ocupa el 100 % del espacio.
        stage.setOnCloseRequest(event -> System.exit(0));

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
