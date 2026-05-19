package org.pspro.chattcpmultisala.common;

import java.util.regex.Pattern;

/**
 * Validador y saneador de entradas de usuario.
 *
 * Cubre:
 *  1. Formato correcto del nickname (empieza por letra, sin espacios ni especiales)
 *  2. Filtrado/neutralización de URLs para evitar spam o código malicioso
 *  3. Sanitización general de contenido de mensajes
 */
public class ValidadorEntrada {

    // ── Patrones ──────────────────────────────────────────────────────────────

    /** Detecta caracteres especiales prohibidos (basado en el enunciado oficial). */
    private static final Pattern CARACTERES_ESPECIALES =
            Pattern.compile("[\"#$%&€/()=?¿!¡,;:+ \\-]");

    /** Nickname: empieza con letra, solo letras/dígitos. 
     *  Separamos la validación de formato de la de caracteres prohibidos. */
    private static final Pattern PATRON_NICKNAME =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*$");

    /** Detecta URLs (http/https/ftp y bare domains comunes). */
    private static final Pattern PATRON_URL =
            Pattern.compile(
                "(?i)(https?://|ftp://|www\\.)[^\\s]+" +
                "|[a-zA-Z0-9\\-]+\\.(com|es|org|net|io|edu|gov|info|xyz|gg|tv)(/[^\\s]*)?",
                Pattern.CASE_INSENSITIVE
            );

    /** Inyección de comandos o caracteres de control en mensajes. */
    private static final Pattern CARACTERES_CONTROL =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    /** Teléfono: dígitos, +, -, espacios y paréntesis. Entre 6 y 20 caracteres. */
    private static final Pattern PATRON_TELEFONO =
            Pattern.compile("^[+\\d][\\d\\s()\\-]{5,19}$");

    /** Email: formato básico usuario@dominio.tld */
    private static final Pattern PATRON_EMAIL =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Resultado de una validación con mensaje de error opcional.
     */
    public record ResultadoValidacion(boolean valido, String mensajeError) {
        public static ResultadoValidacion ok() { return new ResultadoValidacion(true, null); }
        public static ResultadoValidacion error(String msg) { return new ResultadoValidacion(false, msg); }
    }

    /**
     * Valida el formato del nickname:
     *  - No vacío
     *  - Empieza con letra
     *  - Sin espacios ni caracteres especiales
     *  - Longitud 3-20
     */
    public static ResultadoValidacion validarNickname(String nickname) {
        if (nickname == null || nickname.isBlank())
            return ResultadoValidacion.error("El nickname no puede estar vacío.");

        if (nickname.length() < 3 || nickname.length() > 20)
            return ResultadoValidacion.error("El nickname debe tener entre 3 y 20 caracteres.");

        if (CARACTERES_ESPECIALES.matcher(nickname).find())
            return ResultadoValidacion.error(
                    "El nickname no puede contener espacios ni caracteres especiales (#$%&€/()=?¿!¡,;:+=-).");

        if (!PATRON_NICKNAME.matcher(nickname).matches())
            return ResultadoValidacion.error(
                    "El nickname debe empezar por una letra y contener solo letras y dígitos.");

        // Extra: no permitir URLs dentro del nickname
        if (PATRON_URL.matcher(nickname).find())
            return ResultadoValidacion.error("El nickname no puede contener URLs.");

        return ResultadoValidacion.ok();
    }

    /**
     * Valida la contraseña:
     *  - Longitud mínima 6 caracteres
     */
    public static ResultadoValidacion validarPassword(String password) {
        if (password == null || password.length() < 6)
            return ResultadoValidacion.error("La contraseña debe tener al menos 6 caracteres.");
        return ResultadoValidacion.ok();
    }

    /**
     * Filtra/elimina URLs dentro del contenido de un mensaje para que no
     * sean clicables ni propaguen spam.
     * Las URLs se eliminan completamente.
     */
    public static String filtrarURLs(String contenido) {
        if (contenido == null) return null;
        return PATRON_URL.matcher(contenido).replaceAll("");
    }

    /**
     * Elimina caracteres de control del mensaje para evitar inyección
     * de comandos o corrupción del protocolo.
     */
    public static String eliminarCaracteresControl(String contenido) {
        if (contenido == null) return null;
        return CARACTERES_CONTROL.matcher(contenido).replaceAll("");
    }

    /**
     * Sanitiza completamente el contenido de un mensaje:
     *  1. Elimina caracteres de control
     *  2. Filtra URLs (las elimina)
     *  3. Trunca si excede 4000 caracteres
     */
    public static String sanitizarMensaje(String contenido) {
        if (contenido == null) return null;
        contenido = eliminarCaracteresControl(contenido);
        contenido = filtrarURLs(contenido);
        if (contenido.length() > 4000) contenido = contenido.substring(0, 4000) + "…";
        return contenido.trim();
    }

    /**
     * Comprueba si un texto contiene una URL (para mostrar advertencia en la UI).
     */
    public static boolean contieneURL(String texto) {
        if (texto == null) return false;
        return PATRON_URL.matcher(texto).find();
    }

    /**
     * Valida el formato de un número de teléfono (campo opcional).
     * Permite vacío; solo valida si hay contenido.
     * No permite URLs.
     */
    public static ResultadoValidacion validarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) return ResultadoValidacion.ok();
        
        if (contieneURL(telefono))
            return ResultadoValidacion.error("El teléfono no puede contener enlaces.");

        if (!PATRON_TELEFONO.matcher(telefono).matches())
            return ResultadoValidacion.error("Teléfono inválido. Solo dígitos, +, -, espacios y paréntesis (6-20 caracteres).");
        
        return ResultadoValidacion.ok();
    }

    /**
     * Valida el formato de un correo electrónico (campo opcional).
     * Permite vacío; solo valida si hay contenido.
     * El patrón de email ya es suficientemente restrictivo para no permitir enlaces.
     */
    public static ResultadoValidacion validarEmail(String email) {
        if (email == null || email.isBlank()) return ResultadoValidacion.ok();

        if (!PATRON_EMAIL.matcher(email).matches())
            return ResultadoValidacion.error("Formato de correo electrónico inválido.");
        
        return ResultadoValidacion.ok();
    }

    /**
     * Sanitiza un campo de perfil de texto libre (nombre, biografía…):
     *  1. Elimina caracteres de control
     *  2. Filtra URLs (las elimina)
     *  3. Trunca a 200 caracteres
     */
    public static String sanitizarCampoPerfil(String valor) {
        if (valor == null) return null;
        valor = eliminarCaracteresControl(valor);
        valor = filtrarURLs(valor);
        if (valor.length() > 200) valor = valor.substring(0, 200);
        return valor.trim();
    }
}
