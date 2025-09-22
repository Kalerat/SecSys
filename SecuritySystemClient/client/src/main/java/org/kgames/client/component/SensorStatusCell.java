package org.kgames.client.component;

import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Insets;
import org.kgames.client.model.SensorStatus;

/**
 * Custom ListCell for displaying SensorStatus objects with proper styling.
 */
public class SensorStatusCell extends ListCell<SensorStatus> {
    
    private HBox content;
    private VBox textContainer;
    private Label statusLabel;
    private Label timestampLabel;
    
    public SensorStatusCell() {
        super();
        createContent();
    }
    
    private void createContent() {
        // Main container
        content = new HBox();
        content.setSpacing(10);
        content.setPadding(new Insets(8, 12, 8, 12));
        
        // Container for status and timestamp
        textContainer = new VBox();
        textContainer.setSpacing(2);
        HBox.setHgrow(textContainer, Priority.ALWAYS);
        
        // Status label
        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        
        // Timestamp label
        timestampLabel = new Label();
        timestampLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");
        
        textContainer.getChildren().addAll(statusLabel, timestampLabel);
        content.getChildren().add(textContainer);
    }
    
    @Override
    protected void updateItem(SensorStatus sensorStatus, boolean empty) {
        super.updateItem(sensorStatus, empty);
        
        if (empty || sensorStatus == null) {
            setGraphic(null);
            setText(null);
        } else {
            updateContent(sensorStatus);
            setGraphic(content);
            setText(null);
        }
    }
    
    private void updateContent(SensorStatus sensorStatus) {
        // Update text labels
        statusLabel.setText(sensorStatus.getDisplayString());
        timestampLabel.setText("Last updated: " + sensorStatus.getFormattedLastUpdate());
        
        // Apply styling based on status
        String backgroundColor;
        String textColor = "#ffffff";
        
        switch (sensorStatus.getStatus()) {
            case ACTIVE:
                backgroundColor = "#4CAF50"; // Green
                break;
            case INACTIVE:
                backgroundColor = "#FF9800"; // Orange  
                textColor = "#000000"; // Black text for better contrast on orange
                break;
            case ERROR:
                backgroundColor = "#F44336"; // Red
                break;
            case UNKNOWN:
            default:
                backgroundColor = "#9E9E9E"; // Gray
                break;
        }
        
        // Apply the background color and text color
        content.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-background-radius: 6px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 2, 0, 0, 1);",
            backgroundColor
        ));
        
        statusLabel.setStyle(String.format(
            "-fx-font-size: 13px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: %s;",
            textColor
        ));
        
        // Adjust timestamp color for better visibility
        String timestampColor = "#000000".equals(textColor) ? "#333333" : "#cccccc";
        timestampLabel.setStyle(String.format(
            "-fx-font-size: 11px; " +
            "-fx-text-fill: %s;",
            timestampColor
        ));
    }
}
