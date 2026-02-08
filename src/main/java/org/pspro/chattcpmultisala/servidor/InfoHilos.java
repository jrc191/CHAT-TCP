package org.pspro.chattcpmultisala.servidor;

import java.net.Socket;

public class InfoHilos {
    private int conexiones; // Total de conexiones
    private int actuales;   // Clientes conectados actualmente
    private int maximo;     // Máximo permitido
    private Socket[] tabla; // Array de sockets
    private String mensajes; // Historial del chat

    public InfoHilos(int maximo) {
        this.maximo = maximo;
        this.conexiones = 0;
        this.actuales = 0;
        this.tabla = new Socket[maximo];
        this.mensajes = "";
    }

    // TODOS SINCRONIZADOS
    public synchronized void anadirATabla(Socket s, int indice) {
        if (indice < maximo) {
            tabla[indice] = s;
        }
    }

    public synchronized String getMensajes() {
        return mensajes;
    }

    public synchronized void setMensajes(String mensajes) {
        this.mensajes = mensajes;
    }

    public synchronized int getConexiones() {
        return conexiones;
    }

    public synchronized void incrementarConexiones() {
        this.conexiones++;
    }

    public synchronized int getActuales() {
        return actuales;
    }

    public synchronized void incrementarActuales() {
        this.actuales++;
    }

    public synchronized void decrementarActuales() {
        this.actuales--;
    }

    public synchronized Socket[] getTabla() {
        return tabla;
    }
}