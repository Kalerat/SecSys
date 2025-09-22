package org.kgames.client.controller;

import javafx.application.Platform;
import javafx.scene.control.*;
import org.kgames.client.model.User;
import org.kgames.client.service.DatabaseService;
import org.kgames.client.service.MqttService;

import java.sql.SQLException;

/**
 * Controller for RFID Card Encoding functionality.
 * Handles the encoding of secret keys to RFID cards via Arduino communication.
 * Note: UI controls are managed by MainController and passed to this controller via initializeWithControls()
 */
public class RfidEncoderController {

    // UI Control references
    private RadioButton manualSecretRadio;
    private RadioButton databaseUserRadio;
    private ToggleGroup secretSourceGroup;
    private TextField manualSecretField;
    private Label selectedSecretLabel;
    private Button initializeWriteButton;
    private TextArea statusArea;
    private ProgressIndicator progressIndicator;

    // Services
    private DatabaseService databaseService;
    private MqttService mqttService;

    // Current selected user from the user management table
    private User selectedUser;

    // State management
    private String currentSecretKey = "";
    private int retryCount = 0;
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 250;
    private static final long WRITE_READY_TIMEOUT_MS = 10000; // 10 seconds to receive STATUS_RFID_WRITE_READY
    private static final long WRITE_OPERATION_TIMEOUT_MS = 15000; // 15 seconds for write operation to complete

    // Timeout tracking
    private volatile boolean isWaitingForResponse = false;
    private Thread timeoutThread;

    /**
     * Initializes the RFID Encoder controller.
     * Sets up UI bindings and loads users from database.
     * Called from initializeWithControls() after UI controls are set.
     */
    private void initialize() {
        setupUIBindings();
        progressIndicator.setVisible(false);
        statusArea.setEditable(false);
        initializeWriteButton.setDisable(true);

        // Setup secret source radio button group
        secretSourceGroup = new ToggleGroup();
        manualSecretRadio.setToggleGroup(secretSourceGroup);
        databaseUserRadio.setToggleGroup(secretSourceGroup);

        // Default to user selection
        databaseUserRadio.setSelected(true);
        updateUIState();
    }

    /**
     * Sets the required services for this controller.
     * @param databaseService The database service instance
     * @param mqttService The MQTT service instance
     * @param mainController The main controller instance
     */
    public void setServices(DatabaseService databaseService, MqttService mqttService) {
        this.databaseService = databaseService;
        this.mqttService = mqttService;
    }

    /**
     * Initializes the RFID Encoder controller with external controls.
     * This method is called from MainController since the controls are defined in the main FXML.
     */
    public void initializeWithControls(
            RadioButton manualSecretRadio, RadioButton databaseUserRadio,
            TextField manualSecretField, Label selectedSecretLabel, 
            Button initializeWriteButton, TextArea statusArea, 
            ProgressIndicator progressIndicator) {

        this.manualSecretRadio = manualSecretRadio;
        this.databaseUserRadio = databaseUserRadio;
        this.manualSecretField = manualSecretField;
        this.selectedSecretLabel = selectedSecretLabel;
        this.initializeWriteButton = initializeWriteButton;
        this.statusArea = statusArea;
        this.progressIndicator = progressIndicator;

        initialize();
    }

    /**
     * Sets up UI data bindings and event handlers.
     */
    private void setupUIBindings() {
        // Listen for radio button changes
        manualSecretRadio.selectedProperty().addListener((_, _, _) -> updateUIState());
        databaseUserRadio.selectedProperty().addListener((_, _, _) -> updateUIState());

        // Listen for manual secret key input
        manualSecretField.textProperty().addListener((_, _, newVal) -> {
            if (manualSecretRadio.isSelected()) {
                validateAndUpdateSecret(newVal);
            }
        });
    }

    /**
     * Updates UI state based on selected secret source.
     */
    private void updateUIState() {
        boolean isManualMode = manualSecretRadio.isSelected();

        manualSecretField.setDisable(!isManualMode);

        if (isManualMode) {
            validateAndUpdateSecret(manualSecretField.getText());
        } else {
            if (selectedUser != null) {
                loadSecretForUser(selectedUser);
            } else {
                updateSelectedSecret("");
            }
        }
    }

    /**
     * Validates and updates the current secret key.
     * @param secret The secret key to validate
     */
    private void validateAndUpdateSecret(String secret) {
        if (secret == null) secret = "";

        // RFID secret must be exactly 16 hexadecimal characters
        if (secret.length() == 16 && secret.matches("[0-9A-Fa-f]+")) {
            updateSelectedSecret(secret.toUpperCase());
            initializeWriteButton.setDisable(false);
        } else {
            updateSelectedSecret("");
            initializeWriteButton.setDisable(true);
        }
    }

    /**
     * Updates the selected secret display and internal state.
     * @param secret The secret key to display
     */
    private void updateSelectedSecret(String secret) {
        currentSecretKey = secret;
        if (secret.isEmpty()) {
            selectedSecretLabel.setText("No valid secret selected");
            selectedSecretLabel.setStyle("-fx-text-fill: red;");
        } else {
            selectedSecretLabel.setText("Secret: " + secret);
            selectedSecretLabel.setStyle("-fx-text-fill: green;");
        }
    }

    /**
     * Sets the currently selected user from the User Management table.
     * This method is called by MainController when the user selection changes.
     * @param user The selected user, or null if no user is selected
     */
    public void setSelectedUser(User user) {
        this.selectedUser = user;
        updateUIState();
    }

    /**
     * Loads the secret key for the selected user from the database.
     * @param user The selected user
     */
    private void loadSecretForUser(User user) {
        if (databaseService == null || user == null) return;

        try {
            String secret = databaseService.getActiveRfidSecretForUser(user.getUserId());
            if (secret != null && !secret.isEmpty()) {
                validateAndUpdateSecret(secret);
                appendStatus("Loaded secret for user: " + user.getName());
            } else {
                updateSelectedSecret("");
                appendStatus("No active RFID card found for user: " + user.getName());
            }
        } catch (SQLException e) {
            appendStatus("Error loading RFID secret for user: " + e.getMessage());
            updateSelectedSecret("");
        }
    }

    /**
     * Handles the Initialize RFID Write button click.
     */
    public void onInitializeWrite() {
        if (currentSecretKey.isEmpty()) {
            appendStatus("Error: No valid secret key selected");
            return;
        }

        if (mqttService == null) {
            appendStatus("Error: MQTT service not available");
            return;
        }

        startRfidWriteProcess();
    }

    /**
     * Starts the RFID write process by sending the command to Arduino.
     */
    private void startRfidWriteProcess() {
        retryCount = 0;
        setUIBusy(true);
        appendStatus("Initializing RFID write process...");
        appendStatus("Secret key: " + currentSecretKey);

        sendRfidWriteCommand();
    }

    /**
     * Sends the RFID write command to the Arduino.
     */
    private void sendRfidWriteCommand() {
        try {
            String command = "CMD_RFID_WRITE_PREPARE:" + currentSecretKey;
            mqttService.publishMessage("home/arduino/command", command);
            appendStatus("Attempt " + (retryCount + 1) + "/" + MAX_RETRIES + ": Sent prepare command to Arduino");
            startTimeoutThread(WRITE_READY_TIMEOUT_MS, this::onWriteReadyTimeout);
        } catch (Exception e) {
            appendStatus("Error sending command: " + e.getMessage());
            setUIBusy(false);
        }
    }

    /**
     * Handles MQTT messages related to RFID encoding.
     * Called by MainController when relevant messages are received.
     * @param topic The MQTT topic
     * @param message The message content
     */
    public void handleMqttMessage(String topic, String message) {
        if (!topic.equals("home/arduino/events")) return;

        Platform.runLater(() -> {
            if (message.startsWith("STATUS_RFID_WRITE_PREPARED:")) {
                handleWritePreparedMessage(message);
            } else if (message.startsWith("STATUS_RFID_WRITE_READY:")) {
                handleWriteReadyMessage(message);
            } else if (message.equals("STATUS_RFID_WRITE_SUCCESS")) {
                handleWriteSuccess();
            } else if (message.equals("STATUS_RFID_WRITE_FAILED")) {
                handleWriteFailure();
            } else if (message.equals("STATUS_RFID_WRITE_COMPLETED")) {
                handleWriteCompleted();
            } else if (message.startsWith("LOG_")) {
                handleLogMessage(message);
            }
        });
    }

    /**
     * Handles the STATUS_RFID_WRITE_PREPARED message from Arduino.
     * @param message The complete message
     */
    private void handleWritePreparedMessage(String message) {
        stopTimeoutThread();

        String receivedSecret = message.substring("STATUS_RFID_WRITE_PREPARED:".length());

        if (receivedSecret.equals(currentSecretKey)) {
            appendStatus("Arduino prepared for write with correct secret key");
            showCardPresentationDialog();
        } else {
            appendStatus("Secret key mismatch! Expected: " + currentSecretKey + ", Got: " + receivedSecret);
            retryOrAbort();
        }
    }

    /**
     * Handles the STATUS_RFID_WRITE_READY message from Arduino.
     * @param message The complete message
     */
    private void handleWriteReadyMessage(String message) {
        appendStatus("Arduino confirmed write mode is ready");
    }

    /**
     * Shows dialog asking user to present RFID card.
     */
    private void showCardPresentationDialog() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Present RFID Card");
        alert.setHeaderText("Ready to write to RFID card");
        alert.setContentText("WARNING: All data on the card will be overwritten!\n\n" +
                           "Please present the RFID card to the reader now.\n" +
                           "The write process will begin automatically once you confirm.");

        ButtonType proceedButton = new ButtonType("I understand, proceed");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(proceedButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == proceedButton) {
                appendStatus("User confirmed write operation - sending initialize command...");
                sendInitializeWriteCommand();
            } else {
                appendStatus("Write process cancelled by user");
                sendAbortCommand();
                setUIBusy(false);
            }
        });
    }

    /**
     * Sends the confirm write command to Arduino after user confirmation.
     * This tells the Arduino to actually start the write process.
     */
    private void sendInitializeWriteCommand() {
        try {
            String command = "CMD_RFID_WRITE_CONFIRM";
            mqttService.publishMessage("home/arduino/command", command);
            appendStatus("Sent confirmation to Arduino - write mode now active");
            appendStatus("Present your RFID card to the reader now...");

            // Start timeout for card presentation and write operation
            startTimeoutThread(WRITE_OPERATION_TIMEOUT_MS, this::onWriteOperationTimeout);
        } catch (Exception e) {
            appendStatus("Error sending confirmation: " + e.getMessage());
            setUIBusy(false);
        }
    }

    /**
     * Handles successful RFID write.
     */
    private void handleWriteSuccess() {
        stopTimeoutThread();

        appendStatus("✓ RFID card write successful!");
        setUIBusy(false);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText("RFID Card Write Successful");
        alert.setContentText("The secret key has been successfully written to the RFID card.");
        alert.showAndWait();
    }

    /**
     * Handles failed RFID write.
     */
    private void handleWriteFailure() {
        stopTimeoutThread();

        appendStatus("RFID card write failed!");
        setUIBusy(false);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Write Failed");
        alert.setHeaderText("RFID Card Write Failed");
        alert.setContentText("The write operation failed. Please check the logs for detailed error messages and try again.");

        ButtonType retryButton = new ButtonType("Try Again");
        ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(retryButton, okButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == retryButton) {
                startRfidWriteProcess();
            }
        });
    }

    /**
     * Handles RFID write completed message
     */
    private void handleWriteCompleted() {
        appendStatus("RFID write operation completed - system ready for next operation");
    }

    /**
     * Handles log messages from Arduino.
     * @param message The log message
     */
    private void handleLogMessage(String message) {
        appendStatus(message);
    }

    /**
     * Retries the write command or aborts if max retries reached.
     */
    private void retryOrAbort() {
        retryCount++;

        if (retryCount >= MAX_RETRIES) {
            appendStatus("Maximum retries reached. Aborting write process.");
            sendAbortCommand();
            setUIBusy(false);

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Write Failed");
            alert.setHeaderText("Communication Error");
            alert.setContentText("Failed to establish proper communication with Arduino after " + MAX_RETRIES + " attempts.");
            alert.showAndWait();
            return;
        }

        appendStatus("Retrying in " + RETRY_DELAY_MS + "ms...");

        sendAbortCommand();

        // Wait and retry
        new Thread(() -> {
            try {
                Thread.sleep(RETRY_DELAY_MS);
                Platform.runLater(this::sendRfidWriteCommand);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Sends abort command to Arduino.
     */
    private void sendAbortCommand() {
        try {
            mqttService.publishMessage("home/arduino/command", "CMD_ABORT");
            appendStatus("Sent abort command to Arduino");
        } catch (Exception e) {
            appendStatus("Error sending abort command: " + e.getMessage());
        }
    }

    /**
     * Sets the UI to busy or idle state.
     * @param busy True if UI should show busy state
     */
    private void setUIBusy(boolean busy) {
        progressIndicator.setVisible(busy);
        initializeWriteButton.setDisable(busy);
        manualSecretRadio.setDisable(busy);
        databaseUserRadio.setDisable(busy);
        manualSecretField.setDisable(busy || !manualSecretRadio.isSelected());
    }

    /**
     * Appends a status message to the status area.
     * @param message The message to append
     */
    private void appendStatus(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            statusArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    /**
     * Clears the status area.
     */
    public void onClearStatus() {
        statusArea.clear();
    }

    /**
     * Starts a timeout thread for the given duration.
     * @param durationMs The duration in milliseconds
     * @param timeoutAction The action to perform on timeout
     */
    private void startTimeoutThread(long durationMs, Runnable timeoutAction) {
        stopTimeoutThread();

        isWaitingForResponse = true;
        timeoutThread = new Thread(() -> {
            try {
                Thread.sleep(durationMs);
                if (isWaitingForResponse) {
                    Platform.runLater(timeoutAction);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }

    /**
     * Stops the current timeout thread if running.
     */
    private void stopTimeoutThread() {
        isWaitingForResponse = false;
        if (timeoutThread != null && timeoutThread.isAlive()) {
            timeoutThread.interrupt();
        }
    }

    /**
     * Timeout handler for write ready state.
     */
    private void onWriteReadyTimeout() {
        appendStatus("Timeout waiting for Arduino response (STATUS_RFID_WRITE_PREPARED)");
        retryOrAbort();
    }

    /**
     * Timeout handler for write operation.
     */
    private void onWriteOperationTimeout() {
        appendStatus("Timeout waiting for write operation to complete");
        sendAbortCommand();
        setUIBusy(false);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Write Failed");
        alert.setHeaderText("Write Operation Timeout");
        alert.setContentText("The write process was aborted because the operation did not complete in time.");
        alert.showAndWait();
    }
}
