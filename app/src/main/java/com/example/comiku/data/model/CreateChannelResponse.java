package com.example.comiku.data.model;

import java.util.List;

// Modelo para la respuesta de creación de canal
public class CreateChannelResponse {
    private boolean ok;
    private ChannelInfo channel;
    private String message;

    public CreateChannelResponse() {
    }

    // Clase interna para representar la información del canal
    public static class ChannelInfo {
        private String id;
        private String type;
        private List<String> members;

        public ChannelInfo() {
        }

        public ChannelInfo(String id, String type, List<String> members) {
            this.id = id;
            this.type = type;
            this.members = members;
        }

        // Getters y Setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getMembers() {
            return members;
        }

        public void setMembers(List<String> members) {
            this.members = members;
        }
    }

    // Getters y Setters
    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public ChannelInfo getChannel() {
        return channel;
    }

    public void setChannel(ChannelInfo channel) {
        this.channel = channel;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
