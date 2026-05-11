package org.pspro.chattcpmultisala.servidor;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.net.ssl.*;

import org.pspro.chattcpmultisala.servidor.GestorUsuarios;

/**
 * Punto de entrada del servidor con soporte SSL/TLS.
 * Novedades respecto a la versión anterior:
 *  - Implementa SSLServerSocket para comunicaciones cifradas.
 *  - Inicializa {@link GestorUsuarios} y crea el archivo de usuarios si no existe.
 *  - Pulsar ENTER en la consola genera un backup del log en backup_YYYYMMDD_HHmmss.log.
 *  - Pasa {@link GestorUsuarios} a cada {@link HiloServidorChat}.
 */
public class ServidorChat {

    private static final DateTimeFormatter FMT_ARCHIVO =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String KEYSTORE_FILE = "/servidor.p12";
    private static final String KEYSTORE_PASS = "chat-password";

    public static void main(String[] args) {

        int puerto             = 55555;
        int numMaxConexiones   = 4;

        for (int i = 0; i < args.length; i++) {
            if ("-usuariosMaximos".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try { numMaxConexiones = Integer.parseInt(args[i + 1]); }
                catch (NumberFormatException e) {
                    System.err.println("Argumento inválido para -usuariosMaximos");
                }
            }
        }

        // ── Inicializar gestor de usuarios ────────────────────────────────────
        GestorUsuarios gestorUsuarios = new GestorUsuarios();
        gestorUsuarios.inicializarArchivoPorDefecto();

        InfoHilos infoh = new InfoHilos(numMaxConexiones);

        // ── Hilo de backup del log (pulsar ENTER en consola) ──────────────────
        Thread hiloBackup = new Thread(() -> {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("[INFO] Pulsa ENTER para generar un backup del log del servidor.");
            while (true) {
                try {
                    br.readLine();  // espera ENTER
                    generarBackupLog(infoh);
                } catch (IOException e) {
                    System.err.println("[Backup] Error leyendo entrada: " + e.getMessage());
                    break;
                }
            }
        });
        hiloBackup.setDaemon(true);
        hiloBackup.start();

        // ── Configurar SSL ────────────────────────────────────────────────────
        SSLServerSocketFactory ssf = crearSSLFactory();
        if (ssf == null) {
            System.err.println("[ERROR] No se pudo configurar SSL. Abortando.");
            return;
        }

        // ── Bucle principal de aceptación de conexiones ───────────────────────
        try (SSLServerSocket servidor = (SSLServerSocket) ssf.createServerSocket(puerto)) {

            String msgInicio = "=== Servidor iniciado en puerto " + puerto + " ===";
            System.out.println(msgInicio);
            infoh.agregarMensaje("[SISTEMA] " + msgInicio);

            System.out.println("======================================");
            System.out.println(" ChatTCP Servidor SSL - PSPro 2026");
            System.out.println("======================================");
            System.out.println("Puerto  : " + puerto);
            System.out.println("Máx.    : " + numMaxConexiones + " usuarios");
            System.out.println("Seguridad: SSL/TLS (JSSE)");
            System.out.println("======================================");

            while (!Thread.currentThread().isInterrupted()) {

                Socket socketCliente = servidor.accept();

                if (infoh.getActuales() >= numMaxConexiones) {
                    System.out.println("[INFO] Servidor lleno. Rechazando conexión entrante.");
                }

                infoh.anadirATabla(socketCliente, infoh.getConexiones());
                infoh.incrementarActuales();

                System.out.println("[CONEXIÓN] Nueva conexión SEGURA: "
                        + socketCliente.getInetAddress().getHostAddress()
                        + " | Conectados: " + infoh.getActuales() + "/" + numMaxConexiones);

                HiloServidorChat hilo = new HiloServidorChat(socketCliente, infoh, gestorUsuarios);
                hilo.start();
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // Utilidades SSL
    // =========================================================================

    private static SSLServerSocketFactory crearSSLFactory() {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            InputStream is = ServidorChat.class.getResourceAsStream(KEYSTORE_FILE);
            if (is == null) {
                System.err.println("No se encontró el archivo de almacén de claves: " + KEYSTORE_FILE);
                return null;
            }
            ks.load(is, KEYSTORE_PASS.toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, KEYSTORE_PASS.toCharArray());

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), null, null);

            return sc.getServerSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // =========================================================================
    // Backup de log
    // =========================================================================

    private static void generarBackupLog(InfoHilos infoh) {
        String nombreArchivo = "backup_" + LocalDateTime.now().format(FMT_ARCHIVO) + ".log";
        try {
            Files.writeString(Paths.get(nombreArchivo),
                    "=== BACKUP LOG ChatTCP - " + LocalDateTime.now() + " ===\n\n"
                    + infoh.getMensajes());
            System.out.println("[BACKUP] Log guardado en: " + nombreArchivo);
        } catch (IOException e) {
            System.err.println("[BACKUP] Error guardando log: " + e.getMessage());
        }
    }
}

