package org.pspro.chattcpmultisala.servidor;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InfoHilos {
    private int actuales;            // Clientes conectados actualmente
    private int conexiones;          // Total histórico de conexiones (nunca decrece)
    private int maximo;              // Máximo permitido
    private StringBuilder mensajes;  // Historial acumulado del chat

    // Array de sockets requerido por el enunciado: tabla[maximo]
    private Socket[] tabla;

    // Mapa de usuarios conectados: nombreUsuario -> UsuarioConectado
    private Map<String, UsuarioConectado> usuariosConectados = new ConcurrentHashMap<>();

    public InfoHilos(int maximo) {
        this.maximo = maximo;
        this.actuales = 0;
        this.conexiones = 0;
        this.mensajes = new StringBuilder();
        this.tabla = new Socket[maximo];  // inicializado con el máximo permitido
    }

    // ---- Gestión del array tabla[] (requerido por el enunciado) ----

    /**
     * Almacena el socket del cliente en la posición indicada del array tabla[].
     * Se llama con el índice de conexiones actual antes de incrementarlo.
     * Equivale a: infoh.anadirATabla(socket, infoh.getConexiones())
     */
    public synchronized void anadirATabla(Socket socket, int indice) {
        if (indice >= 0 && indice < tabla.length) {
            tabla[indice] = socket;
        }
    }

    /** Devuelve el socket almacenado en la posición indicada del array tabla[] */
    public synchronized Socket getSocketTabla(int indice) {
        if (indice >= 0 && indice < tabla.length) {
            return tabla[indice];
        }
        return null;
    }

    /** Devuelve una copia del array completo de sockets */
    public synchronized Socket[] getTabla() {
        return tabla.clone();
    }

    // ---- Contadores ----

    public synchronized int getActuales() {
        return actuales;
    }

    public synchronized void incrementarActuales() {
        this.actuales++;
        this.conexiones++;
    }

    public synchronized void decrementarActuales() {
        this.actuales--;
        if (this.actuales < 0) {
            this.actuales = 0;
        }
    }

    public synchronized int getMaximo() {
        return maximo;
    }

    public synchronized int getConexiones() {
        return conexiones;
    }

    // ---- Historial de mensajes ----

    public synchronized String getMensajes() {
        return mensajes.toString();
    }

    public synchronized void agregarMensaje(String linea) {
        mensajes.append(linea).append("\n");
    }

    // ---- Gestión de usuarios conectados ----

    public synchronized boolean existeUsuario(String nombreUsuario) {
        return usuariosConectados.containsKey(nombreUsuario);
    }

    public synchronized void agregarUsuario(String nombreUsuario, UsuarioConectado usuario) {
        usuariosConectados.put(nombreUsuario, usuario);
    }

    public synchronized void eliminarUsuario(String nombreUsuario) {
        usuariosConectados.remove(nombreUsuario);
    }

    public synchronized UsuarioConectado obtenerUsuario(String nombreUsuario) {
        return usuariosConectados.get(nombreUsuario);
    }

    public synchronized Map<String, UsuarioConectado> getUsuariosConectados() {
        return new ConcurrentHashMap<>(usuariosConectados);
    }

    /** Devuelve una lista con los nombres de los usuarios actualmente conectados */
    public synchronized List<String> getNombresUsuarios() {
        return new ArrayList<>(usuariosConectados.keySet());
    }
}
