package org.pspro.chattcpmultisala.cliente;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class HiloCliente extends Thread {
    private Socket socket;
    private VBox chatContainer;
    private DataInputStream entrada;

    public HiloCliente(Socket socket, VBox chatContainer) {
        this.socket = socket;
        this.chatContainer = chatContainer;
        try {
            this.entrada = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Esperamos a que llegue un mensaje del servidor
                String mensajeRecibido = entrada.readUTF();

                Platform.runLater(() -> {

                    HBox contenedorMensaje = new HBox();
                    VBox burbuja = new VBox();

                    Label texto = new Label(mensajeRecibido);

                    // Aplicar clases del CSS
                    burbuja.getStyleClass().add("message-bubble");
                    burbuja.getStyleClass().add("bubble-received"); // Por defecto recibido
                    texto.getStyleClass().add("message-text");

                    burbuja.getChildren().add(texto);
                    contenedorMensaje.getChildren().add(burbuja);

                    // Alineación a la izquierda (recibidos)
                    contenedorMensaje.setAlignment(Pos.CENTER_LEFT);

                    chatContainer.getChildren().add(contenedorMensaje);
                });
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                chatContainer.getChildren().add(new Label("--- Conexión perdida con el servidor ---"));
            });
        }
    }
}
