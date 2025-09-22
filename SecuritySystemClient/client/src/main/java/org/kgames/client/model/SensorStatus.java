package org.kgames.client.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model class representing the status of a sensor in the security system.
 */
public class SensorStatus {
    
    public enum SensorType {
        MOTION_SENSOR,
        RFID_READER,
        CAMERA,
        ARDUINO,
        PICO,
        NETWORK
    }
    
    public enum Status {
        ACTIVE,
        INACTIVE,
        ERROR,
        UNKNOWN
    }
    
    private final SensorType sensorType;
    private Status status;
    private String message;
    private LocalDateTime lastUpdate;
    private String additionalInfo;
    
    public SensorStatus(SensorType sensorType, Status status, String message) {
        this.sensorType = sensorType;
        this.status = status;
        this.message = message;
        this.lastUpdate = LocalDateTime.now();
        this.additionalInfo = "";
    }
    
    public SensorStatus(SensorType sensorType, Status status, String message, String additionalInfo) {
        this.sensorType = sensorType;
        this.status = status;
        this.message = message;
        this.lastUpdate = LocalDateTime.now();
        this.additionalInfo = additionalInfo != null ? additionalInfo : "";
    }
    
    // Getters
    public SensorType getSensorType() {
        return sensorType;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }
    
    public String getAdditionalInfo() {
        return additionalInfo;
    }
    
    // Setters
    public void setStatus(Status status) {
        this.status = status;
        this.lastUpdate = LocalDateTime.now();
    }
    
    public void setMessage(String message) {
        this.message = message;
        this.lastUpdate = LocalDateTime.now();
    }
    
    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo != null ? additionalInfo : "";
        this.lastUpdate = LocalDateTime.now();
    }
    
    public void updateStatus(Status status, String message) {
        this.status = status;
        this.message = message;
        this.lastUpdate = LocalDateTime.now();
    }
    
    public void updateStatus(Status status, String message, String additionalInfo) {
        this.status = status;
        this.message = message;
        this.additionalInfo = additionalInfo != null ? additionalInfo : "";
        this.lastUpdate = LocalDateTime.now();
    }
    
    /**
     * Returns a formatted display string for the sensor status.
     * Format: "SensorName: Status - Message"
     */
    public String getDisplayString() {
        String sensorName = getSensorDisplayName();
        return String.format("%s: %s - %s", sensorName, getStatusDisplayString(), message);
    }
    
    /**
     * Returns a user-friendly display name for the sensor type.
     */
    public String getSensorDisplayName() {
        switch (sensorType) {
            case MOTION_SENSOR:
                return "Motion Sensor";
            case RFID_READER:
                return "RFID Reader";
            case CAMERA:
                return "Camera System";
            case ARDUINO:
                return "Arduino Controller";
            case PICO:
                return "Pico Controller";
            case NETWORK:
                return "Network Connection";
            default:
                return "Unknown Sensor";
        }
    }
    
    /**
     * Returns a user-friendly display string for the status.
     */
    public String getStatusDisplayString() {
        switch (status) {
            case ACTIVE:
                return "Active";
            case INACTIVE:
                return "Inactive";
            case ERROR:
                return "Error";
            case UNKNOWN:
                return "Unknown";
            default:
                return "Unknown";
        }
    }
    
    /**
     * Returns the CSS class name for styling based on status.
     */
    public String getStatusCssClass() {
        switch (status) {
            case ACTIVE:
                return "sensor-active";
            case INACTIVE:
                return "sensor-inactive";
            case ERROR:
                return "sensor-error";
            case UNKNOWN:
                return "sensor-unknown";
            default:
                return "sensor-unknown";
        }
    }
    
    /**
     * Returns formatted timestamp string.
     */
    public String getFormattedLastUpdate() {
        return lastUpdate.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    
    @Override
    public String toString() {
        return String.format("SensorStatus{type=%s, status=%s, message='%s', lastUpdate=%s}", 
                sensorType, status, message, getFormattedLastUpdate());
    }
}
