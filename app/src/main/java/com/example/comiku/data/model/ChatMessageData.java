package com.example.comiku.data.model;

import java.util.Date;

// Representa un mensaje en Stream Chat
public class ChatMessageData {
    private String id;
    private String channelId;
    private String userId;
    private String text;
    private Date createdAt;
    private Date updatedAt;
    private String status; // "sending", "sent", "delivered", "read", "error"
    private int readBy;
    private boolean isOwn;
    private UserProfileData user;
    private String attachmentType;
    private String attachmentUrl;
    private String attachmentFileName;
    private String streamMessageId;

    public ChatMessageData() {
    }

    public ChatMessageData(String id, String channelId, String userId, String text) {
        this.id = id;
        this.channelId = channelId;
        this.userId = userId;
        this.text = text;
        this.createdAt = new Date();
        this.status = "sending";
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getReadBy() {
        return readBy;
    }

    public void setReadBy(int readBy) {
        this.readBy = readBy;
    }

    public boolean isOwn() {
        return isOwn;
    }

    public void setOwn(boolean own) {
        isOwn = own;
    }

    public UserProfileData getUser() {
        return user;
    }

    public void setUser(UserProfileData user) {
        this.user = user;
    }

    public String getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(String attachmentType) {
        this.attachmentType = attachmentType;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public String getAttachmentFileName() {
        return attachmentFileName;
    }

    public void setAttachmentFileName(String attachmentFileName) {
        this.attachmentFileName = attachmentFileName;
    }

    public String getStreamMessageId() {
        return streamMessageId;
    }

    public void setStreamMessageId(String streamMessageId) {
        this.streamMessageId = streamMessageId;
    }

    // Indica si el mensaje tiene un adjunto.
    public boolean hasAttachment() {
        return attachmentType != null && !attachmentType.trim().isEmpty();
    }

    // Validar si el mensaje es válido
    public boolean isValid() {
        boolean tieneTexto = text != null && !text.trim().isEmpty();
        if (!tieneTexto && !hasAttachment()) {
            return false;
        }
        if (tieneTexto && text.length() > 5000) {
            return false;
        }
        // No permitir caracteres prohibidos
        return !tieneTexto || !text.matches(".*[@#$^&*{}\\[\\]<>].*");
    }

    public boolean isSent() {
        return "sent".equals(status) || "delivered".equals(status) || "read".equals(status);
    }

    public boolean isDelivered() {
        return "delivered".equals(status) || "read".equals(status);
    }

    public boolean isRead() {
        return "read".equals(status);
    }

    public boolean hasError() {
        return "error".equals(status);
    }

    // Convertir a mapa para guardar en Firestore
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", id);
        map.put("channelId", channelId);
        map.put("userId", userId);
        map.put("text", text);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        map.put("status", status);
        map.put("readBy", readBy);
        map.put("attachmentType", attachmentType);
        map.put("attachmentUrl", attachmentUrl);
        map.put("attachmentFileName", attachmentFileName);
        map.put("streamMessageId", streamMessageId);
        return map;
    }
}
