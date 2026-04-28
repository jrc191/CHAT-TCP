package org.pspro.chattcpmultisala.common;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Gestor para manejar perfiles de usuario.
 * Proporciona métodos para crear, obtener, actualizar y eliminar perfiles.
 */
public class ProfileManager {

    private Map<String, UserProfile> profiles;
    private UserProfile currentProfile;
    private final Path profileStoragePath;

    public ProfileManager(String storagePath) {
        this.profiles = new LinkedHashMap<>();
        this.profileStoragePath = Paths.get(storagePath);
        ensureStorageDirectoryExists();
        loadProfiles();
    }

    /**
     * Constructor con ruta de almacenamiento predeterminada
     */
    public ProfileManager() {
        this(System.getProperty("user.home") + "/.chattcp/profiles");
    }

    /**
     * Asegura que el directorio de almacenamiento existe
     */
    private void ensureStorageDirectoryExists() {
        try {
            Files.createDirectories(profileStoragePath);
        } catch (IOException e) {
            System.err.println("Error creando directorio de perfiles: " + e.getMessage());
        }
    }

    /**
     * Carga todos los perfiles desde el almacenamiento
     */
    private void loadProfiles() {
        try {
            File[] files = profileStoragePath.toFile().listFiles((dir, name) -> name.endsWith(".profile"));
            if (files != null) {
                for (File file : files) {
                    UserProfile profile = loadProfileFromFile(file);
                    if (profile != null) {
                        profiles.put(profile.getUsername(), profile);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error cargando perfiles: " + e.getMessage());
        }
    }

    /**
     * Carga un perfil desde un archivo
     */
    private UserProfile loadProfileFromFile(File file) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (UserProfile) ois.readObject();
        } catch (Exception e) {
            System.err.println("Error cargando perfil desde " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Crea un nuevo perfil de usuario
     */
    public UserProfile createProfile(String username, String displayName) {
        UserProfile profile = new UserProfile(username, displayName);
        saveProfile(profile);
        profiles.put(profile.getUsername(), profile);
        return profile;
    }

    /**
     * Obtiene un perfil por su username
     */
    public UserProfile getProfile(String username) {
        return profiles.get(username);
    }

    /**
     * Obtiene un perfil por su nombre de usuario (alias)
     */
    public UserProfile getProfileByUsername(String username) {
        return profiles.values().stream()
                .filter(p -> p.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }

    /**
     * Obtiene todos los perfiles
     */
    public List<UserProfile> getAllProfiles() {
        return new ArrayList<>(profiles.values());
    }

    /**
     * Actualiza un perfil existente
     */
    public void updateProfile(UserProfile profile) {
        if (profile != null && profile.getUsername() != null) {
            profiles.put(profile.getUsername(), profile);
            saveProfile(profile);
        }
    }

    /**
     * Guarda un perfil en el almacenamiento
     */
    public void saveProfile(UserProfile profile) {
        try {
            Path filePath = profileStoragePath.resolve(profile.getUsername() + ".profile");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath.toFile()))) {
                oos.writeObject(profile);
            }
        } catch (IOException e) {
            System.err.println("Error guardando perfil: " + e.getMessage());
        }
    }

    /**
     * Elimina un perfil
     */
    public boolean deleteProfile(String username) {
        try {
            Path filePath = profileStoragePath.resolve(username + ".profile");
            Files.deleteIfExists(filePath);
            profiles.remove(username);
            if (currentProfile != null && currentProfile.getUsername().equals(username)) {
                currentProfile = null;
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error eliminando perfil: " + e.getMessage());
            return false;
        }
    }

    /**
     * Establece el perfil actual
     */
    public void setCurrentProfile(UserProfile profile) {
        this.currentProfile = profile;
    }

    /**
     * Obtiene el perfil actual
     */
    public UserProfile getCurrentProfile() {
        return currentProfile;
    }

    /**
     * Verifica si hay un perfil actual
     */
    public boolean hasCurrentProfile() {
        return currentProfile != null;
    }

    /**
     * Actualiza el estado del usuario actual
     */
    public void updateCurrentUserStatus(String status) {
        if (currentProfile != null) {
            currentProfile.setStatus(status);
            updateProfile(currentProfile);
        }
    }

    /**
     * Actualiza la información de contacto del perfil actual
     */
    public void updateCurrentUserContact(String phone, String email) {
        if (currentProfile != null) {
            currentProfile.setPhoneNumber(phone);
            currentProfile.setEmail(email);
            updateProfile(currentProfile);
        }
    }

    /**
     * Actualiza la información personal del perfil actual
     */
    public void updateCurrentUserInfo(String displayName, String bio) {
        if (currentProfile != null) {
            currentProfile.setDisplayName(displayName);
            currentProfile.setBio(bio);
            updateProfile(currentProfile);
        }
    }

    /**
     * Obtiene el avatar del perfil actual como iniciales
     */
    public String getCurrentUserAvatarInitials() {
        return currentProfile != null ? currentProfile.getAvatarInitials() : "U";
    }

    /**
     * Obtiene el nombre a mostrar del perfil actual
     */
    public String getCurrentUserDisplayName() {
        return currentProfile != null ? currentProfile.getDisplayName() : "Usuario";
    }

    /**
     * Obtiene el estado del perfil actual
     */
    public String getCurrentUserStatusText() {
        return currentProfile != null ? currentProfile.getStatusText() : "Sin conexión";
    }

    /**
     * Obtiene el emoji de estado del perfil actual
     */
    public String getCurrentUserStatusEmoji() {
        return currentProfile != null ? currentProfile.getStatusEmoji() : "⚫";
    }

    /**
     * Verifica si el perfil actual está en línea
     */
    public boolean isCurrentUserOnline() {
        return currentProfile != null && currentProfile.isOnline();
    }

    /**
     * Busca perfiles por nombre
     */
    public List<UserProfile> searchProfiles(String query) {
        if (query == null || query.isBlank()) {
            return getAllProfiles();
        }
        String lowerQuery = query.toLowerCase();
        return profiles.values().stream()
                .filter(p -> p.getDisplayName().toLowerCase().contains(lowerQuery) ||
                             p.getUsername().toLowerCase().contains(lowerQuery) ||
                             (p.getBio() != null && p.getBio().toLowerCase().contains(lowerQuery)))
                .sorted(Comparator.comparing(UserProfile::getDisplayName))
                .toList();
    }

    /**
     * Obtiene estadísticas de perfiles
     */
    public Map<String, Object> getProfilesStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProfiles", profiles.size());
        stats.put("onlineProfiles", profiles.values().stream()
                .filter(UserProfile::isOnline)
                .count());
        stats.put("awayProfiles", profiles.values().stream()
                .filter(p -> "away".equalsIgnoreCase(p.getStatus()))
                .count());
        stats.put("busyProfiles", profiles.values().stream()
                .filter(p -> "busy".equalsIgnoreCase(p.getStatus()))
                .count());
        return stats;
    }

    /**
     * Limpia todos los perfiles (cuidado, no se puede deshacer)
     */
    public void clearAll() {
        try {
            File[] files = profileStoragePath.toFile().listFiles((dir, name) -> name.endsWith(".profile"));
            if (files != null) {
                for (File file : files) {
                    Files.deleteIfExists(file.toPath());
                }
            }
            profiles.clear();
            currentProfile = null;
        } catch (IOException e) {
            System.err.println("Error limpiando perfiles: " + e.getMessage());
        }
    }

    /**
     * Exporta todos los perfiles a un archivo JSON
     */
    public void exportProfilesToJSON(String filePath) {
        try {
            // Implementaría serialización a JSON con librería como Gson o Jackson
            System.out.println("Exportando perfiles a: " + filePath);
        } catch (Exception e) {
            System.err.println("Error exportando perfiles: " + e.getMessage());
        }
    }

    /**
     * Importa perfiles desde un archivo JSON
     */
    public void importProfilesFromJSON(String filePath) {
        try {
            // Implementaría deserialización de JSON
            System.out.println("Importando perfiles desde: " + filePath);
        } catch (Exception e) {
            System.err.println("Error importando perfiles: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "ProfileManager{" +
                "totalProfiles=" + profiles.size() +
                ", currentProfile=" + (currentProfile != null ? currentProfile.getDisplayName() : "null") +
                '}';
    }
}
