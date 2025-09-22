package org.kgames.client.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the Configuration tab.
 * Handles system configuration settings, particularly media storage paths.
 */
public class ConfigurationTabController extends BaseTabController implements Initializable {

    // Configuration Tab Controls
    @FXML private CheckBox customPathsCheckBox;
    @FXML private TextField recordingsPathField;
    @FXML private TextField screenshotsPathField;
    @FXML private Button browseRecordingsButton;
    @FXML private Button browseScreenshotsButton;
    @FXML private Button saveConfigButton;
    @FXML private Button resetConfigButton;
    @FXML private Button validatePathsButton;
    @FXML private Label configStatusLabel;
    @FXML private TextArea configSummaryArea;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initial setup will be done when services are initialized
    }
    
    @Override
    protected void onServicesInitialized() {
        if (configurationService != null) {
            setupConfigurationTab();
            loadConfigurationSettings();
            updateConfigurationSummary();
        }
    }

    /**
     * Sets up the configuration tab UI and loads current settings.
     */
    private void setupConfigurationTab() {        
        if (customPathsCheckBox != null) {
            customPathsCheckBox.selectedProperty().addListener((_, _, newValue) -> {
                boolean customEnabled = newValue != null && newValue;
                recordingsPathField.setDisable(!customEnabled);
                screenshotsPathField.setDisable(!customEnabled);
                browseRecordingsButton.setDisable(!customEnabled);
                browseScreenshotsButton.setDisable(!customEnabled);
            });
        }
        
        updateConfigurationFieldStates();
    }

    /**
     * Loads current configuration settings into the UI fields.
     */
    private void loadConfigurationSettings() {
        if (recordingsPathField != null) {
            recordingsPathField.setText(configurationService.getRecordingsPath());
        }
        if (screenshotsPathField != null) {
            screenshotsPathField.setText(configurationService.getScreenshotsPath());
        }
        if (customPathsCheckBox != null) {
            customPathsCheckBox.setSelected(configurationService.isCustomPathsEnabled());
        }
        if (configStatusLabel != null) {
            configStatusLabel.setText("Configuration loaded");
        }
    }

    /**
     * Updates the field states based on configuration settings.
     */
    private void updateConfigurationFieldStates() {
        boolean customEnabled = configurationService.isCustomPathsEnabled();
        if (recordingsPathField != null) recordingsPathField.setDisable(!customEnabled);
        if (screenshotsPathField != null) screenshotsPathField.setDisable(!customEnabled);
        if (browseRecordingsButton != null) browseRecordingsButton.setDisable(!customEnabled);
        if (browseScreenshotsButton != null) browseScreenshotsButton.setDisable(!customEnabled);
    }

    /**
     * Updates the configuration summary text area.
     */
    private void updateConfigurationSummary() {
        if (configSummaryArea != null) {
            StringBuilder summary = new StringBuilder();
            summary.append("Current Configuration:\n\n");
            summary.append("Custom Paths: ").append(configurationService.isCustomPathsEnabled() ? "Enabled" : "Disabled").append("\n");
            summary.append("Recordings Directory: ").append(configurationService.getRecordingsPath()).append("\n");
            summary.append("Screenshots Directory: ").append(configurationService.getScreenshotsPath()).append("\n\n");
            
            summary.append("Storage Information:\n");
            File recordingsDir = new File(configurationService.getRecordingsPath());
            File screenshotsDir = new File(configurationService.getScreenshotsPath());
            
            summary.append("Recordings exists: ").append(recordingsDir.exists() ? "Yes" : "No").append("\n");
            summary.append("Screenshots exists: ").append(screenshotsDir.exists() ? "Yes" : "No").append("\n");
            
            if (recordingsDir.exists()) {
                File[] recordings = recordingsDir.listFiles((_, name) -> name.toLowerCase().endsWith(".avi"));
                summary.append("Recording files: ").append(recordings != null ? recordings.length : 0).append("\n");
            }
            
            if (screenshotsDir.exists()) {
                File[] screenshots = screenshotsDir.listFiles((_, name) -> 
                    name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));
                summary.append("Screenshot files: ").append(screenshots != null ? screenshots.length : 0).append("\n");
            }
            
            configSummaryArea.setText(summary.toString());
        }
    }

    /**
     * Handles the custom paths checkbox toggle.
     */
    @FXML
    private void onCustomPathsToggled() {
        boolean customEnabled = customPathsCheckBox.isSelected();
        configurationService.setCustomPathsEnabled(customEnabled);
        updateConfigurationFieldStates();
        updateConfigurationSummary();
        updateConfigStatusLabel("Custom paths " + (customEnabled ? "enabled" : "disabled"));
    }

    /**
     * Handles browsing for recordings directory.
     */
    @FXML
    private void onBrowseRecordingsPath() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Recordings Directory");
        
        File currentDir = new File(configurationService.getRecordingsPath());
        if (currentDir.exists()) {
            directoryChooser.setInitialDirectory(currentDir.getParentFile());
        }
        
        File selectedDirectory = directoryChooser.showDialog(recordingsPathField.getScene().getWindow());
        if (selectedDirectory != null) {
            recordingsPathField.setText(selectedDirectory.getAbsolutePath());
            updateConfigStatusLabel("Recordings path updated");
        }
    }

    /**
     * Handles browsing for screenshots directory.
     */
    @FXML
    private void onBrowseScreenshotsPath() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Screenshots Directory");
        
        File currentDir = new File(configurationService.getScreenshotsPath());
        if (currentDir.exists()) {
            directoryChooser.setInitialDirectory(currentDir.getParentFile());
        }
        
        File selectedDirectory = directoryChooser.showDialog(screenshotsPathField.getScene().getWindow());
        if (selectedDirectory != null) {
            screenshotsPathField.setText(selectedDirectory.getAbsolutePath());
            updateConfigStatusLabel("Screenshots path updated");
        }
    }

    /**
     * Handles saving the configuration.
     */
    @FXML
    private void onSaveConfiguration() {
        try {
            configurationService.setRecordingsPath(recordingsPathField.getText());
            configurationService.setScreenshotsPath(screenshotsPathField.getText());
            
            configurationService.saveConfiguration();
            
            updateConfigurationSummary();
            updateConfigStatusLabel("Configuration saved successfully");
            
        } catch (Exception e) {
            updateConfigStatusLabel("Error saving configuration: " + e.getMessage());
            showError("Configuration Error", "Failed to save configuration: " + e.getMessage());
        }
    }

    /**
     * Handles resetting configuration to defaults.
     */
    @FXML
    private void onResetConfiguration() {
        try {
            configurationService.resetToDefaults();
            loadConfigurationSettings();
            updateConfigurationSummary();
            updateConfigStatusLabel("Configuration reset to defaults");
            
        } catch (Exception e) {
            updateConfigStatusLabel("Error resetting configuration: " + e.getMessage());
            showError("Configuration Error", "Failed to reset configuration: " + e.getMessage());
        }
    }

    /**
     * Handles validating the configured paths.
     */
    @FXML
    private void onValidatePaths() {
        try {
            boolean isValid = configurationService.validateMediaPaths();
            if (isValid) {
                updateConfigStatusLabel("All paths are valid and accessible");
                updateConfigurationSummary();
            } else {
                updateConfigStatusLabel("Some paths are invalid or inaccessible");
                showWarning("Path Validation", "Some configured paths are invalid or inaccessible. Please check the configuration summary for details.");
            }
        } catch (Exception e) {
            updateConfigStatusLabel("Error validating paths: " + e.getMessage());
            showError("Validation Error", "Failed to validate paths: " + e.getMessage());
        }
    }

    /**
     * Updates the configuration status label.
     */
    private void updateConfigStatusLabel(String message) {
        if (configStatusLabel != null) {
            configStatusLabel.setText(message);
        }
    }
    
    @Override
    public void onTabActivated() {
        // Refresh configuration when tab becomes active
        if (configurationService != null) {
            loadConfigurationSettings();
            updateConfigurationSummary();
        }
    }
}
