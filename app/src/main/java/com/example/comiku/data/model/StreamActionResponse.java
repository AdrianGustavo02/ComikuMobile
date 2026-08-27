package com.example.comiku.data.model;

// Respuesta de acciones del backend Stream
public class StreamActionResponse {
    private boolean ok;
    private String message;

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
