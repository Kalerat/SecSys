package org.kgames.client.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.kgames.client.service.AlarmService;

import java.util.Optional;

/**
 * Dialog for selecting alarm disable duration.
 */
public class AlarmDisableDialog extends Dialog<AlarmService.DisableMode> {
    
    private ComboBox<AlarmService.DisableMode> durationComboBox;
    private Label warningLabel;
    private boolean includeResetOption;
    
    public AlarmDisableDialog(Window owner) {
        this(owner, true);
    }
    
    public AlarmDisableDialog(Window owner, boolean includeResetOption) {
        this.includeResetOption = includeResetOption;
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("Alarm Control");
        setHeaderText("Select action for the alarm system");
        
        createContent();
        
        ButtonType actionButtonType = new ButtonType("Apply Action", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(actionButtonType, cancelButtonType);
        
        // Style the dialog
        getDialogPane().getStylesheets().add(
            getClass().getResource("/org/kgames/client/styles.css").toExternalForm()
        );
        getDialogPane().getStyleClass().add("dialog-pane");
        
        // Enable/disable the action button depending on whether an option is selected
        Button actionButton = (Button) getDialogPane().lookupButton(actionButtonType);
        actionButton.setDisable(durationComboBox.getSelectionModel().getSelectedItem() == null);
        
        // Listener to enable action button when an option is selected
        durationComboBox.getSelectionModel().selectedItemProperty().addListener(
            (_, _, newSelection) -> {
                actionButton.setDisable(newSelection == null);
                updateWarningLabel(newSelection);
            }
        );
        
        // Update warning label for the preselected item
        if (durationComboBox.getSelectionModel().getSelectedItem() != null) {
            updateWarningLabel(durationComboBox.getSelectionModel().getSelectedItem());
        }
        
        // Convert the result when the action button is clicked
        setResultConverter(dialogButton -> {
            if (dialogButton == actionButtonType) {
                return durationComboBox.getSelectionModel().getSelectedItem();
            }
            return null;
        });
        
        durationComboBox.requestFocus();
    }
    
    private void createContent() {
        VBox content = new VBox();
        content.setSpacing(15);
        content.setPadding(new Insets(15));
        content.setPrefWidth(480);
        
        Label descriptionLabel = new Label(
            "Choose an action for the alarm system: Reset to immediately stop the alarm " +
            "and rearm the system, or disable for a specific time period."
        );
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("dialog-content-text");
        descriptionLabel.setPrefWidth(450);
        
        // Duration selection
        VBox durationBox = new VBox();
        durationBox.setSpacing(8);
        
        Label durationLabel = new Label("Action:");
        durationLabel.getStyleClass().add("dialog-label");
        
        durationComboBox = new ComboBox<>();
        // Add items based on whether reset option should be included
        if (includeResetOption) {
            durationComboBox.getItems().addAll(AlarmService.DisableMode.values());
        } else {
            // Filter out the RESET_REARM option when alarm is not active
            for (AlarmService.DisableMode mode : AlarmService.DisableMode.values()) {
                if (!mode.isReset()) {
                    durationComboBox.getItems().add(mode);
                }
            }
        }
        durationComboBox.setPromptText("Select action...");
        durationComboBox.setMaxWidth(Double.MAX_VALUE);
        
        // Preselect the first option
        if (!durationComboBox.getItems().isEmpty()) {
            durationComboBox.getSelectionModel().selectFirst();
        }
        
        // Cell factory to show display names
        durationComboBox.setCellFactory(_ -> new ListCell<AlarmService.DisableMode>() {
            @Override
            protected void updateItem(AlarmService.DisableMode mode, boolean empty) {
                super.updateItem(mode, empty);
                if (empty || mode == null) {
                    setText(null);
                } else {
                    setText(mode.getDisplayName());
                    if (mode.isReset()) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #4CAF50;");
                    } else if (mode.isPermanent()) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #f44336;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        // Set button cell to show display names
        durationComboBox.setButtonCell(new ListCell<AlarmService.DisableMode>() {
            @Override
            protected void updateItem(AlarmService.DisableMode mode, boolean empty) {
                super.updateItem(mode, empty);
                if (empty || mode == null) {
                    setText(null);
                } else {
                    setText(mode.getDisplayName());
                }
            }
        });
        
        durationBox.getChildren().addAll(durationLabel, durationComboBox);
        
        // Warning label
        warningLabel = new Label();
        warningLabel.setWrapText(true);
        warningLabel.getStyleClass().add("warning-text");
        warningLabel.setPrefWidth(450);
        warningLabel.setVisible(false);
        
        // Note label
        Label noteLabel = new Label(
            "Note: RFID authentication cannot disable a manually activated alarm. " +
            "Only manual deactivation through this app will work."
        );
        noteLabel.setWrapText(true);
        noteLabel.getStyleClass().add("info-text");
        noteLabel.setPrefWidth(450);
        
        content.getChildren().addAll(descriptionLabel, durationBox, warningLabel, noteLabel);
        
        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(520);
    }
    
    private void updateWarningLabel(AlarmService.DisableMode mode) {
        if (mode == null) {
            warningLabel.setVisible(false);
            return;
        }
        
        if (mode.isReset()) {
            warningLabel.setText(
                "Reset/Rearm: This will immediately stop the alarm and return the system " +
                "to ready state without any disable period. The system will be fully active."
            );
            warningLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        } else if (mode.isPermanent()) {
            warningLabel.setText(
                "WARNING: Permanent disable means the alarm will remain off until you " +
                "manually reactivate it. The system will not provide any security monitoring."
            );
            warningLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold;");
        } else {
            warningLabel.setText(
                String.format("The alarm will be automatically reactivated after %s.", 
                            mode.getDisplayName().toLowerCase())
            );
            warningLabel.setStyle("-fx-text-fill: #ff9800;");
        }
        warningLabel.setVisible(true);
    }
    
    /**
     * Show the dialog and return the selected disable mode.
     */
    public static Optional<AlarmService.DisableMode> showDialog(Window owner) {
        AlarmDisableDialog dialog = new AlarmDisableDialog(owner);
        return dialog.showAndWait();
    }
    
    /**
     * Show the dialog with filtered options and return the selected disable mode.
     */
    public static Optional<AlarmService.DisableMode> showDialog(Window owner, boolean includeResetOption) {
        AlarmDisableDialog dialog = new AlarmDisableDialog(owner, includeResetOption);
        return dialog.showAndWait();
    }
}
