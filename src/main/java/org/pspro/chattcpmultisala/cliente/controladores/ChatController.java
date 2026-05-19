package org.pspro.chattcpmultisala.cliente.controladores;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.pspro.chattcpmultisala.cliente.ContactManager;
import org.pspro.chattcpmultisala.cliente.HiloCliente;
import org.pspro.chattcpmultisala.cliente.ProfileService;
import org.pspro.chattcpmultisala.common.*;
import org.pspro.chattcpmultisala.cliente.ChatService;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Controlador principal del chat.
 *
 * Novedades:
 *  - Recibe y expone el rol del usuario (USER / MODERATOR).
 *  - Cifra los mensajes privados y de canal antes de enviarlos.
 *  - Descifra los mensajes recibidos antes de mostrarlos.
 *  - Permite enviar archivos (cifrados con AES-256-GCM).
 *  - Permite borrar mensajes propios (privados).
 *  - Moderadores: banear usuarios, suspender canal, promover temporalmente.
 *  - Muestra el rol del remitente en la burbuja de los canales.
 */
public class ChatController {

    // ── FXML ─────────────────────────────────────────────────────────────────
    @FXML public VBox      contactList;
    @FXML public VBox      chatContainer;
    @FXML public TextField txtMensaje;
    @FXML public Button    btnEnviar;
    @FXML public Button    cerrarAppBtn;
    @FXML public Label     lblTituloChat;
    @FXML public Label     lblPanelTitulo;
    @FXML public TextField txtBuscador;
    @FXML public ScrollPane scrollChat;
    @FXML public AnchorPane profilePanel;
    @FXML public HBox      foldersContainer;
    @FXML public Label     userAvatar;
    @FXML public Label     userName;
    @FXML public Label     userStatus;
    @FXML public Label     profileAvatarBig;
    @FXML public Label     profileName;
    @FXML public Label     profileStatusLabel;
    @FXML public Label     profilePhone;
    @FXML public Label     profileEmail;
    @FXML public Label     profileBio;

    // Nuevos campos para Info Contextual
    @FXML public VBox      containerMiembros;
    @FXML public VBox      listMiembros;
    @FXML public Button    btnEditarPerfil;
    @FXML public Button    btnEliminarCanal;
    @FXML public Label     infoIcon1;
    @FXML public Label     infoLabel1;
    @FXML public Label     infoIcon2;
    @FXML public Label     infoLabel2;
    @FXML public Label     infoIcon3;
    @FXML public Label     infoLabel3;
    @FXML public HBox      rowEmail;
    @FXML public HBox      mainRoot; // Para el modo oscuro

    // ── Estado ────────────────────────────────────────────────────────────────
    private boolean     profilePanelVisible = false;
    private boolean     isDarkMode = false; // Estado del modo oscuro
    private ProfileManager     profileManager;
    private ChatFolderManager  folderManager;
    private UserProfile        currentUserProfile;

    private Socket            socket;
    private ObjectOutputStream salida;
    private ObjectInputStream  entrada;
    private String             nombreUsuario;
    private String             rolUsuario = "USER";   // "USER" o "MODERATOR"
    private String             passwordUsuario;       // Para reconexión automática
    private ChatService        chatService;
    private ProfileService profileService;
    private ContactManager contactManager;

    private int                intentosReconexion = 0;
    private static final int   MAX_INTENTOS = 3;

    private String              destinatarioActual    = "GENERAL";
    private final Map<String, List<Node>> historialesChat    = new HashMap<>();
    private final Set<String>  chatsActivos          = new LinkedHashSet<>();
    private final Set<String>  canalesActivos         = new HashSet<>();
    private final Map<String, List<String>> miembrosCanales  = new HashMap<>();
    private final List<String> usuariosEnLinea        = new ArrayList<>();
    private final Set<String>  chatsConMensajesNuevos = new HashSet<>();

    /** mensajeId → HBox burbuja, para poder borrarla después. */
    private final Map<String, HBox> burbujasById = new HashMap<>();

    // =========================================================================
    // INICIALIZACIÓN
    // =========================================================================

    /**
     * Nuevo método con rol.
     */
    public void inicializarConexion(Socket socket, ObjectOutputStream salida,
                                    ObjectInputStream entrada, String nombreUsuario, String rol, String pass) {
        this.socket        = socket;
        this.salida        = salida;
        this.entrada       = entrada;
        this.nombreUsuario = nombreUsuario;
        this.rolUsuario    = rol != null ? rol : "USER";
        this.passwordUsuario = pass;
        this.chatService   = new ChatService(salida);
        this.contactManager = new ContactManager(this);

        chatsActivos.add("GENERAL");
        historialesChat.put("GENERAL", new ArrayList<>());

        inicializarGestoresPerfilesYCarpetas();

        if (userAvatar != null) {
            userAvatar.setCursor(javafx.scene.Cursor.HAND);
            userAvatar.setOnMouseClicked(e -> mostrarPerfilPropio());
        }

        iniciarHiloReceptor();

        if (txtBuscador != null)
            txtBuscador.textProperty().addListener((o, v, n) -> dibujarContactosActivos());

        if (chatContainer != null && scrollChat != null)
            chatContainer.heightProperty().addListener((o, v, n) -> scrollChat.setVvalue(1.0));

        dibujarContactosActivos();
    }

    private void iniciarHiloReceptor() {
        HiloCliente hilo = new HiloCliente(socket, entrada, nombreUsuario, this);
        hilo.setDaemon(true);
        hilo.start();
    }

    public void intentarReconectar() {
        if (intentosReconexion >= MAX_INTENTOS) {
            Platform.runLater(() -> {
                mostrarAlertaError("Se ha perdido la conexión de forma permanente tras 3 intentos. Volviendo al login.");
                volverAlLogin();
            });
            return;
        }

        intentosReconexion++;
        registrarMensajeSistema("Intento de reconexión " + intentosReconexion + "/" + MAX_INTENTOS + "...");

        new Thread(() -> {
            try {
                // Pequeña espera entre intentos
                Thread.sleep(2000);

                // Recrear SSLSocket
                javax.net.ssl.SSLSocketFactory ssf = crearSSLSocketFactory();
                socket = ssf.createSocket("localhost", 55555);
                salida = new ObjectOutputStream(socket.getOutputStream());
                salida.flush();
                entrada = new ObjectInputStream(socket.getInputStream());

                // Re-autenticar
                DatosMensaje loginMsg = new DatosMensaje();
                loginMsg.setTipo(TipoMensaje.LOGIN_REGISTER);
                loginMsg.setRemitente(nombreUsuario);
                loginMsg.setPassword(passwordUsuario);
                
                salida.writeObject(loginMsg);
                salida.flush();

                DatosMensaje resp = (DatosMensaje) entrada.readObject();
                if (resp != null && resp.isSuccess()) {
                    intentosReconexion = 0;
                    registrarMensajeSistema("¡Reconexión exitosa!");
                    iniciarHiloReceptor();
                } else {
                    intentarReconectar(); // Reintento recursivo (con control de MAX_INTENTOS)
                }

            } catch (Exception e) {
                System.err.println("Fallo en reconexión: " + e.getMessage());
                intentarReconectar();
            }
        }).start();
    }

    private void volverAlLogin() {
        try {
            // Limpiar recursos
            try { if (entrada != null) entrada.close(); } catch (Exception ignored) {}
            try { if (salida  != null) salida.close();  } catch (Exception ignored) {}
            try { if (socket  != null) socket.close();  } catch (Exception ignored) {}
            socket = null; salida = null; entrada = null;

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/pspro/chattcpmultisala/login.fxml"));
            Scene scene = new Scene(loader.load(), 800, 500);
            Stage stage = (Stage) btnEnviar.getScene().getWindow();
            stage.setTitle("ChatTCP SSL - Iniciar Sesión");
            stage.setScene(scene);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private javax.net.ssl.SSLSocketFactory crearSSLSocketFactory() throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
        InputStream is = getClass().getResourceAsStream("/servidor.p12");
        if (is == null) throw new FileNotFoundException("Keystore not found");
        ks.load(is, "chat-password".toCharArray());

        javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
        sc.init(null, tmf.getTrustManagers(), null);
        return sc.getSocketFactory();
    }

    @FXML
    public void onToggleDarkMode(ActionEvent event) {
        if (mainRoot == null) return;
        
        isDarkMode = !isDarkMode;
        
        // Guardar preferencia en el perfil
        if (currentUserProfile != null) {
            currentUserProfile.setDarkMode(isDarkMode);
            profileManager.updateProfile(currentUserProfile);
        }
        
        aplicarTema(isDarkMode);
    }

    private void aplicarTema(boolean dark) {
        if (mainRoot == null) return;
        
        String cssPath = getClass().getResource("/org/pspro/chattcpmultisala/dark-mode.css").toExternalForm();
        
        if (dark) {
            if (!mainRoot.getStyleClass().contains("dark-mode")) {
                mainRoot.getStyleClass().add("dark-mode");
            }
            if (!mainRoot.getStylesheets().contains(cssPath)) {
                mainRoot.getStylesheets().add(cssPath);
            }
        } else {
            mainRoot.getStyleClass().remove("dark-mode");
            mainRoot.getStylesheets().remove(cssPath);
        }
    }

    private void mostrarPerfilPropio() {
        if (profilePanel == null) return;
        profilePanelVisible = true;
        profilePanel.setVisible(true);
        profilePanel.setManaged(true);
        actualizarPanelPerfil(); 
    }

    private void solicitarInfoContexto() {
        try {
            DatosMensaje req = new DatosMensaje();
            req.setTipo(TipoMensaje.REQUEST_CONTEXT_INFO);
            req.setDestino(destinatarioActual);
            enviarAlServidorExterno(req);
        } catch (IOException e) { e.printStackTrace(); }
    }

    /** Compatibilidad retroactiva (sin rol). */
    public void inicializarConexion(Socket socket, ObjectOutputStream salida,
                                    ObjectInputStream entrada, String nombreUsuario) {
        inicializarConexion(socket, salida, entrada, nombreUsuario, "USER", null);
    }

    public boolean esModerador() {
        return "MODERATOR".equals(rolUsuario) || esModeradorEfectivo(destinatarioActual);
    }

    private final Set<String> canalesDondeSoyModTemporal = new HashSet<>();

    private boolean esModeradorEfectivo(String canal) {
        if ("MODERATOR".equals(rolUsuario)) return true;
        return canal != null && canalesDondeSoyModTemporal.contains(canal);
    }

    // =========================================================================
    // ENVÍO DE MENSAJES
    // =========================================================================

    @FXML
    public void onEnviarClick(ActionEvent actionEvent) {
        String raw = txtMensaje.getText();
        if (ValidadorEntrada.contieneURL(raw)) {
            mostrarAlertaError("No se permite el envío de enlaces para evitar SPAM.");
            return;
        }
        
        String contenido = ValidadorEntrada.sanitizarMensaje(raw);
        if (contenido == null || contenido.isEmpty() || salida == null) return;

        boolean esCanal   = canalesActivos.contains(destinatarioActual);
        boolean esGeneral = "GENERAL".equals(destinatarioActual);

        try {
            DatosMensaje mensaje = new DatosMensaje();
            mensaje.setRemitente(nombreUsuario);
            mensaje.setDestino(destinatarioActual);
            mensaje.setTimestamp(LocalDateTime.now());
            mensaje.setMensajeId(UUID.randomUUID().toString());

            if (esCanal) {
                mensaje.setTipo(TipoMensaje.MENSAJE_CANAL);
                // Cifrar para canal
                mensaje.setContenido(CifradoMensajes.cifrar(contenido));
                mensaje.setCifrado(true);
            } else if (esGeneral) {
                mensaje.setTipo(TipoMensaje.MENSAJE_GENERAL);
                mensaje.setContenido(contenido);
            } else {
                mensaje.setTipo(TipoMensaje.MENSAJE_PRIVADO);
                // Cifrar para privado
                mensaje.setContenido(CifradoMensajes.cifrar(contenido));
                mensaje.setCifrado(true);
            }

            enviarAlServidorExterno(mensaje);

            // Mostrar el mensaje localmente con texto plano (propio)
            DatosMensaje msgLocal = chatService.clonarConContenido(mensaje, contenido);
            if (esGeneral) registrarMensaje(msgLocal, "GENERAL", true);

            if ("*****".equals(contenido)) System.exit(0);
            txtMensaje.clear();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // ENVÍO DE ARCHIVOS
    // =========================================================================

    @FXML
    public void onEnviarArchivoClick(ActionEvent actionEvent) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar archivo");
        File archivo = fc.showOpenDialog(btnEnviar.getScene().getWindow());
        if (archivo == null) return;

        try {
            byte[] datos = Files.readAllBytes(archivo.toPath());
            if (datos.length > 10 * 1024 * 1024) {  // Máx 10 MB
                mostrarAlertaError("El archivo supera el límite de 10 MB.");
                return;
            }

            String datosCifrados = CifradoMensajes.cifrarBytes(datos);

            DatosMensaje msg = new DatosMensaje();
            msg.setTipo(TipoMensaje.ENVIAR_ARCHIVO);
            msg.setRemitente(nombreUsuario);
            msg.setDestino(destinatarioActual);
            msg.setNombreArchivo(archivo.getName());
            msg.setDatosArchivoCifrado(datosCifrados);
            msg.setTamanoArchivo(datos.length);
            msg.setTimestamp(LocalDateTime.now());
            String aid = UUID.randomUUID().toString();
            msg.setArchivoId(aid);
            msg.setMensajeId(aid); // Para que el sistema de borrado lo encuentre

            enviarAlServidorExterno(msg);

            // No registramos localmente aquí para evitar duplicados. 
            // El servidor nos lo devolverá (echo) y HiloCliente lo registrará.

        } catch (IOException e) {
            mostrarAlertaError("Error leyendo el archivo: " + e.getMessage());
        }
    }

    // =========================================================================
    // BORRAR MENSAJE PROPIO (PRIVADO)
    // =========================================================================

    private void borrarMensajePropio(DatosMensaje mensaje, String sala) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sentAt = mensaje.getTimestamp();
        boolean menosDe5Min = sentAt != null && java.time.Duration.between(sentAt, now).toMinutes() < 5;

        if (menosDe5Min) {
            // Mostrar diálogo para elegir entre borrar para mí o para todos
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Eliminar mensaje");
            alert.setHeaderText("¿Cómo quieres eliminar este mensaje?");
            
            ButtonType btnParaTodos = new ButtonType("Eliminar para todos");
            ButtonType btnParaMi = new ButtonType("Eliminar para mí");
            ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
            
            alert.getButtonTypes().setAll(btnParaTodos, btnParaMi, btnCancelar);
            
            alert.showAndWait().ifPresent(tipo -> {
                if (tipo == btnParaTodos) {
                    ejecutarBorrado(mensaje.getMensajeId(), sala, true);
                } else if (tipo == btnParaMi) {
                    sustituirBurbujaPorBorradoLocal(mensaje.getMensajeId(), sala);
                }
            });
        } else {
            // Solo se puede borrar para mí (localmente)
            sustituirBurbujaPorBorradoLocal(mensaje.getMensajeId(), sala);
            mostrarAlerta("Han pasado más de 5 minutos. El mensaje solo se ha eliminado de tu vista.");
        }
    }

    private void ejecutarBorrado(String mensajeId, String destino, boolean paraTodos) {
        try {
            DatosMensaje msg = new DatosMensaje();
            msg.setTipo(TipoMensaje.BORRAR_MENSAJE);
            msg.setRemitente(nombreUsuario);
            msg.setDestino(destino);
            msg.setMensajeId(mensajeId);
            msg.setSuccess(paraTodos); // Reutilizamos success para indicar si es global
            enviarAlServidorExterno(msg);

            // Actualizar localmente si es global
            if (paraTodos) {
                sustituirBurbujaPorBorrado(mensajeId, destino);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sustituirBurbujaPorBorradoLocal(String mensajeId, String sala) {
        Platform.runLater(() -> {
            HBox burbujaContenedora = burbujasById.get(mensajeId);
            if (burbujaContenedora != null && !burbujaContenedora.getChildren().isEmpty()) {
                // Ahora es un StackPane que contiene el VBox de la burbuja y el menú
                Node root = burbujaContenedora.getChildren().get(0);
                procesarNodosBorrado(root, "ELIMINASTE ESTE MENSAJE PARA TÍ MISMO");
            }
        });
    }

    public void sustituirBurbujaPorBorrado(String mensajeId, String sala) {
        Platform.runLater(() -> {
            HBox burbujaContenedora = burbujasById.get(mensajeId);
            if (burbujaContenedora != null && !burbujaContenedora.getChildren().isEmpty()) {
                Node root = burbujaContenedora.getChildren().get(0);
                procesarNodosBorrado(root, "EL MENSAJE HA SIDO ELIMINADO");
            }
        });
    }

    /**
     * Recorre recursivamente los nodos de una burbuja para ocultar botones/labels extras
     * y sustituir el texto principal.
     */
    private void procesarNodosBorrado(Node nodo, String textoSustituto) {
        if (nodo instanceof Parent p) {
            // Copia para evitar ConcurrentModificationException si se alteraran hijos (aunque aquí solo cambiamos propiedades)
            new ArrayList<>(p.getChildrenUnmodifiable()).forEach(child -> procesarNodosBorrado(child, textoSustituto));
        }

        if (nodo instanceof Label lbl) {
            if (lbl.getStyleClass().contains("message-text") || lbl.getText().startsWith("📎")) {
                lbl.setText(textoSustituto);
                lbl.setStyle("-fx-font-style: italic; -fx-text-fill: #999;");
            } else if (lbl.getStyleClass().contains("timestamp") || "▼".equals(lbl.getText())) {
                nodo.setVisible(false);
                nodo.setManaged(false);
            }
        } else if (nodo instanceof Button) {
            nodo.setVisible(false);
            nodo.setManaged(false);
        }
    }

    public void eliminarBurbujaLocal(String mensajeId, String sala) {
        Platform.runLater(() -> {
            HBox burbuja = burbujasById.remove(mensajeId);
            if (burbuja != null) {
                chatContainer.getChildren().remove(burbuja);
                List<Node> hist = historialesChat.get(sala);
                if (hist != null) hist.remove(burbuja);
            }
        });
    }

    // =========================================================================
    // MODERACIÓN (solo MODERATOR)
    // =========================================================================

    /**
     * Menú de moderación rápida al hacer clic en un nombre de usuario en el chat.
     */
    private void mostrarMenuModeracionRapida(String objetivo, String canal) {
        if (!esModerador()) return;
        if (objetivo.equals(nombreUsuario) || "SISTEMA".equals(objetivo)) return;

        List<String> opciones = new ArrayList<>();
        opciones.add("Ver Perfil");
        
        // El baneo solo tiene sentido en canales específicos, no en GENERAL
        if (canal != null && !canal.equals("GENERAL") && canalesActivos.contains(canal)) {
            opciones.add("Banear de este canal");
        }
        
        opciones.add("Promover temporalmente");
        
        // Gestionar canal tampoco aplica a GENERAL
        if (canal != null && !canal.equals("GENERAL")) {
            opciones.add("Gestionar Canal (Suspender/Reactivar)");
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(opciones.get(0), opciones);
        dialog.setTitle("Moderación Rápida");
        dialog.setHeaderText("Acción sobre el usuario: " + objetivo + " en " + canal);
        dialog.setContentText("Elige una acción:");

        dialog.showAndWait().ifPresent(accion -> {
            switch (accion) {
                case "Ver Perfil" -> solicitarPerfilExterno(objetivo);
                case "Banear de este canal" -> ejecutarBanDirecto(objetivo, canal);
                case "Promover temporalmente" -> ejecutarPromocionDirecta(objetivo, canal);
                case "Gestionar Canal (Suspender/Reactivar)" -> mostrarMenuModeracion();
            }
        });
    }

    private void solicitarPerfilExterno(String objetivo) {
        try {
            DatosMensaje req = new DatosMensaje();
            req.setTipo(TipoMensaje.REQUEST_PROFILE);
            req.setRemitente(nombreUsuario);
            req.setDestino(objetivo);
            enviarAlServidorExterno(req);
            registrarMensajeSistema("Solicitando perfil de " + objetivo + "...");
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void mostrarPerfilUsuarioExterno(UserProfile p) {
        if (profilePanel == null) return;
        Platform.runLater(() -> {
            if (p == null) {
                mostrarAlertaError("No se pudo obtener la información del perfil del usuario.");
                return;
            }

            profilePanelVisible = true;
            profilePanel.setVisible(true);
            profilePanel.setManaged(true);

            if (lblPanelTitulo != null) lblPanelTitulo.setText("Perfil de Usuario");
            
            String initials = p.getAvatarInitials();
            if (initials == null || initials.isBlank()) initials = "?";
            
            if (profileAvatarBig != null) {
                profileAvatarBig.setText(initials);
                profileAvatarBig.setStyle("-fx-background-color: #005f9e; -fx-text-fill: white; -fx-font-size: 26; -fx-font-weight: bold; -fx-background-radius: 40;");
            }
            
            String displayName = p.getDisplayName();
            if (profileName != null) profileName.setText((displayName != null ? displayName : "Usuario") + " (@" + p.getUsername() + ")");
            
            // Restaurar etiquetas de Perfil
            if (infoIcon1 != null) infoIcon1.setText("📞");
            if (infoLabel1 != null) infoLabel1.setText("Teléfono");
            if (infoIcon2 != null) infoIcon2.setText("✉");
            if (infoLabel2 != null) infoLabel2.setText("Correo");
            if (infoIcon3 != null) infoIcon3.setText("📝");
            if (infoLabel3 != null) infoLabel3.setText("Biografía");
            if (rowEmail != null) { rowEmail.setVisible(true); rowEmail.setManaged(true); }

            if (profilePhone != null) profilePhone.setText(nvl(p.getPhoneNumber(), "No especificado"));
            if (profileEmail != null) profileEmail.setText(nvl(p.getEmail(), "No especificado"));
            if (profileBio != null) profileBio.setText(nvl(p.getBio(), "No especificado"));

            // Visibilidad
            if (containerMiembros != null) { containerMiembros.setVisible(false); containerMiembros.setManaged(false); }
            if (btnEditarPerfil != null) {
                boolean esElMio = p.getUsername() != null && p.getUsername().equals(nombreUsuario);
                btnEditarPerfil.setVisible(esElMio);
                btnEditarPerfil.setManaged(esElMio);
            }
            if (btnEliminarCanal != null) { btnEliminarCanal.setVisible(false); btnEliminarCanal.setManaged(false); }
        });
    }

    private void ejecutarBanDirecto(String objetivo, String canal) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Baneo");
        confirm.setHeaderText("¿Estás seguro de que quieres expulsar a " + objetivo + " del canal " + canal + "?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    DatosMensaje msg = new DatosMensaje();
                    msg.setTipo(TipoMensaje.BANEAR_USUARIO);
                    msg.setRemitente(nombreUsuario);
                    msg.setDestino(objetivo);
                    msg.setContenido(canal);
                    enviarAlServidorExterno(msg);
                } catch (IOException e) { e.printStackTrace(); }
            }
        });
    }

    private void ejecutarPromocionDirecta(String objetivo, String canal) {
        TextInputDialog td = new TextInputDialog("300");
        td.setTitle("Promover temporal");
        td.setHeaderText("Promocionar a " + objetivo + " en el canal " + (canal != null ? canal : "actual"));
        td.setContentText("Segundos (máx. 3600):");
        td.showAndWait().ifPresent(segsStr -> {
            try {
                long segs = Long.parseLong(segsStr.trim());
                DatosMensaje msg = new DatosMensaje();
                msg.setTipo(TipoMensaje.PROMOVER_TEMPORAL);
                msg.setRemitente(nombreUsuario);
                msg.setDestino(objetivo);
                msg.setContenido(canal); // Contexto del canal
                msg.setSegundosPromocion(segs);
                enviarAlServidorExterno(msg);
            } catch (NumberFormatException | IOException e) { e.printStackTrace(); }
        });
    }

    private void mostrarMenuModeracion() {
        if (!esModerador()) { mostrarAlertaError("Sin permisos de moderador."); return; }

        ChoiceDialog<String> dialog = new ChoiceDialog<>("Banear usuario",
                List.of("Banear usuario del canal",
                        "Suspender canal",
                        "Reactivar canal",
                        "Promover temporalmente"));
        dialog.setTitle("Moderación");
        dialog.setHeaderText("Elige una acción de moderación:");
        dialog.showAndWait().ifPresent(accion -> {
            switch (accion) {
                case "Banear usuario del canal"  -> mostrarDialogoBanear();
                case "Suspender canal"           -> suspenderCanal(true);
                case "Reactivar canal"           -> suspenderCanal(false);
                case "Promover temporalmente"    -> mostrarDialogoPromocion();
            }
        });
    }

    private void mostrarDialogoBanear() {
        if (!canalesActivos.contains(destinatarioActual)) {
            mostrarAlertaError("Solo puedes banear usuarios en un canal.");
            return;
        }
        List<String> candidatos = miembrosCanales.getOrDefault(destinatarioActual, List.of())
                .stream().filter(u -> !u.equals(nombreUsuario)).toList();
        if (candidatos.isEmpty()) { mostrarAlertaError("No hay usuarios que banear."); return; }

        ChoiceDialog<String> d = new ChoiceDialog<>(candidatos.get(0), candidatos);
        d.setTitle("Banear usuario"); d.setHeaderText("Selecciona el usuario a expulsar:");
        d.showAndWait().ifPresent(objetivo -> {
            try {
                DatosMensaje msg = new DatosMensaje();
                msg.setTipo(TipoMensaje.BANEAR_USUARIO);
                msg.setRemitente(nombreUsuario);
                msg.setDestino(objetivo);
                msg.setContenido(destinatarioActual);
                enviarAlServidorExterno(msg);
            } catch (IOException e) { e.printStackTrace(); }
        });
    }

    private void suspenderCanal(boolean suspender) {
        if (!canalesActivos.contains(destinatarioActual)) {
            mostrarAlertaError("Solo puedes suspender un canal activo."); return;
        }
        try {
            DatosMensaje msg = new DatosMensaje();
            msg.setTipo(TipoMensaje.SUSPENDER_CANAL);
            msg.setRemitente(nombreUsuario);
            msg.setDestino(destinatarioActual);
            msg.setContenido(String.valueOf(suspender));
            enviarAlServidorExterno(msg);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void mostrarDialogoPromocion() {
        List<String> candidatos = usuariosEnLinea.stream()
                .filter(u -> !u.equals(nombreUsuario)).toList();
        if (candidatos.isEmpty()) { mostrarAlertaError("No hay usuarios disponibles."); return; }

        ChoiceDialog<String> d = new ChoiceDialog<>(candidatos.get(0), candidatos);
        d.setTitle("Promover temporal"); d.setHeaderText("Selecciona usuario a promover:");
        d.showAndWait().ifPresent(objetivo -> {
            TextInputDialog td = new TextInputDialog("300");
            td.setTitle("Duración"); td.setHeaderText("Segundos de promoción (máx. 3600):");
            td.showAndWait().ifPresent(segsStr -> {
                try {
                    long segs = Long.parseLong(segsStr.trim());
                    DatosMensaje msg = new DatosMensaje();
                    msg.setTipo(TipoMensaje.PROMOVER_TEMPORAL);
                    msg.setRemitente(nombreUsuario);
                    msg.setDestino(objetivo);
                    msg.setSegundosPromocion(segs);
                    enviarAlServidorExterno(msg);
                } catch (NumberFormatException | IOException e) { e.printStackTrace(); }
            });
        });
    }

    // =========================================================================
    // LISTA DE USUARIOS / CONTACTOS
    // =========================================================================

    public void actualizarListaUsuarios(List<String> usuarios) {
        this.usuariosEnLinea.clear();
        this.usuariosEnLinea.addAll(usuarios);
        Platform.runLater(this::dibujarContactosActivos);
    }

    public void dibujarContactosActivos() {
        if (contactList == null) return;
        String query = txtBuscador != null ? txtBuscador.getText() : "";
        
        contactManager.dibujarContactos(
            contactList, chatsActivos, canalesActivos, chatsConMensajesNuevos, 
            destinatarioActual, query, folderManager.getCurrentFolderId(), 
            folderManager.getChatsInFolder(folderManager.getCurrentFolderId()),
            (label, handler) -> {
                // Mapeo de botones de acción
                switch (label) {
                    case "➕ Nuevo Chat" -> addBotonAccion(label, e -> mostrarDialogoNuevoChat());
                    case "📢 Nuevo Canal" -> addBotonAccion(label, e -> mostrarDialogoNuevoCanal());
                    case "📎 Enviar archivo" -> addBotonAccion(label, e -> onEnviarArchivoClick(null));
                }
            },
            this::cambiarDestinatario
        );
        
        // Botones condicionales (Moderación, Añadir Miembros) se añaden después o se integran en el manager
        Platform.runLater(() -> {
            if (esModerador())
                addBotonAccion("🛡️ Moderación", e -> mostrarMenuModeracion());

            if (canalesActivos.contains(destinatarioActual))
                addBotonAccion("👥 Añadir miembros", e -> mostrarDialogoAñadirMiembros(destinatarioActual));
        });
    }

    private void addBotonAccion(String texto, javafx.event.EventHandler<ActionEvent> handler) {
        Button b = new Button(texto);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("contact-button");
        b.setStyle("-fx-text-fill: #5085a8; -fx-font-weight: bold;");
        b.setOnAction(handler);
        contactList.getChildren().add(b);
    }

    // =========================================================================
    // REGISTRAR MENSAJES EN UI
    // =========================================================================

    public void registrarMensaje(DatosMensaje mensaje, String salaAsociada, boolean esPropio) {
        // Descifrar si viene cifrado
        if (mensaje.isCifrado() && mensaje.getContenido() != null) {
            String descifrado = CifradoMensajes.descifrar(mensaje.getContenido());
            mensaje.setContenido(descifrado != null ? descifrado : "[Mensaje no descifrable]");
            mensaje.setCifrado(false);
        }

        if (!salaAsociada.equals("GENERAL") && !chatsActivos.contains(salaAsociada)) {
            if (mensaje.getTipo() == TipoMensaje.MENSAJE_CANAL) canalesActivos.add(salaAsociada);
            chatsActivos.add(salaAsociada);
            folderManager.addChatToFolder(salaAsociada, "all");
            Platform.runLater(this::dibujarContactosActivos);
        }

        if (!esPropio && !destinatarioActual.equals(salaAsociada)) {
            chatsConMensajesNuevos.add(salaAsociada);
            Platform.runLater(this::actualizarUIUnread);
        }

        HBox contenedor = new HBox();
        boolean esSistema = "SISTEMA".equals(mensaje.getRemitente());

        if (esSistema) {
            contenedor.setAlignment(Pos.CENTER);
            contenedor.setPadding(new javafx.geometry.Insets(5, 0, 5, 0));
            Label txt = new Label(mensaje.getContenido());
            txt.setStyle("-fx-font-size:12px;-fx-text-fill:white;-fx-background-color:rgba(0,0,0,0.2);-fx-background-radius:12;-fx-padding:4 12 4 12;");
            contenedor.getChildren().add(txt);

        } else {
            contenedor.setAlignment(esPropio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            
            StackPane bubbleStack = UIFactory.crearBurbujaMensaje(
                mensaje, esPropio, salaAsociada, formatearHora(mensaje.getTimestamp()), 
                esModerador(), this::borrarMensajePropio, this::mostrarMenuModeracionRapida
            );

            contenedor.getChildren().add(bubbleStack);
            if (esPropio) HBox.setMargin(bubbleStack, new javafx.geometry.Insets(0,0,0,50));
            else          HBox.setMargin(bubbleStack, new javafx.geometry.Insets(0,50,0,0));
        }

        if (mensaje.getMensajeId() != null) burbujasById.put(mensaje.getMensajeId(), contenedor);

        historialesChat.computeIfAbsent(salaAsociada, k -> new ArrayList<>()).add(contenedor);
        if (destinatarioActual.equals(salaAsociada)) {
            Platform.runLater(() -> { chatContainer.getChildren().add(contenedor); scrollAlFinal(); });
        }
    }

    public void registrarArchivoEnUI(DatosMensaje msg, boolean esPropio) {
        Platform.runLater(() -> {
            boolean esCanal = canalesActivos.contains(msg.getDestino());
            boolean esGeneral = "GENERAL".equals(msg.getDestino());

            HBox contenedor = new HBox();
            contenedor.setAlignment(esPropio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

            boolean soyModeradorAqui = (esGeneral || esCanal) && esModerador();
            
            StackPane bubbleStack = UIFactory.crearBurbujaArchivo(
                msg, esPropio, msg.getDestino(), formatearHora(msg.getTimestamp()),
                soyModeradorAqui, this::borrarMensajePropio, this::descargarArchivo
            );

            contenedor.getChildren().add(bubbleStack);
            if (msg.getArchivoId() != null) burbujasById.put(msg.getArchivoId(), contenedor);

            String salaAsociada = msg.getDestino();
            if (nombreUsuario.equals(msg.getDestino())) {
                salaAsociada = msg.getRemitente();
            }

            historialesChat.computeIfAbsent(salaAsociada, k -> new ArrayList<>()).add(contenedor);
            if (destinatarioActual.equals(salaAsociada)) {
                chatContainer.getChildren().add(contenedor);
                scrollAlFinal();
            }
        });
    }

    private void descargarArchivo(DatosMensaje msg) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Guardar archivo");
        fc.setInitialFileName(msg.getNombreArchivo());
        File destino = fc.showSaveDialog(btnEnviar.getScene().getWindow());
        if (destino == null) return;
        try {
            byte[] datos = CifradoMensajes.descifrarBytes(msg.getDatosArchivoCifrado());
            Files.write(destino.toPath(), datos);
        } catch (IOException e) {
            mostrarAlertaError("Error guardando archivo: " + e.getMessage());
        }
    }

    private void eliminarArchivoRemoto(DatosMensaje msg) {
        try {
            DatosMensaje del = new DatosMensaje();
            del.setTipo(TipoMensaje.ELIMINAR_ARCHIVO);
            del.setRemitente(nombreUsuario);
            del.setDestino(msg.getDestino());
            del.setArchivoId(msg.getArchivoId());
            enviarAlServidorExterno(del);
            // La UI se actualizará cuando el servidor nos mande la confirmación
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void registrarMensajeSistema(String texto) {
        if (texto == null) return;
        
        // Detectar si nos han dado permisos de moderador temporal
        if (texto.contains("Has recibido permisos de moderador temporal en el canal")) {
            int start = texto.indexOf("'") + 1;
            int end = texto.indexOf("'", start);
            if (start > 0 && end > start) {
                String canal = texto.substring(start, end);
                canalesDondeSoyModTemporal.add(canal);
                Platform.runLater(() -> {
                    dibujarContactosActivos();
                    if (destinatarioActual.equals(canal)) {
                        refrescarChatActual(); // Re-renderizar para habilitar clicks
                    }
                });
            }
        }
        
        // Detectar si han expirado
        if (texto.contains("Tu promoción temporal de moderador en el canal") && texto.contains("ha expirado")) {
            int start = texto.indexOf("'") + 1;
            int end = texto.indexOf("'", start);
            if (start > 0 && end > start) {
                String canal = texto.substring(start, end);
                canalesDondeSoyModTemporal.remove(canal);
                Platform.runLater(() -> {
                    dibujarContactosActivos();
                    if (destinatarioActual.equals(canal)) {
                        refrescarChatActual(); // Re-renderizar para quitar clicks
                    }
                });
            }
        }

        DatosMensaje msg = new DatosMensaje();
        msg.setRemitente("SISTEMA");
        msg.setContenido(texto);
        registrarMensaje(msg, destinatarioActual, false);
    }

    // =========================================================================
    // CANALES
    // =========================================================================

    public void agregarCanalLocal(String nombre, List<String> miembros) {
        canalesActivos.add(nombre);
        chatsActivos.add(nombre);
        if (miembros != null) miembrosCanales.put(nombre, new ArrayList<>(miembros));
        Platform.runLater(this::dibujarContactosActivos);
    }

    public void actualizarMiembrosCanal(String nombre, List<String> nuevos) {
        List<String> act = miembrosCanales.computeIfAbsent(nombre, k -> new ArrayList<>());
        for (String m : nuevos) if (!act.contains(m)) act.add(m);
    }

    private void mostrarDialogoNuevoCanal() {
        String nombreCanal = "";
        while (true) {
            TextInputDialog nd = new TextInputDialog(nombreCanal);
            nd.setTitle("Nuevo Canal"); nd.setHeaderText("Nombre del canal:");
            Optional<String> r = nd.showAndWait();
            if (r.isEmpty()) return;
            nombreCanal = ValidadorEntrada.sanitizarCampoPerfil(r.get());
            if (nombreCanal == null || nombreCanal.isBlank()) { mostrarAlertaError("El nombre no puede estar vacío."); continue; }
            if (ValidadorEntrada.contieneURL(nombreCanal)) { mostrarAlertaError("El nombre del canal no puede contener URLs."); continue; }

            ListView<String> lv = new ListView<>();
            lv.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            lv.getItems().addAll(usuariosEnLinea.stream().filter(u -> !u.equals(nombreUsuario)).toList());

            Dialog<List<String>> d = new Dialog<>();
            d.setTitle("Seleccionar miembros"); d.setHeaderText("Miembros del canal: " + nombreCanal);
            ButtonType ok = new ButtonType("Crear", ButtonBar.ButtonData.OK_DONE);
            d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
            d.getDialogPane().setContent(lv);
            d.setResultConverter(db -> db == ok ? new ArrayList<>(lv.getSelectionModel().getSelectedItems()) : null);

            Optional<List<String>> mr = d.showAndWait();
            if (mr.isEmpty()) continue;
            List<String> miembros = mr.get();
            if (miembros.isEmpty()) { mostrarAlertaError("Selecciona al menos un usuario."); continue; }
            if (!miembros.contains(nombreUsuario)) miembros.add(nombreUsuario);

            try {
                DatosMensaje msg = new DatosMensaje();
                msg.setTipo(TipoMensaje.CREAR_CANAL);
                msg.setRemitente(nombreUsuario); msg.setDestino(nombreCanal); msg.setMiembros(miembros);
                enviarAlServidorExterno(msg);
                return;
            } catch (IOException e) { e.printStackTrace(); break; }
        }
    }

    private void mostrarDialogoAñadirMiembros(String canal) {
        List<String> actuales = miembrosCanales.getOrDefault(canal, new ArrayList<>());
        List<String> candidatos = usuariosEnLinea.stream().filter(u -> !actuales.contains(u)).toList();
        if (candidatos.isEmpty()) {
            mostrarAlerta("Todos los usuarios ya están en el canal."); return;
        }

        ListView<String> lv = new ListView<>();
        lv.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lv.getItems().addAll(candidatos);

        Dialog<List<String>> d = new Dialog<>();
        d.setTitle("Añadir miembros"); d.setHeaderText("Canal: " + canal);
        ButtonType ok = new ButtonType("Añadir", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        d.getDialogPane().setContent(lv);
        d.setResultConverter(db -> db == ok ? new ArrayList<>(lv.getSelectionModel().getSelectedItems()) : null);

        d.showAndWait().ifPresent(sel -> {
            if (sel.isEmpty()) return;
            try {
                for (String usuario : sel) {
                    DatosMensaje msg = new DatosMensaje();
                    msg.setTipo(TipoMensaje.SOLICITUD_UNION_CANAL);
                    msg.setDestino(usuario);
                    msg.setContenido(canal);
                    enviarAlServidorExterno(msg);
                }
            } catch (IOException e) { e.printStackTrace(); }
        });
    }

    private void mostrarDialogoNuevoChat() {
        List<String> opciones = usuariosEnLinea.stream()
                .filter(u -> !u.equals(nombreUsuario) && !chatsActivos.contains(u)).toList();
        if (opciones.isEmpty()) {
            mostrarAlerta("No hay usuarios nuevos disponibles."); return;
        }
        ChoiceDialog<String> d = new ChoiceDialog<>(opciones.get(0), opciones);
        d.setTitle("Nuevo Chat"); d.setHeaderText("Selecciona usuario para enviar solicitud:");
        d.showAndWait().ifPresent(u -> {
            try {
                DatosMensaje req = new DatosMensaje();
                req.setTipo(TipoMensaje.SOLICITUD_CHAT_PRIVADO);
                req.setDestino(u);
                enviarAlServidorExterno(req);
            } catch (IOException e) { e.printStackTrace(); }
        });
    }

    public void manejarSolicitudChat(DatosMensaje msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Solicitud de Chat");
            alert.setHeaderText(msg.getRemitente() + " quiere chatear contigo por privado.");
            alert.setContentText("¿Aceptas la solicitud?");

            ButtonType btnAceptar = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
            ButtonType btnRechazar = new ButtonType("Rechazar", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(btnAceptar, btnRechazar);

            alert.showAndWait().ifPresent(tipo -> {
                boolean aceptado = (tipo == btnAceptar);
                try {
                    DatosMensaje resp = new DatosMensaje();
                    resp.setTipo(TipoMensaje.RESPUESTA_CHAT_PRIVADO);
                    resp.setDestino(msg.getRemitente());
                    resp.setSuccess(aceptado);
                    enviarAlServidorExterno(resp);

                    if (aceptado) {
                        chatsActivos.add(msg.getRemitente());
                        cambiarDestinatario(msg.getRemitente());
                    }
                } catch (IOException e) { e.printStackTrace(); }
            });
        });
    }

    public void manejarRespuestaChat(DatosMensaje msg) {
        Platform.runLater(() -> {
            if (msg.isSuccess()) {
                mostrarAlerta(msg.getRemitente() + " ha aceptado tu solicitud de chat.");
                chatsActivos.add(msg.getRemitente());
                cambiarDestinatario(msg.getRemitente());
            } else {
                mostrarAlertaError(msg.getRemitente() + " ha rechazado tu solicitud de chat.");
            }
        });
    }

    public void manejarSolicitudCanal(DatosMensaje msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Invitación a Canal");
            alert.setHeaderText(msg.getRemitente() + " te invita al canal: " + msg.getContenido());
            alert.setContentText("¿Quieres unirte?");

            ButtonType btnAceptar = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
            ButtonType btnRechazar = new ButtonType("Rechazar", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(btnAceptar, btnRechazar);

            alert.showAndWait().ifPresent(tipo -> {
                boolean aceptado = (tipo == btnAceptar);
                try {
                    DatosMensaje resp = new DatosMensaje();
                    resp.setTipo(TipoMensaje.RESPUESTA_UNION_CANAL);
                    resp.setDestino(msg.getRemitente());
                    resp.setContenido(msg.getContenido());
                    resp.setSuccess(aceptado);
                    enviarAlServidorExterno(resp);
                } catch (IOException e) { e.printStackTrace(); }
            });
        });
    }

    // =========================================================================
    // NAVEGACIÓN
    // =========================================================================

    private void cambiarDestinatario(String destino) {
        destinatarioActual = destino;
        if (chatsConMensajesNuevos.remove(destino)) actualizarUIUnread();
        dibujarContactosActivos();
        if (lblTituloChat != null) {
            if ("GENERAL".equals(destino)) lblTituloChat.setText("Sala de Chat General");
            else if (canalesActivos.contains(destino)) lblTituloChat.setText("Canal: " + destino);
            else lblTituloChat.setText("Chat privado con: " + destino);
        }
        chatContainer.getChildren().clear();
        chatContainer.getChildren().addAll(historialesChat.getOrDefault(destino, List.of()));
        scrollAlFinal();
    }

    private void actualizarUIUnread() {
        folderManager.updateFolderUnreadCount("unread", chatsConMensajesNuevos.size());
        inicializarCarpetas();
        dibujarContactosActivos();
    }

    // =========================================================================
    // CLOSE
    // =========================================================================

    @FXML
    public void onCerrarClick(ActionEvent actionEvent) {
        try {
            if (salida != null) {
                DatosMensaje despedida = new DatosMensaje();
                despedida.setTipo(TipoMensaje.MENSAJE_GENERAL);
                despedida.setRemitente(nombreUsuario);
                despedida.setContenido("*****");
                enviarAlServidorExterno(despedida);
            }
        } catch (IOException e) { e.printStackTrace(); }
        System.exit(0);
    }

    // =========================================================================
    // PERFIL / CARPETAS
    // =========================================================================

    public void inicializarGestoresPerfilesYCarpetas() {
        profileManager = new ProfileManager();
        folderManager  = new ChatFolderManager();
        profileService = new ProfileService(this, profileManager);
        
        UserProfile perfil = profileManager.getProfileByUsername(nombreUsuario);
        if (perfil == null) perfil = profileManager.createProfile(nombreUsuario, nombreUsuario);
        currentUserProfile = perfil;
        profileManager.setCurrentProfile(perfil);

        this.isDarkMode = currentUserProfile.isDarkMode();
        aplicarTema(isDarkMode);

        actualizarPanelPerfil();
        inicializarCarpetas();
        sincronizarPerfilConServidor();
    }
    private void sincronizarPerfilConServidor() {
        if (currentUserProfile == null) return;
        try {
            DatosMensaje sync = new DatosMensaje();
            sync.setTipo(TipoMensaje.SYNC_PROFILE);
            sync.setRemitente(nombreUsuario);
            sync.setUserProfile(currentUserProfile);
            enviarAlServidorExterno(sync);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void actualizarPanelPerfil() {
        if (currentUserProfile == null) return;
        Platform.runLater(() -> {
            if (userAvatar    != null) userAvatar.setText(currentUserProfile.getAvatarInitials());
            if (userName      != null) userName.setText(currentUserProfile.getDisplayName()
                    + (esModerador() ? " 🛡️" : ""));
            if (userStatus    != null) userStatus.setText(currentUserProfile.getStatusText());
            if (lblPanelTitulo != null) lblPanelTitulo.setText("Perfil de Usuario");
            if (profileAvatarBig != null) {
                profileAvatarBig.setText(currentUserProfile.getAvatarInitials());
                profileAvatarBig.setStyle("-fx-background-color: #005f9e; -fx-text-fill: white; -fx-font-size: 26; -fx-font-weight: bold; -fx-background-radius: 40;");
            }
            if (profileName   != null) profileName.setText(currentUserProfile.getDisplayName());
            
            // Restaurar etiquetas de Perfil de Usuario
            if (infoIcon1 != null) infoIcon1.setText("📞");
            if (infoLabel1 != null) infoLabel1.setText("Teléfono");
            if (infoIcon2 != null) infoIcon2.setText("✉");
            if (infoLabel2 != null) infoLabel2.setText("Correo");
            if (infoIcon3 != null) infoIcon3.setText("📝");
            if (infoLabel3 != null) infoLabel3.setText("Biografía");
            if (rowEmail != null) { rowEmail.setVisible(true); rowEmail.setManaged(true); }

            if (profilePhone  != null) profilePhone.setText(nvl(currentUserProfile.getPhoneNumber(), "No especificado"));
            if (profileEmail  != null) profileEmail.setText(nvl(currentUserProfile.getEmail(), "No especificado"));
            if (profileBio    != null) profileBio.setText(nvl(currentUserProfile.getBio(), "No especificado"));

            // Visibilidad de componentes
            if (containerMiembros != null) { containerMiembros.setVisible(false); containerMiembros.setManaged(false); }
            if (btnEditarPerfil != null) { btnEditarPerfil.setVisible(true); btnEditarPerfil.setManaged(true); }
            if (btnEliminarCanal != null) { btnEliminarCanal.setVisible(false); btnEliminarCanal.setManaged(false); }
        });
    }

    private void inicializarCarpetas() {
        if (foldersContainer == null) return;
        Platform.runLater(() -> {
            foldersContainer.getChildren().clear();
            for (ChatFolder c : folderManager.getAllFolders()) {
                Button btn = new Button(c.getIcon() + " " + c.getName()
                        + (c.getUnreadCount() > 0 ? " (" + c.getUnreadCount() + ")" : ""));
                btn.setStyle("-fx-background-color:transparent;-fx-text-fill:#5085a8;-fx-border-color:transparent;-fx-padding:6 12;-fx-font-size:12;-fx-cursor:hand;"
                        + (c.getId().equals(folderManager.getCurrentFolderId())
                           ? "-fx-border-color:#5085a8;-fx-border-width:0 0 2 0;" : ""));
                btn.setOnAction(e -> seleccionarCarpeta(c.getId()));
                foldersContainer.getChildren().add(btn);
            }
        });
    }

    public void seleccionarCarpeta(String id) {
        folderManager.setCurrentFolder(id);
        dibujarContactosActivos();
        inicializarCarpetas();
    }

    @FXML
    public void onToggleProfilePanel(ActionEvent event) {
        if (profilePanel != null) {
            profilePanelVisible = !profilePanelVisible;
            profilePanel.setVisible(profilePanelVisible);
            profilePanel.setManaged(profilePanelVisible);

            if (profilePanelVisible) {
                solicitarInfoContexto();
            }
        }
    }

    public void mostrarInfoContexto(Map<String, Object> data) {
        if (data == null || profilePanel == null) return;
        Platform.runLater(() -> {
            String type = (String) data.get("type");
            String name = (String) data.get("name");
            String id   = (String) data.get("id");
            String initials = name != null && !name.isEmpty() ? name.substring(0, 1).toUpperCase() : "?";
            
            if (profileAvatarBig != null) {
                profileAvatarBig.setText(initials);
                profileAvatarBig.setStyle("-fx-background-color: " + 
                    ("SALA".equals(type) ? "#27ae60" : ("CANAL".equals(type) ? "#e67e22" : "#005f9e")) + 
                    "; -fx-text-fill: white; -fx-font-size: 26; -fx-font-weight: bold; -fx-background-radius: 40;");
            }
            if (profileName != null) profileName.setText(name);
            if (lblPanelTitulo != null) {
                if ("SALA".equals(type)) lblPanelTitulo.setText("Datos de la Sala");
                else if ("CANAL".equals(type)) lblPanelTitulo.setText("Datos del Canal");
                else lblPanelTitulo.setText("Perfil de Usuario");
            }

            // Reutilizamos campos para mostrar info del canal/sala
            if ("SALA".equals(type) || "CANAL".equals(type)) {
                // Ajustar etiquetas
                if (infoIcon1 != null) infoIcon1.setText("📅");
                if (infoLabel1 != null) infoLabel1.setText("Fecha de creación");
                if (infoIcon3 != null) infoIcon3.setText("📄");
                if (infoLabel3 != null) infoLabel3.setText("Descripción");
                
                // El email no lo usamos aquí, usamos la lista de miembros
                if (rowEmail != null) { rowEmail.setVisible(false); rowEmail.setManaged(false); }

                if (profilePhone != null) {
                    if ("SALA".equals(type)) profilePhone.setText("Canal predeterminado");
                    else {
                        Object createdObj = data.get("created");
                        if (createdObj instanceof LocalDateTime created) {
                            profilePhone.setText(created.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                        } else {
                            profilePhone.setText("Desconocida");
                        }
                    }
                }
                
                if (profileBio != null) {
                    if ("SALA".equals(type)) profileBio.setText((String) data.get("description"));
                    else profileBio.setText("Moderador: " + data.get("moderator"));
                }

                // Lista de miembros
                if (containerMiembros != null) { containerMiembros.setVisible(true); containerMiembros.setManaged(true); }
                contactManager.dibujarMiembros(listMiembros, (List<String>) data.get("members"), 
                                             usuariosEnLinea, nombreUsuario, esModerador(), id, 
                                             this::mostrarMenuModeracionRapida);

                // Botones
                if (btnEditarPerfil != null) { btnEditarPerfil.setVisible(false); btnEditarPerfil.setManaged(false); }
                if (btnEliminarCanal != null) {
                    btnEliminarCanal.setVisible(true);
                    btnEliminarCanal.setManaged(true);
                    btnEliminarCanal.setDisable("GENERAL".equals(id));
                }

            } else {
                // Si es un usuario (chat privado), pedimos el perfil completo
                solicitarPerfilExterno(name);
            }
        });
    }

    @FXML
    public void onEliminarCanalClick(ActionEvent event) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar Canal");
        confirm.setHeaderText("¿Estás seguro de que quieres eliminar el canal '" + destinatarioActual + "'?");
        confirm.setContentText("Esta acción no se puede deshacer.");

        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                mostrarAlerta("Funcionalidad de borrado de canal en desarrollo.");
            }
        });
    }

    @FXML
    public void onEditarPerfilClick(ActionEvent event) {
        profileService.iniciarEdicionPerfil(currentUserProfile, usuariosEnLinea, p -> {
            currentUserProfile = p;
            guardarYRefrescarPerfil();
        });
    }

    private void guardarYRefrescarPerfil() {
        profileManager.updateProfile(currentUserProfile);
        actualizarPanelPerfil();
        sincronizarPerfilConServidor(); 
    }

    @FXML public void buscando(InputMethodEvent e) {}

    // =========================================================================
    // UTILIDADES
    // =========================================================================

    public void enviarAlServidorExterno(DatosMensaje msg) throws IOException {
        chatService.enviarAlServidor(msg);
    }

    private Button crearBotonContacto(String etiqueta, String destino) {
        Button btn = new Button(etiqueta);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("contact-button");
        btn.setOnAction(e -> cambiarDestinatario(destino));
        return btn;
    }

    private void scrollAlFinal() {
        Platform.runLater(() -> { if (scrollChat != null) scrollChat.setVvalue(1.0); });
    }

    /**
     * Re-renderiza el chat actual basándose en el historial guardado.
     * Útil para cuando cambian los permisos de moderación.
     */
    private void refrescarChatActual() {
        if (destinatarioActual != null) {
            cambiarDestinatario(destinatarioActual);
        }
    }

    private String formatearHora(LocalDateTime ts) {
        if (ts == null) return "";
        return String.format("%02d:%02d", ts.getHour(), ts.getMinute());
    }

    private void mostrarAlertaError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    private void mostrarAlerta(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Info"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    private static String nvl(String v, String def) {
        return (v != null && !v.isBlank()) ? v : def;
    }

    // Getters para HiloCliente / externos
    public ChatFolderManager  getFolderManager()      { return folderManager; }
    public ProfileManager     getProfileManager()     { return profileManager; }
    public UserProfile        getCurrentUserProfile() { return currentUserProfile; }
    public String             getNombreUsuario()      { return nombreUsuario; }
    public String             getRolUsuario()         { return rolUsuario; }
}
