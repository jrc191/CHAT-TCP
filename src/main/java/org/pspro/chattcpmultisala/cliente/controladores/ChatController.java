package org.pspro.chattcpmultisala.cliente.controladores;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.pspro.chattcpmultisala.cliente.HiloCliente;
import org.pspro.chattcpmultisala.common.DatosMensaje;
import org.pspro.chattcpmultisala.common.TipoMensaje;

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

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private String nombreUsuario;
    private String destinatarioActual = "GENERAL";
    private Map<String, List<Node>> historialesChat = new HashMap<>();
    private Set<String> chatsActivos = new LinkedHashSet<>();
    private List<String> usuariosEnLinea = new ArrayList<>();

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

        HiloCliente hiloEscucha = new HiloCliente(socket, entrada, nombreUsuario, this);
        hiloEscucha.setDaemon(true);
        hiloEscucha.start();

        // Listener para el buscador en tiempo real
        if (txtBuscador != null) {
            txtBuscador.textProperty().addListener((observable, oldValue, newValue) -> {
                dibujarContactosActivos();
            });
        }

        // --- Autoscroll---
        if (chatContainer != null && scrollChat != null) {
            chatContainer.heightProperty().addListener((observable, oldValue, newValue) -> {
                scrollChat.setVvalue(1.0);
            });
        }

        dibujarContactosActivos();
    }

    // -------------------------------------------------------------------------
    // ENVÍO DE MENSAJES
    // -------------------------------------------------------------------------

    @FXML
    public void onEnviarClick(ActionEvent actionEvent) {
        String contenido = txtMensaje.getText().trim();
        if (contenido.isEmpty() || salida == null) return;

        boolean esPrivado = !"GENERAL".equals(destinatarioActual);

        try {
            DatosMensaje mensaje = new DatosMensaje();
            mensaje.setTipo(esPrivado ? TipoMensaje.MENSAJE_PRIVADO : TipoMensaje.MENSAJE_GENERAL);
            mensaje.setRemitente(nombreUsuario);
            mensaje.setDestino(destinatarioActual);
            mensaje.setContenido(contenido);
            mensaje.setTimestamp(LocalDateTime.now());

            synchronized (salida) {
                salida.reset();
                salida.writeObject(mensaje);
                salida.flush();
            }

            // Los generales no rebotan al emisor, así que lo dibujamos localmente
            // (Los privados SÍ rebotan desde el servidor, así que no los duplicamos aquí)
            if (!esPrivado) {
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

    // -------------------------------------------------------------------------
    // GESTIÓN DE LA LISTA LATERAL Y NUEVOS CHATS
    // -------------------------------------------------------------------------

    public void actualizarListaUsuarios(List<String> usuarios) {
        this.usuariosEnLinea = usuarios;
        // Solo actualizamos visualmente para refrescar si alguien se desconectó
        Platform.runLater(this::dibujarContactosActivos);
    }

    private void dibujarContactosActivos() {
        contactList.getChildren().clear();

        // 1. Botón de Nuevo Chat (estilo WhatsApp)
        Button btnNuevoChat = new Button("➕ Nuevo Chat");
        btnNuevoChat.setMaxWidth(Double.MAX_VALUE);
        btnNuevoChat.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; "
                + "-fx-font-weight: bold; -fx-padding: 10; -fx-cursor: hand;");
        btnNuevoChat.setOnAction(e -> mostrarDialogoNuevoChat());
        contactList.getChildren().add(btnNuevoChat);

        // 2. Separador visual
        Label separador = new Label(" Conversaciones");
        separador.setStyle("-fx-text-fill: #bdc3c7; -fx-padding: 10 0 5 0; -fx-font-size: 11px;");
        contactList.getChildren().add(separador);

        // --- NUEVO: Obtenemos el texto del buscador (en minúsculas para evitar problemas) ---
        String textoBusqueda = "";
        if (txtBuscador != null && txtBuscador.getText() != null) {
            textoBusqueda = txtBuscador.getText().toLowerCase().trim();
        }

        // 3. Listar solo los chats activos que coincidan con la búsqueda
        for (String chat : chatsActivos) {

            // --- NUEVO: Lógica de filtrado ---
            // Si el buscador tiene texto y el nombre del chat NO contiene ese texto, lo saltamos
            if (!textoBusqueda.isEmpty() && !chat.toLowerCase().contains(textoBusqueda)) {
                continue;
            }

            String etiqueta = chat.equals("GENERAL") ? "💬 General" : "👤 " + chat;
            Button btn = crearBotonContacto(etiqueta, chat);

            // Resaltar el chat actual
            if (chat.equals(destinatarioActual)) {
                btn.setStyle(btn.getStyle() + "-fx-background-color: #34495e; -fx-font-weight: bold;");
            }
            contactList.getChildren().add(btn);
        }
    }

    private Button crearBotonContacto(String etiqueta, String destino) {
        Button btn = new Button(etiqueta);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; "
                + "-fx-alignment: CENTER_LEFT; -fx-padding: 10; -fx-font-size: 14px; -fx-cursor: hand;");
        
        btn.setOnAction(e -> cambiarDestinatario(destino));
        return btn;
    }

    private void mostrarDialogoNuevoChat() {
        // Filtrar gente conectada que no seas tú y con la que no tengas chat ya
        List<String> opciones = usuariosEnLinea.stream()
                .filter(u -> !u.equals(nombreUsuario) && !chatsActivos.contains(u))
                .toList();

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
        dibujarContactosActivos();

        if (lblTituloChat != null) {
            if ("GENERAL".equals(destino)) {
                lblTituloChat.setText("Sala de Chat General");
            } else {
                lblTituloChat.setText("Chat privado con: " + destino);
            }
        }

        // Limpiar pantalla y cargar el historial correspondiente
        chatContainer.getChildren().clear();
        List<Node> historial = historialesChat.getOrDefault(destino, new ArrayList<>());
        chatContainer.getChildren().addAll(historial);
        scrollAlFinal();
    }

    // -------------------------------------------------------------------------
    // RENDERIZADO DE MENSAJES UNIFICADO
    // -------------------------------------------------------------------------

    public void registrarMensaje(DatosMensaje mensaje, String salaAsociada, boolean esPropio) {
        // Si alguien nos abre privado de repente, lo añadimos a activos
        if (!salaAsociada.equals("GENERAL") && !chatsActivos.contains(salaAsociada)) {
            chatsActivos.add(salaAsociada);
            Platform.runLater(this::dibujarContactosActivos);
        }

        HBox contenedorMensaje = new HBox();
        boolean esSistema = "SISTEMA".equals(mensaje.getRemitente());

        if (esSistema) {
            contenedorMensaje.setAlignment(Pos.CENTER);
            Label texto = new Label(mensaje.getContenido());
            texto.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d; -fx-font-style: italic;");
            contenedorMensaje.getChildren().add(texto);
            
        } else {
            contenedorMensaje.setAlignment(esPropio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            
            VBox burbuja = new VBox();
            burbuja.getStyleClass().addAll("message-bubble", esPropio ? "bubble-sent" : "bubble-received");

            // Solo mostramos quién lo envió si NO es mío y estamos en el GENERAL
            // (En privados ya sabes con quién hablas)
            if (!esPropio && salaAsociada.equals("GENERAL")) {
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
        }

        // 1. Guardar en el historial de la sala en memoria
        historialesChat.computeIfAbsent(salaAsociada, k -> new ArrayList<>()).add(contenedorMensaje);

        // 2. Si estamos viendo esa sala actualmente, lo inyectamos visualmente
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
        // Lo mandamos al chat actual para informar de la desconexión
        registrarMensaje(msg, destinatarioActual, false);
    }

    private void scrollAlFinal() {
        Platform.runLater(() -> {
            if (scrollChat != null) {
                scrollChat.setVvalue(1.0);
            }
        });
    }

    private String formatearHora(LocalDateTime timestamp) {
        if (timestamp == null) return "";
        return String.format("%02d:%02d", timestamp.getHour(), timestamp.getMinute());
    }

    // -------------------------------------------------------------------------
    // CERRAR APP
    // -------------------------------------------------------------------------

    @FXML
    public void onCerrarClick(ActionEvent actionEvent) {
        try {
            if (salida != null) {
                DatosMensaje despedida = new DatosMensaje();
                despedida.setTipo(TipoMensaje.MENSAJE_GENERAL);
                despedida.setRemitente(nombreUsuario);
                despedida.setContenido("*****");
                synchronized (salida) {
                    salida.reset();
                    salida.writeObject(despedida);
                    salida.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    @FXML
    public void buscando(InputMethodEvent inputMethodEvent) {
        // TODO: filtrar lista de contactos
    }
}