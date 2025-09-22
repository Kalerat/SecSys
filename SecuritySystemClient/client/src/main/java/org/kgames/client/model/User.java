package org.kgames.client.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import java.time.LocalDateTime;

/**
 * User model representing a system user with RFID access.
 * Contains user information including security credentials and contact details.
 */
public class User {
    private final SimpleIntegerProperty userId;
    private final SimpleStringProperty name;
    private final SimpleStringProperty role;
    private final SimpleIntegerProperty securityLevel;
    private final SimpleStringProperty contactInfo;
    private final SimpleStringProperty rfidUid;
    private final SimpleObjectProperty<LocalDateTime> createdAt;

    public User(int userId, String name, String role, int securityLevel, String contactInfo, String rfidUid, LocalDateTime createdAt) {
        this.userId = new SimpleIntegerProperty(userId);
        this.name = new SimpleStringProperty(name);
        this.role = new SimpleStringProperty(role);
        this.securityLevel = new SimpleIntegerProperty(securityLevel);
        this.contactInfo = new SimpleStringProperty(contactInfo);
        this.rfidUid = new SimpleStringProperty(rfidUid);
        this.createdAt = new SimpleObjectProperty<>(createdAt);
    }

    // Getters
    public int getUserId() { return userId.get(); }
    public String getName() { return name.get(); }
    public String getRole() { return role.get(); }
    public int getSecurityLevel() { return securityLevel.get(); }
    public String getContactInfo() { return contactInfo.get(); }
    public String getRfidUid() { return rfidUid.get(); }
    public LocalDateTime getCreatedAt() { return createdAt.get(); }

    // Setters
    public void setName(String name) { this.name.set(name); }
    public void setRole(String role) { this.role.set(role); }
    public void setSecurityLevel(int level) { this.securityLevel.set(level); }
    public void setContactInfo(String info) { this.contactInfo.set(info); }
    public void setRfidUid(String uid) { this.rfidUid.set(uid); }
}

