package org.pspro.chattcpmultisala.cliente;

import javafx.application.Platform;
import org.pspro.chattcpmultisala.cliente.controladores.ChatController;
import org.pspro.chattcpmultisala.common.DatosMensaje;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class HiloCliente extends Thread {

    private final Socket socket;
    private final ObjectInputStream entrada;
    private final String nombreUsuarioLocal;
    private final ChatController chatController;

    // Se ha quitado el chatContainer de aquí, el Controller gestionará toda la vista
    public HiloCliente(Socket socket, ObjectInputStream entrada,
                       String nombreUsuarioLocal, ChatController chatController) {
        this.socket = socket;
        this.entrada = entrada;
        this.nombreUsuarioLocal = nombreUsuarioLocal;
        this.chatController = chatController;
    }

    @Override
    public void run() {
        try {
            while (true) {
                DatosMensaje mensaje = (DatosMensaje) entrada.readObject();

                switch (mensaje.getTipo()) {
                    case LISTA_USUARIOS:
                        String contenido = mensaje.getContenido();
                        List<String> usuarios = contenido == null || contenido.isBlank()
                                ? List.of()
                                : Arrays.asList(contenido.split(","));
                        chatController.actualizarListaUsuarios(usuarios);
                        break;

                    case MENSAJE_PRIVADO:
                        // Si yo lo envié (el servidor me devuelve la copia), pertenece a la sala del "Destino"
                        if (nombreUsuarioLocal.equals(mensaje.getRemitente())) {
                            Platform.runLater(() -> chatController.registrarMensaje(mensaje, mensaje.getDestino(), true));
                        } else {
                            // Si lo recibo, pertenece a la sala del "Remitente"
                            Platform.runLater(() -> chatController.registrarMensaje(mensaje, mensaje.getRemitente(), false));
                        }
                        break;

                    case MENSAJE_GENERAL:
                    default:
                        boolean esMio = nombreUsuarioLocal.equals(mensaje.getRemitente());
                        Platform.runLater(() -> chatController.registrarMensaje(mensaje, "GENERAL", esMio));
                        break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            Platform.runLater(() -> chatController.registrarMensajeSistema("--- Conexión perdida con el servidor ---"));
        }
    }
}