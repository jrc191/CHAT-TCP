package org.pspro.chattcpmultisala.cliente.controladores;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.pspro.chattcpmultisala.common.DatosMensaje;
import org.pspro.chattcpmultisala.common.TipoMensaje;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class LoginController {

    @FXML public TextField txtNombreUsuario;
    @FXML public Button btnEntrarInvitado;
    @FXML public Label lblError;

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;

    @FXML
    public void onEntrarInvitadoClick(ActionEvent actionEvent) {
        String nombre = txtNombreUsuario.getText().trim();
        if (!validarNombre(nombre)) return;

        lblError.setVisible(false);

        try {
            conectar();

            DatosMensaje loginMsg = new DatosMensaje();
            loginMsg.setTipo(TipoMensaje.LOGIN_ANON);
            loginMsg.setRemitente(nombre);

            salida.writeObject(loginMsg);
            salida.flush();

            DatosMensaje respuesta = (DatosMensaje) entrada.readObject();

            if (respuesta.isSuccess()) {
                abrirVentanaChat(nombre);
            } else {
                mostrarError(respuesta.getReason());
                cerrarConexion();
            }

        } catch (IOException | ClassNotFoundException e) {
            mostrarError("No se pudo conectar al servidor.");
            e.printStackTrace();
            cerrarConexion();
        }
    }

    private void abrirVentanaChat(String nombre) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pspro/chattcpmultisala/principal.fxml"));

            if (loader.getLocation() == null) {
                mostrarError("No se encontró principal.fxml.");
                cerrarConexion();
                return;
            }

            Scene scene = new Scene(loader.load(), 900, 600);

            ChatController chatController = loader.getController();
            chatController.inicializarConexion(socket, salida, entrada, nombre);

            Stage stage = (Stage) btnEntrarInvitado.getScene().getWindow();
            stage.setTitle("ChatTCP - " + nombre);
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            mostrarError("Error al cargar la ventana del chat.");
            e.printStackTrace();
            cerrarConexion();
        }
    }

    private void conectar() throws IOException {
        socket = new Socket("localhost", 55555);
        salida = new ObjectOutputStream(socket.getOutputStream());
        salida.flush();
        entrada = new ObjectInputStream(socket.getInputStream());
    }

    private boolean validarNombre(String nombre) {
        if (nombre.isEmpty()) {
            mostrarError("Por favor, escribe un nombre de usuario.");
            return false;
        }
        if (nombre.length() < 3) {
            mostrarError("El nombre debe tener al menos 3 caracteres.");
            return false;
        }
        return true;
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void cerrarConexion() {
        try { if (entrada != null) entrada.close(); } catch (IOException ignored) {}
        try { if (salida != null)  salida.close();  } catch (IOException ignored) {}
        try { if (socket != null)  socket.close();  } catch (IOException ignored) {}
        socket = null; salida = null; entrada = null;
    }
}
