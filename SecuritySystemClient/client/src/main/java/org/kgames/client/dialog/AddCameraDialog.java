package org.kgames.client.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.kgames.client.model.CameraConfiguration;
import org.kgames.client.service.CameraService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dialog for adding new cameras to the multi-camera system.
 */
public class AddCameraDialog extends Dialog<CameraConfiguration> {
    
    private final TextField nameField = new TextField();
    private final ComboBox<String> typeComboBox = new ComboBox<>();
    
    // Local camera fields
    private final ComboBox<String> localCameraComboBox = new ComboBox<>();
    private final VBox localCameraPane = new VBox(5);
    
    // IP camera fields
    private final TextField ipAddressField = new TextField();
    private final TextField portField = new TextField("554");
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField rtspPathField = new TextField();
    private final GridPane ipCameraPane = new GridPane();
    
    private final CameraService cameraService;
    private final boolean showAllCameras;
    
    public AddCameraDialog(CameraService cameraService) {
        this(cameraService, false);
    }
    
    public AddCameraDialog(CameraService cameraService, boolean showAllCameras) {
        this.cameraService = cameraService;
        this.showAllCameras = showAllCameras;
        
        setTitle("Add Camera");
        setHeaderText("Configure a new camera");
        
        setupUI();
        setupValidation();
        
        // Convert the result when OK is pressed
        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return createCameraConfiguration();
            }
            return null;
        });
    }
    
    private void setupUI() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        // Camera name
        content.getChildren().addAll(
            new Label("Camera Name:"),
            nameField
        );
        
        // Camera type selection
        typeComboBox.getItems().addAll("Local Camera", "IP Camera");
        typeComboBox.setValue("Local Camera");
        typeComboBox.setOnAction(_ -> updateCameraTypeUI());
        
        content.getChildren().addAll(
            new Label("Camera Type:"),
            typeComboBox
        );
        
        // Local camera configuration
        setupLocalCameraPane();
        content.getChildren().add(localCameraPane);
        
        // IP camera configuration
        setupIPCameraPane();
        content.getChildren().add(ipCameraPane);
        
        // Initial state
        updateCameraTypeUI();
        
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Set preferred size
        getDialogPane().setPrefSize(400, 300);
    }
    
    private void setupLocalCameraPane() {
        localCameraPane.getChildren().addAll(
            new Label("Select Local Camera:"),
            localCameraComboBox
        );
        
        // Populate local cameras
        populateLocalCameras();
    }
    
    private void setupIPCameraPane() {
        ipCameraPane.setHgap(10);
        ipCameraPane.setVgap(5);
        
        ipCameraPane.add(new Label("IP Address:"), 0, 0);
        ipCameraPane.add(ipAddressField, 1, 0);
        
        ipCameraPane.add(new Label("Port:"), 0, 1);
        ipCameraPane.add(portField, 1, 1);
        
        ipCameraPane.add(new Label("Username:"), 0, 2);
        ipCameraPane.add(usernameField, 1, 2);
        
        ipCameraPane.add(new Label("Password:"), 0, 3);
        ipCameraPane.add(passwordField, 1, 3);
        
        ipCameraPane.add(new Label("RTSP Path:"), 0, 4);
        ipCameraPane.add(rtspPathField, 1, 4);

        //ipCameraPane.add(ipCameraInfoLabel, 0, 5, 2, 1);

        // Set field widths
        ipAddressField.setPrefWidth(200);
        portField.setPrefWidth(80);
        usernameField.setPrefWidth(200);
        passwordField.setPrefWidth(200);
        rtspPathField.setPrefWidth(200);
        
        // Add helpful placeholder text
        ipAddressField.setPromptText("192.168.1.100");
        rtspPathField.setPromptText("stream/channel/1");
    }
    
    private void populateLocalCameras() {
        localCameraComboBox.getItems().clear();
        
        if (cameraService != null) {
            List<org.kgames.client.model.CameraInfo> cameras = cameraService.getAvailableCameras();
            for (org.kgames.client.model.CameraInfo camera : cameras) {
                // Show all cameras if requested, or only available ones by default
                if (showAllCameras || camera.isAvailable()) {
                    String displayText = "Camera " + camera.getCameraId() + " - " + camera.getDescription();
                    if (!camera.isAvailable()) {
                        displayText += " (Not Working)";
                    }
                    localCameraComboBox.getItems().add(displayText);
                }
            }
        }
        
        if (!localCameraComboBox.getItems().isEmpty()) {
            localCameraComboBox.setValue(localCameraComboBox.getItems().get(0));
        } else {
            localCameraComboBox.getItems().add("No cameras detected");
        }
    }
    
    private void updateCameraTypeUI() {
        boolean isLocal = "Local Camera".equals(typeComboBox.getValue());
        localCameraPane.setVisible(isLocal);
        localCameraPane.setManaged(isLocal);
        ipCameraPane.setVisible(!isLocal);
        ipCameraPane.setManaged(!isLocal);
        
        // Resize dialog
        getDialogPane().getScene().getWindow().sizeToScene();
    }
    
    private void setupValidation() {
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        
        // Initially disable OK button
        okButton.setDisable(true);
        
        // Add listeners for validation
        nameField.textProperty().addListener((_, _, _) -> validateInput(okButton));
        typeComboBox.valueProperty().addListener((_, _, _) -> validateInput(okButton));
        localCameraComboBox.valueProperty().addListener((_, _, _) -> validateInput(okButton));
        ipAddressField.textProperty().addListener((_, _, _) -> validateInput(okButton));

        // Initial validation
        validateInput(okButton);
    }
    
    private void validateInput(Button okButton) {
        boolean valid = false;
        
        if (nameField.getText() != null && !nameField.getText().trim().isEmpty()) {
            if ("Local Camera".equals(typeComboBox.getValue())) {
                String selected = localCameraComboBox.getValue();
                // Valid if a camera is selected and it's not the "No cameras detected" placeholder
                valid = selected != null && !selected.equals("No cameras detected");
            } else {
                valid = ipAddressField.getText() != null && !ipAddressField.getText().trim().isEmpty();
            }
        }
        
        okButton.setDisable(!valid);
    }
    
    private CameraConfiguration createCameraConfiguration() {
        String name = nameField.getText().trim();
        
        if ("Local Camera".equals(typeComboBox.getValue())) {
            // Extract camera ID from the selected item
            String selected = localCameraComboBox.getValue();
            if (selected != null && !selected.equals("No cameras detected")) {
                // Parse "Camera X - Description" or "Camera X - Description (Not Working)" format
                String[] parts = selected.split(" - ");
                if (parts.length > 0) {
                    String cameraStr = parts[0].replace("Camera ", "");
                    try {
                        int localId = Integer.parseInt(cameraStr);
                        return new CameraConfiguration(UUID.randomUUID().toString(), name, localId);
                    } catch (NumberFormatException e) {
                        // Fallback to 0
                        return new CameraConfiguration(UUID.randomUUID().toString(), name, 0);
                    }
                }
            }
            return new CameraConfiguration(UUID.randomUUID().toString(), name, 0);
        } else {
            // IP Camera
            String ipAddress = ipAddressField.getText().trim();
            int port = 8080;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ignored) {}
            
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            String rtspPath = rtspPathField.getText().trim();
            
            return new CameraConfiguration(UUID.randomUUID().toString(), name, ipAddress, port, username, password, rtspPath);
        }
    }
    
    /**
     * Shows the dialog and returns the result.
     */
    public static Optional<CameraConfiguration> showDialog(CameraService cameraService) {
        AddCameraDialog dialog = new AddCameraDialog(cameraService);
        return dialog.showAndWait();
    }
}
