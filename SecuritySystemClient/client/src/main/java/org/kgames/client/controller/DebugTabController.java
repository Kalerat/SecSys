package org.kgames.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * Controller for the Debug tab.
 * Handles MQTT debugging functionality including connection testing and message publishing.
 */
public class DebugTabController extends BaseTabController implements Initializable {

    // MQTT Controls
    @FXML private TextField mqttBrokerField;
    @FXML private TextField mqttTopicField;
    @FXML private Button mqttConnectButton;
    @FXML private Button mqttDisconnectButton;
    @FXML private Button mqttTestButton;
    @FXML private TextField mqttPublishField;
    @FXML private Button mqttPublishButton;
    @FXML private TextArea mqttLogArea;
    @FXML private Button clearLogButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Basic initialization - detailed setup happens after services are initialized
        mqttLogArea.setEditable(false);
        mqttLogArea.setWrapText(true);
        mqttDisconnectButton.setDisable(true);
        
        appendLog("MQTT Debug Console initialized");
        setupEventHandlers();
    }

    @Override
    protected void onServicesInitialized() {
        super.onServicesInitialized();
        initializeMqttFields();
    }

    @Override
    public void onTabActivated() {
        super.onTabActivated();
        appendLog("Debug tab activated - MQTT tools ready");
        
        // Check if MQTT is already connected and update UI accordingly
        if (mqttService != null && mqttService.isConnected()) {
            appendLog("MQTT is already connected - monitoring all traffic");
            mqttConnectButton.setDisable(true);
            mqttDisconnectButton.setDisable(false);
            mqttTestButton.setDisable(false);
            mqttPublishButton.setDisable(false);
        } else {
            appendLog("MQTT is not connected - click Connect to establish connection");
        }
    }

    /**
     * Sets default values for MQTT connection fields.
     */
    private void initializeMqttFields() {
        if (clientConfig != null) {
            mqttBrokerField.setText(clientConfig.getMqttBroker());
            mqttTopicField.setText(clientConfig.getTopicSub());
            
            appendLog("Default broker: " + clientConfig.getMqttBroker());
            appendLog("Default topic: " + clientConfig.getTopicSub());
        }
    }

    /**
     * Sets up event handlers for UI controls.
     */
    private void setupEventHandlers() {
        // Enable/disable publish controls based on connection status
        mqttPublishField.setOnAction(_ -> onMqttPublishButtonClick());
    }

    // MQTT Event Handlers

    @FXML
    private void onMqttConnectButtonClick() {
        String broker = mqttBrokerField.getText().trim();
        String topic = mqttTopicField.getText().trim();

        if (broker.isEmpty()) {
            appendLog("ERROR: Broker address cannot be empty");
            return;
        }

        if (topic.isEmpty()) {
            appendLog("ERROR: Topic cannot be empty");
            return;
        }

        if (mqttService != null) {
            // Check if MQTT is already connected
            if (mqttService.isConnected()) {
                appendLog("MQTT is already connected and receiving messages");
                appendLog("You can use this debug console to monitor all MQTT traffic");
                
                // Update UI to reflect that we're connected
                mqttConnectButton.setDisable(true);
                mqttDisconnectButton.setDisable(false);
                mqttTestButton.setDisable(false);
                mqttPublishButton.setDisable(false);
            } else {
                // Attempt to connect
                mqttConnectButton.setDisable(true);
                mqttDisconnectButton.setDisable(false);
                mqttTestButton.setDisable(false);
                mqttPublishButton.setDisable(false);

                mqttService.connect(broker, topic);
                appendLog("Attempting to connect to MQTT broker: " + broker);
                appendLog("Subscribing to topic: " + topic);
            }
        } else {
            appendLog("ERROR: MQTT service is not available");
            resetConnectionButtons();
        }
    }

    @FXML
    private void onMqttDisconnectButtonClick() {
        if (mqttService != null) {
            mqttService.disconnect();
            appendLog("Disconnecting from MQTT broker...");
        }

        resetConnectionButtons();
    }

    @FXML
    private void onMqttTestButtonClick() {
        String topic = mqttTopicField.getText().trim();
        if (topic.isEmpty()) {
            appendLog("ERROR: Topic cannot be empty for test message");
            return;
        }

        publishMessage(topic, "Hello from JavaFX Debug Console! [" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "]");
    }

    @FXML
    private void onMqttPublishButtonClick() {
        String payload = mqttPublishField.getText().trim();
        if (payload.isEmpty()) {
            appendLog("ERROR: Cannot publish empty message");
            return;
        }

        if (clientConfig != null) {
            publishMessage(clientConfig.getTopicPub(), payload);
        }
        mqttPublishField.clear();
    }

    @FXML
    private void onClearLogClick() {
        mqttLogArea.clear();
        appendLog("Log cleared - MQTT Debug Console ready");
    }


    /**
     * Publishes a message to the specified MQTT topic.
     * @param topic The topic to publish to
     * @param message The message to publish
     */
    private void publishMessage(String topic, String message) {
        if (mqttService == null) {
            appendLog("ERROR: MQTT service is not available");
            return;
        }

        try {
            mqttService.publishMessage(topic, message);
            appendLog("Published to '" + topic + "': " + message);
        } catch (MqttException e) {
            appendLog("Failed to publish to '" + topic + "': " + e.getMessage());
        }
    }

    /**
     * Appends a message to the MQTT log area with timestamp.
     * @param message The message to append
     */
    private void appendLog(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String logEntry = "[" + timestamp + "] " + message + "\n";
            mqttLogArea.appendText(logEntry);
            
            // Auto-scroll to bottom
            mqttLogArea.positionCaret(mqttLogArea.getLength());
        });
    }

    /**
     * Resets connection buttons to default state.
     */
    private void resetConnectionButtons() {
        Platform.runLater(() -> {
            mqttConnectButton.setDisable(false);
            mqttDisconnectButton.setDisable(true);
            mqttTestButton.setDisable(true);
            mqttPublishButton.setDisable(true);
        });
    }

    /**
     * Called when MQTT connection is established successfully.
     * This method should be called by the MQTT service callback.
     */
    public void onMqttConnected() {
        appendLog("MQTT connection established successfully");
        appendLog("Ready to send and receive messages");
    }

    /**
     * Called when MQTT connection is lost.
     * @param cause The cause of connection loss
     */
    public void onMqttConnectionLost(String cause) {
        appendLog("MQTT connection lost: " + cause);
        resetConnectionButtons();
    }

    /**
     * Called when a message is received via MQTT.
     * @param topic The topic the message was received on
     * @param message The received message
     */
    public void onMqttMessageReceived(String topic, String message) {
        appendLog("Received on '" + topic + "': " + message);
    }

    /**
     * Called when MQTT connection fails.
     * @param error The error that occurred
     */
    public void onMqttConnectionFailed(String error) {
        appendLog("MQTT connection failed: " + error);
        resetConnectionButtons();
    }

    @Override
    public void cleanup() {
        super.cleanup();
        if (mqttService != null && mqttService.isConnected()) {
            mqttService.disconnect();
        }
    }
}
