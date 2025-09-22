package org.kgames.client.controller;

import javafx.scene.control.Alert;
import org.kgames.client.config.ClientConfig;
import org.kgames.client.service.DatabaseService;
import org.kgames.client.service.MqttService;
import org.kgames.client.service.CameraService;
import org.kgames.client.service.MultiCameraService;
import org.kgames.client.service.ConfigurationService;
import org.kgames.client.service.SensorStatusService;

/**
 * Abstract base controller for all tab controllers.
 * Provides common functionality and shared services.
 */
public abstract class BaseTabController {
    
    // Shared services
    protected DatabaseService databaseService;
    protected MqttService mqttService;
    protected CameraService cameraService;
    protected MultiCameraService multiCameraService;
    protected ConfigurationService configurationService;
    protected SensorStatusService sensorStatusService;
    protected ClientConfig clientConfig;
    
    /**
     * Initializes the tab controller with shared services.
     * Called by MainController when setting up tabs.
     */
    public void initializeServices(DatabaseService databaseService, 
                                 MqttService mqttService,
                                 CameraService cameraService,
                                 MultiCameraService multiCameraService,
                                 ConfigurationService configurationService,
                                 SensorStatusService sensorStatusService,
                                 ClientConfig clientConfig) {
        this.databaseService = databaseService;
        this.mqttService = mqttService;
        this.cameraService = cameraService;
        this.multiCameraService = multiCameraService;
        this.configurationService = configurationService;
        this.sensorStatusService = sensorStatusService;
        this.clientConfig = clientConfig;
        
        onServicesInitialized();
    }
    
    /**
     * Called after services are initialized.
     * Subclasses can override this to perform custom initialization.
     */
    protected void onServicesInitialized() {
        // Default implementation does nothing
    }
    
    /**
     * Called when the tab becomes active.
     * Subclasses can override this to refresh data or update UI.
     */
    public void onTabActivated() {
        // Default implementation does nothing
    }
    
    /**
     * Called when the tab becomes inactive.
     * Subclasses can override this to pause operations or save state.
     */
    public void onTabDeactivated() {
        // Default implementation does nothing
    }
    
    /**
     * Called when the application is shutting down.
     * Subclasses should override this to clean up resources.
     */
    public void cleanup() {
        // Default implementation does nothing
    }
    
    /**
     * Shows an alert dialog with the specified title and message.
     */
    protected void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Shows an error alert dialog.
     */
    protected void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Shows a warning alert dialog.
     */
    protected void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
