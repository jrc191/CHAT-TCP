package org.pspro.chattcpmultisala.cliente.controladores;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.pspro.chattcpmultisala.common.DatosMensaje;
import org.pspro.chattcpmultisala.common.TipoMensaje;
import org.pspro.chattcpmultisala.common.ValidadorEntrada;

import java.io.*;
import java.net.Socket;
import java.security.KeyStore;
import javax.net.ssl.*;

/**
 * Controlador del formulario de login con soporte SSL/TLS.
 *
 * Cambios:
 *  1. Usa SSLSocket para conexión cifrada.
 *  2. Campo de contraseña (PasswordField).
 *  3. Envía LOGIN_REGISTER (nick + password).
 *  4. Validación de formato y sanitización de nickname.
 */
public class LoginController {

    @FXML public TextField     txtNombreUsuario;
    @FXML public PasswordField txtPassword;
    @FXML public TextField     txtPasswordVisible;
    @FXML public Button        btnEntrar;
    @FXML public Button        btnRegistrar;
    @FXML public Button        btnTogglePassword;
    @FXML public Label         lblError;

    private boolean isPasswordVisible = false;

    private Socket            socket;
    private ObjectOutputStream salida;
    private ObjectInputStream  entrada;

    private static final String TRUSTSTORE_FILE = "/servidor.p12";
    private static final String TRUSTSTORE_PASS = "chat-password";

    // =========================================================================
    // EVENTOS
    // =========================================================================

    @FXML
    public void onTogglePasswordClick(ActionEvent event) {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            txtPasswordVisible.setText(txtPassword.getText());
            txtPasswordVisible.setVisible(true);
            txtPasswordVisible.setManaged(true);
            txtPassword.setVisible(false);
            txtPassword.setManaged(false);
            btnTogglePassword.setText("🙈");
        } else {
            txtPassword.setText(txtPasswordVisible.getText());
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);
            txtPasswordVisible.setVisible(false);
            txtPasswordVisible.setManaged(false);
            btnTogglePassword.setText("👁");
        }
    }

    private String getPassword() {
        return isPasswordVisible ? txtPasswordVisible.getText() : txtPassword.getText();
    }

    @FXML
    public void onEntrarClick(ActionEvent actionEvent) {
        String nick  = txtNombreUsuario.getText().trim();
        String pass  = getPassword();

        if (!validarCampos(nick, pass)) return;

        try {
            asegurarConexion();

            DatosMensaje loginMsg = new DatosMensaje();
            loginMsg.setTipo(TipoMensaje.LOGIN_REGISTER);
            loginMsg.setRemitente(nick);
            loginMsg.setPassword(pass);

            salida.writeObject(loginMsg);
            salida.flush();

            DatosMensaje respuesta = (DatosMensaje) entrada.readObject();

            if (respuesta.isSuccess()) {
                abrirVentanaChat(respuesta.getRemitente(), respuesta.getRolRemitente());
            } else {
                mostrarError(respuesta.getReason());
                // Si el error es de credenciales, no cerramos para permitir reintentar
                if (respuesta.getReason().contains("bloqueada") || respuesta.getReason().contains("lleno")) {
                    cerrarConexion();
                }
            }

        } catch (Exception e) {
            mostrarError("Error de conexión: " + e.getMessage());
            cerrarConexion();
        }
    }

    @FXML
    public void onRegistrarClick(ActionEvent actionEvent) {
        String nick  = txtNombreUsuario.getText().trim();
        String pass  = getPassword();

        if (!validarCampos(nick, pass)) return;

        try {
            asegurarConexion();

            DatosMensaje regMsg = new DatosMensaje();
            regMsg.setTipo(TipoMensaje.REGISTER_REQUEST);
            regMsg.setRemitente(nick);
            regMsg.setPassword(pass);

            salida.writeObject(regMsg);
            salida.flush();

            DatosMensaje respuesta = (DatosMensaje) entrada.readObject();

            if (respuesta.isSuccess()) {
                mostrarInfo(respuesta.getReason());
            } else {
                mostrarError(respuesta.getReason());
            }

        } catch (Exception e) {
            mostrarError("Error de registro: " + e.getMessage());
            cerrarConexion();
        }
    }

    // =========================================================================
    // Privado
    // =========================================================================

    private boolean validarCampos(String nick, String pass) {
        ValidadorEntrada.ResultadoValidacion rvNick = ValidadorEntrada.validarNickname(nick);
        if (!rvNick.valido()) { mostrarError(rvNick.mensajeError()); return false; }

        ValidadorEntrada.ResultadoValidacion rvPass = ValidadorEntrada.validarPassword(pass);
        if (!rvPass.valido()) { mostrarError(rvPass.mensajeError()); return false; }

        lblError.setVisible(false);
        return true;
    }

    private void asegurarConexion() throws Exception {
        if (socket == null || socket.isClosed()) {
            conectar();
        }
    }

    private void mostrarInfo(String mensaje) {
        lblError.setText(mensaje);
        lblError.setStyle("-fx-text-fill: #27ae60;"); // Verde para éxito
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void abrirVentanaChat(String nombre, String rol) {
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
            chatController.inicializarConexion(socket, salida, entrada, nombre, rol, getPassword());

            Stage stage = (Stage) btnEntrar.getScene().getWindow();
            String tituloRol = "MODERATOR".equals(rol) ? " [Moderador]" : "";
            stage.setTitle("ChatTCP SSL — " + nombre + tituloRol);
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            mostrarError("Error al cargar la ventana del chat.");
            cerrarConexion();
        }
    }

    private void conectar() throws Exception {
        SSLSocketFactory ssf = crearSSLSocketFactory();
        socket = ssf.createSocket("localhost", 55555);
        
        salida = new ObjectOutputStream(socket.getOutputStream());
        salida.flush();
        entrada = new ObjectInputStream(socket.getInputStream());
    }

    private SSLSocketFactory crearSSLSocketFactory() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        InputStream is = getClass().getResourceAsStream(TRUSTSTORE_FILE);
        if (is == null) throw new FileNotFoundException("Truststore not found: " + TRUSTSTORE_FILE);
        ks.load(is, TRUSTSTORE_PASS.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, tmf.getTrustManagers(), null);
        return sc.getSocketFactory();
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setStyle("-fx-text-fill: #e74c3c;"); // Rojo para error
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void cerrarConexion() {
        try { if (entrada != null) entrada.close(); } catch (IOException ignored) {}
        try { if (salida  != null) salida.close();  } catch (IOException ignored) {}
        try { if (socket  != null) socket.close();  } catch (IOException ignored) {}
        socket = null; salida = null; entrada = null;
    }
}

