package org.pspro.chattcpmultisala.common;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Modelo que representa el perfil de un usuario en el sistema de chat.
 * Contiene información personal y de estado del usuario.
 */
public class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private String avatarInitials;
    private String status;
    private LocalDateTime lastSeen;
    private String phoneNumber;
    private String email;
    private String avatarColor;
    private boolean verified;
    private int unreadCount;
    private boolean darkMode; // Preferencia de tema

    // Constructor vacío para serialización
    public UserProfile() {
        this.status = "offline";
        this.avatarColor = "#005f9e";
        this.verified = false;
        this.unreadCount = 0;
        this.darkMode = false;
    }

    // Constructor con datos básicos
    public UserProfile(String username, String displayName) {
        this();
        this.username = username;
        this.displayName = displayName;
        this.avatarInitials = generateInitials(displayName);
    }

    // Constructor completo
    public UserProfile(String username, String displayName, String bio, String avatarUrl) {
        this(username, displayName);
        this.bio = bio;
        this.avatarUrl = avatarUrl;
    }

    // Getters y Setters
    public boolean isDarkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        if (displayName != null) {
            this.avatarInitials = generateInitials(displayName);
        }
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAvatarInitials() {
        return avatarInitials;
    }

    public void setAvatarInitials(String avatarInitials) {
        this.avatarInitials = avatarInitials;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarColor() {
        return avatarColor;
    }

    public void setAvatarColor(String avatarColor) {
        this.avatarColor = avatarColor;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    // Métodos de utilidad
    public String getStatusEmoji() {
        return switch (status) {
            case "online" -> "🟢";
            case "away" -> "🟡";
            case "busy" -> "🔴";
            default -> "⚫";
        };
    }

    public boolean isOnline() {
        return "online".equalsIgnoreCase(status);
    }

    public String getStatusText() {
        return switch (status) {
            case "online" -> "en línea";
            case "away" -> "ausente";
            case "busy" -> "ocupado";
            default -> "sin conexión";
        };
    }

    private String generateInitials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (initials.length() < 2 && !part.isEmpty()) {
                initials.append(part.charAt(0));
            }
        }
        return initials.toString().toUpperCase();
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "username='" + username + '\'' +
                ", displayName='" + displayName + '\'' +
                ", status='" + status + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserProfile that = (UserProfile) o;
        return username != null && username.equals(that.username);
    }

    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }
}
