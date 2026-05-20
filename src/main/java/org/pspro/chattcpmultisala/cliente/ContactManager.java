package org.pspro.chattcpmultisala.cliente;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.pspro.chattcpmultisala.cliente.controladores.ChatController;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Gestor para la lista de contactos y miembros.
 * Separa la lógica de dibujo de la UI del controlador principal.
 */
public class ContactManager {

    private final ChatController controller;

    public ContactManager(ChatController controller) {
        this.controller = controller;
    }

    public void dibujarContactos(VBox contactList, Set<String> chatsActivos, Set<String> canalesActivos,
                                Set<String> nuevosMensajes, String actual, String filtro, String currentFolder,
                                List<String> chatsEnFolder, BiConsumer<String, EventHandler<ActionEvent>> addAccion,
                                Consumer<String> onSelect) {
        
        Platform.runLater(() -> {
            contactList.getChildren().clear();
            
            // Botones de acción base (delegados al controlador)
            addAccion.accept("➕ Nuevo Chat", null);
            addAccion.accept("📢 Nuevo Canal", null);
            addAccion.accept("📎 Enviar archivo", null);
            
            if (controller.esModerador()) {
                addAccion.accept("🛡️ Moderación", null);
            }

            Label sep = new Label("CONVERSACIONES");
            sep.setStyle("-fx-text-fill: #999; -fx-padding: 15 15 5 15; -fx-font-size: 11px; -fx-font-weight: bold;");
            contactList.getChildren().add(sep);

            String query = filtro != null ? filtro.toLowerCase().trim() : "";

            for (String chat : chatsActivos) {
                if (!query.isEmpty() && !chat.toLowerCase().contains(query)) continue;

                boolean visible = "all".equals(currentFolder)
                        || ("unread".equals(currentFolder) && nuevosMensajes.contains(chat))
                        || chatsEnFolder.contains(chat);
                if (!visible) continue;

                String etiqueta = switch (chat) {
                    case "GENERAL" -> "💬 Sala General";
                    default -> canalesActivos.contains(chat) ? "📢 " + chat : "👤 " + chat;
                };
                if (nuevosMensajes.contains(chat) && !chat.equals(actual))
                    etiqueta += " 🔔";

                Button btn = crearBoton(chat, etiqueta, chat.equals(actual), onSelect);
                contactList.getChildren().add(btn);
            }
        });
    }

    public void dibujarMiembros(VBox listMiembros, List<String> miembros, List<String> online, 
                               String miNick, boolean soyMod, String salaId,
                               BiConsumer<String, String> onModerar) {
        Platform.runLater(() -> {
            listMiembros.getChildren().clear();
            if (miembros == null) return;

            for (String m : miembros) {
                boolean isOnline = online.contains(m);
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                
                Label dot = new Label("●");
                dot.setStyle("-fx-text-fill: " + (isOnline ? "#2ecc71" : "#95a5a6") + "; -fx-font-size: 14;");
                
                Label lblM = new Label(m + (m.equals(miNick) ? " (Tú)" : ""));
                lblM.setStyle("-fx-text-fill: #1a1c1c; -fx-font-size: 13;");

                if (soyMod && !m.equals(miNick)) {
                    lblM.setStyle(lblM.getStyle() + "; -fx-cursor: hand; -fx-underline: true;");
                    lblM.setOnMouseClicked(e -> onModerar.accept(m, salaId));
                }

                row.getChildren().addAll(dot, lblM);
                listMiembros.getChildren().add(row);
            }
        });
    }

    private Button crearBoton(String id, String texto, boolean active, Consumer<String> onSelect) {
        Button b = new Button(texto);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("contact-button");
        if (active) b.getStyleClass().add("contact-button-active");
        b.setOnAction(e -> onSelect.accept(id));
        return b;
    }
}
