package org.kgames.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the Media Browser tab.
 * Handles browsing, viewing, and managing recorded videos and screenshots.
 * Consolidated media browser functionality into the tab controller for better cohesion.
 */
public class MediaBrowserTabController extends BaseFilterableTabController<MediaBrowserTabController.MediaFile> implements Initializable {

    // UI Controls
    @FXML private ListView<MediaFile> mediaListView;
    @FXML private ImageView previewImageView;
    @FXML private Label mediaInfoLabel;
    @FXML private Label mediaCountLabel;
    @FXML private Button refreshButton;
    @FXML private Button openFileButton;
    @FXML private Button openFolderButton;
    @FXML private Button deleteFileButton;
    @FXML private Button exportFileButton;
    @FXML private ComboBox<String> mediaTypeFilter;
    @FXML private TextField nameFilterField;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private Button clearFiltersButton;
    @FXML private Label filterStatusLabel;
    @FXML private javafx.scene.layout.StackPane mediaContainer;
    
    // Video Player Controls
    @FXML private VBox videoPlayerContainer;
    @FXML private HBox videoControlsBox;
    @FXML private Button playPauseButton;
    @FXML private Button stopButton;
    @FXML private Slider volumeSlider;
    @FXML private Label timeLabel;

    // Data
    private Path currentMediaPath;
    
    // Video Player
    private MediaPlayer currentMediaPlayer;
    private MediaView mediaView;
    private int mediaPlayerRetryCount = 0;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private File currentVideoFile = null;
    
    // Supported file extensions
    private static final List<String> VIDEO_EXTENSIONS = Arrays.asList(".avi", ".mp4", ".mov", ".mkv");
    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(".png", ".jpg", ".jpeg", ".bmp", ".gif");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeFilterableData();
        setupMediaList();
        setupPreview();
        setupFilters();
        setupEventHandlers();
    }

    @Override
    protected void onServicesInitialized() {
        super.onServicesInitialized();
        loadCurrentMediaPath();
    }

    @Override
    public void onTabActivated() {
        super.onTabActivated();
        refreshData();
    }
    
    @Override
    public void onTabDeactivated() {
        super.onTabDeactivated();
        stopCurrentVideo();
    }

    /**
     * Sets up the media list view.
     */
    private void setupMediaList() {
        mediaListView.setItems(getFilteredItems());
        mediaListView.setCellFactory(_ -> new MediaFileCell());
        
        mediaListView.getSelectionModel().selectedItemProperty().addListener((_, _, newSelection) -> {
            updatePreview(newSelection);
            updateButtonStates(newSelection != null);
        });
    }

    /**
     * Sets up the preview area.
     */
    private void setupPreview() {
        previewImageView.setPreserveRatio(true);
        previewImageView.setSmooth(true);
        
        previewImageView.setFitWidth(400);
        previewImageView.setFitHeight(300);
        
        Platform.runLater(() -> {
            if (mediaContainer != null && mediaContainer.getScene() != null) {
                mediaContainer.getScene().widthProperty().addListener((_, _, _) -> updatePreviewSizing());
                mediaContainer.getScene().heightProperty().addListener((_, _, _) -> updatePreviewSizing());
                
                updatePreviewSizing();
            }
        });
        
        mediaInfoLabel.setText("Select a file to view details");
        
        setupVideoPlayer();
    }

    /**
     * Sets up filter controls.
     */
    private void setupFilters() {
        // Media type filter
        mediaTypeFilter.setItems(FXCollections.observableArrayList(
            "All Files", "Videos Only", "Images Only"
        ));
        mediaTypeFilter.setValue("All Files");
        
        // Date range filter
        dateRangeFilter.setItems(FXCollections.observableArrayList(
            "All Time", "Today", "Last 3 Days", "Last Week", "Last Month", "Last 3 Months"
        ));
        dateRangeFilter.setValue("All Time");
        
        Platform.runLater(() -> updateFilterStatus());
    }

    /**
     * Sets up event handlers.
     */
    private void setupEventHandlers() {
        mediaTypeFilter.valueProperty().addListener((_, _, _) -> applyFilters());
        dateRangeFilter.valueProperty().addListener((_, _, _) -> applyFilters());
        nameFilterField.textProperty().addListener((_, _, _) -> applyFilters());
    }

    /**
     * Loads the current media path from configuration.
     */
    private void loadCurrentMediaPath() {
        if (configurationService != null) {
            String mediaPath = configurationService.getRecordingsPath();
            currentMediaPath = Paths.get(mediaPath).getParent();
            
            try {
                Files.createDirectories(currentMediaPath);
            } catch (IOException e) {
                showError("Path Error", "Could not create media directory: " + e.getMessage());
            }
        }
    }

    // Button Event Handlers

    @FXML
    private void onRefreshButtonClick() {
        refreshMediaFiles();
    }

    @FXML
    private void onOpenFileButtonClick() {
        MediaFile selectedFile = mediaListView.getSelectionModel().getSelectedItem();
        if (selectedFile != null) {
            openFileWithDefaultApplication(selectedFile.getFile());
        }
    }

    @FXML
    private void onOpenFolderButtonClick() {
        if (currentMediaPath != null) {
            openFileWithDefaultApplication(currentMediaPath.toFile());
        }
    }

    @FXML
    private void onDeleteFileButtonClick() {
        MediaFile selectedFile = mediaListView.getSelectionModel().getSelectedItem();
        if (selectedFile != null) {
            deleteMediaFile(selectedFile);
        }
    }

    @FXML
    private void onExportFileButtonClick() {
        MediaFile selectedFile = mediaListView.getSelectionModel().getSelectedItem();
        if (selectedFile != null) {
            exportMediaFile(selectedFile);
        }
    }

    @FXML
    private void onClearFiltersButtonClick() {
        mediaTypeFilter.setValue("All Files");
        dateRangeFilter.setValue("All Time");
        nameFilterField.clear();
        applyFilters();
    }

    // Video Player Event Handlers
    
    @FXML
    private void onPlayPauseButtonClick() {
        if (currentMediaPlayer != null) {
            MediaPlayer.Status status = currentMediaPlayer.getStatus();
            if (status == MediaPlayer.Status.PLAYING) {
                currentMediaPlayer.pause();
                playPauseButton.setText("Play");
            } else if (status == MediaPlayer.Status.PAUSED || status == MediaPlayer.Status.READY || status == MediaPlayer.Status.STOPPED) {
                currentMediaPlayer.play();
                playPauseButton.setText("Pause");
            }
        }
    }
    
    @FXML
    private void onStopButtonClick() {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.stop();
            playPauseButton.setText("Play");
            updateTimeLabel(Duration.ZERO, currentMediaPlayer.getTotalDuration());
        }
    }

    @Override
    public void refreshData() {
        refreshMediaFiles();
    }

    // Media Management Methods

    /**
     * Refreshes the media file list from the current directory.
     */
    private void refreshMediaFiles() {
        if (currentMediaPath == null || !Files.exists(currentMediaPath)) {
            allItems.clear();
            applyFilters();
            return;
        }

        Platform.runLater(() -> {
            try {
                List<MediaFile> mediaFiles = Files.walk(currentMediaPath)
                    .filter(Files::isRegularFile)
                    .filter(this::isMediaFile)
                    .map(MediaFile::new)
                    .sorted((a, b) -> Long.compare(b.getFile().lastModified(), a.getFile().lastModified()))
                    .collect(Collectors.toList());

                allItems.setAll(mediaFiles);
                applyFilters();
                
            } catch (IOException e) {
                showError("File System Error", "Could not read media directory: " + e.getMessage());
            }
        });
    }

    /**
     * Applies current filters to the media file list.
     */
    public void applyFilters() {
        String typeFilter = mediaTypeFilter.getValue();
        String dateFilter = dateRangeFilter.getValue();
        String nameFilter = nameFilterField.getText();
        
        filteredItems.setPredicate(mediaFile -> {
            // Type filter
            switch (typeFilter) {
                case "Videos Only":
                    if (!isVideoFile(mediaFile.getPath())) return false;
                    break;
                case "Images Only":
                    if (!isImageFile(mediaFile.getPath())) return false;
                    break;
            }
            
            // Name filter
            if (nameFilter != null && !nameFilter.trim().isEmpty()) {
                if (!mediaFile.getFile().getName().toLowerCase()
                        .contains(nameFilter.toLowerCase().trim())) {
                    return false;
                }
            }
            
            // Date filter
            if (!"All Time".equals(dateFilter)) {
                long fileTime = mediaFile.getFile().lastModified();
                long currentTime = System.currentTimeMillis();
                long cutoffTime = getCutoffTimeForDateFilter(dateFilter, currentTime);
                if (fileTime < cutoffTime) return false;
            }
            
            return true;
        });

        mediaCountLabel.setText("(" + getFilteredItemCount() + " files)");
        updateFilterStatus();
    }

    /**
     * Updates the preview for the selected media file.
     */
    private void updatePreview(MediaFile mediaFile) {
        stopCurrentVideo();
        
        if (mediaFile == null) {
            previewImageView.setImage(null);
            previewImageView.setVisible(true);
            videoPlayerContainer.setVisible(false);
            mediaInfoLabel.setText("Select a file to view details");
            return;
        }

        // Update info label
        File file = mediaFile.getFile();
        String info = String.format(
            "File: %s\nSize: %s\nModified: %s\nType: %s",
            file.getName(),
            formatFileSize(file.length()),
            LocalDateTime.ofEpochSecond(file.lastModified() / 1000, 0, java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now()))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            isVideoFile(mediaFile.getPath()) ? "Video" : "Image"
        );
        mediaInfoLabel.setText(info);

        // Show appropriate preview
        if (isImageFile(mediaFile.getPath())) {
            showImagePreview(file);
        } else if (isVideoFile(mediaFile.getPath())) {
            showVideoPreview(file);
        }
    }

    /**
     * Updates button states based on selection.
     */
    private void updateButtonStates(boolean hasSelection) {
        openFileButton.setDisable(!hasSelection);
        deleteFileButton.setDisable(!hasSelection);
        exportFileButton.setDisable(!hasSelection);
    }

    /**
     * Opens a file or directory with the default system application.
     */
    private void openFileWithDefaultApplication(File file) {
        try {
            Desktop.getDesktop().open(file);
        } catch (IOException e) {
            showError("Open Error", "Could not open file: " + e.getMessage());
        }
    }

    /**
     * Deletes the selected media file.
     */
    private void deleteMediaFile(MediaFile mediaFile) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Delete File");
        confirmAlert.setHeaderText("Delete Media File");
        confirmAlert.setContentText("Are you sure you want to delete '" + mediaFile.getFile().getName() + "'?\nThis action cannot be undone.");

        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                Files.delete(mediaFile.getPath());
                refreshMediaFiles();
                showAlert("File Deleted", "Media file deleted successfully.");
            } catch (IOException e) {
                showError("Delete Error", "Could not delete file: " + e.getMessage());
            }
        }
    }

    /**
     * Exports the selected media file to a chosen location.
     */
    private void exportMediaFile(MediaFile mediaFile) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Media File");
        fileChooser.setInitialFileName(mediaFile.getFile().getName());
        
        File exportFile = fileChooser.showSaveDialog(exportFileButton.getScene().getWindow());
        if (exportFile != null) {
            try {
                Files.copy(mediaFile.getPath(), exportFile.toPath());
                showAlert("Export Successful", "Media file exported to: " + exportFile.getAbsolutePath());
            } catch (IOException e) {
                showError("Export Error", "Could not export file: " + e.getMessage());
            }
        }
    }

    // Video Player Methods
    
    /**
     * Sets up the video player components.
     */
    private void setupVideoPlayer() {
        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);
        
        mediaView.setFitWidth(400);
        mediaView.setFitHeight(300);
        
        // Setup volume slider
        if (volumeSlider != null) {
            volumeSlider.setValue(50.0);
            volumeSlider.valueProperty().addListener((_, _, newValue) -> {
                if (currentMediaPlayer != null) {
                    currentMediaPlayer.setVolume(newValue.doubleValue() / 100.0);
                }
            });
        }
    }
    
    /**
     * Shows image preview.
     */
    private void showImagePreview(File file) {
        stopCurrentVideo();
        
        previewImageView.setVisible(true);
        videoPlayerContainer.setVisible(false);
        
        try {
            Image image = new Image(file.toURI().toString());
            previewImageView.setImage(image);
        } catch (Exception e) {
            previewImageView.setImage(null);
            showError("Image Error", "Could not load image: " + e.getMessage());
        }
    }
    
    /**
     * Shows video preview.
     */
    private void showVideoPreview(File file) {
        currentVideoFile = file;
        mediaPlayerRetryCount = 0;
        
        loadVideoWithRetry();
    }
    
    /**
     * Attempts to load video with retry logic.
     */
    private void loadVideoWithRetry() {
        previewImageView.setVisible(false);
        videoPlayerContainer.setVisible(true);
        
        if (!videoPlayerContainer.getChildren().contains(mediaView)) {
            videoPlayerContainer.getChildren().add(0, mediaView);
            VBox.setVgrow(mediaView, javafx.scene.layout.Priority.ALWAYS);
        }
        
        // Validate file before attempting to load
        if (currentVideoFile == null || !currentVideoFile.exists() || !currentVideoFile.canRead()) {
            showError("Video Error", "Cannot access video file: " + (currentVideoFile != null ? currentVideoFile.getName() : "null"));
            return;
        }
        
        playPauseButton.setText("Play");
        timeLabel.setText("00:00 / 00:00");
        
        if (currentMediaPlayer != null) {
            try {
                currentMediaPlayer.stop();
                currentMediaPlayer.dispose();
            } catch (Exception e) {
                // Ignore
            }
            currentMediaPlayer = null;
            mediaView.setMediaPlayer(null);
        }
        
        try {
            String mediaUri = currentVideoFile.toURI().toString();
            Media media = new Media(mediaUri);
            currentMediaPlayer = new MediaPlayer(media);
            
            // Set up error handling with retry logic
            // This is currently neccesary due to a Bug causing the MediaPlayer to never enter ready State
            // TODO: Fix the damn bug...
            currentMediaPlayer.setOnError(() -> {
                Platform.runLater(() -> {
                    String errorMsg = "Unknown media error";
                    if (currentMediaPlayer.getError() != null) {
                        errorMsg = currentMediaPlayer.getError().getMessage();
                    }
                    
                    // Check it is ERROR_MEDIA_INVALID and retry if it is
                    if (errorMsg.contains("ERROR_MEDIA_INVALID") && mediaPlayerRetryCount < MAX_RETRY_ATTEMPTS) {
                        mediaPlayerRetryCount++;
                        System.out.println("MediaPlayer error (attempt " + mediaPlayerRetryCount + "/" + MAX_RETRY_ATTEMPTS + "): " + errorMsg + ". Retrying...");
                        
                        if (currentMediaPlayer != null) {
                            try {
                                currentMediaPlayer.dispose();
                            } catch (Exception ex) {
                                // Ignore
                            }
                            currentMediaPlayer = null;
                            mediaView.setMediaPlayer(null);
                        }
                        
                        // Retry after a short delay
                        Platform.runLater(() -> {
                            try {
                                Thread.sleep(250);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                            loadVideoWithRetry();
                        });
                    } else {
                        // Either not the retryable error or max attempts reached
                        if (mediaPlayerRetryCount >= MAX_RETRY_ATTEMPTS) {
                            showError("Video Error", "Failed to load video after " + MAX_RETRY_ATTEMPTS + " attempts: " + errorMsg);
                        } else {
                            showError("Video Error", "Could not load video: " + errorMsg);
                        }
                        
                        if (currentMediaPlayer != null) {
                            try {
                                currentMediaPlayer.dispose();
                            } catch (Exception ex) {
                                // Ignore
                            }
                            currentMediaPlayer = null;
                            mediaView.setMediaPlayer(null);
                        }
                    }
                });
            });
            
            // Set up ready handler
            currentMediaPlayer.setOnReady(() -> {
                Platform.runLater(() -> {
                    if (currentMediaPlayer != null && currentMediaPlayer.getStatus() == MediaPlayer.Status.READY) {
                        System.out.println("Video loaded successfully" + (mediaPlayerRetryCount > 0 ? " after " + mediaPlayerRetryCount + " retries" : ""));
                        
                        Duration totalDuration = currentMediaPlayer.getTotalDuration();
                        updateTimeLabel(Duration.ZERO, totalDuration);
                        
                        currentMediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
                        mediaView.setMediaPlayer(currentMediaPlayer);
                    }
                });
            });
            
            // Set up other handlers
            currentMediaPlayer.currentTimeProperty().addListener((_, _, newTime) -> {
                Platform.runLater(() -> {
                    if (currentMediaPlayer != null) {
                        // Update time regardless of status - we just need a valid MediaPlayer
                        updateTimeLabel(newTime, currentMediaPlayer.getTotalDuration());
                    }
                });
            });
            
            currentMediaPlayer.setOnEndOfMedia(() -> {
                Platform.runLater(() -> {
                    if (currentMediaPlayer != null) {
                        playPauseButton.setText("Play");
                        currentMediaPlayer.seek(Duration.ZERO);
                    }
                });
            });
            
        } catch (Exception e) {
            if (mediaPlayerRetryCount < MAX_RETRY_ATTEMPTS) {
                mediaPlayerRetryCount++;
                System.out.println("Exception creating MediaPlayer (attempt " + mediaPlayerRetryCount + "/" + MAX_RETRY_ATTEMPTS + "): " + e.getMessage() + ". Retrying...");
                Platform.runLater(() -> loadVideoWithRetry());
            } else {
                showError("Video Error", "Failed to create media player after " + MAX_RETRY_ATTEMPTS + " attempts: " + e.getMessage());
            }
        }
    }
    
    /**
     * Stops the current video playback.
     */
    private void stopCurrentVideo() {
        if (currentMediaPlayer != null) {
            try {
                // Remove all event handlers to prevent callbacks during disposal
                currentMediaPlayer.setOnReady(null);
                currentMediaPlayer.setOnError(null);
                currentMediaPlayer.setOnEndOfMedia(null);
                
                // Stop and dispose of MediaPlayer
                currentMediaPlayer.stop();
                currentMediaPlayer.dispose();
                
            } catch (Exception e) {
                // Ignore
            } finally {
                currentMediaPlayer = null;
                playPauseButton.setText("Play");
                timeLabel.setText("00:00 / 00:00");
            }
        }
        
        if (mediaView != null) {
            mediaView.setMediaPlayer(null);
        }
    }
    
    /**
     * Updates the time label with current and total duration.
     */
    private void updateTimeLabel(Duration currentTime, Duration totalTime) {
        String current = formatDuration(currentTime);
        String total = formatDuration(totalTime);
        timeLabel.setText(current + " / " + total);
    }
    
    /**
     * Formats a duration for display.
     */
    private String formatDuration(Duration duration) {
        if (duration == null || duration.isUnknown()) {
            return "00:00";
        }
        
        int totalSeconds = (int) duration.toSeconds();
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Utility Methods

    /**
     * Checks if a path represents a media file.
     */
    private boolean isMediaFile(Path path) {
        return isVideoFile(path) || isImageFile(path);
    }

    /**
     * Checks if a path represents a video file.
     */
    private boolean isVideoFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return VIDEO_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    /**
     * Checks if a path represents an image file.
     */
    private boolean isImageFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    /**
     * Formats file size in human-readable format.
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Gets the cutoff time for date filtering.
     */
    private long getCutoffTimeForDateFilter(String dateFilter, long currentTime) {
        switch (dateFilter) {
            case "Today":
                return currentTime - (24 * 60 * 60 * 1000); // 1 day
            case "Last 3 Days":
                return currentTime - (3 * 24 * 60 * 60 * 1000); // 3 days
            case "Last Week":
                return currentTime - (7 * 24 * 60 * 60 * 1000); // 7 days
            case "Last Month":
                return currentTime - (30L * 24 * 60 * 60 * 1000); // 30 days
            case "Last 3 Months":
                return currentTime - (90L * 24 * 60 * 60 * 1000); // 90 days
            default:
                return 0; // All time
        }
    }

    /**
     * Updates the filter status label.
     */
    public void updateFilterStatus() {
        String typeFilter = mediaTypeFilter.getValue();
        String dateFilter = dateRangeFilter.getValue();
        String nameFilter = nameFilterField.getText();
        
        boolean hasFilters = !"All Files".equals(typeFilter) || 
                           !"All Time".equals(dateFilter) || 
                           (nameFilter != null && !nameFilter.trim().isEmpty());
        
        if (!hasFilters) {
            filterStatusLabel.setText("No filters applied");
        } else {
            StringBuilder status = new StringBuilder("Filters: ");
            boolean first = true;
            
            if (!"All Files".equals(typeFilter)) {
                status.append(typeFilter);
                first = false;
            }
            
            if (!"All Time".equals(dateFilter)) {
                if (!first) status.append(", ");
                status.append(dateFilter);
                first = false;
            }
            
            if (nameFilter != null && !nameFilter.trim().isEmpty()) {
                if (!first) status.append(", ");
                status.append("Name: '").append(nameFilter.trim()).append("'");
            }
            
            filterStatusLabel.setText(status.toString());
        }
    }

    // Inner Classes

    /**
     * Represents a media file with additional metadata.
     */
    public static class MediaFile {
        private final Path path;
        private final File file;

        public MediaFile(Path path) {
            this.path = path;
            this.file = path.toFile();
        }

        public Path getPath() { return path; }
        public File getFile() { return file; }

        @Override
        public String toString() {
            return file.getName();
        }
    }

    /**
     * Custom cell factory for the media list view.
     */
    private class MediaFileCell extends ListCell<MediaFile> {
        @Override
        protected void updateItem(MediaFile item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                File file = item.getFile();
                String text = String.format("%s (%s)", 
                    file.getName(), 
                    formatFileSize(file.length()));
                setText(text);
                
                // Add type indicator
                String style = isVideoFile(item.getPath()) ? 
                    "-fx-text-fill: #87CEEB;" :  // Light blue for videos
                    "-fx-text-fill: #98FB98;";   // Light green for images
                setStyle(style);
            }
        }
    }
    
    /**
     * Updates the sizing of preview components based on scene/window dimensions.
     */
    private void updatePreviewSizing() {
        if (mediaContainer == null || mediaContainer.getScene() == null) return;
        
        double sceneWidth = mediaContainer.getScene().getWidth();
        double sceneHeight = mediaContainer.getScene().getHeight();
        
        // Default values if scene isn't properly sized yet
        if (sceneWidth <= 100 || sceneHeight <= 100) {
            sceneWidth = 1200;
            sceneHeight = 800;
        }
        
        double previewWidth = Math.max(200, (sceneWidth - 450));  // Scene width minus left panel and margins
        double previewHeight = Math.max(150, (sceneHeight - 250)); // Scene height minus header, controls, details panel
        
        System.out.println("Updating media preview sizing: scene " + sceneWidth + "x" + sceneHeight + 
                         " -> preview " + previewWidth + "x" + previewHeight);
        
        previewImageView.setFitWidth(previewWidth);
        previewImageView.setFitHeight(previewHeight);
        
        if (mediaView != null) {
            mediaView.setFitWidth(previewWidth);
            mediaView.setFitHeight(previewHeight - 60); // Account for video controls
        }
    }
    
    /**
     * Cleanup method to properly dispose of resources when the controller is destroyed.
     * Should be called when the tab is closed or the application shuts down.
     */
    public void cleanup() {
        stopCurrentVideo();
        
        if (mediaView != null) {
            mediaView.setMediaPlayer(null);
        }
        if (allItems != null) {
            allItems.clear();
        }
        if (filteredItems != null) {
            filteredItems.setPredicate(null);
        }
    }
}
