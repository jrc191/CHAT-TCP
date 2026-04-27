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
    LOGIN_ANON,         // petición de login anónimo
    LOGIN_REGISTER,     // petición de login con registro (usuario+contraseña)
    LOGIN_RESPONSE,     // respuesta del servidor al login (success/fail)
    REGISTER_RESPONSE,  // respuesta al registro (success/fail)

    // ========================================
    // SINCRONIZACIÓN DE ESTADO
    // ========================================
    LISTA_USUARIOS,     // servidor → clientes: lista actualizada de conectados

    ARCHIVE_CHAT,
}
