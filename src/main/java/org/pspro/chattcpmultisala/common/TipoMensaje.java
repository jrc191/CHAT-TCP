package org.pspro.chattcpmultisala.common;

/**
 * Enumeración que define todos los tipos de mensajes
 * soportados en el protocolo del chat TCP.
 */
public enum TipoMensaje {
    // ========================================
    // MENSAJES DE CHAT
    // ========================================
    MENSAJE_GENERAL,
    MENSAJE_PRIVADO,

    // ========================================
    // GESTIÓN DE CONEXIÓN (Cliente → Servidor)
    // ========================================
    LOGIN_ANON,          // mantenido por retrocompatibilidad (no usado)
    LOGIN_REGISTER,      // login con nick + password
    REGISTER_REQUEST,    // petición de registro de nuevo usuario
    LOGIN_RESPONSE,
    REGISTER_RESPONSE,

    // ========================================
    // GESTIÓN DE CANALES
    // ========================================
    CREAR_CANAL,
    ADD_MIEMBROS_CANAL,
    MENSAJE_CANAL,

    // ========================================
    // MODERACIÓN
    // ========================================
    BANEAR_USUARIO,       // Moderador expulsa usuario de un canal
    SUSPENDER_CANAL,      // Moderador suspende el canal
    PROMOVER_TEMPORAL,    // Moderador da permisos temporales a un usuario
    REVOCAR_PROMOCION,    // El tiempo expiró; se revocan permisos temporales

    // ========================================
    // ENVÍO DE ARCHIVOS
    // ========================================
    ENVIAR_ARCHIVO,       // Envío de fichero (privado o canal)
    ELIMINAR_ARCHIVO,     // El moderador o el emisor elimina un fichero del tablón
    BORRAR_MENSAJE,       // El emisor borra su propio mensaje (privado)

    // ========================================
    // SINCRONIZACIÓN DE ESTADO
    // ========================================
    LISTA_USUARIOS,

    ARCHIVE_CHAT,

    // ========================================
    // PETICIÓN DE PERFIL (Moderación)
    // ========================================
    REQUEST_PROFILE,
    PROFILE_RESPONSE,
}
