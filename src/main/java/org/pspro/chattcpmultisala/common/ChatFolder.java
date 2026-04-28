package org.pspro.chattcpmultisala.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo que representa una carpeta para organizar chats.
 * Las carpetas permiten agrupar conversaciones por categoría o propósito.
 */
public class ChatFolder implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String name;
    private String icon;
    private String color;
    private int displayOrder;
    private boolean isDefault;
    private List<String> chatIds;
    private int unreadCount;

    // Constructor para carpeta vacía
    public ChatFolder() {
        this.chatIds = new ArrayList<>();
        this.unreadCount = 0;
        this.color = "#005f9e";
        this.displayOrder = 0;
        this.isDefault = false;
    }

    // Constructor con parámetros básicos
    public ChatFolder(String id, String name, String icon) {
        this();
        this.id = id;
        this.name = name;
        this.icon = icon;
    }

    // Constructor completo
    public ChatFolder(String id, String name, String icon, String color, int displayOrder) {
        this();
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.displayOrder = displayOrder;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public List<String> getChatIds() {
        return chatIds;
    }

    public void setChatIds(List<String> chatIds) {
        this.chatIds = chatIds;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    // Métodos de utilidad
    public void addChat(String chatId) {
        if (!chatIds.contains(chatId)) {
            chatIds.add(chatId);
        }
    }

    public void removeChat(String chatId) {
        chatIds.remove(chatId);
    }

    public boolean containsChat(String chatId) {
        return chatIds.contains(chatId);
    }

    public int getChatCount() {
        return chatIds.size();
    }

    public void clearChats() {
        chatIds.clear();
    }

    // Carpetas predefinidas
    public static ChatFolder createAllChatsFolder() {
        ChatFolder folder = new ChatFolder("all", "Todos", "💬");
        folder.setColor("#005f9e");
        folder.setDefault(true);
        folder.setDisplayOrder(0);
        return folder;
    }

    public static ChatFolder createUnreadFolder() {
        ChatFolder folder = new ChatFolder("unread", "No leídos", "🔔");
        folder.setColor("#ba1a1a");
        folder.setDisplayOrder(1);
        return folder;
    }

    public static ChatFolder createFavoritesFolder() {
        ChatFolder folder = new ChatFolder("favorites", "Favoritos", "⭐");
        folder.setColor("#894d00");
        folder.setDisplayOrder(2);
        return folder;
    }

    public static ChatFolder createWorkFolder() {
        ChatFolder folder = new ChatFolder("work", "Trabajo", "💼");
        folder.setColor("#1278c3");
        folder.setDisplayOrder(3);
        return folder;
    }

    public static ChatFolder createPersonalFolder() {
        ChatFolder folder = new ChatFolder("personal", "Personal", "👤");
        folder.setColor("#56624b");
        folder.setDisplayOrder(4);
        return folder;
    }

    public static ChatFolder createChannelsFolder() {
        ChatFolder folder = new ChatFolder("channels", "Canales", "📢");
        folder.setColor("#ad6200");
        folder.setDisplayOrder(5);
        return folder;
    }

    @Override
    public String toString() {
        return "ChatFolder{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", icon='" + icon + '\'' +
                ", chatCount=" + chatIds.size() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatFolder that = (ChatFolder) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
