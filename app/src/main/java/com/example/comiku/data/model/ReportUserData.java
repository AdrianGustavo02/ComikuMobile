package com.example.comiku.data.model;

public class ReportUserData {
    private String reporteId;
    private String reportadorUid;
    private String usuarioReportadoUid;
    private String usuarioReportadoNick;
    private String motivo;
    private String descripcion;
    private long fechaReporte;
    private String estado;

    public ReportUserData() {
    }

    public ReportUserData(
            String reporteId,
            String reportadorUid,
            String usuarioReportadoUid,
            String usuarioReportadoNick,
            String motivo,
            String descripcion,
            long fechaReporte,
            String estado
    ) {
        this.reporteId = reporteId;
        this.reportadorUid = reportadorUid;
        this.usuarioReportadoUid = usuarioReportadoUid;
        this.usuarioReportadoNick = usuarioReportadoNick;
        this.motivo = motivo;
        this.descripcion = descripcion;
        this.fechaReporte = fechaReporte;
        this.estado = estado;
    }

    public String getReporteId() {
        return reporteId;
    }

    public void setReporteId(String reporteId) {
        this.reporteId = reporteId;
    }

    public String getReportadorUid() {
        return reportadorUid;
    }

    public void setReportadorUid(String reportadorUid) {
        this.reportadorUid = reportadorUid;
    }

    public String getUsuarioReportadoUid() {
        return usuarioReportadoUid;
    }

    public void setUsuarioReportadoUid(String usuarioReportadoUid) {
        this.usuarioReportadoUid = usuarioReportadoUid;
    }

    public String getUsuarioReportadoNick() {
        return usuarioReportadoNick;
    }

    public void setUsuarioReportadoNick(String usuarioReportadoNick) {
        this.usuarioReportadoNick = usuarioReportadoNick;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public long getFechaReporte() {
        return fechaReporte;
    }

    public void setFechaReporte(long fechaReporte) {
        this.fechaReporte = fechaReporte;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
