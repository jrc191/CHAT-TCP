package org.pspro.chattcpmultisala.cliente;

import javafx.application.Platform;
import org.pspro.chattcpmultisala.cliente.controladores.ChatController;
import org.pspro.chattcpmultisala.common.DatosMensaje;
import org.pspro.chattcpmultisala.common.TipoMensaje;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

/**
 * Hilo receptor del cliente. Escucha mensajes del servidor en bucle.
 *
 * Novedades:
 *  - Maneja ENVIAR_ARCHIVO, ELIMINAR_ARCHIVO, BORRAR_MENSAJE.
 *  - Maneja BANEAR_USUARIO, SUSPENDER_CANAL, PROMOVER_TEMPORAL, REVOCAR_PROMOCION.
 */
public class HiloCliente extends Thread {

    private final Socket        socket;
    private final ObjectInputStream entrada;
    private final String        nombreUsuarioLocal;
    private final ChatController chatController;

    public HiloCliente(Socket socket, ObjectInputStream entrada,
                       String nombreUsuarioLocal, ChatController chatController) {
        this.socket             = socket;
        this.entrada            = entrada;
        this.nombreUsuarioLocal = nombreUsuarioLocal;
        this.chatController     = chatController;
    }

    @Override
    public void run() {
        try {
            while (true) {
                DatosMensaje mensaje = (DatosMensaje) entrada.readObject();

                switch (mensaje.getTipo()) {

                    case LISTA_USUARIOS -> {
                        String contenido = mensaje.getContenido();
                        List<String> usuarios = (contenido == null || contenido.isBlank())
                                ? List.of()
                                : Arrays.asList(contenido.split(","));
                        chatController.actualizarListaUsuarios(usuarios);
                    }

                    case MENSAJE_PRIVADO -> {
                        if (nombreUsuarioLocal.equals(mensaje.getRemitente())) {
                            Platform.runLater(() -> chatController.registrarMensaje(mensaje, mensaje.getDestino(), true));
                        } else {
                            Platform.runLater(() -> chatController.registrarMensaje(mensaje, mensaje.getRemitente(), false));
                        }
                    }

                    case CREAR_CANAL -> {
                        if (mensaje.isSuccess()) {
                            chatController.agregarCanalLocal(mensaje.getDestino(), mensaje.getMiembros());
                            Platform.runLater(() -> chatController.registrarMensaje(mensaje, mensaje.getDestino(), false));
                        } else {
                            Platform.runLater(() -> chatController.registrarMensajeSistema(
                                    "Error en canal: " + mensaje.getReason()));
                        }
                    }

                    case MENSAJE_CANAL -> {
                        boolean esMioCanal = nombreUsuarioLocal.equals(mensaje.getRemitente());
                        Platform.runLater(() -> chatController.registrarMensaje(mensaje, mensaje.getDestino(), esMioCanal));
                    }

                    // ── Archivos ─────────────────────────────────────────────
                    case ENVIAR_ARCHIVO -> {
                        boolean esPropio = nombreUsuarioLocal.equals(mensaje.getRemitente());
                        chatController.registrarArchivoEnUI(mensaje, esPropio);
                    }

                    case ELIMINAR_ARCHIVO -> {
                        String aid = mensaje.getArchivoId();
                        if (aid != null) {
                            Platform.runLater(() -> chatController.eliminarBurbujaLocal(aid, mensaje.getDestino()));
                        }
                    }

                    // ── Borrar mensaje propio ─────────────────────────────────
                    // ── Borrar mensaje propio ─────────────────────────────────
                    case BORRAR_MENSAJE -> {
                        String mid = mensaje.getMensajeId();
                        if (mid != null) {
                            if (mensaje.isSuccess()) {
                                // Borrado global (sustituir por placeholder)
                                Platform.runLater(() -> chatController.sustituirBurbujaPorBorrado(mid, mensaje.getDestino()));
                            } else {
                                // Borrado local (sustituir por placeholder local)
                                Platform.runLater(() -> chatController.sustituirBurbujaPorBorradoLocal(mid, mensaje.getDestino()));
                            }
                        }
                    }

                    // ── Moderación ────────────────────────────────────────────
                    case BANEAR_USUARIO -> Platform.runLater(() -> {
                        chatController.registrarMensajeSistema("[Moderación] " + mensaje.getContenido(), mensaje.getDestino());
                        chatController.removerCanalLocal(mensaje.getDestino());
                    });

                    case NOTIFICACION_SISTEMA -> Platform.runLater(() -> {
                        chatController.registrarMensaje(mensaje, mensaje.getDestino(), false);
                    });

                    case SUSPENDER_CANAL ->
                        Platform.runLater(() -> chatController.registrarMensajeSistema(
                                "[Canal] " + mensaje.getContenido()));

                    case PROMOVER_TEMPORAL -> Platform.runLater(() -> {
                        chatController.registrarMensajeSistema("⭐ " + mensaje.getContenido(), mensaje.getDestino());
                        chatController.agregarPromocionLocal(mensaje.getDestino());
                    });

                    case REVOCAR_PROMOCION -> Platform.runLater(() -> {
                        chatController.registrarMensajeSistema("ℹ️ " + mensaje.getContenido(), mensaje.getDestino());
                        chatController.removerPromocionLocal(mensaje.getDestino());
                    });

                    case PROFILE_RESPONSE -> {
                        // Recibimos respuesta de perfil
                        Platform.runLater(() -> chatController.mostrarPerfilUsuarioExterno(mensaje.getUserProfile()));
                    }

                    case SOLICITUD_CHAT_PRIVADO -> chatController.manejarSolicitudChat(mensaje);
                    case RESPUESTA_CHAT_PRIVADO -> chatController.manejarRespuestaChat(mensaje);
                    case SOLICITUD_UNION_CANAL  -> chatController.manejarSolicitudCanal(mensaje);

                    case CONTEXT_INFO_RESPONSE -> chatController.mostrarInfoContexto(mensaje.getContextData());

                    // ── General (fallback) ────────────────────────────────────
                    default -> {
                        boolean esMio = nombreUsuarioLocal.equals(mensaje.getRemitente());
                        Platform.runLater(() -> chatController.registrarMensaje(mensaje, "GENERAL", esMio));
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            Platform.runLater(() -> {
                chatController.registrarMensajeSistema("--- Conexión perdida con el servidor ---");
                chatController.intentarReconectar();
            });
        }
    }
}
