package org.kgames.client.model;

import java.time.LocalDateTime;

/**
 * Model class representing an access log entry in the security system.
 * Contains information about access attempts including user, device, and result.
 */
public class AccessLog {
    private int logId;
    private LocalDateTime timestamp;
    private Integer userId;
    private String userName;
    private Integer cardId;
    private String cardUid;
    private Integer deviceId;
    private String deviceLocation;
    private AccessResult accessResult;
    private String reason;

    /**
     * Enum representing the possible access results.
     */
    public enum AccessResult {
        GRANTED("Granted"),
        DENIED("Denied");

        private final String displayName;

        AccessResult(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Default constructor for AccessLog.
     */
    public AccessLog() {}

    /**
     * Constructor for AccessLog with all fields.
     *
     * @param logId The unique log identifier
     * @param timestamp The timestamp of the access attempt
     * @param userId The user ID (can be null for unknown users)
     * @param userName The user name for display
     * @param cardId The RFID card ID (can be null)
     * @param cardUid The RFID card UID for display
     * @param deviceId The device ID (can be null)
     * @param deviceLocation The device location for display
     * @param accessResult The result of the access attempt
     * @param reason The reason for the access result
     */
    public AccessLog(int logId, LocalDateTime timestamp, Integer userId, String userName,
                    Integer cardId, String cardUid, Integer deviceId, String deviceLocation,
                    AccessResult accessResult, String reason) {
        this.logId = logId;
        this.timestamp = timestamp;
        this.userId = userId;
        this.userName = userName;
        this.cardId = cardId;
        this.cardUid = cardUid;
        this.deviceId = deviceId;
        this.deviceLocation = deviceLocation;
        this.accessResult = accessResult;
        this.reason = reason;
    }

    // Getters and Setters
    public int getLogId() {
        return logId;
    }

    public void setLogId(int logId) {
        this.logId = logId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName != null ? userName : "Unknown";
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Integer getCardId() {
        return cardId;
    }

    public void setCardId(Integer cardId) {
        this.cardId = cardId;
    }

    public String getCardUid() {
        return cardUid != null ? cardUid : "N/A";
    }

    public void setCardUid(String cardUid) {
        this.cardUid = cardUid;
    }

    public Integer getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Integer deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceLocation() {
        return deviceLocation != null ? deviceLocation : "Unknown";
    }

    public void setDeviceLocation(String deviceLocation) {
        this.deviceLocation = deviceLocation;
    }

    public AccessResult getAccessResult() {
        return accessResult;
    }

    public void setAccessResult(AccessResult accessResult) {
        this.accessResult = accessResult;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Returns a formatted timestamp string for display.
     * @return Formatted timestamp string
     */
    public String getFormattedTimestamp() {
        if (timestamp == null) return "N/A";
        return timestamp.toString().replace('T', ' ');
    }

    @Override
    public String toString() {
        return String.format("AccessLog{logId=%d, timestamp=%s, user='%s', result=%s, reason='%s'}",
                logId, timestamp, getUserName(), accessResult, reason);
    }
}
