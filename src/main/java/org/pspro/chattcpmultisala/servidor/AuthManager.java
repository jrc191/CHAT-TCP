package org.pspro.chattcpmultisala.servidor;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/***
 * Clase responsable de gestionar la autenticación de usuarios.
 * Almacena los usuarios y sus contraseñas (hash) en un archivo de propiedades
 * en el sistema de archivos. Proporciona métodos para registrar nuevos usuarios
 * y validar credenciales.
 */
public class AuthManager {
    private final Path dbFile;
    private final Map<String,String> users = new ConcurrentHashMap<>();

    public AuthManager(Path dbFile) {
        this.dbFile = dbFile;
        load();
    }

    private synchronized void load() {
        try {
            if (Files.exists(dbFile)) {
                Properties p = new Properties();
                try (InputStream in = Files.newInputStream(dbFile)) {
                    p.load(in);
                }
                for (String k : p.stringPropertyNames()) {
                    users.put(k, p.getProperty(k));
                }
            } else {
                Files.createDirectories(dbFile.getParent());
                Files.createFile(dbFile);
            }
        } catch (IOException e) {
            System.err.println("AuthManager load error: " + e.getMessage());
        }
    }

    private synchronized void persist() {
        try {
            Properties p = new Properties();
            p.putAll(users);
            try (OutputStream out = Files.newOutputStream(dbFile)) {
                p.store(out, "Usuarios registrados (usuario=hash)");
            }
        } catch (IOException e) {
            System.err.println("AuthManager persist error: " + e.getMessage());
        }
    }

    public synchronized boolean register(String user, String passwordHash) {
        if (users.containsKey(user)) return false;
        users.put(user, passwordHash);
        persist();
        return true;
    }

    public boolean validate(String user, String passwordHash) {
        String stored = users.get(user);
        return stored != null && stored.equals(passwordHash);
    }

    public boolean exists(String user) {
        return users.containsKey(user);
    }
}