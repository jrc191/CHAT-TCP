package org.pspro.chattcpmultisala.common;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gestor centralizado para manejar carpetas de chat.
 * Proporciona métodos para crear, actualizar, eliminar y consultar carpetas.
 */
public class ChatFolderManager {

    private Map<String, ChatFolder> folders;
    private Map<String, String> chatFolderMap;  // chatId -> folderId
    private String currentFolderId;

    public ChatFolderManager() {
        this.folders = new LinkedHashMap<>();
        this.chatFolderMap = new HashMap<>();
        this.currentFolderId = "all";
        initializePredefinedFolders();
    }

    /**
     * Inicializa las carpetas predefinidas del sistema
     */
    private void initializePredefinedFolders() {
        addFolder(ChatFolder.createAllChatsFolder());
        addFolder(ChatFolder.createUnreadFolder());
    }

    /**
     * Agrega una nueva carpeta al gestor
     */
    public void addFolder(ChatFolder folder) {
        if (folder != null && folder.getId() != null) {
            folders.put(folder.getId(), folder);
        }
    }

    /**
     * Obtiene una carpeta por su ID
     */
    public ChatFolder getFolder(String folderId) {
        return folders.get(folderId);
    }

    /**
     * Obtiene todas las carpetas ordenadas por displayOrder
     */
    public List<ChatFolder> getAllFolders() {
        return folders.values().stream()
                .sorted(Comparator.comparingInt(ChatFolder::getDisplayOrder))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene solo las carpetas personalizadas (no predefinidas)
     */
    public List<ChatFolder> getCustomFolders() {
        return folders.values().stream()
                .filter(f -> !f.isDefault() && !f.getId().equals("unread"))
                .sorted(Comparator.comparingInt(ChatFolder::getDisplayOrder))
                .collect(Collectors.toList());
    }

    /**
     * Elimina una carpeta por su ID
     */
    public boolean removeFolder(String folderId) {
        if (folderId.equals("all") || folderId.equals("unread")) {
            return false;  // No se pueden eliminar carpetas del sistema
        }
        ChatFolder removed = folders.remove(folderId);
        if (removed != null) {
            // Mover chats de la carpeta eliminada a "all"
            for (String chatId : removed.getChatIds()) {
                chatFolderMap.put(chatId, "all");
            }
            return true;
        }
        return false;
    }

    /**
     * Agrega un chat a una carpeta
     */
    public void addChatToFolder(String chatId, String folderId) {
        ChatFolder folder = getFolder(folderId);
        if (folder != null) {
            folder.addChat(chatId);
            chatFolderMap.put(chatId, folderId);
            // Agregar también a "all" si no está
            ChatFolder allFolder = getFolder("all");
            if (allFolder != null && !allFolder.containsChat(chatId)) {
                allFolder.addChat(chatId);
            }
        }
    }

    /**
     * Remueve un chat de una carpeta
     */
    public void removeChatFromFolder(String chatId, String folderId) {
        ChatFolder folder = getFolder(folderId);
        if (folder != null) {
            folder.removeChat(chatId);
        }
    }

    /**
     * Mueve un chat de una carpeta a otra
     */
    public void moveChatToFolder(String chatId, String fromFolderId, String toFolderId) {
        removeChatFromFolder(chatId, fromFolderId);
        addChatToFolder(chatId, toFolderId);
    }

    /**
     * Obtiene la carpeta actual donde está un chat
     */
    public String getChatFolder(String chatId) {
        return chatFolderMap.getOrDefault(chatId, "all");
    }

    /**
     * Obtiene todos los chats en una carpeta
     */
    public List<String> getChatsInFolder(String folderId) {
        ChatFolder folder = getFolder(folderId);
        return folder != null ? new ArrayList<>(folder.getChatIds()) : new ArrayList<>();
    }

    /**
     * Obtiene el número de chats en una carpeta
     */
    public int getChatCountInFolder(String folderId) {
        ChatFolder folder = getFolder(folderId);
        return folder != null ? folder.getChatCount() : 0;
    }

    /**
     * Obtiene la carpeta actual seleccionada
     */
    public String getCurrentFolderId() {
        return currentFolderId;
    }

    /**
     * Establece la carpeta actual seleccionada
     */
    public void setCurrentFolder(String folderId) {
        if (folders.containsKey(folderId)) {
            this.currentFolderId = folderId;
        }
    }

    /**
     * Obtiene la carpeta actual como objeto
     */
    public ChatFolder getCurrentFolder() {
        return getFolder(currentFolderId);
    }

    /**
     * Filtra chats por búsqueda en una carpeta
     */
    public List<String> searchChatsInFolder(String folderId, String query) {
        if (query == null || query.isBlank()) {
            return getChatsInFolder(folderId);
        }
        return getChatsInFolder(folderId).stream()
                .filter(chatId -> chatId.toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Actualiza el conteo de no leídos en una carpeta
     */
    public void updateFolderUnreadCount(String folderId, int count) {
        ChatFolder folder = getFolder(folderId);
        if (folder != null) {
            folder.setUnreadCount(count);
        }
    }

    /**
     * Calcula el total de no leídos en todas las carpetas
     */
    public int getTotalUnreadCount() {
        return folders.values().stream()
                .mapToInt(ChatFolder::getUnreadCount)
                .sum();
    }

    /**
     * Limpia todas las carpetas
     */
    public void clearAll() {
        folders.clear();
        chatFolderMap.clear();
        currentFolderId = "all";
        initializePredefinedFolders();
    }

    /**
     * Obtiene estadísticas de uso de carpetas
     */
    public Map<String, Integer> getFolderStatistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (ChatFolder folder : getAllFolders()) {
            stats.put(folder.getName(), folder.getChatCount());
        }
        return stats;
    }

    @Override
    public String toString() {
        return "ChatFolderManager{" +
                "totalFolders=" + folders.size() +
                ", currentFolder='" + currentFolderId + '\'' +
                '}';
    }
}
