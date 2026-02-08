package org.pspro.chattcpmultisala.servidor;

import java.io.*;
import java.net.Socket;

public class HiloServidorChat extends Thread {

    private Socket socket;
    private InfoHilos infoh;
    private DataInputStream entrada;
    private DataOutputStream salida;

    public HiloServidorChat(Socket s, InfoHilos infoh) {
        this.socket = s;
        this.infoh = infoh;

        //Flujos
        try {
            this.entrada = new DataInputStream(socket.getInputStream());
            this.salida = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        String cadena = "";

        // Evitamo repeticion al conectar
        String mensajesAnteriores = infoh.getMensajes();

        enviarMensajesATodos("Nuevo usuario conectado.");

        try {
            while (true) {
                // Leemos lo que envía el cliente
                cadena = entrada.readUTF();

                // Salida
                if (cadena.equals("*****")) {
                    infoh.decrementarActuales();
                    enviarMensajesATodos("--- Un usuario se ha desconectado ---");
                    break;
                }

                // Guardamos mensaje en el historial compartido
                // Nota: Aquí se suele formatear con el nombre del usuario si se envió
                infoh.setMensajes(infoh.getMensajes() + cadena + "\n");

                // Enviamos el mensaje a todos los conectados
                enviarMensajesATodos(cadena);
            }
        } catch (IOException e) {
            System.out.println("Error en la comunicación con un cliente.");
        } finally {
            // Cierre del socket del cliente
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Método privado para hacer broadcast
    private void enviarMensajesATodos(String texto) {
        Socket[] tabla = infoh.getTabla();
        for (int i = 0; i < infoh.getConexiones(); i++) {
            Socket s = tabla[i];
            try {
                // Solo enviamos si NO es nuestro propio socket
                if (s != null && !s.isClosed() && s != this.socket) {
                    DataOutputStream fsalida = new DataOutputStream(s.getOutputStream());
                    fsalida.writeUTF(texto);
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
}