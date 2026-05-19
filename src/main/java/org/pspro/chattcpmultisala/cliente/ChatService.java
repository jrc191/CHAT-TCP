package org.pspro.chattcpmultisala.cliente;

import org.pspro.chattcpmultisala.common.DatosMensaje;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * Servicio centralizado para la lógica de negocio y comunicación del chat.
 * Implementa la separación de responsabilidades y el desacoplamiento.
 */
public class ChatService {
    
    private final ObjectOutputStream salida;

    public ChatService(ObjectOutputStream salida) {
        this.salida = salida;
    }

    /**
     * Envía un mensaje al servidor de forma sincronizada y segura.
     */
    public void enviarAlServidor(DatosMensaje msg) throws IOException {
        if (salida == null) return;
        synchronized (salida) {
            salida.reset();
            salida.writeObject(msg);
            salida.flush();
        }
    }

    /**
     * Lógica para clonar un mensaje con nuevo contenido.
     */
    public DatosMensaje clonarConContenido(DatosMensaje original, String nuevoContenido) {
        DatosMensaje clon = new DatosMensaje();
        clon.setTipo(original.getTipo());
        clon.setRemitente(original.getRemitente());
        clon.setDestino(original.getDestino());
        clon.setContenido(nuevoContenido);
        clon.setTimestamp(original.getTimestamp());
        clon.setMensajeId(original.getMensajeId());
        clon.setCifrado(false);
        return clon;
    }
}
