package org.kgames.client.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kgames.client.model.CameraConfiguration;

/**
 * Custom component representing a single camera feed with controls.
 */
public class CameraFeedComponent extends VBox {
    
    private final CameraConfiguration cameraConfig;
    private final ImageView cameraFeed;
    private final Label cameraNameLabel;
    private final Label statusLabel;
    private final Button recordButton;
    private final Button screenshotButton;
    private final Button removeButton;
    
    private CameraFeedListener listener;
    private boolean isRecording = false;
    
    /**
     * Interface for handling camera feed events.
     */
    public interface CameraFeedListener {
        void onStartRecording(String cameraId);
        void onStopRecording(String cameraId);
        void onTakeScreenshot(String cameraId);
        void onRemoveCamera(String cameraId);
    }
    
    public CameraFeedComponent(CameraConfiguration cameraConfig) {
        this.cameraConfig = cameraConfig;
        this.cameraFeed = new ImageView();
        this.cameraNameLabel = new Label(cameraConfig.getName());
        this.statusLabel = new Label("Disconnected");
        this.recordButton = new Button("Record");
        this.screenshotButton = new Button("Screenshot");
        this.removeButton = new Button("×");
        
        setupUI();
        setupEventHandlers();
    }
    
    private void setupUI() {
        setSpacing(1); // Reduced spacing from 2 to 1 for more video space
        setPadding(new Insets(2)); // Reduced padding from 3 to 2 for more video space
        setStyle("-fx-border-color: #444444; -fx-border-width: 0.5px; -fx-background-color: #2d2d2d;"); // Thinner border
        
        // Camera name and remove button header
        HBox headerBox = new HBox(5);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setMaxHeight(25);
        
        // Left spacer to center the title
        HBox leftSpacer = new HBox();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        headerBox.getChildren().add(leftSpacer);
        
        cameraNameLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
        headerBox.getChildren().add(cameraNameLabel);
        
        // Right spacer push remove button to the right
        HBox rightSpacer = new HBox();
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);
        headerBox.getChildren().add(rightSpacer);
        
        // Remove button
        removeButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; " +
                            "-fx-border-radius: 3px; -fx-background-radius: 3px; " +
                            "-fx-font-weight: bold; -fx-font-size: 12px;");
        removeButton.setPrefSize(20, 20);
        headerBox.getChildren().add(removeButton);
        
        // Camera feed
        cameraFeed.setPreserveRatio(true);
        cameraFeed.setSmooth(true);
        cameraFeed.setStyle("-fx-background-color: #1a1a1a;"); // Removed border for cleaner look
        
        // Initial size - will be updated by setCameraFeedSize()
        cameraFeed.setFitWidth(400);  // Increased from 300
        cameraFeed.setFitHeight(300); // Increased from 200 

        // Container to center the camera feed and expand to fill available space
        VBox cameraContainer = new VBox();
        cameraContainer.setAlignment(Pos.CENTER);
        cameraContainer.getChildren().add(cameraFeed);
        VBox.setVgrow(cameraContainer, Priority.ALWAYS);
        cameraContainer.setMaxWidth(Double.MAX_VALUE);
        cameraContainer.setMaxHeight(Double.MAX_VALUE);
        
        // Bottom control section with status and buttons
        HBox bottomControlsBox = new HBox();
        bottomControlsBox.setAlignment(Pos.CENTER);
        bottomControlsBox.setMaxHeight(30);
        bottomControlsBox.setMinHeight(30);
        
        // Left section for status label
        HBox leftSection = new HBox();
        leftSection.setAlignment(Pos.CENTER_LEFT);
        leftSection.setPrefWidth(100);
        leftSection.setMaxWidth(100);
        
        // Status label
        statusLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 10px;");
        statusLabel.setMaxHeight(15);
        leftSection.getChildren().add(statusLabel);
        bottomControlsBox.getChildren().add(leftSection);
        
        // Center section for buttons
        HBox centerSection = new HBox(5);
        centerSection.setAlignment(Pos.CENTER);
        HBox.setHgrow(centerSection, Priority.ALWAYS);
        
        // Control buttons
        recordButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; " +
                            "-fx-border-radius: 3px; -fx-background-radius: 3px;");
        screenshotButton.setStyle("-fx-background-color: #4444ff; -fx-text-fill: white; " +
                                "-fx-border-radius: 3px; -fx-background-radius: 3px;");
        recordButton.setMinWidth(70);
        screenshotButton.setMinWidth(90);
        
        centerSection.getChildren().addAll(recordButton, screenshotButton);
        bottomControlsBox.getChildren().add(centerSection);
        
        // Right section to balance the layout for proper centering
        HBox rightSection = new HBox();
        rightSection.setAlignment(Pos.CENTER_RIGHT);
        rightSection.setPrefWidth(100);
        rightSection.setMaxWidth(100);
        bottomControlsBox.getChildren().add(rightSection);

        // Add all components to the main container (removed spacer for better space utilization)
        getChildren().addAll(headerBox, cameraContainer, bottomControlsBox);
        
        // Initially disable controls
        recordButton.setDisable(true);
        screenshotButton.setDisable(true);
    }

    /**
     * Sets up event handlers for the control buttons.
     */
    private void setupEventHandlers() {
        recordButton.setOnAction(_ -> {
            if (listener != null) {
                if (isRecording) {
                    listener.onStopRecording(cameraConfig.getId());
                } else {
                    listener.onStartRecording(cameraConfig.getId());
                }
            }
        });
        
        screenshotButton.setOnAction(_ -> {
            if (listener != null) {
                listener.onTakeScreenshot(cameraConfig.getId());
            }
        });
        
        removeButton.setOnAction(_ -> {
            if (listener != null) {
                listener.onRemoveCamera(cameraConfig.getId());
            }
        });
    }
    
    /**
     * Updates the camera feed image.
     */
    public void updateFrame(Image frame) {
        cameraFeed.setImage(frame);
        
        // Enable controls when we have a frame
        if (frame != null && recordButton.isDisabled()) {
            recordButton.setDisable(false);
            screenshotButton.setDisable(false);
        }
    }
    
    /**
     * Updates the camera status.
     */
    public void updateStatus(String status) {
        statusLabel.setText(status);
    }
    
    /**
     * Updates the recording state.
     */
    public void setRecording(boolean recording) {
        this.isRecording = recording;
        if (recording) {
            recordButton.setText("Stop");
            recordButton.setStyle("-fx-background-color: #ffaa00; -fx-text-fill: white; " +
                                "-fx-border-radius: 3px; -fx-background-radius: 3px;");
        } else {
            recordButton.setText("Record");
            recordButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; " +
                                "-fx-border-radius: 3px; -fx-background-radius: 3px;");
        }
    }
    
    /**
     * Sets the listener for camera feed events.
     */
    public void setListener(CameraFeedListener listener) {
        this.listener = listener;
    }
    
    /**
     * Gets the camera configuration.
     */
    public CameraConfiguration getCameraConfig() {
        return cameraConfig;
    }
    
    /**
     * Sets the preferred size for the camera feed component.
     */
    public void setCameraFeedSize(double width, double height) {
        setPrefWidth(width);
        setPrefHeight(height);
        setMaxWidth(width * 1.2);
        setMaxHeight(height * 1.2);
        setMinWidth(width * 0.8);
        setMinHeight(height * 0.8);
        
        // Calculate available space more accurately:
        // - Padding: 2px top + 2px bottom = 4px
        // - Header: 25px max height
        // - Bottom controls: 30px max height  
        // - Spacing: 1px between elements (3 elements = 2 spacings) = 2px
        // - Border: 0.5px top + 0.5px bottom = 1px
        // Total overhead: 4 + 25 + 30 + 2 + 1 = 62px
        
        double totalVerticalOverhead = 62; // Reduced overhead calculation with thinner borders/padding
        double totalHorizontalOverhead = 5; // Padding (2+2) + border (0.5+0.5) = 5px
        
        // Set camera feed size to use maximum available space
        // With preserveRatio=true, setting both dimensions ensures maximum utilization
        double feedWidth = Math.max(120, width - totalHorizontalOverhead);
        double feedHeight = Math.max(80, height - totalVerticalOverhead);
        
        System.out.println("Setting camera feed size for " + cameraConfig.getName() + 
                          " - Available: " + (int)width + "x" + (int)height + 
                          " Feed: " + (int)feedWidth + "x" + (int)feedHeight);
        
        // Calculate optimal size for the video based on container aspect ratio
        // This ensures we use maximum space while preserving aspect ratio
        double containerAspect = feedWidth / feedHeight;
        
        // For 4:3 cameras (aspect ~1.33), prioritize width
        // For 16:9 cameras (aspect ~1.78), use both dimensions
        if (containerAspect > 1.5) {
            // Wide container - set both dimensions to fill space
            cameraFeed.setFitWidth(feedWidth);
            cameraFeed.setFitHeight(feedHeight);
        } else {
            // More square container - prioritize width for 4:3 videos
            cameraFeed.setFitWidth(feedWidth);
            cameraFeed.setFitHeight(0); // Let height adjust automatically
        }
        
        System.out.println("Container aspect ratio: " + String.format("%.2f", containerAspect) + 
                          " Using fit strategy for optimal space usage");
    }
}
