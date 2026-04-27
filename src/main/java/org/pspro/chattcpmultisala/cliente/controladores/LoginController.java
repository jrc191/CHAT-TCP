package org.pspro.chattcpmultisala.cliente.controladores;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.pspro.chattcpmultisala.common.DatosMensaje;
import org.pspro.chattcpmultisala.common.TipoMensaje;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LoginController {

    // Campos obligatorios en login.fxml
    @FXML public TextField txtNombreUsuario;
    @FXML public Button btnEntrarInvitado;
    @FXML public Label lblError;

    // Campos opcionales: si no existen en el FXML simplemente quedan null
    @FXML public PasswordField txtPassword;
    @FXML public Button btnEntrarRegistrado;

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;

    // -------------------------------------------------------------------------
    // LOGIN ANÓNIMO  (botón principal del FXML actual)
    // -------------------------------------------------------------------------

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

        } catch (IOException e) {
            mostrarError("No se pudo conectar al servidor. ¿Está arrancado?");
            e.printStackTrace();
            cerrarConexion();
        } catch (ClassNotFoundException e) {
            mostrarError("Error de protocolo al leer respuesta.");
            e.printStackTrace();
            cerrarConexion();
        }
    }

    // -------------------------------------------------------------------------
    // LOGIN CON CUENTA  (botón opcional — solo si existe btnEntrarRegistrado en FXML)
    // -------------------------------------------------------------------------

    @FXML
    public void onEntrarRegistradoClick(ActionEvent actionEvent) {
        String nombre = txtNombreUsuario.getText().trim();
        String pass = (txtPassword != null) ? txtPassword.getText() : "";

        if (!validarNombre(nombre)) return;

        if (pass.isEmpty()) {
            mostrarError("Introduce una contraseña para acceder con cuenta.");
            return;
        }

        lblError.setVisible(false);

        try {
            conectar();

            DatosMensaje loginMsg = new DatosMensaje();
            loginMsg.setTipo(TipoMensaje.LOGIN_REGISTER);
            loginMsg.setRemitente(nombre);
            loginMsg.setPassword(sha256(pass));

            salida.writeObject(loginMsg);
            salida.flush();

            DatosMensaje respuesta = (DatosMensaje) entrada.readObject();

            if (respuesta.isSuccess()) {
                abrirVentanaChat(nombre);
            } else {
                mostrarError(respuesta.getReason());
                cerrarConexion();
            }

        } catch (IOException e) {
            mostrarError("No se pudo conectar al servidor.");
            e.printStackTrace();
            cerrarConexion();
        } catch (ClassNotFoundException e) {
            mostrarError("Error de protocolo.");
            e.printStackTrace();
            cerrarConexion();
        }
    }

    // -------------------------------------------------------------------------
    // ABRIR VENTANA DE CHAT
    // -------------------------------------------------------------------------

    private void abrirVentanaChat(String nombre) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/pspro/chattcpmultisala/principal.fxml"));

            if (loader.getLocation() == null) {
                mostrarError("No se encontró principal.fxml en el classpath.");
                cerrarConexion();
                return;
            }

            Scene scene = new Scene(loader.load(), 800, 500);

            ChatController chatController = loader.getController();
            chatController.inicializarConexion(socket, salida, entrada, nombre);

            // Obtener el Stage desde cualquier botón que exista
            Stage stage = obtenerStage();
            if (stage == null) {
                mostrarError("Error interno: no se pudo obtener la ventana.");
                cerrarConexion();
                return;
            }

            stage.setTitle("Chat TCP - " + nombre);
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            mostrarError("Error al cargar la ventana del chat: " + e.getMessage());
            e.printStackTrace();
            cerrarConexion();
        }
    }

    /** Obtiene el Stage desde el primer botón disponible */
    private Stage obtenerStage() {
        if (btnEntrarInvitado != null && btnEntrarInvitado.getScene() != null) {
            return (Stage) btnEntrarInvitado.getScene().getWindow();
        }
        if (btnEntrarRegistrado != null && btnEntrarRegistrado.getScene() != null) {
            return (Stage) btnEntrarRegistrado.getScene().getWindow();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // UTILIDADES
    // -------------------------------------------------------------------------

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
        if (nombre.length() > 20) {
            mostrarError("El nombre no puede tener más de 20 caracteres.");
            return false;
        }
        return true;
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
    }

    private void cerrarConexion() {
        try { if (entrada != null) entrada.close(); } catch (IOException ignored) {}
        try { if (salida != null)  salida.close();  } catch (IOException ignored) {}
        try { if (socket != null)  socket.close();  } catch (IOException ignored) {}
        socket = null; salida = null; entrada = null;
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }
}
