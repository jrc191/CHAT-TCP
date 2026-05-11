package org.pspro.chattcpmultisala.servidor;

import org.pspro.chattcpmultisala.common.CifradoMensajes;
import org.pspro.chattcpmultisala.common.DatosMensaje;
import org.pspro.chattcpmultisala.common.TipoMensaje;
import org.pspro.chattcpmultisala.common.ValidadorEntrada;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class HiloServidorChat extends Thread {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    /** Máximo de intentos de login fallidos antes de bloquear la IP (protección DoS/fuerza bruta). */
    private static final int MAX_INTENTOS_LOGIN = 5;

    private final Socket socket;
    private final InfoHilos infoh;
    private final GestorUsuarios gestorUsuarios;

    private ObjectInputStream  entrada;
    private ObjectOutputStream salida;
    private String nombreUsuario;
    private GestorUsuarios.Rol rolUsuario;
    private boolean loginExitoso = false;

    // ── Promociones temporales: "usuario:canal" → instante de expiración ──────
    private static final Map<String, Long> promocionesTempo = new ConcurrentHashMap<>();

    public HiloServidorChat(Socket s, InfoHilos infoh, GestorUsuarios gestorUsuarios) {
        this.socket         = s;
        this.infoh          = infoh;
        this.gestorUsuarios = gestorUsuarios;
        try {
            this.salida = new ObjectOutputStream(socket.getOutputStream());
            this.salida.flush();
            this.entrada = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // run
    // =========================================================================

    @Override
    public void run() {
        try {
            // Fase de autenticación (Login o Registro)
            if (!faseAutenticacion()) return;

            loginExitoso = true;
            String entradaMsg = nombreUsuario + " (" + rolUsuario.name() + ") se ha unido al chat.";
            infoh.agregarMensaje("[SISTEMA] " + entradaMsg);
            enviarNotificacionATodos(entradaMsg);
            difundirListaUsuarios();

            while (true) {
                DatosMensaje mensaje = (DatosMensaje) entrada.readObject();

                // Salida con *****
                if (mensaje.getTipo() == TipoMensaje.MENSAJE_GENERAL
                        && "*****".equals(mensaje.getContenido())) break;

                // Descifrar contenido si viene cifrado
                if (mensaje.isCifrado() && mensaje.getContenido() != null) {
                    String descifrado = CifradoMensajes.descifrar(mensaje.getContenido());
                    if (descifrado == null) {
                        System.err.println("[SEGURIDAD] Mensaje con tag GCM inválido de " + nombreUsuario);
                        continue;
                    }
                    mensaje.setContenido(descifrado);
                    mensaje.setCifrado(false);
                }

                // Sanitizar contenido
                if (mensaje.getContenido() != null) {
                    mensaje.setContenido(ValidadorEntrada.sanitizarMensaje(mensaje.getContenido()));
                }

                // Adjuntar rol efectivo en el destino (para el escudo 🛡️)
                mensaje.setRolRemitente(obtenerRolEfectivo(nombreUsuario, mensaje.getDestino()).name());

                switch (mensaje.getTipo()) {
                    case MENSAJE_GENERAL       -> procesarMensajeGeneral(mensaje);
                    case MENSAJE_PRIVADO       -> procesarMensajePrivado(mensaje);
                    case CREAR_CANAL           -> procesarCrearCanal(mensaje);
                    case ADD_MIEMBROS_CANAL    -> procesarAddMiembrosCanal(mensaje);
                    case MENSAJE_CANAL         -> procesarMensajeCanal(mensaje);
                    case ENVIAR_ARCHIVO        -> procesarEnvioArchivo(mensaje);
                    case ELIMINAR_ARCHIVO      -> procesarEliminarArchivo(mensaje);
                    case BORRAR_MENSAJE        -> procesarBorrarMensaje(mensaje);
                    case BANEAR_USUARIO        -> procesarBanear(mensaje);
                    case SUSPENDER_CANAL       -> procesarSuspenderCanal(mensaje);
                    case PROMOVER_TEMPORAL     -> procesarPromoverTemporal(mensaje);
                    case REQUEST_PROFILE, PROFILE_RESPONSE -> procesarReenvioPerfil(mensaje);
                    default -> System.out.println("Tipo no soportado: " + mensaje.getTipo());
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Conexión interrumpida: "
                    + (nombreUsuario != null ? nombreUsuario : socket.getInetAddress()));
        } finally {
            desconectarUsuario();
        }
    }

    // =========================================================================
    // AUTENTICACIÓN (Login / Registro)
    // =========================================================================

    private boolean faseAutenticacion() throws IOException, ClassNotFoundException {
        String ip = socket.getInetAddress().getHostAddress();

        while (true) {
            // Comprobar si la IP está bloqueada por fuerza bruta
            if (infoh.ipBloqueada(ip)) {
                enviarRespuestaError(TipoMensaje.LOGIN_RESPONSE,
                        "Demasiados intentos fallidos. IP bloqueada temporalmente.");
                return false;
            }

            DatosMensaje msg = (DatosMensaje) entrada.readObject();
            
            if (msg.getTipo() == TipoMensaje.LOGIN_REGISTER) {
                if (procesarLogin(msg, ip)) return true;
            } else if (msg.getTipo() == TipoMensaje.REGISTER_REQUEST) {
                procesarRegistro(msg);
            } else {
                enviarRespuestaError(TipoMensaje.LOGIN_RESPONSE, "Se requiere login o registro.");
            }
        }
    }

    private boolean procesarLogin(DatosMensaje loginMsg, String ip) {
        try {
            DatosMensaje respuesta = new DatosMensaje();
            respuesta.setTipo(TipoMensaje.LOGIN_RESPONSE);

            if (infoh.getActuales() >= infoh.getMaximo()) {
                respuesta.setSuccess(false);
                respuesta.setReason("Servidor lleno. Inténtalo más tarde.");
                enviarObjeto(salida, respuesta);
                return false;
            }

            String nick = loginMsg.getRemitente();
            String pass = loginMsg.getPassword();

            // Validar formato del nickname
            ValidadorEntrada.ResultadoValidacion rv = ValidadorEntrada.validarNickname(nick);
            if (!rv.valido()) {
                respuesta.setSuccess(false);
                respuesta.setReason(rv.mensajeError());
                enviarObjeto(salida, respuesta);
                return false;
            }

            // Validar credenciales
            if (!gestorUsuarios.validarCredenciales(nick, pass)) {
                infoh.registrarIntentoFallido(ip);
                int restantes = MAX_INTENTOS_LOGIN - infoh.getIntentosFallidos(ip);
                respuesta.setSuccess(false);
                respuesta.setReason("Credenciales incorrectas." +
                        (restantes > 0 ? " Intentos restantes: " + restantes : " IP bloqueada."));
                enviarObjeto(salida, respuesta);
                return false;
            }

            // Comprobar que no está ya conectado
            if (infoh.existeUsuario(nick)) {
                respuesta.setSuccess(false);
                respuesta.setReason("El usuario '" + nick + "' ya está conectado.");
                enviarObjeto(salida, respuesta);
                return false;
            }

            // Login correcto
            infoh.resetearIntentosFallidos(ip);
            this.nombreUsuario = gestorUsuarios.obtenerNicknameOriginal(nick);
            this.rolUsuario    = gestorUsuarios.obtenerRol(nick);

            infoh.agregarUsuario(nombreUsuario,
                    new UsuarioConectado(nombreUsuario, socket, salida, true));

            respuesta.setSuccess(true);
            respuesta.setRemitente(nombreUsuario);
            respuesta.setRolRemitente(rolUsuario.name());
            enviarObjeto(salida, respuesta);

            System.out.println("[LOGIN] " + nombreUsuario + " (" + rolUsuario + ")"
                    + " | Conectados: " + infoh.getActuales() + "/" + infoh.getMaximo());
            return true;

        } catch (IOException e) {
            return false;
        }
    }

    private void procesarRegistro(DatosMensaje regMsg) {
        try {
            String nick = regMsg.getRemitente();
            String pass = regMsg.getPassword();
            
            DatosMensaje respuesta = new DatosMensaje();
            respuesta.setTipo(TipoMensaje.REGISTER_RESPONSE);

            // Validar nickname
            ValidadorEntrada.ResultadoValidacion rvNick = ValidadorEntrada.validarNickname(nick);
            if (!rvNick.valido()) {
                respuesta.setSuccess(false);
                respuesta.setReason(rvNick.mensajeError());
                enviarObjeto(salida, respuesta);
                return;
            }

            // Validar password
            ValidadorEntrada.ResultadoValidacion rvPass = ValidadorEntrada.validarPassword(pass);
            if (!rvPass.valido()) {
                respuesta.setSuccess(false);
                respuesta.setReason(rvPass.mensajeError());
                enviarObjeto(salida, respuesta);
                return;
            }

            // Intentar registrar
            if (gestorUsuarios.registrarUsuario(nick, pass, GestorUsuarios.Rol.USER)) {
                respuesta.setSuccess(true);
                respuesta.setReason("Usuario registrado con éxito. Ya puedes iniciar sesión.");
                System.out.println("[REGISTRO] Nuevo usuario: " + nick);
            } else {
                respuesta.setSuccess(false);
                respuesta.setReason("El nombre de usuario ya está en uso.");
            }
            
            enviarObjeto(salida, respuesta);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // MENSAJES
    // =========================================================================

    private void procesarMensajeGeneral(DatosMensaje mensaje) {
        mensaje.setRemitente(nombreUsuario);
        mensaje.setDestino("GENERAL");
        if (mensaje.getTimestamp() == null) mensaje.setTimestamp(LocalDateTime.now());
        if (mensaje.getMensajeId() == null) mensaje.setMensajeId(UUID.randomUUID().toString());

        infoh.agregarMensaje("[" + mensaje.getTimestamp().format(FMT) + "] "
                + nombreUsuario + ": " + mensaje.getContenido());

        // Cifrar antes de reenviar
        DatosMensaje msgCifrado = cifrarContenido(mensaje);

        for (UsuarioConectado dest : infoh.getUsuariosConectados().values()) {
            if (!dest.getNombreUsuario().equals(nombreUsuario)) {
                enviarSeguro(dest.getSalida(), msgCifrado);
            }
        }
    }

    private void procesarMensajePrivado(DatosMensaje mensaje) {
        mensaje.setRemitente(nombreUsuario);
        if (mensaje.getTimestamp() == null) mensaje.setTimestamp(LocalDateTime.now());
        if (mensaje.getMensajeId() == null) mensaje.setMensajeId(UUID.randomUUID().toString());

        UsuarioConectado dest = infoh.obtenerUsuario(mensaje.getDestino());
        if (dest == null) {
            enviarMensajeSistema("El usuario '" + mensaje.getDestino() + "' no está conectado.");
            return;
        }

        DatosMensaje msgCifrado = cifrarContenido(mensaje);
        enviarSeguro(dest.getSalida(), msgCifrado);
        enviarSeguro(salida, msgCifrado);  // Copia al emisor
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

        // El creador es moderador del canal automáticamente
        infoh.agregarCanal(nombreCanal, miembros);
        infoh.establecerModeradorCanal(nombreCanal, nombreUsuario);
        System.out.println("[CANAL] Creado: " + nombreCanal + " por " + nombreUsuario);

        for (String miembro : miembros) notificarUnionCanal(miembro, nombreCanal, miembros, true);
    }

    private void procesarAddMiembrosCanal(DatosMensaje mensaje) {
        String nombreCanal    = mensaje.getDestino();
        List<String> nuevos   = mensaje.getMiembros();

        if (!infoh.existeCanal(nombreCanal)) {
            enviarRespuestaError(TipoMensaje.ADD_MIEMBROS_CANAL, "El canal no existe.");
            return;
        }

        List<String> actuales  = infoh.obtenerMiembrosCanal(nombreCanal);
        Set<String>  setActual = new HashSet<>(actuales);
        for (String m : nuevos) {
            if (setActual.add(m)) notificarUnionCanal(m, nombreCanal, null, false);
        }
        infoh.agregarCanal(nombreCanal, List.copyOf(setActual));
    }

    private void procesarMensajeCanal(DatosMensaje mensaje) {
        String nombreCanal = mensaje.getDestino();
        List<String> miembros = infoh.obtenerMiembrosCanal(nombreCanal);

        if (miembros == null) {
            enviarRespuestaError(TipoMensaje.MENSAJE_CANAL, "El canal no existe.");
            return;
        }
        if (infoh.canalSuspendido(nombreCanal)) {
            enviarMensajeSistema("El canal '" + nombreCanal + "' está suspendido temporalmente.");
            return;
        }

        if (mensaje.getTimestamp() == null) mensaje.setTimestamp(LocalDateTime.now());
        if (mensaje.getMensajeId() == null) mensaje.setMensajeId(UUID.randomUUID().toString());

        DatosMensaje msgCifrado = cifrarContenido(mensaje);
        for (String miembro : miembros) {
            UsuarioConectado uc = infoh.obtenerUsuario(miembro);
            if (uc != null) enviarSeguro(uc.getSalida(), msgCifrado);
        }
    }

    // =========================================================================
    // ARCHIVOS
    // =========================================================================

    private void procesarEnvioArchivo(DatosMensaje mensaje) {
        mensaje.setRemitente(nombreUsuario);
        if (mensaje.getTimestamp() == null) mensaje.setTimestamp(LocalDateTime.now());
        if (mensaje.getArchivoId()  == null) mensaje.setArchivoId(UUID.randomUUID().toString());

        // Los datos del archivo llegan ya cifrados desde el cliente (AES-256-GCM)
        String destino = mensaje.getDestino();

        if ("GENERAL".equals(destino)) {
            // Tablón general
            for (UsuarioConectado uc : infoh.getUsuariosConectados().values()) {
                if (!uc.getNombreUsuario().equals(nombreUsuario))
                    enviarSeguro(uc.getSalida(), mensaje);
            }
        } else if (infoh.existeCanal(destino)) {
            // Tablón del canal
            infoh.agregarArchivoCanal(destino, mensaje.getArchivoId(), mensaje);
            List<String> miembros = infoh.obtenerMiembrosCanal(destino);
            if (miembros != null) {
                for (String m : miembros) {
                    UsuarioConectado uc = infoh.obtenerUsuario(m);
                    if (uc != null) enviarSeguro(uc.getSalida(), mensaje);
                }
            }
        } else {
            // Privado
            UsuarioConectado dest = infoh.obtenerUsuario(destino);
            if (dest != null) enviarSeguro(dest.getSalida(), mensaje);
            enviarSeguro(salida, mensaje);
        }
    }

    private void procesarEliminarArchivo(DatosMensaje mensaje) {
        String canal    = mensaje.getDestino();
        String archivoId= mensaje.getArchivoId();
        GestorUsuarios.Rol rolEfectivo = obtenerRolEfectivo(nombreUsuario, canal);

        DatosMensaje archivoOriginal = infoh.obtenerArchivoCanal(canal, archivoId);
        if (archivoOriginal == null) {
            enviarMensajeSistema("El archivo no existe en el tablón.");
            return;
        }

        // Solo el moderador del canal o el propio emisor pueden eliminarlo
        boolean esModerador = rolEfectivo == GestorUsuarios.Rol.MODERATOR
                || nombreUsuario.equals(infoh.obtenerModeradorCanal(canal));
        boolean esEmisor    = nombreUsuario.equals(archivoOriginal.getRemitente());

        if (!esModerador && !esEmisor) {
            enviarMensajeSistema("No tienes permisos para eliminar ese archivo.");
            return;
        }

        infoh.eliminarArchivoCanal(canal, archivoId);
        // Notificar a todos los miembros del canal
        List<String> miembros = infoh.obtenerMiembrosCanal(canal);
        if (miembros != null) {
            DatosMensaje notif = new DatosMensaje();
            notif.setTipo(TipoMensaje.ELIMINAR_ARCHIVO);
            notif.setRemitente("SISTEMA");
            notif.setDestino(canal);
            notif.setArchivoId(archivoId);
            notif.setTimestamp(LocalDateTime.now());
            for (String m : miembros) {
                UsuarioConectado uc = infoh.obtenerUsuario(m);
                if (uc != null) enviarSeguro(uc.getSalida(), notif);
            }
        }
        System.out.println("[ARCHIVO] Eliminado " + archivoId + " del canal " + canal + " por " + nombreUsuario);
    }

    private void procesarBorrarMensaje(DatosMensaje mensaje) {
        // Solo el emisor puede borrar su propio mensaje
        if (!nombreUsuario.equals(mensaje.getRemitente())) {
            enviarMensajeSistema("Solo puedes borrar tus propios mensajes.");
            return;
        }

        String destino = mensaje.getDestino();

        if ("GENERAL".equals(destino)) {
            // Borrar en sala general: notificar a todos
            for (UsuarioConectado uc : infoh.getUsuariosConectados().values()) {
                if (!uc.getNombreUsuario().equals(nombreUsuario))
                    enviarSeguro(uc.getSalida(), mensaje);
            }
        } else if (infoh.existeCanal(destino)) {
            // Borrar en un canal: notificar a todos los miembros
            List<String> miembros = infoh.obtenerMiembrosCanal(destino);
            if (miembros != null) {
                for (String m : miembros) {
                    UsuarioConectado uc = infoh.obtenerUsuario(m);
                    if (uc != null) enviarSeguro(uc.getSalida(), mensaje);
                }
            }
        } else {
            // Borrar mensaje privado: notificar al destinatario
            UsuarioConectado dest = infoh.obtenerUsuario(destino);
            if (dest != null) enviarSeguro(dest.getSalida(), mensaje);
        }
    }

    // =========================================================================
    // MODERACIÓN
    // =========================================================================

    private void procesarBanear(DatosMensaje mensaje) {
        String canal    = mensaje.getContenido(); // nombre del canal
        if (!esModeradorEfectivo(canal)) {
            enviarMensajeSistema("No tienes permisos de moderador en este canal.");
            return;
        }
        String objetivo = mensaje.getDestino();

        UsuarioConectado ucObj = infoh.obtenerUsuario(objetivo);
        if (ucObj == null) { enviarMensajeSistema("Usuario no encontrado."); return; }

        // Eliminar del canal
        List<String> miembros = infoh.obtenerMiembrosCanal(canal);
        if (miembros != null) {
            miembros.remove(objetivo);
            infoh.agregarCanal(canal, miembros);
        }

        // Notificar al baneado
        DatosMensaje ban = new DatosMensaje();
        ban.setTipo(TipoMensaje.BANEAR_USUARIO);
        ban.setRemitente("SISTEMA");
        ban.setDestino(canal);
        ban.setContenido("Has sido expulsado del canal '" + canal + "' por " + nombreUsuario + ".");
        ban.setTimestamp(LocalDateTime.now());
        enviarSeguro(ucObj.getSalida(), ban);

        // Notificar al canal
        enviarNotificacionCanal(canal, objetivo + " ha sido expulsado por el moderador " + nombreUsuario + ".");
        System.out.println("[MODERACIÓN] " + nombreUsuario + " baneó a " + objetivo + " del canal " + canal);
    }

    private void procesarSuspenderCanal(DatosMensaje mensaje) {
        String canal = mensaje.getDestino();
        if (!esModeradorEfectivo(canal)) {
            enviarMensajeSistema("No tienes permisos de moderador en este canal.");
            return;
        }
        boolean suspender = Boolean.parseBoolean(mensaje.getContenido());
        infoh.setSuspendidoCanal(canal, suspender);

        String accion = suspender ? "suspendido" : "reactivado";
        enviarNotificacionCanal(canal, "El canal ha sido " + accion + " por el moderador " + nombreUsuario + ".");
        System.out.println("[MODERACIÓN] Canal " + canal + " " + accion + " por " + nombreUsuario);
    }

    private void procesarPromoverTemporal(DatosMensaje mensaje) {
        if (!esModeradorEfectivo(mensaje.getContenido())) { // El moderador debe serlo en ese canal
            enviarMensajeSistema("No tienes permisos de moderador en este canal.");
            return;
        }
        String objetivo = mensaje.getDestino();
        String canal    = mensaje.getContenido(); // El canal donde se promueve
        long   segundos = mensaje.getSegundosPromocion();
        if (segundos <= 0 || segundos > 3600) segundos = 300; 

        String key = objetivo + ":" + canal;
        long expira = System.currentTimeMillis() + segundos * 1000L;
        promocionesTempo.put(key, expira);

        // Notificar al usuario promovido
        DatosMensaje notif = new DatosMensaje();
        notif.setTipo(TipoMensaje.PROMOVER_TEMPORAL);
        notif.setRemitente("SISTEMA");
        notif.setDestino(canal);
        notif.setContenido("Has recibido permisos de moderador temporal en el canal '" + canal + "' durante " + segundos + " segundos.");
        notif.setTimestamp(LocalDateTime.now());
        UsuarioConectado ucObj = infoh.obtenerUsuario(objetivo);
        if (ucObj != null) enviarSeguro(ucObj.getSalida(), notif);

        // Programar revocación
        final long duracion = segundos;
        Thread revocador = new Thread(() -> {
            try { Thread.sleep(duracion * 1000L); } catch (InterruptedException ignored) {}
            promocionesTempo.remove(key);
            UsuarioConectado uc2 = infoh.obtenerUsuario(objetivo);
            if (uc2 != null) {
                DatosMensaje rev = new DatosMensaje();
                rev.setTipo(TipoMensaje.REVOCAR_PROMOCION);
                rev.setRemitente("SISTEMA");
                rev.setDestino(canal);
                rev.setContenido("Tu promoción temporal de moderador en el canal '" + canal + "' ha expirado.");
                rev.setTimestamp(LocalDateTime.now());
                enviarSeguro(uc2.getSalida(), rev);
            }
            System.out.println("[MODERACIÓN] Promoción temporal de " + objetivo + " en " + canal + " expirada.");
        });
        revocador.setDaemon(true);
        revocador.start();

        System.out.println("[MODERACIÓN] " + nombreUsuario + " promovió temporalmente a " + objetivo
                + " en " + canal + " por " + segundos + "s.");
    }

    private void procesarReenvioPerfil(DatosMensaje mensaje) {
        // El servidor simplemente actúa como puente
        String destino = mensaje.getDestino();
        UsuarioConectado uc = infoh.obtenerUsuario(destino);
        if (uc != null) {
            enviarSeguro(uc.getSalida(), mensaje);
        }
    }

    // =========================================================================
    // UTILIDADES
    // =========================================================================

    /** Obtiene el rol efectivo en un canal concreto. */
    private GestorUsuarios.Rol obtenerRolEfectivo(String nick, String canal) {
        // 1. ¿Es moderador global?
        if (gestorUsuarios.obtenerRol(nick) == GestorUsuarios.Rol.MODERATOR) return GestorUsuarios.Rol.MODERATOR;

        // 2. ¿Es el moderador designado del canal?
        if (canal != null && nick.equals(infoh.obtenerModeradorCanal(canal))) return GestorUsuarios.Rol.MODERATOR;

        // 3. ¿Tiene una promoción temporal activa en este canal?
        if (canal != null) {
            Long expira = promocionesTempo.get(nick + ":" + canal);
            if (expira != null && System.currentTimeMillis() < expira) return GestorUsuarios.Rol.MODERATOR;
        }

        return GestorUsuarios.Rol.USER;
    }

    private boolean esModeradorEfectivo(String canal) {
        return obtenerRolEfectivo(nombreUsuario, canal) == GestorUsuarios.Rol.MODERATOR;
    }

    /**
     * Cifra el contenido del mensaje con AES-256-GCM antes de enviarlo.
     */
    private DatosMensaje cifrarContenido(DatosMensaje original) {
        if (original.getContenido() == null) return original;
        try {
            DatosMensaje copia = clonarMensaje(original);
            copia.setContenido(CifradoMensajes.cifrar(original.getContenido()));
            copia.setCifrado(true);
            return copia;
        } catch (Exception e) {
            System.err.println("[Cifrado] Error cifrando mensaje: " + e.getMessage());
            return original;
        }
    }

    /** Clonación superficial para no modificar el original. */
    private DatosMensaje clonarMensaje(DatosMensaje original) {
        DatosMensaje copia = new DatosMensaje();
        copia.setTipo(original.getTipo());
        copia.setRemitente(original.getRemitente());
        copia.setDestino(original.getDestino());
        copia.setContenido(original.getContenido());
        copia.setTimestamp(original.getTimestamp());
        copia.setMiembros(original.getMiembros());
        copia.setMensajeId(original.getMensajeId());
        copia.setRolRemitente(original.getRolRemitente());
        copia.setNombreArchivo(original.getNombreArchivo());
        copia.setDatosArchivoCifrado(original.getDatosArchivoCifrado());
        copia.setTamanoArchivo(original.getTamanoArchivo());
        copia.setTipoArchivo(original.getTipoArchivo());
        copia.setArchivoId(original.getArchivoId());
        copia.setSuccess(original.isSuccess());
        copia.setReason(original.getReason());
        return copia;
    }

    private void notificarUnionCanal(String miembro, String canal, List<String> todosMiembros, boolean esCreacion) {
        UsuarioConectado uc = infoh.obtenerUsuario(miembro);
        if (uc == null) return;
        DatosMensaje notif = new DatosMensaje();
        notif.setTipo(TipoMensaje.CREAR_CANAL);
        notif.setDestino(canal);
        notif.setRemitente("SISTEMA");
        notif.setContenido(esCreacion
                ? "Has sido añadido al canal: " + canal
                : "Has sido invitado al canal: " + canal);
        if (todosMiembros != null) notif.setMiembros(todosMiembros);
        notif.setSuccess(true);
        enviarSeguro(uc.getSalida(), notif);
    }

    private void enviarNotificacionATodos(String texto) {
        DatosMensaje notif = new DatosMensaje();
        notif.setTipo(TipoMensaje.MENSAJE_GENERAL);
        notif.setRemitente("SISTEMA");
        notif.setDestino("GENERAL");
        notif.setContenido(texto);
        notif.setTimestamp(LocalDateTime.now());
        for (UsuarioConectado u : infoh.getUsuariosConectados().values())
            enviarSeguro(u.getSalida(), notif);
    }

    private void enviarNotificacionCanal(String canal, String texto) {
        DatosMensaje notif = new DatosMensaje();
        notif.setTipo(TipoMensaje.MENSAJE_CANAL);
        notif.setRemitente("SISTEMA");
        notif.setDestino(canal);
        notif.setContenido(texto);
        notif.setTimestamp(LocalDateTime.now());
        List<String> miembros = infoh.obtenerMiembrosCanal(canal);
        if (miembros == null) return;
        for (String m : miembros) {
            UsuarioConectado uc = infoh.obtenerUsuario(m);
            if (uc != null) enviarSeguro(uc.getSalida(), notif);
        }
    }

    private void difundirListaUsuarios() {
        String lista = String.join(",", infoh.getNombresUsuarios());
        DatosMensaje msg = new DatosMensaje();
        msg.setTipo(TipoMensaje.LISTA_USUARIOS);
        msg.setRemitente("SISTEMA");
        msg.setContenido(lista);
        msg.setTimestamp(LocalDateTime.now());
        for (UsuarioConectado u : infoh.getUsuariosConectados().values())
            enviarSeguro(u.getSalida(), msg);
    }

    private void enviarMensajeSistema(String texto) {
        DatosMensaje msg = new DatosMensaje();
        msg.setTipo(TipoMensaje.MENSAJE_GENERAL);
        msg.setRemitente("SISTEMA");
        msg.setContenido(texto);
        msg.setTimestamp(LocalDateTime.now());
        try { enviarObjeto(salida, msg); } catch (IOException ignored) {}
    }

    private void enviarRespuestaError(TipoMensaje tipo, String razon) {
        DatosMensaje msg = new DatosMensaje();
        msg.setTipo(tipo);
        msg.setSuccess(false);
        msg.setReason(razon);
        try { enviarObjeto(salida, msg); } catch (IOException ignored) {}
    }

    private void enviarSeguro(ObjectOutputStream oos, DatosMensaje msg) {
        try { enviarObjeto(oos, msg); }
        catch (IOException e) { System.err.println("[Red] Error enviando a " + msg.getDestino()); }
    }

    private void enviarObjeto(ObjectOutputStream oos, DatosMensaje msg) throws IOException {
        synchronized (oos) { oos.reset(); oos.writeObject(msg); oos.flush(); }
    }

    // =========================================================================
    // DESCONEXIÓN
    // =========================================================================

    private void desconectarUsuario() {
        if (nombreUsuario != null) {
            // Comprobar si al irse deja algún canal vacío (extra del PDF)
            for (String canal : infoh.getCanales().keySet()) {
                List<String> miembros = infoh.obtenerMiembrosCanal(canal);
                if (miembros != null && miembros.contains(nombreUsuario)) {
                    // Nota: En este sistema los miembros son persistentes en el canal,
                    // pero el PDF sugiere loguear cuando el canal queda vacío de clientes CONECTADOS.
                    long conectados = miembros.stream().filter(infoh::existeUsuario).count();
                    if (conectados <= 1) { // el que se va era el último conectado
                        infoh.agregarMensaje("[SISTEMA] El canal '" + canal + "' se ha quedado vacío de usuarios conectados.");
                    }
                }
            }
            infoh.eliminarUsuario(nombreUsuario);
        }
        if (loginExitoso) {
            infoh.decrementarActuales();
            String salMsg = nombreUsuario + " se ha desconectado.";
            infoh.agregarMensaje("[SISTEMA] " + salMsg);
            enviarNotificacionATodos(salMsg);
            difundirListaUsuarios();
            System.out.println("[DESCONEXIÓN] " + nombreUsuario
                    + " | Conectados: " + infoh.getActuales() + "/" + infoh.getMaximo());
        } else {
            infoh.decrementarActuales();
        }
        cerrarConexion();
    }

    private void cerrarConexion() {
        try { if (entrada != null) entrada.close(); } catch (IOException ignored) {}
        try { if (salida  != null) salida.close();  } catch (IOException ignored) {}
        try { if (socket  != null) socket.close();  } catch (IOException ignored) {}
    }
}
