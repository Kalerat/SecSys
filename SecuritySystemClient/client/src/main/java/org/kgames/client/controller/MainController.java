package org.kgames.client.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.kgames.client.config.ClientConfig;
import org.kgames.client.service.CameraService;
import org.kgames.client.service.MultiCameraService;
import org.kgames.client.service.ConfigurationService;
import org.kgames.client.service.DatabaseService;
import org.kgames.client.service.MqttService;
import org.kgames.client.service.SensorStatusService;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Main Controller for the Security System
 * Coordinates between tab controllers and manages shared services.
 */
public class MainController implements Initializable, MqttService.MqttMessageListener {

    @FXML private TabPane mainTabPane;

    // Configuration
    private final ClientConfig clientConfig = new ClientConfig();

    // Shared services
    private final MqttService mqttService = new MqttService();
    private final ConfigurationService configurationService = new ConfigurationService();
    private final CameraService cameraService;
    private final MultiCameraService multiCameraService;
    private final DatabaseService databaseService = new DatabaseService();
    private final SensorStatusService sensorStatusService = new SensorStatusService();

    // Tab controllers
    private final Map<String, BaseTabController> tabControllers = new HashMap<>();

    public MainController() {
        this.cameraService = new CameraService(configurationService);
        this.multiCameraService = new MultiCameraService(configurationService);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeDatabase();
        initializeServices();
        initializeTabs();
        setupTabSelectionListener();
    }

    /**
     * Initializes all shared services.
     */
    private void initializeServices() {
        // Setup MQTT Listener connection
        mqttService.setMessageListener(this);
        mqttService.connect(clientConfig.getMqttBroker(), clientConfig.getTopicSub());
    }

    /**
     * Initializes all tabs with their respective controllers.
     */
    private void initializeTabs() {
        try {
            // Security Tab
            Tab securityTab = createTabFromFXML("Security", "/org/kgames/client/security-tab.fxml");
            if (securityTab != null) {
                mainTabPane.getTabs().add(securityTab);
            }

            // User Management Tab
            Tab userManagementTab = createTabFromFXML("User Management", "/org/kgames/client/user-management-tab.fxml");
            if (userManagementTab != null) {
                mainTabPane.getTabs().add(userManagementTab);
            }

            // Media Browser Tab
            Tab mediaBrowserTab = createTabFromFXML("Media Browser", "/org/kgames/client/media-browser-tab.fxml");
            if (mediaBrowserTab != null) {
                mainTabPane.getTabs().add(mediaBrowserTab);
            }

            // Configuration Tab
            Tab configTab = createTabFromFXML("Configuration", "/org/kgames/client/configuration-tab.fxml");
            if (configTab != null) {
                mainTabPane.getTabs().add(configTab);
            }

            // Debug Tab
            Tab debugTab = createTabFromFXML("Debug", "/org/kgames/client/debug-tab.fxml");
            if (debugTab != null) {
                mainTabPane.getTabs().add(debugTab);
            }

        } catch (Exception e) {
            System.err.println("Error initializing tabs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a tab from an FXML file.
     */
    private Tab createTabFromFXML(String tabName, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Tab tab = new Tab(tabName);
            tab.setContent(loader.load());
            tab.setClosable(false);
            tab.setStyle("-fx-background-color: #2d2d2d;");

            BaseTabController controller = loader.getController();
            if (controller != null) {
                controller.initializeServices(databaseService, mqttService, cameraService, multiCameraService, configurationService, sensorStatusService, clientConfig);
                tabControllers.put(tabName, controller);
            }

            return tab;
        } catch (IOException e) {
            System.err.println("Failed to load FXML for " + tabName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Sets up tab selection listener to notify controllers when tabs change.
     */
    private void setupTabSelectionListener() {
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((_, oldTab, newTab) -> {
            if (oldTab != null) {
                BaseTabController oldController = tabControllers.get(oldTab.getText());
                if (oldController != null) {
                    oldController.onTabDeactivated();
                }
            }

            if (newTab != null) {
                BaseTabController newController = tabControllers.get(newTab.getText());
                if (newController != null) {
                    newController.onTabActivated();
                }
            }
        });
    }

    /**
     * Initializes the database connection.
     */
    private void initializeDatabase() {
        try {
            databaseService.connect();
            if (databaseService.isConnected()) {
                System.out.println("Database connected successfully");
            } else {
                System.err.println("Database connection failed - connection is not valid");
            }
        } catch (Exception e) {
            System.err.println("Could not connect to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // MQTT Message Listener Implementation
    @Override
    public void onMessageReceived(String topic, String message) {
        System.out.println("MQTT Message received - Topic: " + topic + ", Message: " + message);
        
        // Forward to sensor status service
        sensorStatusService.onMessageReceived(topic, message);
        
        // Forward alarm-related messages to Security tab
        if (topic.equals("home/arduino/events")) {
            BaseTabController securityTabController = tabControllers.get("Security");
            if (securityTabController instanceof SecurityTabController) {
                ((SecurityTabController) securityTabController).handleAlarmMqttMessage(topic, message);
            }
        }
        
        // Route messages to Debug tab for debugging purposes
        BaseTabController debugTabController = tabControllers.get("Debug");
        if (debugTabController instanceof DebugTabController) {
            ((DebugTabController) debugTabController).onMqttMessageReceived(topic, message);
        }
        
        // Forward RFID messages to UserManagementTabController (which includes RfidEncoderController)
        if (topic.equals("home/arduino/events")) {
            BaseTabController userTabController = tabControllers.get("User Management");
            if (userTabController instanceof UserManagementTabController) {
                ((UserManagementTabController) userTabController).handleMqttMessage(topic, message);
            }
        }
    }

    @Override
    public void onConnectionLost(String cause) {
        System.err.println("MQTT Connection lost: " + cause);
        
        // Forward to sensor status service
        sensorStatusService.onConnectionLost(cause);
        
        // Notify Debug tab of connection loss
        BaseTabController debugTabController = tabControllers.get("Debug");
        if (debugTabController instanceof DebugTabController) {
            ((DebugTabController) debugTabController).onMqttConnectionLost(cause);
        }
    }

    @Override
    public void onConnectionSuccess() {
        System.out.println("MQTT Connected successfully");
        
        // Forward to sensor status service
        sensorStatusService.onConnectionSuccess();
        
        // Notify Debug tab of successful connection
        BaseTabController debugTabController = tabControllers.get("Debug");
        if (debugTabController instanceof DebugTabController) {
            ((DebugTabController) debugTabController).onMqttConnected();
        }
    }

    @Override
    public void onConnectionFailed(String error) {
        System.err.println("MQTT Connection failed: " + error);
        
        // Forward to sensor status service
        sensorStatusService.onConnectionFailed(error);
        
        // Notify Debug tab of connection failure
        BaseTabController debugTabController = tabControllers.get("Debug");
        if (debugTabController instanceof DebugTabController) {
            ((DebugTabController) debugTabController).onMqttConnectionFailed(error);
        }
    }

    /**
     * Shuts down all services when the application closes.
     * Called by MainFrame when the window is closed.
     */
    public void shutdown() {
        for (BaseTabController controller : tabControllers.values()) {
            controller.cleanup();
        }

        mqttService.disconnect();
        cameraService.stopCamera();
        databaseService.disconnect();
    }
}
