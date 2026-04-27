package org.pspro.chattcpmultisala.servidor;

import org.pspro.chattcpmultisala.common.DatosMensaje;
import org.pspro.chattcpmultisala.common.TipoMensaje;

import java.io.*;
import java.net.Socket;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
            // PASO 1: Rechazar si el servidor está lleno
            if (!procesarLogin()) {
                return;
            }

            loginExitoso = true;

            // PASO 2: Notificar entrada y difundir lista de usuarios
            String entradaMsg = nombreUsuario + " se ha unido al chat.";
            infoh.agregarMensaje("[SISTEMA] " + entradaMsg);
            enviarNotificacionATodos(entradaMsg);
            difundirListaUsuarios();

            // PASO 4: Bucle principal
            while (true) {
                DatosMensaje mensaje = (DatosMensaje) this.entrada.readObject();

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
            // 1. El cliente nos envía su petición de entrada
            DatosMensaje loginMsg = (DatosMensaje) this.entrada.readObject();
            DatosMensaje respuesta = new DatosMensaje();
            respuesta.setTipo(TipoMensaje.LOGIN_RESPONSE);

            // 2. NUEVO: Ahora comprobamos si el servidor está lleno
            if (infoh.getActuales() > infoh.getMaximo()) {
                respuesta.setSuccess(false);
                respuesta.setReason("Servidor lleno. Inténtalo más tarde.");
                enviarObjeto(salida, respuesta);
                return false;
            }

            // 3. Si hay hueco, procedemos normal
            if (loginMsg.getTipo() == TipoMensaje.LOGIN_ANON) {
                return procesarLoginAnonimo(loginMsg, respuesta);
            } else if (loginMsg.getTipo() == TipoMensaje.LOGIN_REGISTER) {
                return procesarLoginRegistrado(loginMsg, respuesta);
            } else {
                respuesta.setSuccess(false);
                respuesta.setReason("Tipo de login no reconocido.");
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

        System.out.println("[LOGIN-ANON] " + nombreUsuario
                + " | Conectados: " + infoh.getActuales() + "/" + infoh.getMaximo());
        return true;
    }

    private boolean procesarLoginRegistrado(DatosMensaje loginMsg, DatosMensaje respuesta)
            throws IOException {
        String usuario = loginMsg.getRemitente();
        String hash = loginMsg.getPassword();

        if (usuario == null || hash == null) {
            respuesta.setSuccess(false);
            respuesta.setReason("Credenciales incompletas.");
            enviarObjeto(salida, respuesta);
            return false;
        }

        if (infoh.existeUsuario(usuario)) {
            respuesta.setSuccess(false);
            respuesta.setReason("El usuario '" + usuario + "' ya está conectado.");
            enviarObjeto(salida, respuesta);
            return false;
        }

        boolean esNuevo = !authManager.exists(usuario);
        if (esNuevo) {
            authManager.register(usuario, hash);
            System.out.println("[REGISTER] Nuevo usuario: " + usuario);
        } else {
            if (!authManager.validate(usuario, hash)) {
                respuesta.setSuccess(false);
                respuesta.setReason("Contraseña incorrecta.");
                enviarObjeto(salida, respuesta);
                return false;
            }
        }

        this.nombreUsuario = usuario;
        this.esRegistrado = true;

        infoh.agregarUsuario(nombreUsuario,
                new UsuarioConectado(nombreUsuario, socket, salida, true));

        respuesta.setSuccess(true);
        respuesta.setRemitente(nombreUsuario);
        respuesta.setReason(esNuevo ? "Cuenta creada." : "Bienvenido de nuevo.");
        enviarObjeto(salida, respuesta);

        System.out.println("[LOGIN-REG] " + nombreUsuario
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
     * Thread-safe: synchronized + reset() previenen corrupción cuando varios
     * hilos comparten el mismo ObjectOutputStream.
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
