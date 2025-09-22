package org.kgames.client.service;

import org.kgames.client.model.SensorStatus;
import org.kgames.client.model.SensorStatus.SensorType;
import org.kgames.client.model.SensorStatus.Status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing and tracking sensor statuses.
 * Handles updates from MQTT messages and provides current status information.
 */
public class SensorStatusService implements MqttService.MqttMessageListener {
    
    private final Map<SensorType, SensorStatus> sensorStatuses = new ConcurrentHashMap<>();
    private final List<SensorStatusListener> listeners = new ArrayList<>();
    private final Object listenersLock = new Object();
    
    // Timeout settings (in seconds)
    private static final long ARDUINO_TIMEOUT = 60; // 60 seconds
    private static final long PICO_TIMEOUT = 60;    // 60 seconds
    private static final long MQTT_TIMEOUT = 60;    // 60 seconds
    
    /**
     * Interface for listening to sensor status changes.
     */
    public interface SensorStatusListener {
        void onSensorStatusUpdated(SensorType sensorType, SensorStatus status);
        void onSensorStatusRemoved(SensorType sensorType);
    }
    
    public SensorStatusService() {
        initializeDefaultStatuses();
    }
    
    /**
     * Initialize default sensor statuses.
     */
    private void initializeDefaultStatuses() {
        sensorStatuses.put(SensorType.MOTION_SENSOR, 
            new SensorStatus(SensorType.MOTION_SENSOR, Status.UNKNOWN, "Initializing..."));
        sensorStatuses.put(SensorType.ARDUINO, 
            new SensorStatus(SensorType.ARDUINO, Status.UNKNOWN, "Waiting for connection..."));
        sensorStatuses.put(SensorType.PICO, 
            new SensorStatus(SensorType.PICO, Status.UNKNOWN, "Waiting for connection..."));
        sensorStatuses.put(SensorType.NETWORK, 
            new SensorStatus(SensorType.NETWORK, Status.UNKNOWN, "Connecting to MQTT..."));
    }
    
    /**
     * Adds a listener for sensor status changes.
     */
    public void addListener(SensorStatusListener listener) {
        synchronized (listenersLock) {
            listeners.add(listener);
        }
    }
    
    /**
     * Removes a listener for sensor status changes.
     */
    public void removeListener(SensorStatusListener listener) {
        synchronized (listenersLock) {
            listeners.remove(listener);
        }
    }
    
    /**
     * Notifies all listeners of a sensor status update.
     */
    private void notifyListeners(SensorType sensorType, SensorStatus status) {
        synchronized (listenersLock) {
            for (SensorStatusListener listener : listeners) {
                try {
                    listener.onSensorStatusUpdated(sensorType, status);
                } catch (Exception e) {
                    System.err.println("Error notifying sensor status listener: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Updates the status of a specific sensor.
     */
    public void updateSensorStatus(SensorType sensorType, Status status, String message) {
        updateSensorStatus(sensorType, status, message, null);
    }
    
    /**
     * Updates the status of a specific sensor with additional info.
     */
    public void updateSensorStatus(SensorType sensorType, Status status, String message, String additionalInfo) {
        SensorStatus sensorStatus = sensorStatuses.get(sensorType);
        if (sensorStatus == null) {
            sensorStatus = new SensorStatus(sensorType, status, message, additionalInfo);
            sensorStatuses.put(sensorType, sensorStatus);
        } else {
            sensorStatus.updateStatus(status, message, additionalInfo);
        }
        
        System.out.println("SensorStatusService: Updated " + sensorType + " to " + status + " - " + message);
        notifyListeners(sensorType, sensorStatus);
    }
    
    /**
     * Gets the current status of a specific sensor.
     */
    public SensorStatus getSensorStatus(SensorType sensorType) {
        return sensorStatuses.get(sensorType);
    }
    
    /**
     * Gets all current sensor statuses.
     */
    public Collection<SensorStatus> getAllSensorStatuses() {
        return new ArrayList<>(sensorStatuses.values());
    }
    
    /**
     * Checks for sensor timeouts and updates statuses accordingly.
     */
    public void checkForTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        
        for (SensorStatus status : sensorStatuses.values()) {
            long secondsSinceUpdate = ChronoUnit.SECONDS.between(status.getLastUpdate(), now);
            
            switch (status.getSensorType()) {
                case ARDUINO:
                    if (secondsSinceUpdate > ARDUINO_TIMEOUT && status.getStatus() != Status.ERROR) {
                        System.out.println("SensorStatusService: Arduino timeout detected - " + secondsSinceUpdate + "s since last update (limit: " + ARDUINO_TIMEOUT + "s)");
                        updateSensorStatus(SensorType.ARDUINO, Status.ERROR, "Communication timeout");
                    }
                    break;
                case PICO:
                    if (secondsSinceUpdate > PICO_TIMEOUT && status.getStatus() != Status.ERROR) {
                        System.out.println("SensorStatusService: Pico timeout detected - " + secondsSinceUpdate + "s since last update (limit: " + PICO_TIMEOUT + "s)");
                        updateSensorStatus(SensorType.PICO, Status.ERROR, "Communication timeout");
                        // If Pico times out, Arduino and motion sensor are also unreachable
                        updateSensorStatus(SensorType.MOTION_SENSOR, Status.ERROR, "Communication Error!");
                    }
                    break;
                case NETWORK:
                    if (secondsSinceUpdate > MQTT_TIMEOUT && status.getStatus() != Status.ERROR) {
                        System.out.println("SensorStatusService: Network timeout detected - " + secondsSinceUpdate + "s since last update (limit: " + MQTT_TIMEOUT + "s)");
                        updateSensorStatus(SensorType.NETWORK, Status.ERROR, "MQTT connection timeout");
                    }
                    break;
                default:
                    // No timeout logic for other sensor types
                    break;
            }
        }
    }
    
    // MQTT Message Listener Implementation
    
    @Override
    public void onMessageReceived(String topic, String message) {
        handleMqttMessage(topic, message);
    }
    
    @Override
    public void onConnectionLost(String cause) {
        updateSensorStatus(SensorType.NETWORK, Status.ERROR, "MQTT connection lost: " + cause);
    }
    
    @Override
    public void onConnectionSuccess() {
        updateSensorStatus(SensorType.NETWORK, Status.ACTIVE, "MQTT connected");
    }
    
    @Override
    public void onConnectionFailed(String error) {
        updateSensorStatus(SensorType.NETWORK, Status.ERROR, "MQTT connection failed: " + error);
    }
    
    /**
     * Handles incoming MQTT messages and updates sensor statuses.
     */
    private void handleMqttMessage(String topic, String message) {
        System.out.println("SensorStatusService: Processing message - Topic: " + topic + ", Message: " + message);
        
        // Update network status on any message received
        updateSensorStatus(SensorType.NETWORK, Status.ACTIVE, "MQTT active");
        
        try {
            switch (message) {
                case "STATUS_READY":
                    updateSensorStatus(SensorType.ARDUINO, Status.ACTIVE, "Ready");
                    updateSensorStatus(SensorType.PICO, Status.ACTIVE, "Connected");
                    break;
                    
                case "PICO_READY":
                    updateSensorStatus(SensorType.PICO, Status.ACTIVE, "Ready");
                    break;
                    
                case "ARDUINO_CONNECTED":
                    updateSensorStatus(SensorType.ARDUINO, Status.ACTIVE, "Connected");
                    break;
                    
                case "ARDUINO_HEARTBEAT":
                    updateSensorStatus(SensorType.ARDUINO, Status.ACTIVE, "Active");
                    break;
                    
                case "PICO_HEARTBEAT":
                    updateSensorStatus(SensorType.PICO, Status.ACTIVE, "Connected");
                    break;
                    
                case "ARDUINO_DISCONNECTED":
                    updateSensorStatus(SensorType.ARDUINO, Status.ERROR, "Communication Error!");
                    updateSensorStatus(SensorType.MOTION_SENSOR, Status.ERROR, "Communication Error!");
                    break;
                    
                case "MOTION_DETECTED":
                    updateSensorStatus(SensorType.MOTION_SENSOR, Status.INACTIVE, "Motion Detected!");
                    break;
                    
                case "MOTION_STOPPED":
                    updateSensorStatus(SensorType.MOTION_SENSOR, Status.ACTIVE, "No Motion Detected");
                    break;
                    
                default:
                    // Handle Arduino status updates
                    if (message.startsWith("ARDUINO_STATUS:")) {
                        handleArduinoStatusMessage(message.substring("ARDUINO_STATUS:".length()));
                    }
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error processing MQTT message: " + e.getMessage());
        }
    }
    
    /**
     * Handles detailed Arduino status messages.
     */
    private void handleArduinoStatusMessage(String statusData) {
        try {
            // Parse status data like "MOTION:ACTIVE,TIME:12345"
            String[] parts = statusData.split(",");
            for (String part : parts) {
                String[] keyValue = part.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    
                    if ("MOTION".equals(key)) {
                        if ("ACTIVE".equals(value)) {
                            updateSensorStatus(SensorType.MOTION_SENSOR, Status.INACTIVE, "Motion Detected!");
                        } else if ("INACTIVE".equals(value)) {
                            updateSensorStatus(SensorType.MOTION_SENSOR, Status.ACTIVE, "No Motion Detected");
                        }
                    }
                }
            }
            
            // Update Arduino status on any status message
            updateSensorStatus(SensorType.ARDUINO, Status.ACTIVE, "Active");
            
        } catch (Exception e) {
            System.err.println("Error parsing Arduino status message: " + e.getMessage());
        }
    }
}
