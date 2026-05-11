package org.pspro.chattcpmultisala.cliente.controladores;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.pspro.chattcpmultisala.cliente.HiloCliente;
import org.pspro.chattcpmultisala.common.*;

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

    // ── Estado ────────────────────────────────────────────────────────────────
    private boolean     profilePanelVisible = false;
    private ProfileManager     profileManager;
    private ChatFolderManager  folderManager;
    private UserProfile        currentUserProfile;

    private Socket            socket;
    private ObjectOutputStream salida;
    private ObjectInputStream  entrada;
    private String             nombreUsuario;
    private String             rolUsuario = "USER";   // "USER" o "MODERATOR"

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
                                    ObjectInputStream entrada, String nombreUsuario, String rol) {
        this.socket        = socket;
        this.salida        = salida;
        this.entrada       = entrada;
        this.nombreUsuario = nombreUsuario;
        this.rolUsuario    = rol != null ? rol : "USER";

        chatsActivos.add("GENERAL");
        historialesChat.put("GENERAL", new ArrayList<>());

        inicializarGestoresPerfilesYCarpetas();

        HiloCliente hilo = new HiloCliente(socket, entrada, nombreUsuario, this);
        hilo.setDaemon(true);
        hilo.start();

        if (txtBuscador != null)
            txtBuscador.textProperty().addListener((o, v, n) -> dibujarContactosActivos());

        if (chatContainer != null && scrollChat != null)
            chatContainer.heightProperty().addListener((o, v, n) -> scrollChat.setVvalue(1.0));

        dibujarContactosActivos();
    }

    /** Compatibilidad retroactiva (sin rol). */
    public void inicializarConexion(Socket socket, ObjectOutputStream salida,
                                    ObjectInputStream entrada, String nombreUsuario) {
        inicializarConexion(socket, salida, entrada, nombreUsuario, "USER");
    }

    public boolean esModerador() {
        return "MODERATOR".equals(rolUsuario);
    }

    // =========================================================================
    // ENVÍO DE MENSAJES
    // =========================================================================

    @FXML
    public void onEnviarClick(ActionEvent actionEvent) {
        String contenido = txtMensaje.getText().trim();
        if (contenido.isEmpty() || salida == null) return;

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
            DatosMensaje msgLocal = clonarConContenido(mensaje, contenido);
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
            msg.setArchivoId(UUID.randomUUID().toString());

            enviarAlServidorExterno(msg);

            // Mostrar localmente
            registrarArchivoEnUI(msg, true);

        } catch (IOException e) {
            mostrarAlertaError("Error leyendo el archivo: " + e.getMessage());
        }
    }

    // =========================================================================
    // BORRAR MENSAJE PROPIO (PRIVADO)
    // =========================================================================

    private void borrarMensajePropio(String mensajeId, String destino) {
        try {
            DatosMensaje msg = new DatosMensaje();
            msg.setTipo(TipoMensaje.BORRAR_MENSAJE);
            msg.setRemitente(nombreUsuario);
            msg.setDestino(destino);
            msg.setMensajeId(mensajeId);
            enviarAlServidorExterno(msg);

            // Eliminar de la UI local
            eliminarBurbujaLocal(mensajeId, destino);

        } catch (IOException e) {
            e.printStackTrace();
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
        if (p == null) return;
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Perfil de Usuario");
        info.setHeaderText("Información de " + p.getDisplayName() + " (@" + p.getUsername() + ")");
        
        String sb = "📞 Teléfono: " + nvl(p.getPhoneNumber(), "No especificado") + "\n" +
                "✉ Correo: " + nvl(p.getEmail(), "No especificado") + "\n" +
                "📝 Biografía: " + nvl(p.getBio(), "No especificado") + "\n" +
                "🟢 Estado: " + p.getStatusText();
        
        info.setContentText(sb);
        info.showAndWait();
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

    private void dibujarContactosActivos() {
        contactList.getChildren().clear();

        // Botones de acción
        addBotonAccion("➕ Nuevo Chat",    e -> mostrarDialogoNuevoChat());
        addBotonAccion("📢 Nuevo Canal",   e -> mostrarDialogoNuevoCanal());
        addBotonAccion("📎 Enviar archivo", e -> onEnviarArchivoClick(null));

        if (esModerador())
            addBotonAccion("🛡️ Moderación", e -> mostrarMenuModeracion());

        if (canalesActivos.contains(destinatarioActual))
            addBotonAccion("👥 Añadir miembros", e -> mostrarDialogoAñadirMiembros(destinatarioActual));

        Label sep = new Label("CONVERSACIONES");
        sep.setStyle("-fx-text-fill: #999; -fx-padding: 15 15 5 15; -fx-font-size: 11px; -fx-font-weight: bold;");
        contactList.getChildren().add(sep);

        String busqueda = txtBuscador != null && txtBuscador.getText() != null
                ? txtBuscador.getText().toLowerCase().trim() : "";
        String currentFolder = folderManager.getCurrentFolderId();

        for (String chat : chatsActivos) {
            if (!busqueda.isEmpty() && !chat.toLowerCase().contains(busqueda)) continue;

            boolean visible = "all".equals(currentFolder)
                    || ("unread".equals(currentFolder) && chatsConMensajesNuevos.contains(chat))
                    || folderManager.getChatsInFolder(currentFolder).contains(chat);
            if (!visible) continue;

            String etiqueta = switch (chat) {
                case "GENERAL" -> "💬 Sala General";
                default -> canalesActivos.contains(chat) ? "📢 " + chat : "👤 " + chat;
            };
            if (chatsConMensajesNuevos.contains(chat) && !chat.equals(destinatarioActual))
                etiqueta += " 🔔";

            Button btn = crearBotonContacto(etiqueta, chat);
            if (chat.equals(destinatarioActual)) btn.getStyleClass().add("contact-button-active");
            contactList.getChildren().add(btn);
        }
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
            VBox burbuja = new VBox();
            burbuja.getStyleClass().addAll("message-bubble", esPropio ? "bubble-sent" : "bubble-received");

            // Nombre del remitente + rol en canales/general
            if (!esPropio && (salaAsociada.equals("GENERAL") || canalesActivos.contains(salaAsociada))) {
                String etiquetaRemitente = mensaje.getRemitente();
                if ("MODERATOR".equals(mensaje.getRolRemitente())) etiquetaRemitente += " 🛡️";
                Label nombreLbl = new Label(etiquetaRemitente);
                nombreLbl.getStyleClass().add("sender-name");
                
                // Si somos moderadores, permitimos pinchar en el nombre para moderar a ese usuario
                if (esModerador()) {
                    nombreLbl.setStyle(nombreLbl.getStyle() + "; -fx-cursor: hand;");
                    nombreLbl.setOnMouseClicked(e -> mostrarMenuModeracionRapida(mensaje.getRemitente(), salaAsociada));
                }
                
                burbuja.getChildren().add(nombreLbl);
            }

            Label txt  = new Label(mensaje.getContenido());
            txt.getStyleClass().add("message-text");
            txt.setWrapText(true);

            Label hora = new Label(formatearHora(mensaje.getTimestamp()));
            hora.getStyleClass().add("timestamp");

            burbuja.getChildren().addAll(txt, hora);

            // Botón "Borrar" para mensajes propios (privados, canales y general)
            boolean esMensajeBorrable = esPropio && mensaje.getMensajeId() != null;
            
            if (esMensajeBorrable) {
                Button btnBorrar = new Button("✕");
                btnBorrar.setStyle("-fx-background-color:transparent;-fx-text-fill:#cc0000;-fx-cursor:hand;-fx-font-size:10px;");
                final String mid  = mensaje.getMensajeId();
                final String sala = salaAsociada;
                btnBorrar.setOnAction(e -> borrarMensajePropio(mid, sala));
                burbuja.getChildren().add(btnBorrar);
            }

            contenedor.getChildren().add(burbuja);
            if (esPropio) HBox.setMargin(burbuja, new javafx.geometry.Insets(0,0,0,50));
            else          HBox.setMargin(burbuja, new javafx.geometry.Insets(0,50,0,0));
        }

        if (mensaje.getMensajeId() != null) burbujasById.put(mensaje.getMensajeId(), contenedor);

        historialesChat.computeIfAbsent(salaAsociada, k -> new ArrayList<>()).add(contenedor);
        if (destinatarioActual.equals(salaAsociada)) {
            Platform.runLater(() -> { chatContainer.getChildren().add(contenedor); scrollAlFinal(); });
        }
    }

    public void registrarArchivoEnUI(DatosMensaje msg, boolean esPropio) {
        Platform.runLater(() -> {
            HBox contenedor = new HBox();
            contenedor.setAlignment(esPropio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

            VBox burbuja = new VBox();
            burbuja.getStyleClass().addAll("message-bubble", esPropio ? "bubble-sent" : "bubble-received");

            Label titulo = new Label("📎 " + msg.getNombreArchivo());
            titulo.getStyleClass().add("message-text");
            titulo.setStyle("-fx-font-weight:bold;");

            long kb = msg.getTamanoArchivo() / 1024;
            Label info = new Label((kb > 0 ? kb + " KB" : msg.getTamanoArchivo() + " B")
                    + " · " + formatearHora(msg.getTimestamp()));
            info.getStyleClass().add("timestamp");

            // Botón descargar (descifra y guarda)
            Button btnDescargar = new Button("⬇ Descargar");
            btnDescargar.setStyle("-fx-cursor:hand;-fx-background-color:#005f9e;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:3 8;");
            btnDescargar.setOnAction(e -> descargarArchivo(msg));

            // Botón eliminar (solo moderador o emisor en canal)
            if (esModerador() || esPropio) {
                Button btnEliminar = new Button("🗑");
                btnEliminar.setStyle("-fx-cursor:hand;-fx-background-color:transparent;-fx-text-fill:#cc0000;");
                btnEliminar.setOnAction(ev -> eliminarArchivoRemoto(msg));
                burbuja.getChildren().addAll(titulo, info, btnDescargar, btnEliminar);
            } else {
                burbuja.getChildren().addAll(titulo, info, btnDescargar);
            }

            contenedor.getChildren().add(burbuja);
            if (msg.getArchivoId() != null) burbujasById.put(msg.getArchivoId(), contenedor);

            historialesChat.computeIfAbsent(msg.getDestino(), k -> new ArrayList<>()).add(contenedor);
            if (destinatarioActual.equals(msg.getDestino())) {
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
            nombreCanal = r.get().trim();
            if (nombreCanal.isBlank()) { mostrarAlertaError("El nombre no puede estar vacío."); continue; }

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
                DatosMensaje msg = new DatosMensaje();
                msg.setTipo(TipoMensaje.ADD_MIEMBROS_CANAL);
                msg.setRemitente(nombreUsuario); msg.setDestino(canal); msg.setMiembros(sel);
                enviarAlServidorExterno(msg);
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
        d.setTitle("Nuevo Chat"); d.setHeaderText("Selecciona usuario:");
        d.showAndWait().ifPresent(u -> { chatsActivos.add(u); cambiarDestinatario(u); });
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
        UserProfile perfil = profileManager.getProfileByUsername(nombreUsuario);
        if (perfil == null) perfil = profileManager.createProfile(nombreUsuario, nombreUsuario);
        currentUserProfile = perfil;
        profileManager.setCurrentProfile(perfil);
        actualizarPanelPerfil();
        inicializarCarpetas();
    }

    private void actualizarPanelPerfil() {
        if (currentUserProfile == null) return;
        Platform.runLater(() -> {
            if (userAvatar    != null) userAvatar.setText(currentUserProfile.getAvatarInitials());
            if (userName      != null) userName.setText(currentUserProfile.getDisplayName()
                    + (esModerador() ? " 🛡️" : ""));
            if (userStatus    != null) userStatus.setText(currentUserProfile.getStatusText());
            if (profileAvatarBig != null) profileAvatarBig.setText(currentUserProfile.getAvatarInitials());
            if (profileName   != null) profileName.setText(currentUserProfile.getDisplayName());
            if (profileStatusLabel != null) profileStatusLabel.setText(currentUserProfile.getStatusText());
            if (profilePhone  != null) profilePhone.setText(nvl(currentUserProfile.getPhoneNumber(), "No especificado"));
            if (profileEmail  != null) profileEmail.setText(nvl(currentUserProfile.getEmail(), "No especificado"));
            if (profileBio    != null) profileBio.setText(nvl(currentUserProfile.getBio(), "No especificado"));
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
        }
    }

    @FXML
    public void onEditarPerfilClick(ActionEvent event) {
        if (currentUserProfile == null) return;

        List<String> opciones = List.of(
                "Nombre para mostrar",
                "Teléfono",
                "Correo electrónico",
                "Biografía"
        );

        ChoiceDialog<String> menu = new ChoiceDialog<>(opciones.get(0), opciones);
        menu.setTitle("Editar Perfil");
        menu.setHeaderText("¿Qué campo deseas modificar?");
        menu.setContentText("Selecciona una opción:");

        menu.showAndWait().ifPresent(seleccion -> {
            switch (seleccion) {
                case "Nombre para mostrar" -> editarNombrePublico();
                case "Teléfono"           -> editarTelefono();
                case "Correo electrónico" -> editarEmail();
                case "Biografía"          -> editarBio();
            }
        });
    }

    private void editarNombrePublico() {
        TextInputDialog d = new TextInputDialog(currentUserProfile.getDisplayName());
        d.setTitle("Editar Perfil");
        d.setHeaderText("Cambiar nombre para mostrar");
        d.setContentText("Introduce el nuevo nombre público:");
        
        d.showAndWait().ifPresent(nuevo -> {
            String valor = nuevo.trim();
            if (valor.isEmpty()) {
                mostrarAlertaError("El nombre no puede estar vacío.");
                return;
            }

            // Validación mejorada: no puede ser igual al nick de NADIE que conozcamos
            // (los usuarios en línea son una buena aproximación en cliente)
            boolean existe = usuariosEnLinea.stream()
                    .anyMatch(u -> u.equalsIgnoreCase(valor));

            if (existe && !valor.equalsIgnoreCase(nombreUsuario)) {
                mostrarAlertaError("No puedes usar '" + valor + "' porque ya existe un usuario con ese identificador.");
            } else {
                currentUserProfile.setDisplayName(valor);
                guardarYRefrescarPerfil();
            }
        });
    }

    private void editarTelefono() {
        TextInputDialog d = new TextInputDialog(nvl(currentUserProfile.getPhoneNumber(), ""));
        d.setTitle("Editar Perfil"); d.setHeaderText("Cambiar teléfono");
        d.showAndWait().ifPresent(v -> {
            String valor = v.trim();
            if (!valor.isEmpty()) {
                // Verificar si otro perfil ya tiene este teléfono
                boolean duplicado = profileManager.getAllProfiles().stream()
                        .anyMatch(p -> !p.getUsername().equals(nombreUsuario) && valor.equalsIgnoreCase(p.getPhoneNumber()));
                if (duplicado) {
                    mostrarAlertaError("Este número de teléfono ya está registrado por otro usuario.");
                    return;
                }
            }
            currentUserProfile.setPhoneNumber(valor);
            guardarYRefrescarPerfil();
        });
    }

    private void editarEmail() {
        TextInputDialog d = new TextInputDialog(nvl(currentUserProfile.getEmail(), ""));
        d.setTitle("Editar Perfil"); d.setHeaderText("Cambiar correo");
        d.showAndWait().ifPresent(v -> {
            String valor = v.trim();
            if (!valor.isEmpty()) {
                // Verificar si otro perfil ya tiene este correo
                boolean duplicado = profileManager.getAllProfiles().stream()
                        .anyMatch(p -> !p.getUsername().equals(nombreUsuario) && valor.equalsIgnoreCase(p.getEmail()));
                if (duplicado) {
                    mostrarAlertaError("Este correo electrónico ya está registrado por otro usuario.");
                    return;
                }
            }
            currentUserProfile.setEmail(valor);
            guardarYRefrescarPerfil();
        });
    }

    private void editarBio() {
        TextInputDialog d = new TextInputDialog(nvl(currentUserProfile.getBio(), ""));
        d.setTitle("Editar Perfil"); d.setHeaderText("Cambiar biografía");
        d.showAndWait().ifPresent(v -> {
            currentUserProfile.setBio(v.trim());
            guardarYRefrescarPerfil();
        });
    }

    private void guardarYRefrescarPerfil() {
        profileManager.updateProfile(currentUserProfile);
        actualizarPanelPerfil();
    }

    @FXML public void buscando(InputMethodEvent e) {}

    // =========================================================================
    // UTILIDADES
    // =========================================================================

    public void enviarAlServidorExterno(DatosMensaje msg) throws IOException {
        synchronized (salida) { salida.reset(); salida.writeObject(msg); salida.flush(); }
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

    private DatosMensaje clonarConContenido(DatosMensaje original, String contenidoPlano) {
        DatosMensaje c = new DatosMensaje();
        c.setTipo(original.getTipo());
        c.setRemitente(original.getRemitente());
        c.setDestino(original.getDestino());
        c.setContenido(contenidoPlano);
        c.setTimestamp(original.getTimestamp());
        c.setMensajeId(original.getMensajeId());
        c.setRolRemitente(rolUsuario);
        return c;
    }

    // Getters para HiloCliente / externos
    public ChatFolderManager  getFolderManager()      { return folderManager; }
    public ProfileManager     getProfileManager()     { return profileManager; }
    public UserProfile        getCurrentUserProfile() { return currentUserProfile; }
    public String             getNombreUsuario()      { return nombreUsuario; }
    public String             getRolUsuario()         { return rolUsuario; }
}
