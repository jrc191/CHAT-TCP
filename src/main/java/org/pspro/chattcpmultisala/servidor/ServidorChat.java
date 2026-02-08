package org.pspro.chattcpmultisala.servidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorChat {

    public static void main(String[] args) {
        int puerto = 55555;
        int numMaxConexiones = 4;

        // mensajes gestionados en InfoHilos
        InfoHilos infoh = new InfoHilos(numMaxConexiones);

        try (ServerSocket servidor = new ServerSocket(puerto)) {

            System.out.println("Servidor iniciado en puerto " + puerto);

            // Aceptamos si hay disponibles
            while (infoh.getConexiones() < numMaxConexiones) {

                Socket socketCliente = servidor.accept();

                // Añadimos conexión
                infoh.anadirATabla(socketCliente, infoh.getConexiones());
                infoh.incrementarActuales();
                infoh.incrementarConexiones();

                HiloServidorChat hilo = new HiloServidorChat(socketCliente, infoh);
                hilo.start();
            }

            System.out.println("Se ha alcanzado el número máximo de conexiones.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}