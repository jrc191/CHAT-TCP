package org.pspro.chattcpmultisala.cliente.controladores;

import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.pspro.chattcpmultisala.common.DatosMensaje;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Para la creación de componentes de la interfaz de usuario (Burbujas, menús, etc.)
 */
public class UIFactory {

    /**
     * Crea una burbuja de mensaje
     */
    public static StackPane crearBurbujaMensaje(DatosMensaje mensaje, boolean esPropio, String salaAsociada, 
                                              String horaFormateada, boolean esModerador,
                                              BiConsumer<DatosMensaje, String> onBorrar,
                                              BiConsumer<String, String> onModerar) {
        
        VBox burbuja = new VBox();
        burbuja.getStyleClass().addAll("message-bubble", esPropio ? "bubble-sent" : "bubble-received");

        // Nombre del remitente + rol (si aplica)
        if (!esPropio && (salaAsociada.equals("GENERAL") || salaAsociada.startsWith("Canal:"))) {
            String etiquetaRemitente = mensaje.getRemitente();
            if ("MODERATOR".equals(mensaje.getRolRemitente())) etiquetaRemitente += " 🛡️";
            Label nombreLbl = new Label(etiquetaRemitente);
            nombreLbl.getStyleClass().add("sender-name");
            
            if (esModerador) {
                nombreLbl.setStyle(nombreLbl.getStyle() + "; -fx-cursor: hand;");
                nombreLbl.setOnMouseClicked(e -> onModerar.accept(mensaje.getRemitente(), salaAsociada));
            }
            burbuja.getChildren().add(nombreLbl);
        }

        Label txt = new Label(mensaje.getContenido());
        txt.getStyleClass().add("message-text");
        txt.setWrapText(true);

        Label hora = new Label(horaFormateada);
        hora.getStyleClass().add("timestamp");

        burbuja.getChildren().addAll(txt, hora);

        return envolverEnStackConMenu(burbuja, mensaje, esPropio, salaAsociada, onBorrar, null);
    }

    /**
     * Crea una burbuja de archivo
     */
    public static StackPane crearBurbujaArchivo(DatosMensaje msg, boolean esPropio, String salaAsociada,
                                              String horaFormateada, boolean soyModeradorAqui,
                                              BiConsumer<DatosMensaje, String> onBorrar,
                                              Consumer<DatosMensaje> onDescargar) {
        
        VBox burbujaContenido = new VBox(2);
        burbujaContenido.getStyleClass().addAll("message-bubble", esPropio ? "bubble-sent" : "bubble-received");

        Label titulo = new Label("📎 " + msg.getNombreArchivo());
        titulo.getStyleClass().add("message-text");
        titulo.setStyle("-fx-font-weight:bold;");

        long kb = msg.getTamanoArchivo() / 1024;
        Label info = new Label((kb > 0 ? kb + " KB" : msg.getTamanoArchivo() + " B") + " · " + horaFormateada);
        info.getStyleClass().add("timestamp");

        VBox textos = new VBox(titulo, info);
        
        Button btnDescargar = new Button("⬇");
        btnDescargar.setStyle("-fx-cursor:hand;-fx-background-color:#e0e0e0;-fx-text-fill:#444;-fx-background-radius:15;-fx-font-size:14px;-fx-padding:4 8;");
        btnDescargar.setOnAction(e -> onDescargar.accept(msg));

        HBox layoutHorizontal = new HBox(15, textos, btnDescargar);
        layoutHorizontal.setAlignment(Pos.CENTER_LEFT);
        burbujaContenido.getChildren().add(layoutHorizontal);

        return envolverEnStackConMenu(burbujaContenido, msg, esPropio, salaAsociada, onBorrar, onDescargar);
    }

    private static StackPane envolverEnStackConMenu(Pane burbuja, DatosMensaje msg, boolean esPropio, String sala,
                                                  BiConsumer<DatosMensaje, String> onBorrar,
                                                  Consumer<DatosMensaje> onDescargar) {
        StackPane bubbleStack = new StackPane(burbuja);
        bubbleStack.setAlignment(Pos.TOP_RIGHT);

        Label menuTrigger = new Label("▼");
        menuTrigger.getStyleClass().add("timestamp");
        menuTrigger.setStyle("-fx-cursor: hand; -fx-padding: 2 5; -fx-font-size: 10px;");
        
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getStyleClass().add("context-menu");

        if (esPropio || (onBorrar != null && onDescargar == null && !esPropio)) { // Simplificación para moderadores
             MenuItem itemBorrar = new MenuItem(onDescargar != null ? "Borrar archivo" : "Borrar mensaje");
             itemBorrar.setOnAction(e -> onBorrar.accept(msg, sala));
             contextMenu.getItems().add(itemBorrar);
        }
        
        if (onDescargar != null) {
            MenuItem itemDescargar = new MenuItem("Descargar");
            itemDescargar.setOnAction(e -> onDescargar.accept(msg));
            contextMenu.getItems().add(itemDescargar);
        }

        if (!contextMenu.getItems().isEmpty()) {
            menuTrigger.setOnMouseClicked(e -> contextMenu.show(menuTrigger, Side.BOTTOM, 0, 0));
            bubbleStack.getChildren().add(menuTrigger);
        }

        return bubbleStack;
    }
}
