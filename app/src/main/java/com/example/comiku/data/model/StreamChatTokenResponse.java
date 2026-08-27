package com.example.comiku.data.model;

// Representa la respuesta del token de StreamChat desde el backend
public class StreamChatTokenResponse {
    private boolean ok;
    private String token;
    private String apiKey;
    private String message;

    public StreamChatTokenResponse() {
    }

    public StreamChatTokenResponse(boolean ok, String token, String apiKey) {
        this.ok = ok;
        this.token = token;
        this.apiKey = apiKey;
    }

    // Getters y Setters
    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
