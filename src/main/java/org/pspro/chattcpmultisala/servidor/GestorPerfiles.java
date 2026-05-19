package org.pspro.chattcpmultisala.servidor;

import org.pspro.chattcpmultisala.common.UserProfile;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Gestor de perfiles en el servidor para que el administrador pueda consultarlos
 * incluso si el usuario está desconectado.
 */
public class GestorPerfiles {

    private static final String DIR_PERFILES = "perfiles";
    private final Map<String, UserProfile> cachePerfiles = new ConcurrentHashMap<>();

    public GestorPerfiles() {
        try {
            Files.createDirectories(Paths.get(DIR_PERFILES));
            cargarPerfilesDesdeDisco();
        } catch (IOException e) {
            System.err.println("[GestorPerfiles] Error al crear directorio de perfiles: " + e.getMessage());
        }
    }

    private void cargarPerfilesDesdeDisco() {
        File folder = new File(DIR_PERFILES);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) return;

        for (File f : files) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                UserProfile p = (UserProfile) ois.readObject();
                if (p != null && p.getUsername() != null) {
                    cachePerfiles.put(p.getUsername().toLowerCase(), p);
                }
            } catch (Exception e) {
                System.err.println("[GestorPerfiles] Error cargando " + f.getName() + ": " + e.getMessage());
            }
        }
        System.out.println("[GestorPerfiles] " + cachePerfiles.size() + " perfiles cargados en el servidor.");
    }

    public synchronized void guardarPerfil(UserProfile p) {
        if (p == null || p.getUsername() == null) return;
        
        cachePerfiles.put(p.getUsername().toLowerCase(), p);
        
        String filename = DIR_PERFILES + "/" + p.getUsername().toLowerCase() + ".dat";
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(p);
        } catch (IOException e) {
            System.err.println("[GestorPerfiles] Error guardando perfil de " + p.getUsername() + ": " + e.getMessage());
        }
    }

    public UserProfile obtenerPerfil(String username) {
        if (username == null) return null;
        return cachePerfiles.get(username.toLowerCase());
    }
}
