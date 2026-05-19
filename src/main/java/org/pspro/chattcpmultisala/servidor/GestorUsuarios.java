package org.pspro.chattcpmultisala.servidor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Gestiona el archivo de usuarios con contraseñas hasheadas (SHA-256 + salt) y roles.
 *
 *
 * Formato de línea en usuarios.dat:
 *   nickname:salt:sha256(salt+password):role
 *
 * Roles posibles: USER, MODERATOR
 */
public class GestorUsuarios {

    public enum Rol { USER, MODERATOR }

    private static final String ARCHIVO = "usuarios.dat";
    private static final String ALGORITMO = "SHA-256";

    /** Caché en memoria: nickname → [salt, hashPwd, rol] */
    private final Map<String, String[]> usuariosCargados = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    public GestorUsuarios() {
        cargarUsuarios();
    }

    /** Crea el archivo de usuarios con datos de ejemplo si no existe. */
    public void inicializarArchivoPorDefecto() {
        if (Files.exists(Paths.get(ARCHIVO))) return;

        System.out.println("[GestorUsuarios] Creando archivo de usuarios con cuentas por defecto...");
        registrarUsuario("admin",   "admin123",   Rol.MODERATOR);
        registrarUsuario("alice",   "alice123",   Rol.USER);
        registrarUsuario("bob",     "bob123",     Rol.USER);
        registrarUsuario("charlie", "charlie123", Rol.USER);
        System.out.println("[GestorUsuarios] Archivo " + ARCHIVO + " creado.");
    }

    // -------------------------------------------------------------------------
    // Registro
    // -------------------------------------------------------------------------

    /**
     * Registra un nuevo usuario en el archivo.
     * @return false si el nickname ya existe.
     */
    public synchronized boolean registrarUsuario(String nickname, String password, Rol rol) {
        if (usuariosCargados.containsKey(nickname.toLowerCase())) return false;

        String salt = generarSalt();
        String hash = hashearPassword(salt, password);
        String linea = nickname + ":" + salt + ":" + hash + ":" + rol.name();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCHIVO, true))) {
            bw.write(linea);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("[GestorUsuarios] Error escribiendo archivo: " + e.getMessage());
            return false;
        }

        usuariosCargados.put(nickname.toLowerCase(), new String[]{salt, hash, rol.name()});
        return true;
    }

    // -------------------------------------------------------------------------
    // Validación
    // -------------------------------------------------------------------------

    /**
     * Valida las credenciales del usuario.
     * @return true si el nickname existe y la contraseña es correcta.
     */
    public boolean validarCredenciales(String nickname, String password) {
        String[] datos = usuariosCargados.get(nickname.toLowerCase());
        if (datos == null) return false;

        String salt = datos[0];
        String hashEsperado = datos[1];
        String hashIntento = hashearPassword(salt, password);

        // Comparación segura para evitar timing attacks
        return MessageDigest.isEqual(
                hashEsperado.getBytes(StandardCharsets.UTF_8),
                hashIntento.getBytes(StandardCharsets.UTF_8)
        );
    }

    /** Devuelve el rol del usuario, o null si no existe. */
    public Rol obtenerRol(String nickname) {
        String[] datos = usuariosCargados.get(nickname.toLowerCase());
        if (datos == null) return null;
        try { return Rol.valueOf(datos[2]); }
        catch (IllegalArgumentException e) { return Rol.USER; }
    }

    /** Devuelve el nickname con la capitalización original almacenada. */
    public String obtenerNicknameOriginal(String nickname) {
        // Recorremos el archivo para obtener el nombre tal como fue guardado
        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(":");
                if (partes.length >= 4 && partes[0].equalsIgnoreCase(nickname)) {
                    return partes[0];
                }
            }
        } catch (IOException ignored) {}
        return nickname;
    }

    public boolean existeUsuario(String nickname) {
        return usuariosCargados.containsKey(nickname.toLowerCase());
    }

    // -------------------------------------------------------------------------
    // Hashing
    // -------------------------------------------------------------------------

    private String generarSalt() {
        SecureRandom sr = new SecureRandom();
        byte[] saltBytes = new byte[16];
        sr.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /**
     * SHA-256(salt + password), devuelto en Base64.
     */
    public static String hashearPassword(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITMO);
            md.update((salt + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    // -------------------------------------------------------------------------
    // Carga
    // -------------------------------------------------------------------------

    private void cargarUsuarios() {
        if (!Files.exists(Paths.get(ARCHIVO))) return;
        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) continue;
                String[] partes = linea.split(":");
                if (partes.length < 4) continue;
                usuariosCargados.put(partes[0].toLowerCase(),
                        new String[]{partes[1], partes[2], partes[3]});
            }
            System.out.println("[GestorUsuarios] " + usuariosCargados.size() + " usuarios cargados.");
        } catch (IOException e) {
            System.err.println("[GestorUsuarios] Error cargando archivo: " + e.getMessage());
        }
    }
}
