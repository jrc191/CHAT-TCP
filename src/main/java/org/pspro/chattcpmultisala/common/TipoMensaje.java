package org.pspro.chattcpmultisala.common;

/**
 * Enumeración que define todos los tipos de mensajes
 * soportados en el protocolo del chat TCP.
 *
 * El protocolo es extensible y permite comunicación
 * cliente-servidor bidireccional.
 */
public enum TipoMensaje {
    // ========================================
    // GESTIÓN DE CONEXIÓN (Cliente → Servidor)
    // ========================================
    MENSAJE_GENERAL,
    MENSAJE_PRIVADO,
    LOGIN_ANON,         // petición de login anónimo
    LOGIN_REGISTER,     // petición de login con registro (usuario+contraseña)
    LOGIN_RESPONSE,     // respuesta del servidor al login (success/fail)
    REGISTER_RESPONSE,  // respuesta al registro (success/fail)
    ARCHIVE_CHAT,
}