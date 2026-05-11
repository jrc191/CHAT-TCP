package org.pspro.chattcpmultisala.common;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Utilidad para cifrar y descifrar mensajes y archivos con AES-256-GCM.
 *
 * AES-256-GCM proporciona:
 *  - Confidencialidad (cifrado simétrico)
 *  - Integridad y autenticidad (GCM tag de 128 bits)
 *
 * La clave compartida se deriva de una passphrase común a cliente y servidor
 * mediante PBKDF2WithHmacSHA256.
 *
 * NOTA: Para un sistema real se usaría intercambio de claves asimétrico (RSA/ECDH).
 *       Aquí empleamos una clave precompartida para mantener la simplicidad del proyecto.
 */
public class CifradoMensajes {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final String ALGORITMO       = "AES/GCM/NoPadding";
    private static final int    KEY_LENGTH_BITS = 256;
    private static final int    GCM_IV_LENGTH   = 12;   // bytes
    private static final int    GCM_TAG_BITS    = 128;
    private static final int    PBKDF2_ITERS    = 65_536;

    /** Passphrase/secreto compartido. En producción vendría de un fichero de configuración seguro. */
    private static final String PASSPHRASE = "ChatTCP-PSPro-2026-SecureKey!";
    private static final byte[] SALT_FIJO  = "PSPro2026Salt!XY".getBytes();  // 16 bytes

    // ── Clave derivada (inicializada una vez) ─────────────────────────────────

    private static final SecretKey CLAVE_AES;

    static {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(
                    PASSPHRASE.toCharArray(), SALT_FIJO, PBKDF2_ITERS, KEY_LENGTH_BITS);
            SecretKey tmp = factory.generateSecret(spec);
            CLAVE_AES = new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            throw new RuntimeException("Error inicializando clave AES-256", e);
        }
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Cifra un texto plano con AES-256-GCM.
     * @return IV (12 bytes) + ciphertext + GCM tag, codificado en Base64.
     */
    public static String cifrar(String textPlano) {
        if (textPlano == null) return null;
        try {
            byte[] iv = generarIV();
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, CLAVE_AES, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(textPlano.getBytes("UTF-8"));

            // Concatenar IV + ciphertext
            byte[] resultado = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(ciphertext, 0, resultado, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(resultado);
        } catch (Exception e) {
            throw new RuntimeException("Error cifrando mensaje", e);
        }
    }

    /**
     * Descifra un texto cifrado con AES-256-GCM.
     * @param cifrado Base64 de IV + ciphertext + GCM tag.
     * @return texto plano original, o null si falla la autenticación.
     */
    public static String descifrar(String cifrado) {
        if (cifrado == null) return null;
        try {
            byte[] datos = Base64.getDecoder().decode(cifrado);
            byte[] iv         = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[datos.length - GCM_IV_LENGTH];
            System.arraycopy(datos, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(datos, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, CLAVE_AES, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] textPlano = cipher.doFinal(ciphertext);
            return new String(textPlano, "UTF-8");
        } catch (AEADBadTagException e) {
            System.err.println("[Cifrado] Tag GCM inválido — mensaje manipulado o clave incorrecta.");
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Error descifrando mensaje", e);
        }
    }

    /**
     * Cifra un array de bytes (para archivos binarios).
     * @return IV + datos cifrados como Base64.
     */
    public static String cifrarBytes(byte[] datos) {
        if (datos == null) return null;
        try {
            byte[] iv = generarIV();
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, CLAVE_AES, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(datos);

            byte[] resultado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(cifrado, 0, resultado, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(resultado);
        } catch (Exception e) {
            throw new RuntimeException("Error cifrando bytes", e);
        }
    }

    /**
     * Descifra un array de bytes (para archivos binarios).
     */
    public static byte[] descifrarBytes(String cifradoBase64) {
        if (cifradoBase64 == null) return null;
        try {
            byte[] datos      = Base64.getDecoder().decode(cifradoBase64);
            byte[] iv         = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[datos.length - GCM_IV_LENGTH];
            System.arraycopy(datos, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(datos, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, CLAVE_AES, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Error descifrando bytes", e);
        }
    }

    // ── Privado ───────────────────────────────────────────────────────────────

    private static byte[] generarIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
