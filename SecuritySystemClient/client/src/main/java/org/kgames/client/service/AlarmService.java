package org.kgames.client.service;

import javafx.application.Platform;
import org.kgames.client.config.ClientConfig;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing alarm states and providing real-time updates.
 */
public class AlarmService {
    
    public enum AlarmState {
        READY("Ready", "#4CAF50"),           // Green - System ready
        MOTION_DETECTED("Motion Detected", "#FF9800"),  // Orange - Motion in grace period
        ALARM_ACTIVE("ALARM ACTIVE", "#F44336"),        // Red - Alarm triggered
        ALARM_DISABLED("Disabled", "#9E9E9E");          // Gray - Manually disabled
        
        private final String displayName;
        private final String color;
        
        AlarmState(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }
    
    public enum DisableMode {
        RESET_REARM("Reset/Rearm Only", 0),
        MINUTES_5("5 Minutes", 5),
        MINUTES_15("15 Minutes", 15),
        MINUTES_30("30 Minutes", 30),
        HOUR_1("1 Hour", 60),
        HOURS_2("2 Hours", 120),
        HOURS_4("4 Hours", 240),
        HOURS_8("8 Hours", 480),
        HOURS_12("12 Hours", 720),
        DAY_1("24 Hours", 1440),
        PERMANENT("Permanent", -1);
        
        private final String displayName;
        private final int minutes;
        
        DisableMode(String displayName, int minutes) {
            this.displayName = displayName;
            this.minutes = minutes;
        }
        
        public String getDisplayName() { return displayName; }
        public int getMinutes() { return minutes; }
        public boolean isPermanent() { return minutes == -1; }
        public boolean isReset() { return minutes == 0; }
    }
    
    public interface AlarmStateListener {
        void onAlarmStateChanged(AlarmState state, String message, boolean manuallyActivated);
        void onAlarmDisableCountdown(long remainingMinutes);
    }
    
    private volatile AlarmState currentState = AlarmState.READY;
    private volatile boolean manuallyActivated = false;
    private volatile LocalDateTime lastStateChange = LocalDateTime.now();
    private volatile String lastMessage = "System ready";
    private volatile LocalDateTime disableEndTime = null;
    
    private final List<AlarmStateListener> listeners = new ArrayList<>();
    private final Object listenersLock = new Object();
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> disableTimer = null;
    private ScheduledFuture<?> countdownTimer = null;
    
    private final MqttService mqttService;
    private final ClientConfig clientConfig;
    
    public AlarmService(MqttService mqttService, ClientConfig clientConfig) {
        this.mqttService = mqttService;
        this.clientConfig = clientConfig;
    }
    
    /**
     * Add a listener for alarm state changes.
     */
    public void addListener(AlarmStateListener listener) {
        synchronized (listenersLock) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a listener.
     */
    public void removeListener(AlarmStateListener listener) {
        synchronized (listenersLock) {
            listeners.remove(listener);
        }
    }
    
    /**
     * Get current alarm state.
     */
    public AlarmState getCurrentState() {
        return currentState;
    }
    
    /**
     * Check if alarm was manually activated.
     */
    public boolean isManuallyActivated() {
        return manuallyActivated;
    }
    
    /**
     * Get last state change time.
     */
    public LocalDateTime getLastStateChange() {
        return lastStateChange;
    }
    
    /**
     * Get last message.
     */
    public String getLastMessage() {
        return lastMessage;
    }
    
    /**
     * Get disable end time (null if not disabled or permanent).
     */
    public LocalDateTime getDisableEndTime() {
        return disableEndTime;
    }
    
    /**
     * Manually activate the alarm.
     */
    public void activateAlarm() {
        if (currentState == AlarmState.ALARM_ACTIVE && manuallyActivated) {
            return;
        }
        
        cancelDisableTimer();
        updateState(AlarmState.ALARM_ACTIVE, "Manually activated", true);
        
        // Send MQTT command to Pico
        try {
            mqttService.publishMessage(clientConfig.getTopicPub(), "CMD_ACTIVATE_ALARM");
            debug("Alarm manually activated");
        } catch (Exception e) {
            System.err.println("Failed to send alarm activation command: " + e.getMessage());
        }
    }
    
    /**
     * Disable the alarm for a specified period, permanently, or reset to ready state.
     */
    public void disableAlarm(DisableMode mode) {
        cancelDisableTimer();
        
        if (mode.isReset()) {
            // Reset/Rearm: Go directly back to ready state without any disable period
            updateState(AlarmState.READY, "Alarm reset - system rearmed", false);
            try {
                mqttService.publishMessage(clientConfig.getTopicPub(), "CMD_RESET_ALARM");
                debug("Alarm reset and rearmed");
            } catch (Exception e) {
                System.err.println("Failed to send alarm reset command: " + e.getMessage());
            }
        } else if (mode.isPermanent()) {
            updateState(AlarmState.ALARM_DISABLED, "Permanently disabled", false);
            try {
                mqttService.publishMessage(clientConfig.getTopicPub(), "CMD_DISABLE_ALARM_PERMANENT");
                debug("Alarm permanently disabled");
            } catch (Exception e) {
                System.err.println("Failed to send permanent disable command: " + e.getMessage());
            }
        } else {
            disableEndTime = LocalDateTime.now().plusMinutes(mode.getMinutes());
            updateState(AlarmState.ALARM_DISABLED, 
                       String.format("Disabled for %s", mode.getDisplayName()), false);
            
            startCountdownTimer();
            
            // Schedule re-activation
            disableTimer = scheduler.schedule(() -> {
                disableEndTime = null;
                updateState(AlarmState.READY, "Auto re-enabled after timeout", false);
                try {
                    mqttService.publishMessage(clientConfig.getTopicPub(), "CMD_ENABLE_ALARM");
                    debug("Alarm auto re-enabled after timeout");
                } catch (Exception e) {
                    System.err.println("Failed to send auto re-enable command: " + e.getMessage());
                }
            }, mode.getMinutes(), TimeUnit.MINUTES);
            
            try {
                mqttService.publishMessage(clientConfig.getTopicPub(), 
                                         String.format("CMD_DISABLE_ALARM_TIMED:%d", mode.getMinutes()));
                debug(String.format("Alarm disabled for %d minutes", mode.getMinutes()));
            } catch (Exception e) {
                System.err.println("Failed to send timed disable command: " + e.getMessage());
            }
        }
    }
    
    /**
     * Manually enable/re-activate the alarm system.
     */
    public void enableAlarm() {
        cancelDisableTimer();
        updateState(AlarmState.READY, "Manually enabled", false);
        
        try {
            mqttService.publishMessage(clientConfig.getTopicPub(), "CMD_ENABLE_ALARM");
            debug("Alarm manually enabled");
        } catch (Exception e) {
            System.err.println("Failed to send manual enable command: " + e.getMessage());
        }
    }
    
    /**
     * Handle MQTT message from Pico about alarm state changes.
     */
    public void handleMqttMessage(String topic, String message) {
        debug("Processing alarm MQTT message: " + message);
        
        try {
            switch (message) {
                case "MOTION_DETECTED":
                    if (currentState == AlarmState.READY && !manuallyActivated) {
                        updateState(AlarmState.MOTION_DETECTED, "Motion detected - grace period", false);
                    }
                    break;
                    
                case "MOTION_STOPPED":
                    if (currentState == AlarmState.MOTION_DETECTED && !manuallyActivated) {
                        updateState(AlarmState.READY, "Motion stopped - alarm cancelled", false);
                    }
                    break;
                    
                case "ALARM_TRIGGERED":
                    if (!manuallyActivated) {
                        updateState(AlarmState.ALARM_ACTIVE, "Alarm triggered by motion timeout", false);
                    }
                    break;
                    
                case "ALARM_RESET":
                    cancelDisableTimer();
                    updateState(AlarmState.READY, "Alarm reset and rearmed", false);
                    break;
                    
                case "SYSTEM_REARMED":
                    // Same as ALARM_RESET - triggered by hardware button or reset command
                    cancelDisableTimer();
                    updateState(AlarmState.READY, "System rearmed via hardware button", false);
                    break;
                    
                case "ALARM_DISABLED_RFID":
                    // RFID can only disable automatic alarms, not manual ones
                    if (!manuallyActivated) {
                        cancelDisableTimer();
                        updateState(AlarmState.READY, "Disabled by RFID authentication", false);
                    } else {
                        debug("RFID cannot disable manually activated alarm");
                    }
                    break;
                    
                case "AUTH_SUCCESS_BLOCKED":
                    debug("RFID authentication successful but blocked due to manual activation");
                    break;
                    
                case "ACK_CMD_ACTIVATE_ALARM":
                    debug("Pico acknowledged alarm activation");
                    break;
                    
                case "ACK_CMD_DISABLE_ALARM":
                    debug("Pico acknowledged alarm disable");
                    break;
                    
                default:
                    if (message.startsWith("SECURITY_STATE:")) {
                        handleSecurityStateUpdate(message.substring(15));
                    }
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error processing alarm MQTT message: " + e.getMessage());
        }
    }
    
    /**
     * Handle security state updates from Pico.
     */
    private void handleSecurityStateUpdate(String state) {
        try {
            switch (state) {
                case "READY":
                    if (currentState != AlarmState.ALARM_DISABLED && !manuallyActivated) {
                        updateState(AlarmState.READY, "System ready", false);
                    }
                    break;
                case "MOTION_DETECTED":
                    if (currentState == AlarmState.READY && !manuallyActivated) {
                        updateState(AlarmState.MOTION_DETECTED, "Motion detected", false);
                    }
                    break;
                case "ALARM_ACTIVE":
                    if (currentState != AlarmState.ALARM_ACTIVE) {
                        updateState(AlarmState.ALARM_ACTIVE, 
                                  manuallyActivated ? "Manually activated" : "Triggered by motion", 
                                  manuallyActivated);
                    }
                    break;
                case "ALARM_DISABLED":
                    if (!manuallyActivated) {
                        updateState(AlarmState.ALARM_DISABLED, "Disabled", false);
                    }
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error handling security state update: " + e.getMessage());
        }
    }
    
    /**
     * Update the alarm state and notify listeners.
     */
    private void updateState(AlarmState newState, String message, boolean manuallyActivated) {
        this.currentState = newState;
        this.lastMessage = message;
        this.lastStateChange = LocalDateTime.now();
        this.manuallyActivated = manuallyActivated;
        
        notifyListeners();
    }
    
    /**
     * Start countdown timer for disabled alarm.
     */
    private void startCountdownTimer() {
        if (countdownTimer != null) {
            countdownTimer.cancel(false);
        }
        
        countdownTimer = scheduler.scheduleAtFixedRate(() -> {
            if (disableEndTime != null) {
                long remainingMinutes = java.time.Duration.between(
                    LocalDateTime.now(), disableEndTime).toMinutes();
                
                if (remainingMinutes <= 0) {
                    if (countdownTimer != null) {
                        countdownTimer.cancel(false);
                        countdownTimer = null;
                    }
                } else {
                    Platform.runLater(() -> {
                        synchronized (listenersLock) {
                            for (AlarmStateListener listener : listeners) {
                                try {
                                    listener.onAlarmDisableCountdown(remainingMinutes);
                                } catch (Exception e) {
                                    System.err.println("Error notifying countdown listener: " + e.getMessage());
                                }
                            }
                        }
                    });
                }
            }
        }, 0, 30, TimeUnit.SECONDS); // Update every 30 seconds
    }
    
    /**
     * Cancel disable timer and countdown.
     */
    private void cancelDisableTimer() {
        if (disableTimer != null) {
            disableTimer.cancel(false);
            disableTimer = null;
        }
        if (countdownTimer != null) {
            countdownTimer.cancel(false);
            countdownTimer = null;
        }
        disableEndTime = null;
    }
    
    /**
     * Notify all listeners of state change.
     */
    private void notifyListeners() {
        Platform.runLater(() -> {
            synchronized (listenersLock) {
                for (AlarmStateListener listener : listeners) {
                    try {
                        listener.onAlarmStateChanged(currentState, lastMessage, manuallyActivated);
                    } catch (Exception e) {
                        System.err.println("Error notifying alarm state listener: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    /**
     * Get formatted status string for display.
     */
    public String getFormattedStatus() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timeStr = lastStateChange.format(formatter);
        
        if (currentState == AlarmState.ALARM_DISABLED && disableEndTime != null) {
            long remainingMinutes = java.time.Duration.between(
                LocalDateTime.now(), disableEndTime).toMinutes();
            return String.format("%s (%d min remaining) - %s", 
                               currentState.getDisplayName(), remainingMinutes, timeStr);
        }
        
        return String.format("%s - %s", currentState.getDisplayName(), timeStr);
    }
    
    /**
     * Cleanup resources.
     */
    public void shutdown() {
        cancelDisableTimer();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private void debug(String message) {
        System.out.println("[AlarmService] " + message);
    }
}
