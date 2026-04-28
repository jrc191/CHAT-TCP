package org.pspro.chattcpmultisala.cliente.controladores;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.pspro.chattcpmultisala.cliente.HiloCliente;
import org.pspro.chattcpmultisala.common.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;

public class ChatController {

    @FXML public VBox contactList;
    @FXML public VBox chatContainer;
    @FXML public TextField txtMensaje;
    @FXML public Button btnEnviar;
    @FXML public Button cerrarAppBtn;
    @FXML public Label lblTituloChat;
    @FXML public TextField txtBuscador;
    @FXML public ScrollPane scrollChat;
    @FXML public AnchorPane profilePanel;
    @FXML public HBox foldersContainer;
    @FXML public Label userAvatar;
    @FXML public Label userName;
    @FXML public Label userStatus;
    @FXML public Label profileAvatarBig;
    @FXML public Label profileName;
    @FXML public Label profileStatusLabel;
    @FXML public Label profilePhone;
    @FXML public Label profileEmail;
    @FXML public Label profileBio;
    
    private boolean profilePanelVisible = false;
    
    // Profile and Folder Management
    private ProfileManager profileManager;
    private ChatFolderManager folderManager;
    private UserProfile currentUserProfile;

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private String nombreUsuario;
    private String destinatarioActual = "GENERAL";
    private Map<String, List<Node>> historialesChat = new HashMap<>();
    private Set<String> chatsActivos = new LinkedHashSet<>();
    private Set<String> canalesActivos = new HashSet<>();
    private Map<String, List<String>> miembrosCanales = new HashMap<>();
    private List<String> usuariosEnLinea = new ArrayList<>();
    private Set<String> chatsConMensajesNuevos = new HashSet<>();

    // -------------------------------------------------------------------------
    // INICIALIZACIÓN
    // -------------------------------------------------------------------------

    public void inicializarConexion(Socket socket, ObjectOutputStream salida,
                                    ObjectInputStream entrada, String nombreUsuario) {
        this.socket = socket;
        this.salida = salida;
        this.entrada = entrada;
        this.nombreUsuario = nombreUsuario;

        chatsActivos.add("GENERAL");
        historialesChat.put("GENERAL", new ArrayList<>());

        // Inicializar gestores de perfiles y carpetas
        inicializarGestoresPerfilesYCarpetas();

        HiloCliente hiloEscucha = new HiloCliente(socket, entrada, nombreUsuario, this);
        hiloEscucha.setDaemon(true);
        hiloEscucha.start();

        if (txtBuscador != null) {
            txtBuscador.textProperty().addListener((observable, oldValue, newValue) -> {
                dibujarContactosActivos();
            });
        }

        if (chatContainer != null && scrollChat != null) {
            chatContainer.heightProperty().addListener((observable, oldValue, newValue) -> {
                scrollChat.setVvalue(1.0);
            });
        }

        dibujarContactosActivos();
    }

    @FXML
    public void onEnviarClick(ActionEvent actionEvent) {
        String contenido = txtMensaje.getText().trim();
        if (contenido.isEmpty() || salida == null) return;

        boolean esGeneral = "GENERAL".equals(destinatarioActual);
        boolean esCanal = canalesActivos.contains(destinatarioActual);

        try {
            DatosMensaje mensaje = new DatosMensaje();
            if (esCanal) {
                mensaje.setTipo(TipoMensaje.MENSAJE_CANAL);
            } else if (esGeneral) {
                mensaje.setTipo(TipoMensaje.MENSAJE_GENERAL);
            } else {
                mensaje.setTipo(TipoMensaje.MENSAJE_PRIVADO);
            }

            mensaje.setRemitente(nombreUsuario);
            mensaje.setDestino(destinatarioActual);
            mensaje.setContenido(contenido);
            mensaje.setTimestamp(LocalDateTime.now());

            synchronized (salida) {
                salida.reset();
                salida.writeObject(mensaje);
                salida.flush();
            }

            if (esGeneral) {
                registrarMensaje(mensaje, "GENERAL", true);
            }

            if ("*****".equals(contenido)) {
                System.exit(0);
            }

            txtMensaje.clear();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void actualizarListaUsuarios(List<String> usuarios) {
        this.usuariosEnLinea = usuarios;
        Platform.runLater(this::dibujarContactosActivos);
    }

    private void dibujarContactosActivos() {
        contactList.getChildren().clear();

        Button btnNuevoChat = new Button("➕ Nuevo Chat");
        btnNuevoChat.setMaxWidth(Double.MAX_VALUE);
        btnNuevoChat.getStyleClass().add("contact-button");
        btnNuevoChat.setStyle("-fx-text-fill: #5085a8; -fx-font-weight: bold;");
        btnNuevoChat.setOnAction(e -> mostrarDialogoNuevoChat());
        contactList.getChildren().add(btnNuevoChat);

        Button btnNuevoCanal = new Button("📢 Nuevo Canal");
        btnNuevoCanal.setMaxWidth(Double.MAX_VALUE);
        btnNuevoCanal.getStyleClass().add("contact-button");
        btnNuevoCanal.setStyle("-fx-text-fill: #5085a8; -fx-font-weight: bold;");
        btnNuevoCanal.setOnAction(e -> mostrarDialogoNuevoCanal());
        contactList.getChildren().add(btnNuevoCanal);

        if (canalesActivos.contains(destinatarioActual)) {
            Button btnAddMiembros = new Button("👥 Añadir Miembros");
            btnAddMiembros.setMaxWidth(Double.MAX_VALUE);
            btnAddMiembros.getStyleClass().add("contact-button");
            btnAddMiembros.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
            btnAddMiembros.setOnAction(e -> mostrarDialogoAñadirMiembros(destinatarioActual));
            contactList.getChildren().add(btnAddMiembros);
        }

        Label separador = new Label("CONVERSACIONES");
        separador.setStyle("-fx-text-fill: #999999; -fx-padding: 15 15 5 15; -fx-font-size: 11px; -fx-font-weight: bold;");
        contactList.getChildren().add(separador);

        String textoBusqueda = (txtBuscador != null && txtBuscador.getText() != null) ? txtBuscador.getText().toLowerCase().trim() : "";
        String currentFolderId = folderManager.getCurrentFolderId();
        List<String> chatsEnCarpeta = folderManager.getChatsInFolder(currentFolderId);

        for (String chat : chatsActivos) {
            // Filtro de búsqueda
            if (!textoBusqueda.isEmpty() && !chat.toLowerCase().contains(textoBusqueda)) continue;

            // Filtro de carpeta
            boolean visible = false;
            if ("all".equals(currentFolderId)) {
                visible = true;
            } else if ("unread".equals(currentFolderId)) {
                visible = chatsConMensajesNuevos.contains(chat);
            } else {
                visible = chatsEnCarpeta.contains(chat);
            }

            if (!visible) continue;

            String etiqueta;
            if (chat.equals("GENERAL")) etiqueta = "💬 Sala General";
            else if (canalesActivos.contains(chat)) etiqueta = "📢 " + chat;
            else etiqueta = "👤 " + chat;
            
            // Si tiene mensajes nuevos y no es el actual, añadir un indicador visual
            if (chatsConMensajesNuevos.contains(chat) && !chat.equals(destinatarioActual)) {
                etiqueta += " 🔔";
            }

            Button btn = crearBotonContacto(etiqueta, chat);
            if (chat.equals(destinatarioActual)) {
                btn.getStyleClass().add("contact-button-active");
            }
            contactList.getChildren().add(btn);
        }
    }

    private void mostrarDialogoNuevoCanal() {
        String nombreCanal = "";
        while (true) {
            TextInputDialog nameDialog = new TextInputDialog(nombreCanal);
            nameDialog.setTitle("Nuevo Canal");
            nameDialog.setHeaderText("Crea un nuevo canal");
            nameDialog.setContentText("Nombre del canal:");

            Optional<String> result = nameDialog.showAndWait();
            if (result.isEmpty()) return;

            nombreCanal = result.get().trim();
            if (nombreCanal.isBlank()) {
                mostrarAlertaError("El nombre del canal no puede estar vacío.");
                continue;
            }

            ListView<String> listView = new ListView<>();
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listView.getItems().addAll(usuariosEnLinea.stream().filter(u -> !u.equals(nombreUsuario)).toList());

            Dialog<List<String>> dialog = new Dialog<>();
            dialog.setTitle("Seleccionar Miembros");
            dialog.setHeaderText("Selecciona los miembros para el canal: " + nombreCanal);
            ButtonType okButtonType = new ButtonType("Crear", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);
            dialog.getDialogPane().setContent(listView);
            dialog.setResultConverter(db -> db == okButtonType ? new ArrayList<>(listView.getSelectionModel().getSelectedItems()) : null);

            Optional<List<String>> miembrosResult = dialog.showAndWait();
            if (miembrosResult.isEmpty()) continue;

            List<String> miembros = miembrosResult.get();
            if (miembros.isEmpty()) {
                mostrarAlertaError("Debes seleccionar al menos un usuario para crear un canal.");
                continue;
            }

            if (!miembros.contains(nombreUsuario)) miembros.add(nombreUsuario);

            try {
                DatosMensaje msg = new DatosMensaje();
                msg.setTipo(TipoMensaje.CREAR_CANAL);
                msg.setRemitente(nombreUsuario);
                msg.setDestino(nombreCanal);
                msg.setMiembros(miembros);
                synchronized (salida) { salida.reset(); salida.writeObject(msg); salida.flush(); }
                return;
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    public void agregarCanalLocal(String nombreCanal, List<String> miembros) {
        canalesActivos.add(nombreCanal);
        chatsActivos.add(nombreCanal);
        if (miembros != null) miembrosCanales.put(nombreCanal, new ArrayList<>(miembros));
        Platform.runLater(this::dibujarContactosActivos);
    }

    public void actualizarMiembrosCanal(String nombreCanal, List<String> nuevosMiembros) {
        List<String> actuales = miembrosCanales.computeIfAbsent(nombreCanal, k -> new ArrayList<>());
        for (String m : nuevosMiembros) {
            if (!actuales.contains(m)) actuales.add(m);
        }
    }

    private void mostrarDialogoAñadirMiembros(String nombreCanal) {
        List<String> actuales = miembrosCanales.getOrDefault(nombreCanal, new ArrayList<>());
        while (true) {
            ListView<String> listView = new ListView<>();
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            
            List<String> candidatos = usuariosEnLinea.stream().filter(u -> !actuales.contains(u)).toList();

            if (candidatos.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Añadir Miembros");
                alert.setHeaderText(null);
                alert.setContentText("Todos los usuarios conectados ya están en este canal.");
                alert.showAndWait();
                return;
            }

            listView.getItems().addAll(candidatos);

            Dialog<List<String>> dialog = new Dialog<>();
            dialog.setTitle("Añadir Miembros");
            dialog.setHeaderText("Añadir nuevos miembros al canal: " + nombreCanal);
            ButtonType okButtonType = new ButtonType("Añadir", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);
            dialog.getDialogPane().setContent(listView);
            dialog.setResultConverter(db -> db == okButtonType ? new ArrayList<>(listView.getSelectionModel().getSelectedItems()) : null);

            Optional<List<String>> result = dialog.showAndWait();
            if (result.isEmpty()) return;

            List<String> seleccionados = result.get();
            if (seleccionados.isEmpty()) {
                mostrarAlertaError("Debes seleccionar al menos un usuario.");
                continue;
            }

            try {
                DatosMensaje msg = new DatosMensaje();
                msg.setTipo(TipoMensaje.ADD_MIEMBROS_CANAL);
                msg.setRemitente(nombreUsuario);
                msg.setDestino(nombreCanal);
                msg.setMiembros(seleccionados);
                synchronized (salida) { salida.reset(); salida.writeObject(msg); salida.flush(); }
                return;
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private void mostrarAlertaError(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private Button crearBotonContacto(String etiqueta, String destino) {
        Button btn = new Button(etiqueta);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("contact-button");
        btn.setOnAction(e -> cambiarDestinatario(destino));
        return btn;
    }

    private void mostrarDialogoNuevoChat() {
        List<String> opciones = usuariosEnLinea.stream().filter(u -> !u.equals(nombreUsuario) && !chatsActivos.contains(u)).toList();
        if (opciones.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Nuevo Chat");
            alert.setHeaderText(null);
            alert.setContentText("No hay usuarios nuevos disponibles en este momento.");
            alert.showAndWait();
            return;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(opciones.get(0), opciones);
        dialog.setTitle("Nuevo Chat Privado");
        dialog.setHeaderText("Inicia una nueva conversación");
        dialog.setContentText("Selecciona un usuario:");
        dialog.showAndWait().ifPresent(seleccionado -> {
            chatsActivos.add(seleccionado);
            cambiarDestinatario(seleccionado);
        });
    }

    private void cambiarDestinatario(String destino) {
        destinatarioActual = destino;
        
        // Al entrar en un chat, quitamos la marca de "nuevo mensaje"
        if (chatsConMensajesNuevos.remove(destino)) {
            actualizarUIUnread();
        }

        dibujarContactosActivos();
        if (lblTituloChat != null) {
            if ("GENERAL".equals(destino)) lblTituloChat.setText("Sala de Chat General");
            else if (canalesActivos.contains(destino)) lblTituloChat.setText("Canal: " + destino);
            else lblTituloChat.setText("Chat privado con: " + destino);
        }
        chatContainer.getChildren().clear();
        List<Node> historial = historialesChat.getOrDefault(destino, new ArrayList<>());
        chatContainer.getChildren().addAll(historial);
        scrollAlFinal();
    }

    private void actualizarUIUnread() {
        folderManager.updateFolderUnreadCount("unread", chatsConMensajesNuevos.size());
        inicializarCarpetas();
        // Redibujamos la lista de contactos para que se apliquen los filtros de carpeta (como el de unread)
        dibujarContactosActivos();
    }

    public void registrarMensaje(DatosMensaje mensaje, String salaAsociada, boolean esPropio) {
        if (!salaAsociada.equals("GENERAL") && !chatsActivos.contains(salaAsociada)) {
            if (mensaje.getTipo() == TipoMensaje.MENSAJE_CANAL) canalesActivos.add(salaAsociada);
            chatsActivos.add(salaAsociada);
            // Sincronizar con el gestor de carpetas
            folderManager.addChatToFolder(salaAsociada, "all");
            Platform.runLater(this::dibujarContactosActivos);
        }

        // Si el mensaje no es propio y no estamos en ese chat, marcar como no leído
        if (!esPropio && !destinatarioActual.equals(salaAsociada)) {
            chatsConMensajesNuevos.add(salaAsociada);
            Platform.runLater(this::actualizarUIUnread);
        }

        HBox contenedorMensaje = new HBox();
        boolean esSistema = "SISTEMA".equals(mensaje.getRemitente());
        if (esSistema) {
            contenedorMensaje.setAlignment(Pos.CENTER);
            contenedorMensaje.setPadding(new javafx.geometry.Insets(5, 0, 5, 0));
            Label texto = new Label(mensaje.getContenido());
            texto.setStyle("-fx-font-size: 12px; -fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.2); -fx-background-radius: 12; -fx-padding: 4 12 4 12;");
            contenedorMensaje.getChildren().add(texto);
        } else {
            contenedorMensaje.setAlignment(esPropio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            VBox burbuja = new VBox();
            burbuja.getStyleClass().addAll("message-bubble", esPropio ? "bubble-sent" : "bubble-received");
            
            if (!esPropio && (salaAsociada.equals("GENERAL") || canalesActivos.contains(salaAsociada))) {
                Label nombreRemitente = new Label(mensaje.getRemitente());
                nombreRemitente.getStyleClass().add("sender-name");
                burbuja.getChildren().add(nombreRemitente);
            }
            
            Label texto = new Label(mensaje.getContenido());
            texto.getStyleClass().add("message-text");
            texto.setWrapText(true);
            
            Label hora = new Label(formatearHora(mensaje.getTimestamp()));
            hora.getStyleClass().add("timestamp");
            
            burbuja.getChildren().addAll(texto, hora);
            contenedorMensaje.getChildren().add(burbuja);
            
            // Margenes
            if (esPropio) {
                HBox.setMargin(burbuja, new javafx.geometry.Insets(0, 0, 0, 50));
            } else {
                HBox.setMargin(burbuja, new javafx.geometry.Insets(0, 50, 0, 0));
            }
        }
        historialesChat.computeIfAbsent(salaAsociada, k -> new ArrayList<>()).add(contenedorMensaje);
        if (destinatarioActual.equals(salaAsociada)) {
            Platform.runLater(() -> {
                chatContainer.getChildren().add(contenedorMensaje);
                scrollAlFinal();
            });
        }
    }

    public void registrarMensajeSistema(String texto) {
        DatosMensaje msg = new DatosMensaje();
        msg.setRemitente("SISTEMA");
        msg.setContenido(texto);
        registrarMensaje(msg, destinatarioActual, false);
    }

    private void scrollAlFinal() {
        Platform.runLater(() -> { if (scrollChat != null) scrollChat.setVvalue(1.0); });
    }

    private String formatearHora(LocalDateTime timestamp) {
        if (timestamp == null) return "";
        return String.format("%02d:%02d", timestamp.getHour(), timestamp.getMinute());
    }

    @FXML
    public void onCerrarClick(ActionEvent actionEvent) {
        try {
            if (salida != null) {
                DatosMensaje despedida = new DatosMensaje();
                despedida.setTipo(TipoMensaje.MENSAJE_GENERAL);
                despedida.setRemitente(nombreUsuario);
                despedida.setContenido("*****");
                synchronized (salida) { salida.reset(); salida.writeObject(despedida); salida.flush(); }
            }
        } catch (IOException e) { e.printStackTrace(); }
        System.exit(0);
    }

    @FXML
    public void buscando(InputMethodEvent inputMethodEvent) { }

    @FXML
    public void onToggleProfilePanel(ActionEvent event) {
        if (profilePanel != null) {
            profilePanelVisible = !profilePanelVisible;
            profilePanel.setVisible(profilePanelVisible);
            profilePanel.setManaged(profilePanelVisible);
        }
    }


    // ====== PROFILE MANAGEMENT ======

    /**
     * Inicializa los gestores de perfil y carpetas
     */
    public void inicializarGestoresPerfilesYCarpetas() {
        this.profileManager = new ProfileManager();
        this.folderManager = new ChatFolderManager();
        
        // Crear o cargar perfil del usuario actual
        UserProfile perfil = profileManager.getProfileByUsername(nombreUsuario);
        if (perfil == null) {
            perfil = profileManager.createProfile(nombreUsuario, nombreUsuario);
        }
        
        this.currentUserProfile = perfil;
        profileManager.setCurrentProfile(perfil);
        
        actualizarPanelPerfil();
        inicializarCarpetas();
    }

    /**
     * Actualiza la información del perfil en el panel
     */
    private void actualizarPanelPerfil() {
        if (currentUserProfile == null) return;
        
        Platform.runLater(() -> {
            String iniciales = currentUserProfile.getAvatarInitials();
            
            // Actualizar encabezado del sidebar
            if (userAvatar != null) userAvatar.setText(iniciales);
            if (userName != null) userName.setText(currentUserProfile.getDisplayName());
            if (userStatus != null) userStatus.setText(currentUserProfile.getStatusText());
            
            // Actualizar panel de perfil
            if (profileAvatarBig != null) profileAvatarBig.setText(iniciales);
            if (profileName != null) profileName.setText(currentUserProfile.getDisplayName());
            if (profileStatusLabel != null) profileStatusLabel.setText(currentUserProfile.getStatusText());
            if (profilePhone != null) profilePhone.setText(currentUserProfile.getPhoneNumber() != null ? currentUserProfile.getPhoneNumber() : "No especificado");
            if (profileEmail != null) profileEmail.setText(currentUserProfile.getEmail() != null ? currentUserProfile.getEmail() : "No especificado");
            if (profileBio != null) profileBio.setText(currentUserProfile.getBio() != null ? currentUserProfile.getBio() : "Sin biografía");
        });
    }

    /**
     * Actualiza el estado del usuario
     */
    public void actualizarEstadoUsuario(String nuevoEstado) {
        if (currentUserProfile != null) {
            currentUserProfile.setStatus(nuevoEstado);
            profileManager.updateProfile(currentUserProfile);
            actualizarPanelPerfil();
        }
    }

    /**
     * Actualiza la información del perfil actual
     */
    public void actualizarInformacionPerfil(String displayName, String bio, String phone, String email) {
        if (currentUserProfile != null) {
            currentUserProfile.setDisplayName(displayName);
            currentUserProfile.setBio(bio);
            currentUserProfile.setPhoneNumber(phone);
            currentUserProfile.setEmail(email);
            profileManager.updateProfile(currentUserProfile);
            actualizarPanelPerfil();
        }
    }

    // ====== FOLDER MANAGEMENT ======

    /**
     * Inicializa las carpetas en la UI
     */
    private void inicializarCarpetas() {
        if (foldersContainer == null) return;
        
        Platform.runLater(() -> {
            foldersContainer.getChildren().clear();
            List<ChatFolder> carpetas = folderManager.getAllFolders();
            
            for (ChatFolder carpeta : carpetas) {
                Button btnCarpeta = crearBotonCarpeta(carpeta);
                foldersContainer.getChildren().add(btnCarpeta);
            }
        });
    }

    /**
     * Crea un botón para una carpeta
     */
    private Button crearBotonCarpeta(ChatFolder carpeta) {
        Button btn = new Button();
        btn.setText(carpeta.getIcon() + " " + carpeta.getName());
        btn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: #5085a8; " +
            "-fx-border-color: transparent; " +
            "-fx-padding: 6 12 6 12; " +
            "-fx-font-size: 12; " +
            "-fx-cursor: hand;"
        );
        
        int unreadCount = carpeta.getUnreadCount();
        if (unreadCount > 0) {
            btn.setText(btn.getText() + " (" + unreadCount + ")");
        }
        
        btn.setOnAction(e -> seleccionarCarpeta(carpeta.getId()));
        
        // Resaltar carpeta actual
        if (carpeta.getId().equals(folderManager.getCurrentFolderId())) {
            btn.setStyle(btn.getStyle() + "-fx-border-color: #5085a8; -fx-border-width: 0 0 2 0;");
        }
        
        return btn;
    }

    /**
     * Selecciona una carpeta y filtra los chats
     */
    public void seleccionarCarpeta(String folderId) {
        folderManager.setCurrentFolder(folderId);
        ChatFolder carpetaSeleccionada = folderManager.getCurrentFolder();
        
        if (carpetaSeleccionada != null) {
            // Dibujar la lista de contactos aplicando el nuevo filtro de carpeta
            dibujarContactosActivos();
            inicializarCarpetas(); // Redibujar carpetas para mostrar selección
        }
    }

    /**
     * Agrega un chat a una carpeta
     */
    public void agregarChatACarpeta(String chatId, String folderId) {
        folderManager.addChatToFolder(chatId, folderId);
    }

    /**
     * Mueve un chat de una carpeta a otra
     */
    public void moverChatACarpeta(String chatId, String fromFolderId, String toFolderId) {
        folderManager.moveChatToFolder(chatId, fromFolderId, toFolderId);
        dibujarContactosActivos();
    }

    /**
     * Obtiene el gestor de carpetas
     */
    public ChatFolderManager getFolderManager() {
        return folderManager;
    }

    /**
     * Obtiene el gestor de perfiles
     */
    public ProfileManager getProfileManager() {
        return profileManager;
    }

    /**
     * Obtiene el perfil actual del usuario
     */
    public UserProfile getCurrentUserProfile() {
        return currentUserProfile;
    }
}
