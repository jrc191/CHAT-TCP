package org.pspro.chattcpmultisala.servidor;

import org.pspro.chattcpmultisala.common.DatosMensaje;
import org.pspro.chattcpmultisala.common.TipoMensaje;

import java.io.*;
import java.net.Socket;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HiloServidorChat extends Thread {

    // Ruta absoluta (user.dir = directorio de trabajo de la JVM)
    // → getParent() nunca será null, AuthManager no peta
    private static final AuthManager authManager =
            new AuthManager(Paths.get(System.getProperty("user.dir"), "usuarios.properties"));

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private Socket socket;
    private InfoHilos infoh;
    private ObjectInputStream entrada;
    private ObjectOutputStream salida;
    private String nombreUsuario;
    private boolean esRegistrado;
    private boolean loginExitoso = false;

    public HiloServidorChat(Socket s, InfoHilos infoh) {
        this.socket = s;
        this.infoh = infoh;
        try {
            this.salida = new ObjectOutputStream(socket.getOutputStream());
            this.salida.flush();
            this.entrada = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // Rechazar si el servidor está lleno
            if (!procesarLogin()) {
                return;
            }

            loginExitoso = true;

            // Notificar entrada y difundir lista de usuarios
            String entradaMsg = nombreUsuario + " se ha unido al chat.";
            infoh.agregarMensaje("[SISTEMA] " + entradaMsg);
            enviarNotificacionATodos(entradaMsg);
            difundirListaUsuarios();

            while (true) {
                DatosMensaje mensaje = (DatosMensaje) this.entrada.readObject();

                //salida con *****
                if (mensaje.getTipo() == TipoMensaje.MENSAJE_GENERAL
                        && "*****".equals(mensaje.getContenido())) {
                    break;
                }

                switch (mensaje.getTipo()) {
                    case MENSAJE_GENERAL:
                        procesarMensajeGeneral(mensaje);
                        break;
                    case MENSAJE_PRIVADO:
                        procesarMensajePrivado(mensaje);
                        break;
                    case CREAR_CANAL:
                        procesarCrearCanal(mensaje);
                        break;
                    case ADD_MIEMBROS_CANAL:
                        procesarAddMiembrosCanal(mensaje);
                        break;
                    case MENSAJE_CANAL:
                        procesarMensajeCanal(mensaje);
                        break;
                    default:
                        System.out.println("Tipo no soportado: " + mensaje.getTipo());
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Conexión interrumpida con: "
                    + (nombreUsuario != null ? nombreUsuario : socket.getInetAddress()));
        } finally {
            desconectarUsuario();
        }
    }

    // -------------------------------------------------------------------------
    // LOGIN
    // -------------------------------------------------------------------------

    private boolean procesarLogin() {
        try {
            DatosMensaje loginMsg = (DatosMensaje) this.entrada.readObject();
            DatosMensaje respuesta = new DatosMensaje();
            respuesta.setTipo(TipoMensaje.LOGIN_RESPONSE);

            if (infoh.getActuales() > infoh.getMaximo()) {
                respuesta.setSuccess(false);
                respuesta.setReason("Servidor lleno. Inténtalo más tarde.");
                enviarObjeto(salida, respuesta);
                return false;
            }

            if (loginMsg.getTipo() == TipoMensaje.LOGIN_ANON) {
                return procesarLoginAnonimo(loginMsg, respuesta);
            } else {
                respuesta.setSuccess(false);
                respuesta.setReason("Solo se permite acceso anónimo.");
                enviarObjeto(salida, respuesta);
                return false;
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean procesarLoginAnonimo(DatosMensaje loginMsg, DatosMensaje respuesta)
            throws IOException {
        String nombreSolicitado = loginMsg.getRemitente();

        if (nombreSolicitado == null || nombreSolicitado.isBlank()) {
            respuesta.setSuccess(false);
            respuesta.setReason("El nombre no puede estar vacío.");
            enviarObjeto(salida, respuesta);
            return false;
        }

        if (infoh.existeUsuario(nombreSolicitado)) {
            respuesta.setSuccess(false);
            respuesta.setReason("El nombre '" + nombreSolicitado + "' ya está en uso.");
            enviarObjeto(salida, respuesta);
            return false;
        }

        this.nombreUsuario = nombreSolicitado;
        this.esRegistrado = false;

        infoh.agregarUsuario(nombreUsuario,
                new UsuarioConectado(nombreUsuario, socket, salida, false));

        respuesta.setSuccess(true);
        respuesta.setRemitente(nombreUsuario);
        enviarObjeto(salida, respuesta);

        System.out.println("[LOGIN] " + nombreUsuario
                + " | Conectados: " + infoh.getActuales() + "/" + infoh.getMaximo());
        return true;
    }

    // -------------------------------------------------------------------------
    // MENSAJES
    // -------------------------------------------------------------------------

    private void procesarMensajeGeneral(DatosMensaje mensaje) {
        mensaje.setRemitente(this.nombreUsuario);
        mensaje.setDestino("GENERAL");
        if (mensaje.getTimestamp() == null) mensaje.setTimestamp(LocalDateTime.now());

        infoh.agregarMensaje("[" + mensaje.getTimestamp().format(FMT) + "] "
                + nombreUsuario + ": " + mensaje.getContenido());

        for (UsuarioConectado dest : infoh.getUsuariosConectados().values()) {
            if (!dest.getNombreUsuario().equals(this.nombreUsuario)) {
                try {
                    enviarObjeto(dest.getSalida(), mensaje);
                } catch (IOException e) {
                    System.err.println("Error enviando a " + dest.getNombreUsuario());
                }
            }
        }
    }

    private void procesarMensajePrivado(DatosMensaje mensaje) {
        mensaje.setRemitente(this.nombreUsuario);
        if (mensaje.getTimestamp() == null) mensaje.setTimestamp(LocalDateTime.now());

        UsuarioConectado dest = infoh.obtenerUsuario(mensaje.getDestino());

        if (dest == null) {
            DatosMensaje error = new DatosMensaje();
            error.setTipo(TipoMensaje.MENSAJE_GENERAL);
            error.setRemitente("SISTEMA");
            error.setContenido("El usuario '" + mensaje.getDestino() + "' no está conectado.");
            error.setTimestamp(LocalDateTime.now());
            try { enviarObjeto(salida, error); } catch (IOException ignored) {}
            return;
        }

        // Al destinatario
        try { enviarObjeto(dest.getSalida(), mensaje); }
        catch (IOException e) { System.err.println("Error privado → " + dest.getNombreUsuario()); }

        // Copia al emisor (para mostrarlo en su UI)
        try { enviarObjeto(salida, mensaje); }
        catch (IOException ignored) {}
    }

    private void procesarCrearCanal(DatosMensaje mensaje) {
        String nombreCanal = mensaje.getDestino();
        List<String> miembros = mensaje.getMiembros();

        if (nombreCanal == null || nombreCanal.isBlank()) {
            enviarRespuestaError(TipoMensaje.CREAR_CANAL, "El nombre del canal no puede estar vacío.");
            return;
        }

        if (miembros == null || miembros.isEmpty()) {
            enviarRespuestaError(TipoMensaje.CREAR_CANAL, "No se puede crear un canal sin miembros.");
            return;
        }

        if (infoh.existeCanal(nombreCanal)) {
            enviarRespuestaError(TipoMensaje.CREAR_CANAL, "El canal '" + nombreCanal + "' ya existe.");
            return;
        }

        infoh.agregarCanal(nombreCanal, miembros);
        System.out.println("[CANAL] Creado canal: " + nombreCanal + " con miembros: " + miembros);

        // Notificar a todos los miembros que han sido añadidos al canal
        for (String miembro : miembros) {
            notificarUnionCanal(miembro, nombreCanal, miembros, true);
        }
    }

    private void procesarAddMiembrosCanal(DatosMensaje mensaje) {
        String nombreCanal = mensaje.getDestino();
        List<String> nuevosMiembros = mensaje.getMiembros();

        if (!infoh.existeCanal(nombreCanal)) {
            enviarRespuestaError(TipoMensaje.ADD_MIEMBROS_CANAL, "El canal no existe.");
            return;
        }

        List<String> miembrosActuales = infoh.obtenerMiembrosCanal(nombreCanal);
        Set<String> setMiembros = new HashSet<>(miembrosActuales);
        
        for (String m : nuevosMiembros) {
            if (setMiembros.contains(m)) {
                enviarRespuestaError(TipoMensaje.ADD_MIEMBROS_CANAL, "El usuario '" + m + "' ya está en el canal.");
                continue;
            }
            if (setMiembros.add(m)) {
                // Si es nuevo, le notificamos
                notificarUnionCanal(m, nombreCanal, null, false);
            }
        }

        infoh.agregarCanal(nombreCanal, List.copyOf(setMiembros));
        System.out.println("[CANAL] Miembros añadidos a " + nombreCanal + ": " + nuevosMiembros);
    }

    private void notificarUnionCanal(String miembro, String nombreCanal, List<String> todosMiembros, boolean esCreacion) {
        UsuarioConectado uc = infoh.obtenerUsuario(miembro);
        if (uc != null) {
            DatosMensaje notif = new DatosMensaje();
            notif.setTipo(TipoMensaje.CREAR_CANAL);
            notif.setDestino(nombreCanal);
            notif.setRemitente("SISTEMA");
            notif.setContenido(esCreacion ? "Has sido añadido al canal: " + nombreCanal : "Has sido invitado al canal: " + nombreCanal);
            if (todosMiembros != null) notif.setMiembros(todosMiembros);
            notif.setSuccess(true);
            try {
                enviarObjeto(uc.getSalida(), notif);
            } catch (IOException e) {
                System.err.println("Error enviando notif canal a " + miembro);
            }
        }
    }

    private void procesarMensajeCanal(DatosMensaje mensaje) {
        String nombreCanal = mensaje.getDestino();
        List<String> miembros = infoh.obtenerMiembrosCanal(nombreCanal);

        if (miembros == null) {
            enviarRespuestaError(TipoMensaje.MENSAJE_CANAL, "El canal '" + nombreCanal + "' no existe.");
            return;
        }

        if (mensaje.getTimestamp() == null) mensaje.setTimestamp(LocalDateTime.now());

        for (String miembro : miembros) {
            UsuarioConectado uc = infoh.obtenerUsuario(miembro);
            if (uc != null) {
                try {
                    enviarObjeto(uc.getSalida(), mensaje);
                } catch (IOException e) {
                    System.err.println("Error enviando mensaje canal a " + miembro);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // NOTIFICACIONES Y UTILIDADES
    // -------------------------------------------------------------------------

    private void enviarNotificacionATodos(String texto) {
        DatosMensaje notif = new DatosMensaje();
        notif.setTipo(TipoMensaje.MENSAJE_GENERAL);
        notif.setRemitente("SISTEMA");
        notif.setDestino("GENERAL");
        notif.setContenido(texto);
        notif.setTimestamp(LocalDateTime.now());

        for (UsuarioConectado u : infoh.getUsuariosConectados().values()) {
            try { enviarObjeto(u.getSalida(), notif); }
            catch (IOException e) { System.err.println("Error notif → " + u.getNombreUsuario()); }
        }
    }

    private void difundirListaUsuarios() {
        String lista = String.join(",", infoh.getNombresUsuarios());
        DatosMensaje msg = new DatosMensaje();
        msg.setTipo(TipoMensaje.LISTA_USUARIOS);
        msg.setRemitente("SISTEMA");
        msg.setContenido(lista);
        msg.setTimestamp(LocalDateTime.now());

        for (UsuarioConectado u : infoh.getUsuariosConectados().values()) {
            try { enviarObjeto(u.getSalida(), msg); }
            catch (IOException e) { System.err.println("Error lista → " + u.getNombreUsuario()); }
        }
    }

    private void enviarRespuestaError(TipoMensaje tipo, String razon) {
        DatosMensaje msg = new DatosMensaje();
        msg.setTipo(tipo);
        msg.setSuccess(false);
        msg.setReason(razon);
        try { enviarObjeto(salida, msg); } catch (IOException ignored) {}
    }

    /**
     * Para sincronizar objetos: mensajes, users...
     */
    private void enviarObjeto(ObjectOutputStream oos, DatosMensaje msg) throws IOException {
        synchronized (oos) {
            oos.reset();
            oos.writeObject(msg);
            oos.flush();
        }
    }

    // -------------------------------------------------------------------------
    // DESCONEXIÓN
    // -------------------------------------------------------------------------

    private void desconectarUsuario() {
        if (nombreUsuario != null) {
            infoh.eliminarUsuario(nombreUsuario);
        }
        if (loginExitoso) {
            infoh.decrementarActuales();
            String salidaMsg = nombreUsuario + " se ha desconectado.";
            infoh.agregarMensaje("[SISTEMA] " + salidaMsg);
            enviarNotificacionATodos(salidaMsg);
            difundirListaUsuarios();
            System.out.println("[DESCONEXIÓN] " + nombreUsuario
                    + " | Conectados: " + infoh.getActuales() + "/" + infoh.getMaximo());
        } else {
            infoh.decrementarActuales();
        }
        cerrarConexion();
    }

    private void cerrarConexion() {
        try { if (this.entrada != null) this.entrada.close(); } catch (IOException ignored) {}
        try { if (salida != null) salida.close(); }             catch (IOException ignored) {}
        try { if (socket != null) socket.close(); }             catch (IOException ignored) {}
    }
}
