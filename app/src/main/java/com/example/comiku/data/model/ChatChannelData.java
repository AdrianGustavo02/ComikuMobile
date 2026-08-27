package com.example.comiku.data.model;

import com.google.firebase.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;

// Representa un canal de chat en Stream Chat
public class ChatChannelData {
    private String id;
    private String type; // "personal" o "group"
    private List<String> members;
    private String groupName;
    private String groupDescription;
    private String groupImageUrl;
    private String displayName;
    private String lastMessage;
    private Object lastMessageAt;
    private int unreadCount;
    private List<String> admins;
    private String createdBy;
    private Object createdAt;
    private Object updatedAt;
    private String estado;
    private Map<String, Object> metadata;

    public ChatChannelData() {
    }

    public ChatChannelData(String id, String type, List<String> members) {
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

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupDescription() {
        return groupDescription;
    }

    public void setGroupDescription(String groupDescription) {
        this.groupDescription = groupDescription;
    }

    public String getGroupImageUrl() {
        return groupImageUrl;
    }

    public void setGroupImageUrl(String groupImageUrl) {
        this.groupImageUrl = groupImageUrl;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public long getLastMessageAt() {
        return convertToMillis(lastMessageAt);
    }

    public void setLastMessageAt(Object lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public List<String> getAdmins() {
        return admins;
    }

    public void setAdmins(List<String> admins) {
        this.admins = admins;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getCreatedAt() {
        return convertToMillis(createdAt);
    }

    public void setCreatedAt(Object createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return convertToMillis(updatedAt);
    }

    public void setUpdatedAt(Object updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Retorna el ID del otro usuario en un chat 1:1
    public String getOtherUserId(String currentUserId) {
        if (members != null && members.size() == 2) {
            return members.get(0).equals(currentUserId) ? members.get(1) : members.get(0);
        }
        return null;
    }

    public boolean isPersonalChat() {
        return !isGroupChat() && members != null && members.size() == 2;
    }

    public boolean isGroupChat() {
        if ("group".equals(type)) {
            return true;
        }
        if (members != null && members.size() > 2) {
            return true;
        }
        return (groupName != null && !groupName.trim().isEmpty())
                || (groupImageUrl != null && !groupImageUrl.trim().isEmpty());
    }

    public boolean esAdmin(String usuarioId) {
        return admins != null && usuarioId != null && admins.contains(usuarioId);
    }

    public int cantidadMiembros() {
        return members == null ? 0 : members.size();
    }

    // Convertir a mapa para guardar en Firestore
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", id);
        map.put("type", type);
        map.put("members", members);
        map.put("groupName", groupName);
        map.put("groupDescription", groupDescription);
        map.put("groupImageUrl", groupImageUrl);
        map.put("lastMessage", lastMessage);
        map.put("lastMessageAt", lastMessageAt);
        map.put("unreadCount", unreadCount);
        map.put("admins", admins);
        map.put("createdBy", createdBy);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        map.put("estado", estado);
        map.put("metadata", metadata);
        return map;
    }

    // Convierte distintos formatos de fecha a milisegundos
    private long convertToMillis(Object valorFecha) {
        if (valorFecha == null) {
            return 0L;
        }
        if (valorFecha instanceof Long) {
            return (Long) valorFecha;
        }
        if (valorFecha instanceof Integer) {
            return ((Integer) valorFecha).longValue();
        }
        if (valorFecha instanceof Double) {
            return ((Double) valorFecha).longValue();
        }
        if (valorFecha instanceof Timestamp) {
            return ((Timestamp) valorFecha).toDate().getTime();
        }
        if (valorFecha instanceof Date) {
            return ((Date) valorFecha).getTime();
        }
        if (valorFecha instanceof String) {
            try {
                return Long.parseLong((String) valorFecha);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
