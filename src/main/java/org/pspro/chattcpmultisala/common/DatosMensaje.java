package org.pspro.chattcpmultisala.common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class DatosMensaje implements Serializable {
    private static final long serialVersionUID = 1L;

    private TipoMensaje tipo;
    private String remitente;
    private String destino; // "GENERAL" para chat general
    private String contenido;
    private LocalDateTime timestamp; // Hora de envío del mensaje

    // Canales
    private List<String> miembros;

    // Metadatos cliente/servidor
    private boolean archivado;
    private boolean favorito;
    private boolean leido;

    // Para respuestas de login/registro
    private boolean success;
    private String reason;

    // Para registro/login: campo password (solo para REGISTER/Login with credentials)
    private String password;

    public DatosMensaje() {}

    // getters y setters
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

    public List<String> getMiembros() { return miembros; }
    public void setMiembros(List<String> miembros) { this.miembros = miembros; }
}
