package org.pspro.chattcpmultisala.servidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorChat {

    public static void main(String[] args) {
        int puerto = 55555;
        int numMaxConexiones = 2;

        InfoHilos infoh = new InfoHilos(numMaxConexiones);

        try (ServerSocket servidor = new ServerSocket(puerto)) {

            System.out.println("Servidor iniciado en puerto " + puerto);
            System.out.println("Esperando conexiones (máximo " + numMaxConexiones + " usuarios simultáneos)...");

            while (!Thread.currentThread().isInterrupted()) {

                // Siempre aceptamos la conexión TCP para poder responder correctamente
                Socket socketCliente = servidor.accept();

                if (infoh.getActuales() >= numMaxConexiones) {
                    // Servidor lleno: el hilo gestionará el rechazo y cerrará el socket
                    System.out.println("Servidor lleno. Rechazando conexión entrante.");
                }

                // Almacenar socket en tabla[] usando el índice de conexiones actual
                // (antes de incrementar, para que sirva como índice 0-based)
                infoh.anadirATabla(socketCliente, infoh.getConexiones());

                // Incrementar actuales y conexiones ANTES de lanzar el hilo
                infoh.incrementarActuales();

                System.out.println("Nueva conexión entrante. Usuarios conectados: "
                        + infoh.getActuales() + "/" + numMaxConexiones);

                HiloServidorChat hilo = new HiloServidorChat(socketCliente, infoh);
                hilo.start();
            }

        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
