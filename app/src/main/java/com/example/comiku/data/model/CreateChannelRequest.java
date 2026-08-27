package com.example.comiku.data.model;

import java.util.List;
import java.util.Map;

// Modelo para solicitar la creación de un canal de chat
public class CreateChannelRequest {
    private String type;
    private List<String> members;
    private String groupName;
    private String groupDescription;
    private String groupImageUrl;
    private Map<String, Object> metadata;

    public CreateChannelRequest() {
        this.type = "messaging";
    }

    public CreateChannelRequest(List<String> members) {
        this.type = "messaging";
        this.members = members;
    }

    // Getters y Setters
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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public boolean isValid() {
        return members != null && members.size() > 0 &&
               (members.size() == 2 || (groupName != null && !groupName.trim().isEmpty()));
    }
}
