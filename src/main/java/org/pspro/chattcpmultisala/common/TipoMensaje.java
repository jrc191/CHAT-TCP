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
    LOGIN_ANON,
    LOGIN_REGISTER,
    LOGIN_RESPONSE,
    REGISTER_RESPONSE,

    // ========================================
    // GESTIÓN DE CANALES
    // ========================================
    CREAR_CANAL,
    ADD_MIEMBROS_CANAL,
    MENSAJE_CANAL,

    // ========================================
    // SINCRONIZACIÓN DE ESTADO
    // ========================================
    LISTA_USUARIOS,

    ARCHIVE_CHAT,
}
