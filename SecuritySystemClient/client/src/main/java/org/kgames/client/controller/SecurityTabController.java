package org.kgames.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.Priority;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.util.Duration;
import org.kgames.client.model.AccessLog;
import org.kgames.client.model.CameraConfiguration;
import org.kgames.client.model.SensorStatus;
import org.kgames.client.service.MultiCameraService;
import org.kgames.client.service.SensorStatusService;
import org.kgames.client.service.AlarmService;
import org.kgames.client.component.CameraFeedComponent;
import org.kgames.client.component.SensorStatusCell;
import org.kgames.client.dialog.AddCameraDialog;
import org.kgames.client.dialog.AlarmDisableDialog;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Optional;

/**
 * Controller for the Security tab.
 * Handles multiple camera feeds, access logs, and sensor status.
 */
public class SecurityTabController extends BaseTabController implements Initializable, MultiCameraService.MultiCameraFrameListener {

    // Camera Controls
    @FXML private GridPane cameraFeedsContainer;
    @FXML private ScrollPane cameraScrollPane;
    @FXML private Label cameraStatusLabel;
    @FXML private Button addCameraButton;
    
    // Access Log Controls
    @FXML private TableView<org.kgames.client.model.AccessLog> accessLogTable;
    @FXML private TableColumn<org.kgames.client.model.AccessLog, String> timeColumn;
    @FXML private TableColumn<org.kgames.client.model.AccessLog, String> userColumn;
    @FXML private TableColumn<org.kgames.client.model.AccessLog, String> actionColumn;
    
    // Access Log Filter Controls
    @FXML private TextField accessLogSearchField;
    @FXML private ComboBox<String> accessResultFilterComboBox;
    @FXML private ComboBox<String> timeRangeFilterComboBox;
    @FXML private Button clearAccessLogFiltersButton;
    @FXML private Label accessLogFilterStatusLabel;
    @FXML private Label accessLogCountLabel;
    @FXML private Button previousPageButton;
    @FXML private Label pageInfoLabel;
    @FXML private Button nextPageButton;
    
    // Sensor Status
    @FXML private ListView<SensorStatus> sensorStatusList;
    
    // Alarm Control
    @FXML private Label alarmStatusLabel;
    @FXML private Label alarmMessageLabel;
    @FXML private Label alarmCountdownLabel;
    @FXML private Button activateAlarmButton;
    @FXML private Button disableAlarmButton;
    
    // Access Log Controller
    private AccessLogController accessLogController;
    
    // Access Log Refresh
    private javafx.animation.Timeline accessLogRefreshTimeline;
    private boolean isTabActive = false;
    
    // Camera management
    private final List<CameraFeedComponent> cameraFeedComponents = new ArrayList<>();
    private final int MAX_CAMERAS = 4;
    
    // Background camera detection
    private volatile boolean cameraDetectionInProgress = false;
    private javafx.concurrent.ScheduledService<Void> cameraDetectionService;
    
    // Sensor status management
    private final ObservableList<SensorStatus> sensorStatusData = FXCollections.observableArrayList();
    
    // Alarm management
    private AlarmService alarmService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupAccessLogTable();
        initializeCameraFeeds();
        
        // Add resize listeners to update camera feed sizing when window resizes
        Platform.runLater(() -> {
            try {
                if (cameraScrollPane != null && cameraScrollPane.getScene() != null) {
                    // Scene-level listeners for window resize
                    cameraScrollPane.getScene().widthProperty().addListener(
                        (_, _, _) -> scheduleResizeUpdate()
                    );
                    cameraScrollPane.getScene().heightProperty().addListener(
                        (_, _, _) -> scheduleResizeUpdate()
                    );
                    
                    // ScrollPane-level listeners for immediate layout changes
                    cameraScrollPane.widthProperty().addListener(
                        (_, _, _) -> scheduleResizeUpdate()
                    );
                    cameraScrollPane.heightProperty().addListener(
                        (_, _, _) -> scheduleResizeUpdate()
                    );
                } else {
                    // If scene is not available yet, schedule a retry
                    javafx.animation.Timeline retryTimeline = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(100), _ -> {
                            if (cameraScrollPane != null && cameraScrollPane.getScene() != null) {
                                // Scene-level listeners for window resize
                                cameraScrollPane.getScene().widthProperty().addListener(
                                    (_, _, _) -> scheduleResizeUpdate()
                                );
                                cameraScrollPane.getScene().heightProperty().addListener(
                                    (_, _, _) -> scheduleResizeUpdate()
                                );
                                
                                // ScrollPane-level listeners for immediate layout changes
                                cameraScrollPane.widthProperty().addListener(
                                    (_, _, _) -> scheduleResizeUpdate()
                                );
                                cameraScrollPane.heightProperty().addListener(
                                    (_, _, _) -> scheduleResizeUpdate()
                                );
                            }
                        })
                    );
                    retryTimeline.play();
                }
            } catch (Exception e) {
                System.out.println("Could not add resize listener: " + e.getMessage());
            }
        });
    }
    
    @Override
    protected void onServicesInitialized() {
        // Set up multi-camera service
        if (multiCameraService != null) {
            multiCameraService.setFrameListener(this);
            loadExistingCameras();
            
            // Add a delayed resize to ensure proper sizing after everything is loaded
            javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(500), 
                    _ -> scheduleResizeUpdate())
            );
            timeline.play();
        }
        
        // Start background camera detection
        initializeBackgroundCameraDetection();
        
        setupAccessLogController();
        setupSensorStatus();
        setupAlarmControl();
    }
    
    /**
     * Sets up the access log controller and integrates it with the UI.
     */
    private void setupAccessLogController() {
        if (databaseService != null && databaseService.isConnected()) {
            try {
                accessLogController = new AccessLogController(databaseService);
                accessLogTable.setItems(accessLogController.getAccessLogList());
                
                setupAccessLogTableColumns();
                accessLogController.applySorting(accessLogTable);

                setupAccessLogFilters();
                accessLogController.loadAccessLogsPage(0);
                
                updateAccessLogUI();
                
                setupAccessLogPeriodicRefresh();
                
                System.out.println("Access log controller setup completed successfully");
            } catch (Exception e) {
                System.err.println("Error setting up access log controller: " + e.getMessage());
                e.printStackTrace();
                if (accessLogTable != null) {
                    accessLogTable.setDisable(true);
                }
            }
        } else {
            System.err.println("Database service is not available or not connected for access log controller");
            if (accessLogTable != null) {
                accessLogTable.setDisable(true);
                accessLogTable.setPlaceholder(new javafx.scene.control.Label("Database not connected"));
            }
        }
    }
    
    /**
     * Sets up periodic refresh for access logs.
     */
    private void setupAccessLogPeriodicRefresh() {
        if (accessLogRefreshTimeline != null) {
            accessLogRefreshTimeline.stop();
        }
        
        // Create a timeline that refreshes access logs
        accessLogRefreshTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(5),
                _ -> {
                    System.out.println("Access log refresh timeline triggered - isTabActive: " + isTabActive + ", accessLogController: " + (accessLogController != null));
                    // Refresh if we have an accessLogController, prioritizing active tabs but still working for inactive ones
                    if (accessLogController != null) {
                        try {
                            System.out.println("Executing access log refresh...");
                            // Preserve current page and filters when refreshing
                            int currentPage = accessLogController.getCurrentPage();
                            System.out.println("Current page: " + currentPage);
                            accessLogController.loadAccessLogsPage(currentPage);
                            
                            // Only update UI if tab is active to avoid unnecessary UI updates
                            if (isTabActive) {
                                updateAccessLogUI();
                                System.out.println("Access logs automatically refreshed successfully (with UI update)");
                            } else {
                                System.out.println("Access logs automatically refreshed successfully (data only, no UI update)");
                            }
                        } catch (Exception e) {
                            System.err.println("Error during automatic access log refresh: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            )
        );
        
        // Make the timeline repeat indefinitely
        accessLogRefreshTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        accessLogRefreshTimeline.play();
        System.out.println("Access log periodic refresh started with 5 second interval - Timeline status: " + accessLogRefreshTimeline.getStatus());
    }
    
    /**
     * Sets up the access log table columns with proper cell value factories.
     */
    private void setupAccessLogTableColumns() {
        timeColumn.setCellValueFactory(cellData -> 
            javafx.beans.binding.Bindings.createStringBinding(() -> 
                cellData.getValue().getFormattedTimestamp()));
        
        userColumn.setCellValueFactory(cellData -> 
            javafx.beans.binding.Bindings.createStringBinding(() -> cellData.getValue().getUserName()));
        
        actionColumn.setCellValueFactory(cellData -> 
            javafx.beans.binding.Bindings.createStringBinding(() -> {
                AccessLog log = cellData.getValue();
                String result = log.getAccessResult() != null ? log.getAccessResult().getDisplayName() : "Unknown";
                String reason = log.getReason() != null ? log.getReason() : "";
                return result + (reason.isEmpty() ? "" : " - " + reason);
            }));
        
        accessLogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        javafx.application.Platform.runLater(() -> {
            if (accessLogTable.getParent() != null) {
                accessLogTable.prefWidthProperty().bind(
                    ((javafx.scene.layout.Region) accessLogTable.getParent()).widthProperty()
                );
            }
        });
    }
    
    /**
     * Sets up the access log filter controls.
     */
    private void setupAccessLogFilters() {
        accessResultFilterComboBox.getItems().addAll("All", "GRANTED", "DENIED");
        accessResultFilterComboBox.setValue("All");
        
        timeRangeFilterComboBox.getItems().addAll(
            "All Time", "Last Hour", "Last 24 Hours", "Last Week", "Last Month"
        );
        timeRangeFilterComboBox.setValue("All Time");
    }
    
    /**
     * Sets up sensor status display.
     */
    private void setupSensorStatus() {

        sensorStatusList.setCellFactory(_ -> new SensorStatusCell());
        sensorStatusList.setItems(sensorStatusData);
        
        // Set up sensor status service if available
        if (sensorStatusService != null) {
            sensorStatusService.addListener(new SensorStatusService.SensorStatusListener() {
                @Override
                public void onSensorStatusUpdated(SensorStatus.SensorType sensorType, SensorStatus status) {
                    Platform.runLater(() -> updateSensorStatusInList(status));
                }
                
                @Override
                public void onSensorStatusRemoved(SensorStatus.SensorType sensorType) {
                    Platform.runLater(() -> removeSensorStatusFromList(sensorType));
                }
            });
            
            // Load initial sensor statuses
            Platform.runLater(() -> {
                sensorStatusData.clear();
                sensorStatusData.addAll(sensorStatusService.getAllSensorStatuses());
            });
            
            startSensorTimeoutChecker();
        } else {
            // Add placeholder status if service is not available
            sensorStatusData.add(new SensorStatus(
                SensorStatus.SensorType.NETWORK, 
                SensorStatus.Status.ERROR, 
                "Sensor Status Service not available"
            ));
        }
    }
    
    /**
     * Updates or adds a sensor status in the list.
     */
    private void updateSensorStatusInList(SensorStatus updatedStatus) {
        // Find existing status by sensor type
        int existingIndex = -1;
        for (int i = 0; i < sensorStatusData.size(); i++) {
            if (sensorStatusData.get(i).getSensorType() == updatedStatus.getSensorType()) {
                existingIndex = i;
                break;
            }
        }
        
        if (existingIndex >= 0) {
            // Update existing status
            sensorStatusData.set(existingIndex, updatedStatus);
        } else {
            // Add new status
            sensorStatusData.add(updatedStatus);
        }
    }
    
    /**
     * Removes a sensor status from the list.
     */
    private void removeSensorStatusFromList(SensorStatus.SensorType sensorType) {
        sensorStatusData.removeIf(status -> status.getSensorType() == sensorType);
    }
    
    /**
     * Starts a background thread to periodically check for sensor timeouts.
     */
    private void startSensorTimeoutChecker() {
        if (sensorStatusService == null) return;
        
        Thread timeoutChecker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    sensorStatusService.checkForTimeouts();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error in sensor timeout checker: " + e.getMessage());
                }
            }
        });
        timeoutChecker.setDaemon(true);
        timeoutChecker.setName("SensorTimeoutChecker");
        timeoutChecker.start();
    }
    
    /**
     * Initializes the camera feeds container with empty state and add camera button functionality.
     */
    private void initializeCameraFeeds() {
        if (cameraFeedsContainer != null) {
            cameraFeedsContainer.getChildren().clear();
            updateCameraStatus("Ready to add cameras");
        }
    }

    /**
     * Loads existing cameras from configuration and creates feed components for them.
     */
    private void loadExistingCameras() {
        if (multiCameraService == null) {
            return;
        }
        multiCameraService.loadConfiguredCameras();
        List<CameraConfiguration> savedCameras = multiCameraService.getAllCameras();
        
        for (CameraConfiguration camera : savedCameras) {
            addCameraFeedComponent(camera);
        }

        updateCameraStatus("Loaded " + savedCameras.size() + " saved cameras");
    }

    /**
     * Initializes background camera detection to cache available cameras without blocking the UI.
     * This runs on app startup and periodically refreshes the camera list.
     */
    private void initializeBackgroundCameraDetection() {
        if (cameraService == null) {
            return;
        }

        // Create a scheduled service for periodic camera detection
        cameraDetectionService = new ScheduledService<Void>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        if (!cameraDetectionInProgress) {
                            cameraDetectionInProgress = true;
                            try {
                                // Refresh available cameras in background
                                cameraService.refreshAvailableCameras();
                                Platform.runLater(() -> 
                                    updateCameraStatus("Camera detection updated (" + 
                                        cameraService.getAvailableCameras().size() + " found)")
                                );
                            } catch (Exception e) {
                                System.err.println("Background camera detection failed: " + e.getMessage());
                            } finally {
                                cameraDetectionInProgress = false;
                            }
                        }
                        return null;
                    }
                };
            }
        };

        // Set up the service to run every 2 minutes
        cameraDetectionService.setPeriod(Duration.minutes(2));
        cameraDetectionService.setRestartOnFailure(true);

        // Run initial detection immediately in background
        new Thread(() -> {
            try {
                cameraDetectionInProgress = true;
                updateCameraStatus("Detecting available cameras...");
                cameraService.refreshAvailableCameras();
                Platform.runLater(() -> {
                    updateCameraStatus("Camera detection complete (" + 
                        cameraService.getAvailableCameras().size() + " found)");
                    // Start the periodic service after initial detection
                    cameraDetectionService.start();
                });
            } catch (Exception e) {
                System.err.println("Initial camera detection failed: " + e.getMessage());
                Platform.runLater(() -> updateCameraStatus("Camera detection failed"));
            } finally {
                cameraDetectionInProgress = false;
            }
        }).start();
    }

    /**
     * Adds a new camera feed component to the UI.
     */
    private void addCameraFeedComponent(CameraConfiguration camera) {
        if (cameraFeedComponents.size() >= MAX_CAMERAS) {
            showError("Camera Limit", "Maximum of " + MAX_CAMERAS + " cameras supported");
            return;
        }

        CameraFeedComponent feedComponent = new CameraFeedComponent(camera);
        feedComponent.setListener(new CameraFeedComponent.CameraFeedListener() {
            @Override
            public void onStartRecording(String cameraId) {
                feedComponent.setRecording(true);
                feedComponent.updateStatus("Starting recording...");

                new Thread(() -> {
                    boolean success = multiCameraService.startRecording(cameraId);
                    if (!success) {
                        // If recording failed, revert UI state
                        Platform.runLater(() -> {
                            feedComponent.setRecording(false);
                            feedComponent.updateStatus("Recording failed");
                        });
                    }
                    // If successful, status will be updated via the camera status change callback
                }).start();
            }

            @Override
            public void onStopRecording(String cameraId) { 
                feedComponent.setRecording(false);
                feedComponent.updateStatus("Stopping recording...");
                
                // Run stop operation on background thread
                new Thread(() -> {
                    multiCameraService.stopRecording(cameraId);
                }).start();
            }

            @Override
            public void onTakeScreenshot(String cameraId) {
                multiCameraService.saveScreenshot(cameraId);
            }

            @Override
            public void onRemoveCamera(String cameraId) {
                removeCameraFeedComponent(feedComponent);
            }
        });
        
        cameraFeedComponents.add(feedComponent);
        
        addCameraToGrid(feedComponent);
        scheduleResizeUpdate(); // Use scheduled approach for consistency
        multiCameraService.startCamera(camera.getId());
        
        updateCameraStatus("Camera added: " + camera.getName());
    }

    /**
     * Adds a camera feed component to the grid with proper positioning.
     */
    private void addCameraToGrid(CameraFeedComponent feedComponent) {
        int cameraCount = cameraFeedComponents.size();
        int[] position = getCameraGridPosition(cameraCount - 1);
        
        cameraFeedsContainer.add(feedComponent, position[0], position[1]);
        
        // Single Camera should fill entire Grid
        if (cameraCount == 1) {
            GridPane.setHgrow(feedComponent, Priority.ALWAYS);
            GridPane.setVgrow(feedComponent, Priority.ALWAYS);
            GridPane.setFillWidth(feedComponent, true);
            GridPane.setFillHeight(feedComponent, true);
        } else {
            // Multiple Cameras size themselves appropriately
            GridPane.setHgrow(feedComponent, Priority.SOMETIMES);
            GridPane.setVgrow(feedComponent, Priority.SOMETIMES);
        }
    }
    
    /**
     * Gets the grid position (column, row) for a camera index based on camera count.
     */
    private int[] getCameraGridPosition(int cameraIndex) {
        int totalCameras = cameraFeedComponents.size();
        
        if (totalCameras == 1) {
            // Single camera: center position, spans full grid
            return new int[]{0, 0};
        } else if (totalCameras == 2) {
            // Two cameras: side by side (column 0 and 1, row 0)
            return new int[]{cameraIndex, 0};
        } else if (totalCameras <= 4) {
            // 3-4 cameras: 2x2 grid layout
            // Camera 0: (0,0), Camera 1: (1,0), Camera 2: (0,1), Camera 3: (1,1)
            int col = cameraIndex % 2;
            int row = cameraIndex / 2;
            return new int[]{col, row};
        } else {
            // More than 4 cameras: Not yet supported
            // For now simply extend the grid as needed
            // This should never happen and is just here as failsafe
            int col = cameraIndex % 2;
            int row = cameraIndex / 2;
            return new int[]{col, row};
        }
    }
    
    /**
     * Rebuilds the entire grid layout when cameras are added or removed.
     */
    private void rebuildCameraGrid() {
        cameraFeedsContainer.getChildren().clear();
        
        for (int i = 0; i < cameraFeedComponents.size(); i++) {
            CameraFeedComponent component = cameraFeedComponents.get(i);
            int[] position = getCameraGridPosition(i);
            cameraFeedsContainer.add(component, position[0], position[1]);
        }
    }

    /**
     * Removes a camera feed component from the UI.
     */
    private void removeCameraFeedComponent(CameraFeedComponent component) {
        Platform.runLater(() -> {
            cameraFeedComponents.remove(component);
            
            // Stop the camera in the service
            String cameraId = component.getCameraConfig().getId();
            multiCameraService.stopCamera(cameraId);
            multiCameraService.removeCamera(cameraId);
            
            // Rebuild the grid with remaining cameras
            rebuildCameraGrid();
            scheduleResizeUpdate();
            updateCameraStatus("Camera removed");
        });
    }

    // Resize update timeline to prevent too frequent updates during window resizing
    private javafx.animation.Timeline resizeUpdateTimeline;
    
    /**
     * Schedules a resize update with a short delay to avoid excessive updates during rapid resize events.
     * This helps with window maximizing/minimizing issues where multiple resize events fire rapidly.
     */
    private void scheduleResizeUpdate() {
        if (resizeUpdateTimeline != null) {
            resizeUpdateTimeline.stop();
        }
        
        resizeUpdateTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(50), _ -> {
                updateCameraFeedSizing();
            })
        );
        
        resizeUpdateTimeline.setOnFinished(_ -> {
            Platform.runLater(this::updateCameraFeedSizing);
        });
        
        resizeUpdateTimeline.play();
    }

    /**
     * Updates the sizing of all camera feeds based on the current number of cameras.
     */
    private void updateCameraFeedSizing() {
        int cameraCount = cameraFeedComponents.size();
        if (cameraCount == 0) {
            return;
        }

        // Force layout updates to get accurate container dimensions
        if (cameraScrollPane != null) {
            cameraScrollPane.applyCss();
            cameraScrollPane.layout();
            
            // Also force the parent container to update its layout
            if (cameraScrollPane.getParent() != null) {
                cameraScrollPane.getParent().applyCss();
                cameraScrollPane.getParent().layout();
            }
        }
        
        // Default values should getting the container size fail
        double containerWidth = 800;
        double containerHeight = 600;
        
        try {
            if (cameraScrollPane != null) {
                // Try multiple approaches to get accurate dimensions
                double scrollWidth = cameraScrollPane.getWidth();
                double scrollHeight = cameraScrollPane.getHeight();
                
                // If dimensions are not yet available, try viewport bounds
                if (scrollWidth <= 100 && cameraScrollPane.getViewportBounds() != null) {
                    scrollWidth = cameraScrollPane.getViewportBounds().getWidth();
                    scrollHeight = cameraScrollPane.getViewportBounds().getHeight();
                }
                
                // If still not available, try the scene dimensions
                if (scrollWidth <= 100 && cameraScrollPane.getScene() != null) {
                    scrollWidth = cameraScrollPane.getScene().getWidth() * 0.5; // Approximate half for split pane
                    scrollHeight = cameraScrollPane.getScene().getHeight() * 0.8; // Most of the height
                }
                
                if (scrollWidth > 100) {

                    // Magic numbers to temporarily fix camera spacing
                    containerWidth = scrollWidth - 52;
                    containerHeight = scrollHeight - 167;
                }
            }
        } catch (Exception e) {
            System.out.println("Error calculating container dimensions, using defaults: " + e.getMessage());
        }

        // Calculate dimensions based on grid layout
        double feedWidth, feedHeight;
        
        if (cameraCount == 1) {
            setupGridConstraints(1, 1);
            feedWidth = containerWidth;  // Use full width for single camera
            feedHeight = containerHeight; // Use full height for single camera
        } else if (cameraCount == 2) {
            setupGridConstraints(2, 1);
            feedWidth = (containerWidth - 10) / 2; // Split width, account for 10px gap
            feedHeight = containerHeight; // Use full height for side-by-side layout
        } else if (cameraCount <= 4) {
            setupGridConstraints(2, 2);
            feedWidth = (containerWidth - 10) / 2; // Split width, account for 10px gap  
            feedHeight = (containerHeight - 10) / 2; // Split height, account for 10px gap
        } else {
            // More than 4 cameras - extend grid as needed
            // This should never happen and is just here as failsafe
            int cols = 2;
            int rows = (int) Math.ceil(cameraCount / 2.0);
            setupGridConstraints(cols, rows);
            feedWidth = (containerWidth - (cols - 1) * 10) / cols;
            feedHeight = (containerHeight - (rows - 1) * 10) / rows;
        }

        // Ensure reasonable sizes but respect container bounds
        // Only enforce minimums if container is large enough to accommodate them
        final double finalFeedWidth = Math.max(feedWidth, Math.min(120, containerWidth));
        final double finalFeedHeight = Math.max(feedHeight, Math.min(80, containerHeight));

        System.out.println("Updating camera feed sizing - Container: " + (int)containerWidth + "x" + (int)containerHeight + 
                          " Feed: " + (int)finalFeedWidth + "x" + (int)finalFeedHeight + " Cameras: " + cameraCount);

        for (CameraFeedComponent component : cameraFeedComponents) {
            component.setCameraFeedSize(finalFeedWidth, finalFeedHeight);
        }
    }
    
    /**
     * Sets up the grid constraints for the camera feeds container.
     */
    private void setupGridConstraints(int columns, int rows) {
        cameraFeedsContainer.getColumnConstraints().clear();
        cameraFeedsContainer.getRowConstraints().clear();
        
        // Single Camera should fill entire Space
        if (columns == 1 && rows == 1) {
            ColumnConstraints colConstraints = new ColumnConstraints();
            colConstraints.setHgrow(Priority.ALWAYS);
            colConstraints.setPercentWidth(100.0);
            cameraFeedsContainer.getColumnConstraints().add(colConstraints);
            
            RowConstraints rowConstraints = new RowConstraints();
            rowConstraints.setVgrow(Priority.ALWAYS);
            rowConstraints.setPercentHeight(100.0);
            cameraFeedsContainer.getRowConstraints().add(rowConstraints);
        } else {
            // Multiple cameras - use percentage-based distribution
            for (int i = 0; i < columns; i++) {
                ColumnConstraints colConstraints = new ColumnConstraints();
                colConstraints.setHgrow(Priority.ALWAYS);
                colConstraints.setPercentWidth(100.0 / columns);
                cameraFeedsContainer.getColumnConstraints().add(colConstraints);
            }
            
            for (int i = 0; i < rows; i++) {
                RowConstraints rowConstraints = new RowConstraints();
                rowConstraints.setVgrow(Priority.ALWAYS);
                rowConstraints.setPercentHeight(100.0 / rows);
                cameraFeedsContainer.getRowConstraints().add(rowConstraints);
            }
        }
    }

    /**
     * Handles the add camera button click.
     * Shows the add camera dialog with cached camera list and option to refresh.
     */
    @FXML
    private void onAddCameraButtonClick() {
        if (cameraFeedComponents.size() >= MAX_CAMERAS) {
            showError("Camera Limit", "Maximum of " + MAX_CAMERAS + " cameras supported");
            return;
        }

        // Check if we should refresh cameras (if it's been a while or user requests it)
        boolean shouldRefresh = !cameraDetectionInProgress && 
                               (cameraService.getAvailableCameras().isEmpty());

        if (shouldRefresh) {
            // Show progress while refreshing cameras
            updateCameraStatus("Refreshing camera list...");
            
            // Refresh cameras in background before showing dialog
            new Thread(() -> {
                try {
                    cameraDetectionInProgress = true;
                    cameraService.refreshAvailableCameras();
                    
                    Platform.runLater(() -> {
                        updateCameraStatus("Camera list updated");
                        showAddCameraDialog();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        updateCameraStatus("Camera refresh failed");
                        showAddCameraDialog(); // Show dialog anyway with cached results
                    });
                } finally {
                    cameraDetectionInProgress = false;
                }
            }).start();
        } else {
            // Use cached camera list - show dialog immediately
            showAddCameraDialog();
        }
    }

    /**
     * Shows the add camera dialog with current camera list.
     */
    private void showAddCameraDialog() {
        AddCameraDialog dialog = new AddCameraDialog(cameraService, true); // true = show all cameras
        Optional<CameraConfiguration> result = dialog.showAndWait();
        
        result.ifPresent(camera -> {
            multiCameraService.addNewCamera(camera);
            addCameraFeedComponent(camera);
        });
    }

    private void setupAccessLogTable() {
        accessLogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }
    
    private void updateCameraStatus(String status) {
        if (cameraStatusLabel != null) {
            Platform.runLater(() -> cameraStatusLabel.setText(status));
        }
    }
    
    @Override
    public void onTabActivated() {
        System.out.println("SecurityTabController - onTabActivated() called");
        isTabActive = true;
        
        // Refresh access logs when tab becomes active
        if (accessLogController != null) {
            // Preserve current page when switching back to tab
            int currentPage = accessLogController.getCurrentPage();
            accessLogController.loadAccessLogsPage(currentPage);
            updateAccessLogUI();
        }
        
        // Resume periodic refresh if it was stopped
        if (accessLogRefreshTimeline != null && !accessLogRefreshTimeline.getStatus().equals(javafx.animation.Animation.Status.RUNNING)) {
            accessLogRefreshTimeline.play();
            System.out.println("Resumed access log periodic refresh - Timeline status: " + accessLogRefreshTimeline.getStatus());
        }
    }
    
    @Override
    public void onTabDeactivated() {
        isTabActive = false;
        
        // Pause periodic refresh to save resources when tab is not active
        if (accessLogRefreshTimeline != null) {
            accessLogRefreshTimeline.pause();
        }
    }
    
    @Override
    public void cleanup() {
        // Stop access log refresh timeline
        if (accessLogRefreshTimeline != null) {
            accessLogRefreshTimeline.stop();
            accessLogRefreshTimeline = null;
        }

        // Stop background camera detection service
        if (cameraDetectionService != null) {
            cameraDetectionService.cancel();
            cameraDetectionService = null;
        }

        if (multiCameraService != null) {
            multiCameraService.shutdown();
        }

        cameraFeedComponents.clear();
        
        if (alarmService != null) {
            alarmService.shutdown();
        }
    }    // MultiCameraFrameListener implementation
    
    @Override
    public void onCameraFrameUpdate(String cameraId, Image frame) {
        Platform.runLater(() -> {
            // Find the camera feed component for this camera ID and update its frame
            for (CameraFeedComponent component : cameraFeedComponents) {
                if (component.getCameraConfig().getId().equals(cameraId)) {
                    component.updateFrame(frame);
                    component.updateStatus("Connected");
                    break;
                }
            }
        });
    }
    
    @Override
    public void onCameraStatusChange(String cameraId, String status) {
        Platform.runLater(() -> {
            // Find the camera feed component for this camera ID and update its status
            String cameraName = cameraId; // Default fallback to ID
            for (CameraFeedComponent component : cameraFeedComponents) {
                if (component.getCameraConfig().getId().equals(cameraId)) {
                    component.updateStatus(status);
                    cameraName = component.getCameraConfig().getName(); // Use user-defined name
                    break;
                }
            }
            
            updateCameraStatus("Camera " + cameraName + ": " + status);
        });
    }
    
    @Override
    public void onCameraRecordingComplete(String cameraId, boolean success, String filePath) {
        Platform.runLater(() -> {
            // Find the camera feed component for this camera ID and update recording state
            String cameraName = cameraId; // Default fallback to ID
            for (CameraFeedComponent component : cameraFeedComponents) {
                if (component.getCameraConfig().getId().equals(cameraId)) {
                    component.setRecording(false);
                    cameraName = component.getCameraConfig().getName(); // Use user-defined name
                    if (success) {
                        component.updateStatus("Recording saved");
                    } else {
                        component.updateStatus("Recording failed");
                        showError("Recording Error", "Failed to save recording for camera " + cameraName);
                    }
                    break;
                }
            }
        });
    }
    
    /**
     * Loads access logs using the access log controller.
     */
    private void loadAccessLogs() {
        if (accessLogController != null) {
            // Load the first page to show latest logs
            accessLogController.loadAccessLogsPage(0);
            updateAccessLogUI();
        }
    }

    /**
     * Event handler for access log search field changes.
     */
    @FXML
    private void onAccessLogSearchChanged() { 
        if (accessLogController != null && accessLogSearchField != null) {
            String searchText = accessLogSearchField.getText();
            accessLogController.filterBySearch(searchText);
            updateAccessLogUI();
        }
    }

    /**
     * Event handler for access result filter changes.
     */
    @FXML 
    private void onAccessResultFilterChanged() { 
        if (accessLogController != null && accessResultFilterComboBox != null) {
            String selectedValue = accessResultFilterComboBox.getValue();
            AccessLog.AccessResult result = null;
            
            if ("GRANTED".equals(selectedValue)) {
                result = AccessLog.AccessResult.GRANTED;
            } else if ("DENIED".equals(selectedValue)) {
                result = AccessLog.AccessResult.DENIED;
            }
            
            accessLogController.filterByAccessResult(result);
            updateAccessLogUI();
        }
    }

    /**
     * Event handler for time range filter changes.
     */
    @FXML 
    private void onTimeRangeFilterChanged() { 
        if (accessLogController != null && timeRangeFilterComboBox != null) {
            String selectedValue = timeRangeFilterComboBox.getValue();
            AccessLogController.TimeRange timeRange;
            
            switch (selectedValue) {
                case "Last Hour":
                    timeRange = AccessLogController.TimeRange.LAST_HOUR;
                    break;
                case "Last Day":
                    timeRange = AccessLogController.TimeRange.LAST_24_HOURS;
                    break;
                case "Last Week":
                    timeRange = AccessLogController.TimeRange.LAST_WEEK;
                    break;
                case "Last Month":
                    timeRange = AccessLogController.TimeRange.LAST_MONTH;
                    break;
                default:
                    timeRange = AccessLogController.TimeRange.ALL_TIME;
                    break;
            }
            
            accessLogController.filterByTimeRange(timeRange);
            updateAccessLogUI();
        }
    }

    /**
     * Event handler for clear filters button.
     */
    @FXML 
    private void onClearAccessLogFilters() { 
        if (accessLogController != null) {
            accessLogController.clearFilters();
        }
        
        // Reset UI controls
        if (accessLogSearchField != null) accessLogSearchField.clear();
        if (timeRangeFilterComboBox != null) timeRangeFilterComboBox.setValue("All Time");
        if (accessResultFilterComboBox != null) accessResultFilterComboBox.getSelectionModel().clearSelection();
        loadAccessLogs();
        updateAccessLogUI();
    }
    
    /**
     * Event handler for previous page button.
     */
    @FXML 
    private void onPreviousPage() { 
        if (accessLogController != null) {
            accessLogController.loadPreviousPage();
            updateAccessLogUI();
        }
    }
    
    /**
     * Event handler for next page button.
     */
    @FXML 
    private void onNextPage() { 
        if (accessLogController != null) {
            accessLogController.loadNextPage();
            updateAccessLogUI();
        }
    }

    /**
     * Updates the access log UI elements.
     */
    private void updateAccessLogUI() {
        Platform.runLater(() -> {
            if (accessLogController != null) {
                int currentPage = accessLogController.getCurrentPage();
                int totalItems = accessLogController.getAccessLogList().size();
                
                pageInfoLabel.setText("Page " + (currentPage + 1));
                accessLogCountLabel.setText("(" + totalItems + " logs)");
                accessLogFilterStatusLabel.setText("Showing access logs");
                
                previousPageButton.setDisable(currentPage == 0);
                nextPageButton.setDisable(!accessLogController.hasMorePages());
            }
        });
    }
    
    /**
     * Sets up alarm control functionality.
     */
    private void setupAlarmControl() {
        if (mqttService != null && clientConfig != null) {
            alarmService = new AlarmService(mqttService, clientConfig);
            
            // Add listener for alarm state changes
            alarmService.addListener(new AlarmService.AlarmStateListener() {
                @Override
                public void onAlarmStateChanged(AlarmService.AlarmState state, String message, boolean manuallyActivated) {
                    updateAlarmDisplay(state, message, manuallyActivated);
                }
                
                @Override
                public void onAlarmDisableCountdown(long remainingMinutes) {
                    updateCountdownDisplay(remainingMinutes);
                }
            });
            
            // Initial display update
            updateAlarmDisplay(alarmService.getCurrentState(), alarmService.getLastMessage(), alarmService.isManuallyActivated());
        } else {
            // No MQTT - disable alarm controls
            alarmStatusLabel.setText("OFFLINE");
            alarmStatusLabel.setStyle("-fx-text-fill: #9e9e9e;");
            alarmMessageLabel.setText("MQTT connection not available");
            activateAlarmButton.setDisable(true);
            disableAlarmButton.setDisable(true);
        }
    }
    
    /**
     * Handle alarm activation button click.
     */
    @FXML
    private void onActivateAlarmClick() {
        if (alarmService != null) {
            alarmService.activateAlarm();
        }
    }
    
    /**
     * Handle alarm disable/enable button click.
     */
    @FXML
    private void onDisableAlarmClick() {
        if (alarmService == null) return;
        
        AlarmService.AlarmState currentState = alarmService.getCurrentState();
        
        if (currentState == AlarmService.AlarmState.ALARM_DISABLED) {
            // Re-enable the alarm immediately
            alarmService.enableAlarm();
        } else if (currentState == AlarmService.AlarmState.ALARM_ACTIVE) {
            // Show disable/reset dialog for active alarm
            Optional<AlarmService.DisableMode> result = AlarmDisableDialog.showDialog(
                activateAlarmButton.getScene().getWindow(), true // Include reset option
            );
            
            result.ifPresent(mode -> {
                alarmService.disableAlarm(mode);
            });
        } else {
            // For READY state, show disable dialog (without reset option)
            Optional<AlarmService.DisableMode> result = AlarmDisableDialog.showDialog(
                activateAlarmButton.getScene().getWindow(), false // Exclude reset option
            );
            
            result.ifPresent(mode -> {
                alarmService.disableAlarm(mode);
            });
        }
    }
    
    /**
     * Update alarm display based on current state.
     */
    private void updateAlarmDisplay(AlarmService.AlarmState state, String message, boolean manuallyActivated) {
        Platform.runLater(() -> {
            alarmStatusLabel.setText(state.getDisplayName());
            alarmStatusLabel.setStyle("-fx-text-fill: " + state.getColor() + ";");
            
            String displayMessage = message;
            if (manuallyActivated && state == AlarmService.AlarmState.ALARM_ACTIVE) {
                displayMessage += " (Manual)";
            }
            alarmMessageLabel.setText(displayMessage);
            
            boolean isActive = (state == AlarmService.AlarmState.ALARM_ACTIVE);
            boolean isDisabled = (state == AlarmService.AlarmState.ALARM_DISABLED);
            
            activateAlarmButton.setDisable(isActive);
            disableAlarmButton.setDisable(false);
            
            if (isActive) {
                activateAlarmButton.setText("Alarm Active");
                disableAlarmButton.setText("Disable Alarm");
            } else if (isDisabled) {
                activateAlarmButton.setText("Activate Alarm");
                disableAlarmButton.setText("Re-enable Now");
            } else {
                activateAlarmButton.setText("Activate Alarm");
                disableAlarmButton.setText("Disable Alarm");
            }
        });
    }
    
    /**
     * Update countdown display for timed disable.
     */
    private void updateCountdownDisplay(long remainingMinutes) {
        Platform.runLater(() -> {
            if (remainingMinutes > 0) {
                long hours = remainingMinutes / 60;
                long mins = remainingMinutes % 60;
                
                String countdownText;
                if (hours > 0) {
                    countdownText = String.format("Re-enables in %dh %dm", hours, mins);
                } else {
                    countdownText = String.format("Re-enables in %dm", mins);
                }
                
                alarmCountdownLabel.setText(countdownText);
                alarmCountdownLabel.setVisible(true);
            } else {
                alarmCountdownLabel.setVisible(false);
            }
        });
    }
    
    /**
     * Handle MQTT messages related to alarm system.
     */
    public void handleAlarmMqttMessage(String topic, String message) {
        if (alarmService != null) {
            alarmService.handleMqttMessage(topic, message);
        }
    }
}
