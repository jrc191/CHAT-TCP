package org.pspro.chattcpmultisala.cliente.controladores;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.pspro.chattcpmultisala.cliente.HiloCliente;
import org.pspro.chattcpmultisala.servidor.HiloServidorChat;
import org.pspro.chattcpmultisala.servidor.InfoHilos;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ChatController{

    @FXML public VBox contactList;
    @FXML public VBox chatContainer;
    @FXML public TextField txtMensaje;
    @FXML public Button btnEnviar;
    @FXML public Label lblSalaActual;
    @FXML public ComboBox cmbDestinatario;

    private Socket socket;
    private DataOutputStream salida;

    @FXML
    public void initialize() {
        conectarAlServidor();
    }

    private void conectarAlServidor() {
        try {

            this.socket = new Socket("localhost", 55555);
            this.salida = new DataOutputStream(socket.getOutputStream());

            // 2. Iniciamos el hilo que ESCUCHA y AÑADE mensajes al chatContainer
            HiloCliente hiloEscucha = new HiloCliente(socket, chatContainer);
            hiloEscucha.setDaemon(true); // Para que se cierre al cerrar la app
            hiloEscucha.start();

        } catch (IOException e) {
            chatContainer.getChildren().add(new Label("Error: No se pudo conectar al servidor."));
        }
    }

    @FXML
    public void onEnviarClick(ActionEvent actionEvent) {
        String mensaje = txtMensaje.getText().trim();
        if (!mensaje.isEmpty() && salida != null) {
            try {
                salida.writeUTF(mensaje);

                //A LA DERECHA ENVIADO
                HBox contenedorEnviado = new HBox();
                contenedorEnviado.setAlignment(Pos.CENTER_RIGHT); // Derecha

                VBox burbuja = new VBox();
                burbuja.getStyleClass().addAll("message-bubble", "bubble-sent");

                Label texto = new Label(mensaje);
                texto.getStyleClass().add("message-text");

                burbuja.getChildren().add(texto);
                contenedorEnviado.getChildren().add(burbuja);

                chatContainer.getChildren().add(contenedorEnviado);

                salida.flush();

                if (txtMensaje.getText().equals("*****")) {
                    System.exit(0);
                }

                txtMensaje.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML public void onCerrarClick(ActionEvent actionEvent) {
        try {
            if (salida != null) {
                salida.writeUTF("*****"); // Código de desconexión
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    @FXML public void buscando(InputMethodEvent inputMethodEvent) {

    }
}