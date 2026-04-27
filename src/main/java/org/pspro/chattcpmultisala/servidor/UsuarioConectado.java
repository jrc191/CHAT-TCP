package org.pspro.chattcpmultisala.servidor;

import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Representa un usuario conectado al servidor.
 * Almacena la información de conexión y el nombre de usuario.
 */
public class UsuarioConectado {
    private String nombreUsuario;
    private Socket socket;
    private ObjectOutputStream salida;
    private boolean esRegistrado; // true si tiene cuenta, false si es anónimo

    public UsuarioConectado(String nombreUsuario, Socket socket, ObjectOutputStream salida, boolean esRegistrado) {
        this.nombreUsuario = nombreUsuario;
        this.socket = socket;
        this.salida = salida;
        this.esRegistrado = esRegistrado;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public ObjectOutputStream getSalida() {
        return salida;
    }

    public void setSalida(ObjectOutputStream salida) {
        this.salida = salida;
    }

    public boolean isEsRegistrado() {
        return esRegistrado;
    }

    public void setEsRegistrado(boolean esRegistrado) {
        this.esRegistrado = esRegistrado;
    }
}
