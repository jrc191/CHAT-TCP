package org.pspro.chattcpmultisala.common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO serializable para toda la comunicación cliente ↔ servidor.
 * Incluye campos para cifrado, transferencia de archivos y moderación.
 */
public class DatosMensaje implements Serializable {
    private static final long serialVersionUID = 2L;   // Incrementado por nuevos campos

    // ── Campos base ───────────────────────────────────────────────────────────
    private TipoMensaje tipo;
    private String remitente;
    private String destino;         // "GENERAL", nickname o nombre de canal
    private String contenido;       // Texto del mensaje (cifrado en privados/canal)
    private LocalDateTime timestamp;

    // ── Canales / Moderación ─────────────────────────────────────────────────
    private List<String> miembros;
    /** ID único del mensaje (UUID), para poder borrarlo después. */
    private String mensajeId;
    /** Rol del remitente tal como lo confirma el servidor. */
    private String rolRemitente;    // "USER" o "MODERATOR"
    /** Segundos de promoción temporal (campo PROMOVER_TEMPORAL). */
    private long segundosPromocion;

    // ── Transferencia de archivos ─────────────────────────────────────────────
    /** Nombre del archivo original. */
    private String nombreArchivo;
    /** Contenido del archivo cifrado en Base64 (AES-256-GCM). */
    private String datosArchivoCifrado;
    /** Tamaño original del archivo en bytes. */
    private long tamanoArchivo;
    /** MIME type del archivo. */
    private String tipoArchivo;
    /** ID del archivo en el tablón del canal, para poder eliminarlo. */
    private String archivoId;

    // ── Cifrado de mensajes ───────────────────────────────────────────────────
    /**
     * Indica si el campo {@code contenido} está cifrado con AES-256-GCM.
     * El receptor debe llamar a {@link CifradoMensajes#descifrar(String)} antes de mostrarlo.
     */
    private boolean cifrado;

    // ── Metadatos cliente ─────────────────────────────────────────────────────
    private boolean archivado;
    private boolean favorito;
    private boolean leido;

    // ── Respuestas servidor ───────────────────────────────────────────────────
    private boolean success;
    private String  reason;
    private String  password;   // Solo en LOGIN_REGISTER; NUNCA se reenvía
    private UserProfile userProfile;
    private java.util.Map<String, Object> contextData;

    // ── Constructores ─────────────────────────────────────────────────────────

    public DatosMensaje() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public java.util.Map<String, Object> getContextData() { return contextData; }
    public void setContextData(java.util.Map<String, Object> contextData) { this.contextData = contextData; }

    public TipoMensaje getTipo() { return tipo; }
    public void setTipo(TipoMensaje tipo) { this.tipo = tipo; }

    public String getRemitente() { return remitente; }
    public void setRemitente(String remitente) { this.remitente = remitente; }

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public List<String> getMiembros() { return miembros; }
    public void setMiembros(List<String> miembros) { this.miembros = miembros; }

    public String getMensajeId() { return mensajeId; }
    public void setMensajeId(String mensajeId) { this.mensajeId = mensajeId; }

    public String getRolRemitente() { return rolRemitente; }
    public void setRolRemitente(String rolRemitente) { this.rolRemitente = rolRemitente; }

    public long getSegundosPromocion() { return segundosPromocion; }
    public void setSegundosPromocion(long segundosPromocion) { this.segundosPromocion = segundosPromocion; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getDatosArchivoCifrado() { return datosArchivoCifrado; }
    public void setDatosArchivoCifrado(String datosArchivoCifrado) { this.datosArchivoCifrado = datosArchivoCifrado; }

    public long getTamanoArchivo() { return tamanoArchivo; }
    public void setTamanoArchivo(long tamanoArchivo) { this.tamanoArchivo = tamanoArchivo; }

    public String getTipoArchivo() { return tipoArchivo; }
    public void setTipoArchivo(String tipoArchivo) { this.tipoArchivo = tipoArchivo; }

    public String getArchivoId() { return archivoId; }
    public void setArchivoId(String archivoId) { this.archivoId = archivoId; }

    public boolean isCifrado() { return cifrado; }
    public void setCifrado(boolean cifrado) { this.cifrado = cifrado; }

    public boolean isArchivado() { return archivado; }
    public void setArchivado(boolean archivado) { this.archivado = archivado; }

    public boolean isFavorito() { return favorito; }
    public void setFavorito(boolean favorito) { this.favorito = favorito; }

    public boolean isLeido() { return leido; }
    public void setLeido(boolean leido) { this.leido = leido; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public UserProfile getUserProfile() { return userProfile; }
    public void setUserProfile(UserProfile userProfile) { this.userProfile = userProfile; }
}
