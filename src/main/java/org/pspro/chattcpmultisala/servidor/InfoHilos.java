package org.pspro.chattcpmultisala.servidor;

import org.pspro.chattcpmultisala.common.DatosMensaje;

import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estado compartido entre todos los hilos del servidor.
 * Añade:
 *  - Protección anti fuerza bruta (conteo de intentos fallidos por IP)
 *  - Moderadores de canal
 *  - Estado de canal (suspendido / activo)
 *  - Tablón de archivos por canal
 */
public class InfoHilos {

    // ── Contadores ────────────────────────────────────────────────────────────
    private int actuales;
    private int conexiones;
    private int maximo;
    private StringBuilder mensajes = new StringBuilder();
    private Socket[] tabla;

    // ── Usuarios conectados ───────────────────────────────────────────────────
    private final Map<String, UsuarioConectado> usuariosConectados = new ConcurrentHashMap<>();

    // ── Canales ───────────────────────────────────────────────────────────────
    private final Map<String, List<String>>       canales           = new ConcurrentHashMap<>();
    private final Map<String, String>             moderadoresCanal  = new ConcurrentHashMap<>();
    private final Map<String, Boolean>            canalesSuspendidos = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime>      fechasCreacionCanal = new ConcurrentHashMap<>();
    private final Map<String, Set<String>>        blacklists        = new ConcurrentHashMap<>();

    // ── Tablón de archivos: canalNombre → (archivoId → DatosMensaje) ─────────
    private final Map<String, Map<String, DatosMensaje>> archivosCanal = new ConcurrentHashMap<>();

    // ── Permisos de chat privado: usuario → Set de usuarios aceptados ────────
    private final Map<String, Set<String>> chatPermissions = new ConcurrentHashMap<>();

    // ── Protección anti fuerza bruta ──────────────────────────────────────────
    /** ip → número de intentos fallidos consecutivos */
    private final Map<String, AtomicInteger> intentosFallidos = new ConcurrentHashMap<>();
    /** ip → timestamp de bloqueo (ms). IP bloqueada si está dentro del período. */
    private final Map<String, Long>          ipBloqueadaHasta = new ConcurrentHashMap<>();

    private static final int  MAX_INTENTOS   = 5;
    private static final long BLOQUEO_MS     = 30 * 1000L;  // 30 segundos

    // =========================================================================
    // Constructor
    // =========================================================================

    public InfoHilos(int maximo) {
        this.maximo     = maximo;
        this.actuales   = 0;
        this.conexiones = 0;
        this.tabla      = new Socket[maximo];
    }

    // =========================================================================
    // Tabla / Contadores
    // =========================================================================

    public synchronized void anadirATabla(Socket socket, int indice) {
        if (indice >= 0 && indice < tabla.length) tabla[indice] = socket;
    }
    public synchronized Socket getSocketTabla(int i) {
        return (i >= 0 && i < tabla.length) ? tabla[i] : null;
    }
    public synchronized Socket[] getTabla() { return tabla.clone(); }

    public synchronized int  getActuales()  { return actuales; }
    public synchronized int  getMaximo()    { return maximo; }
    public synchronized int  getConexiones(){ return conexiones; }

    public synchronized void incrementarActuales() { actuales++; conexiones++; }
    public synchronized void decrementarActuales() { if (actuales > 0) actuales--; }

    // =========================================================================
    // Historial de mensajes (para backup de log)
    // =========================================================================

    public synchronized String getMensajes()             { return mensajes.toString(); }
    public synchronized void agregarMensaje(String linea) { mensajes.append(linea).append("\n"); }

    // =========================================================================
    // Usuarios
    // =========================================================================

    public synchronized boolean      existeUsuario(String n) { return usuariosConectados.containsKey(n); }
    public synchronized void         agregarUsuario(String n, UsuarioConectado u) { usuariosConectados.put(n, u); }
    public synchronized void         eliminarUsuario(String n) { usuariosConectados.remove(n); }
    public synchronized UsuarioConectado obtenerUsuario(String n) { return usuariosConectados.get(n); }
    public synchronized Map<String, UsuarioConectado> getUsuariosConectados() {
        return new ConcurrentHashMap<>(usuariosConectados);
    }
    public synchronized List<String> getNombresUsuarios() { return new ArrayList<>(usuariosConectados.keySet()); }

    // =========================================================================
    // Canales
    // =========================================================================

    public synchronized void         agregarCanal(String nombre, List<String> miembros) { 
        canales.put(nombre, miembros);
        fechasCreacionCanal.putIfAbsent(nombre, LocalDateTime.now());
    }
    public synchronized LocalDateTime obtenerFechaCreacionCanal(String nombre) { return fechasCreacionCanal.get(nombre); }
    public synchronized List<String> obtenerMiembrosCanal(String nombre) { return canales.get(nombre); }
    public synchronized boolean      existeCanal(String nombre)          { return canales.containsKey(nombre); }
    public synchronized Map<String, List<String>> getCanales()           { return new ConcurrentHashMap<>(canales); }

    /** Establece el moderador principal de un canal. */
    public synchronized void   establecerModeradorCanal(String canal, String moderador) {
        moderadoresCanal.put(canal, moderador);
    }
    public synchronized String obtenerModeradorCanal(String canal) {
        return moderadoresCanal.get(canal);
    }

    /** Suspende o activa un canal. */
    public synchronized void    setSuspendidoCanal(String canal, boolean suspendido) {
        canalesSuspendidos.put(canal, suspendido);
    }
    public synchronized boolean canalSuspendido(String canal) {
        return Boolean.TRUE.equals(canalesSuspendidos.get(canal));
    }

    /** Blacklist management */
    public synchronized void banearDeCanal(String canal, String usuario) {
        blacklists.computeIfAbsent(canal, k -> ConcurrentHashMap.newKeySet()).add(usuario);
    }
    public synchronized boolean estaBaneado(String canal, String usuario) {
        Set<String> banned = blacklists.get(canal);
        return banned != null && banned.contains(usuario);
    }
    public synchronized void unbanDeCanal(String canal, String usuario) {
        Set<String> banned = blacklists.get(canal);
        if (banned != null) banned.remove(usuario);
    }
    public synchronized List<String> obtenerBaneados(String canal) {
        Set<String> banned = blacklists.get(canal);
        return banned != null ? new ArrayList<>(banned) : Collections.emptyList();
    }

    // =========================================================================
    // Tablón de archivos
    // =========================================================================

    public synchronized void agregarArchivoCanal(String canal, String archivoId, DatosMensaje msg) {
        archivosCanal.computeIfAbsent(canal, k -> new LinkedHashMap<>()).put(archivoId, msg);
    }

    public synchronized DatosMensaje obtenerArchivoCanal(String canal, String archivoId) {
        Map<String, DatosMensaje> mapa = archivosCanal.get(canal);
        return mapa != null ? mapa.get(archivoId) : null;
    }

    public synchronized void eliminarArchivoCanal(String canal, String archivoId) {
        Map<String, DatosMensaje> mapa = archivosCanal.get(canal);
        if (mapa != null) mapa.remove(archivoId);
    }

    public synchronized Map<String, DatosMensaje> getArchivosCanal(String canal) {
        return archivosCanal.getOrDefault(canal, Collections.emptyMap());
    }

    // =========================================================================
    // Permisos de Chat
    // =========================================================================

    public synchronized void concederPermisoChat(String u1, String u2) {
        chatPermissions.computeIfAbsent(u1, k -> ConcurrentHashMap.newKeySet()).add(u2);
        chatPermissions.computeIfAbsent(u2, k -> ConcurrentHashMap.newKeySet()).add(u1);
    }

    public synchronized boolean tienePermisoChat(String u1, String u2) {
        Set<String> p1 = chatPermissions.get(u1);
        return p1 != null && p1.contains(u2);
    }

    // =========================================================================
    // Protección anti fuerza bruta (DoS)
    // =========================================================================

    /**
     * Registra un intento de login fallido para la IP dada.
     * Si alcanza el máximo, bloquea la IP durante BLOQUEO_MS.
     */
    public synchronized void registrarIntentoFallido(String ip) {
        AtomicInteger cnt = intentosFallidos.computeIfAbsent(ip, k -> new AtomicInteger(0));
        int total = cnt.incrementAndGet();
        if (total >= MAX_INTENTOS) {
            ipBloqueadaHasta.put(ip, System.currentTimeMillis() + BLOQUEO_MS);
            System.out.println("[SEGURIDAD] IP bloqueada por fuerza bruta: " + ip);
        }
    }

    /** Devuelve true si la IP está actualmente bloqueada. */
    public synchronized boolean ipBloqueada(String ip) {
        Long hasta = ipBloqueadaHasta.get(ip);
        if (hasta == null) return false;
        if (System.currentTimeMillis() > hasta) {
            // Desbloquear automáticamente tras el período
            ipBloqueadaHasta.remove(ip);
            intentosFallidos.remove(ip);
            return false;
        }
        return true;
    }

    /** Número de intentos fallidos acumulados para una IP. */
    public synchronized int getIntentosFallidos(String ip) {
        AtomicInteger cnt = intentosFallidos.get(ip);
        return cnt != null ? cnt.get() : 0;
    }

    /** Segundos restantes de bloqueo para una IP (0 si no está bloqueada). */
    public synchronized long segundosRestantesBloqueo(String ip) {
        Long hasta = ipBloqueadaHasta.get(ip);
        if (hasta == null) return 0;
        long restantes = (hasta - System.currentTimeMillis()) / 1000;
        return Math.max(restantes, 0);
    }

    /** Resetea el contador de intentos tras un login correcto. */
    public synchronized void resetearIntentosFallidos(String ip) {
        intentosFallidos.remove(ip);
        ipBloqueadaHasta.remove(ip);
    }
}
