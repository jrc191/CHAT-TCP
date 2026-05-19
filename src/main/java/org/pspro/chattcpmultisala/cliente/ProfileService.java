package org.pspro.chattcpmultisala.cliente;

import javafx.application.Platform;
import javafx.scene.control.*;
import org.pspro.chattcpmultisala.common.ProfileManager;
import org.pspro.chattcpmultisala.common.UserProfile;
import org.pspro.chattcpmultisala.common.ValidadorEntrada;
import org.pspro.chattcpmultisala.cliente.controladores.ChatController;

import java.util.List;
import java.util.function.Consumer;

/**
 * Servicio para gestionar la lógica de edición y visualización del perfil.
 * Reduce la complejidad del ChatController.
 */
public class ProfileService {

    private final ChatController controller;
    private final ProfileManager profileManager;

    public ProfileService(ChatController controller, ProfileManager profileManager) {
        this.controller = controller;
        this.profileManager = profileManager;
    }

    public void iniciarEdicionPerfil(UserProfile profile, List<String> usuariosEnLinea, Consumer<UserProfile> onSuccess) {
        if (profile == null) return;

        List<String> opciones = List.of(
                "Nombre para mostrar",
                "Teléfono",
                "Correo electrónico",
                "Biografía"
        );

        ChoiceDialog<String> menu = new ChoiceDialog<>(opciones.get(0), opciones);
        menu.setTitle("Editar Perfil");
        menu.setHeaderText("¿Qué campo deseas modificar?");
        menu.setContentText("Selecciona una opción:");

        menu.showAndWait().ifPresent(seleccion -> {
            switch (seleccion) {
                case "Nombre para mostrar" -> editarNombre(profile, usuariosEnLinea, onSuccess);
                case "Teléfono"           -> editarTelefono(profile, onSuccess);
                case "Correo electrónico" -> editarEmail(profile, onSuccess);
                case "Biografía"          -> editarBio(profile, onSuccess);
            }
        });
    }

    private void editarNombre(UserProfile profile, List<String> usuariosEnLinea, Consumer<UserProfile> onSuccess) {
        TextInputDialog d = new TextInputDialog(profile.getDisplayName());
        d.setTitle("Editar Perfil");
        d.setHeaderText("Cambiar nombre para mostrar");
        
        d.showAndWait().ifPresent(nuevo -> {
            if (ValidadorEntrada.contieneURL(nuevo)) {
                mostrarAlertaError("El nombre no puede contener enlaces.");
                return;
            }
            String valor = ValidadorEntrada.sanitizarCampoPerfil(nuevo);
            if (valor == null || valor.isEmpty()) {
                mostrarAlertaError("El nombre no puede estar vacío.");
                return;
            }
            boolean existe = usuariosEnLinea.stream().anyMatch(u -> u.equalsIgnoreCase(valor));
            if (existe && !valor.equalsIgnoreCase(profile.getUsername())) {
                mostrarAlertaError("Ya existe un usuario con ese identificador.");
            } else {
                profile.setDisplayName(valor);
                onSuccess.accept(profile);
            }
        });
    }

    private void editarTelefono(UserProfile profile, Consumer<UserProfile> onSuccess) {
        TextInputDialog d = new TextInputDialog(nvl(profile.getPhoneNumber(), ""));
        d.setTitle("Editar Perfil"); d.setHeaderText("Cambiar teléfono");
        d.showAndWait().ifPresent(v -> {
            String valor = ValidadorEntrada.eliminarCaracteresControl(v.trim());
            ValidadorEntrada.ResultadoValidacion rv = ValidadorEntrada.validarTelefono(valor);
            if (!rv.valido()) { mostrarAlertaError(rv.mensajeError()); return; }

            if (!valor.isEmpty()) {
                boolean duplicado = profileManager.getAllProfiles().stream()
                        .anyMatch(p -> !p.getUsername().equals(profile.getUsername()) && valor.equalsIgnoreCase(p.getPhoneNumber()));
                if (duplicado) { mostrarAlertaError("Teléfono ya registrado."); return; }
            }
            profile.setPhoneNumber(valor);
            onSuccess.accept(profile);
        });
    }

    private void editarEmail(UserProfile profile, Consumer<UserProfile> onSuccess) {
        TextInputDialog d = new TextInputDialog(nvl(profile.getEmail(), ""));
        d.setTitle("Editar Perfil"); d.setHeaderText("Cambiar correo");
        d.showAndWait().ifPresent(v -> {
            String valor = ValidadorEntrada.eliminarCaracteresControl(v.trim());
            ValidadorEntrada.ResultadoValidacion rv = ValidadorEntrada.validarEmail(valor);
            if (!rv.valido()) { mostrarAlertaError(rv.mensajeError()); return; }

            if (!valor.isEmpty()) {
                boolean duplicado = profileManager.getAllProfiles().stream()
                        .anyMatch(p -> !p.getUsername().equals(profile.getUsername()) && valor.equalsIgnoreCase(p.getEmail()));
                if (duplicado) { mostrarAlertaError("Correo ya registrado."); return; }
            }
            profile.setEmail(valor);
            onSuccess.accept(profile);
        });
    }

    private void editarBio(UserProfile profile, Consumer<UserProfile> onSuccess) {
        TextInputDialog d = new TextInputDialog(nvl(profile.getBio(), ""));
        d.setTitle("Editar Perfil"); d.setHeaderText("Cambiar biografía");
        d.showAndWait().ifPresent(v -> {
            if (ValidadorEntrada.contieneURL(v)) {
                mostrarAlertaError("La biografía no puede contener enlaces.");
                return;
            }
            profile.setBio(ValidadorEntrada.sanitizarCampoPerfil(v));
            onSuccess.accept(profile);
        });
    }

    private void mostrarAlertaError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Error"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
        });
    }

    private String nvl(String v, String def) {
        return (v != null && !v.isBlank()) ? v : def;
    }
}
