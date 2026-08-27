package com.example.comiku.data.model;

public class ReportGroupData {
    private String reporteId;
    private String reportadorUid;
    private String grupoId;
    private String grupoNombre;
    private String motivo;
    private String descripcion;
    private String imagenProuebaUrl;
    private long fechaReporte;
    private String estado;

    public ReportGroupData() {
    }

    public ReportGroupData(
            String reporteId,
            String reportadorUid,
            String grupoId,
            String grupoNombre,
            String motivo,
            String descripcion,
            String imagenProuebaUrl,
            long fechaReporte,
            String estado
    ) {
        this.reporteId = reporteId;
        this.reportadorUid = reportadorUid;
        this.grupoId = grupoId;
        this.grupoNombre = grupoNombre;
        this.motivo = motivo;
        this.descripcion = descripcion;
        this.imagenProuebaUrl = imagenProuebaUrl;
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

    public String getGrupoId() {
        return grupoId;
    }

    public void setGrupoId(String grupoId) {
        this.grupoId = grupoId;
    }

    public String getGrupoNombre() {
        return grupoNombre;
    }

    public void setGrupoNombre(String grupoNombre) {
        this.grupoNombre = grupoNombre;
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

    public String getImagenProuebaUrl() {
        return imagenProuebaUrl;
    }

    public void setImagenProuebaUrl(String imagenProuebaUrl) {
        this.imagenProuebaUrl = imagenProuebaUrl;
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
