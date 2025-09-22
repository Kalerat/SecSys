package org.kgames.client.service;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.kgames.client.model.CameraInfo;
import org.kgames.client.model.CameraConfiguration;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JavaCV-based camera service for individual camera operations.
 * Handles camera detection, frame capture, recording, and image conversion.
 * Can operate in single camera mode or as part of a multi-camera system.
 */
public class CameraService {
    
    /**
     * Represents a camera resolution with width and height.
     */
    public static class CameraResolution {
        private final int width;
        private final int height;
        
        public CameraResolution(int width, int height) {
            this.width = width;
            this.height = height;
        }
        
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getPixelCount() { return width * height; }
        
        @Override
        public String toString() {
            return width + "x" + height;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CameraResolution)) return false;
            CameraResolution other = (CameraResolution) obj;
            return width == other.width && height == other.height;
        }
        
        @Override
        public int hashCode() {
            return width * 1000 + height;
        }
    }
    private static final double DEFAULT_FPS = 30.0;
    private static final int FRAME_DELAY_MS = 33; // ~30 FPS

    // Camera components
    private FrameGrabber frameGrabber; // Use base class to support both local and IP cameras
    private FFmpegFrameRecorder frameRecorder;
    private final OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
    private final Java2DFrameConverter imageConverter = new Java2DFrameConverter();
    
    // State management
    private final AtomicBoolean cameraActive = new AtomicBoolean(false);
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private Thread cameraThread;
    private CameraFrameListener frameListener;
    
    // Camera information
    private int currentCameraId = 0;
    private CameraConfiguration cameraConfig; // Support for CameraConfiguration
    private List<CameraInfo> availableCameras = new ArrayList<>();
    private final Object detectionLock = new Object();
    private boolean camerasDetected = false;
    
    // Recording state
    private String currentRecordingPath = null;
    private Frame lastFrame = null;
    private int recordedFrameCount = 0;
    
    // Configuration service
    private final ConfigurationService configService;

    /**
     * Constructor for single camera mode - initializes with configuration.
     */
    public CameraService(ConfigurationService configService) {
        this.configService = configService;
        this.currentCameraId = configService.getSelectedCameraId();
        this.cameraConfig = null; // Single camera mode
    }
    
    /**
     * Constructor for multi-camera mode - initializes with specific camera configuration.
     */
    public CameraService(ConfigurationService configService, CameraConfiguration cameraConfig) {
        this.configService = configService;
        this.cameraConfig = cameraConfig;
        this.currentCameraId = cameraConfig.getType() == CameraConfiguration.CameraType.LOCAL ? 
            cameraConfig.getLocalId() : -1;
    }

    /**
     * Interface for handling camera frame updates and status changes.
     */
    public interface CameraFrameListener {
        void onFrameUpdate(Image frame);
        void onCameraStatusChange(String status);
        void onRecordingSaveComplete(boolean success, String filePath);
    }

    /**
     * Sets the frame listener for camera events.
     */
    public void setFrameListener(CameraFrameListener listener) {
        this.frameListener = listener;
    }

    /**
     * Detects all available cameras using JavaCV's native device enumeration.
     */
    private void detectAvailableCameras() {
        System.out.println("Starting JavaCV camera detection...");
        availableCameras.clear();
        
        try {
            String[] deviceDescriptions = VideoInputFrameGrabber.getDeviceDescriptions();
            
            if (deviceDescriptions != null && deviceDescriptions.length > 0) {
                System.out.println("Found " + deviceDescriptions.length + " camera device(s):");
                
                for (int i = 0; i < deviceDescriptions.length; i++) {
                    String deviceName = deviceDescriptions[i];
                    if (deviceName != null && !deviceName.trim().isEmpty()) {
                        boolean isAvailable = testCameraAccess(i);
                        String resolution = getCameraResolution(i);
                        String description = deviceName.trim();
                        if (resolution != null) {
                            description += " (" + resolution + ")";
                        }
                        
                        availableCameras.add(new CameraInfo(i, "Camera " + i, description, isAvailable));
                        System.out.println("  Device " + i + ": " + description + " - " + 
                            (isAvailable ? "Available" : "Not accessible"));
                    }
                }
            } else {
                System.out.println("No camera devices found by JavaCV");
            }
            
        } catch (Exception e) {
            System.err.println("Error during camera detection: " + e.getMessage());
            e.printStackTrace();
        }
        
        if (availableCameras.isEmpty()) {
            availableCameras.add(new CameraInfo(0, "Default Camera", "No cameras detected", false));
            System.out.println("No working cameras found, added placeholder");
        }
        
        System.out.println("Camera detection completed. Found " + availableCameras.size() + " camera(s)");
    }

    /**
     * Tests if a camera at the given index is accessible (single attempt only).
     */
    @SuppressWarnings("resource")
    private boolean testCameraAccess(int cameraIndex) {
        VideoInputFrameGrabber testGrabber = null;
        try {
            testGrabber = new VideoInputFrameGrabber(cameraIndex);
            System.out.println("Debug: Starting camera test for Camera " + cameraIndex);
            testGrabber.start();
            
            // Quick test, just try to grab one frame
            Frame frame = testGrabber.grab();
            boolean success = (frame != null && frame.image != null);
            
            if (!success) {
                System.out.println("Camera " + cameraIndex + " test failed - no valid frame");
            }
            
            return success;
        } catch (Exception e) {
            System.out.println("Camera " + cameraIndex + " not accessible: " + e.getMessage());
            return false;
        } finally {
            if (testGrabber != null) {
                try {
                    testGrabber.stop();
                } catch (Exception ignored) {}
                try {
                    testGrabber.release();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Gets camera resolution information.
     */
    @SuppressWarnings("resource")
    private String getCameraResolution(int cameraIndex) {
        VideoInputFrameGrabber testGrabber = null;
        try {
            testGrabber = new VideoInputFrameGrabber(cameraIndex);
            testGrabber.start();
            
            int width = testGrabber.getImageWidth();
            int height = testGrabber.getImageHeight();
            double fps = testGrabber.getFrameRate();
            
            testGrabber.stop();
            
            if (width > 0 && height > 0) {
                return String.format("%dx%d @ %.1f FPS", width, height, fps > 0 ? fps : DEFAULT_FPS);
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (testGrabber != null) {
                try {
                    testGrabber.stop();
                } catch (Exception ignored) {}
                try {
                    testGrabber.release();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Gets the list of available cameras with thread-safe lazy detection.
     */
    public List<CameraInfo> getAvailableCameras() {
        synchronized (detectionLock) {
            if (!camerasDetected) {
                detectAvailableCameras();
                camerasDetected = true;
            }
            return new ArrayList<>(availableCameras);
        }
    }

    /**
     * Sets the selected camera by ID with improved logic.
     */
    public boolean setSelectedCamera(int cameraId) {
        System.out.println("Attempting to switch to camera " + cameraId);
        
        // If switching to the same camera, no need to do anything
        if (this.currentCameraId == cameraId) {
            System.out.println("Already using camera " + cameraId + ", no action needed");
            return true;
        }
        
        // Check if target camera is available
        synchronized (detectionLock) {
            if (!camerasDetected) {
                detectAvailableCameras();
                camerasDetected = true;
            }
        }
        
        boolean cameraExists = availableCameras.stream()
                .anyMatch(camera -> camera.getCameraId() == cameraId && camera.isAvailable());
        
        // If camera is not available, try once more with retry
        if (!cameraExists) {
            System.out.println("Camera " + cameraId + " marked as unavailable, attempting single retry...");
            if (testCameraAccess(cameraId)) {
                // Update the camera list to mark it as available
                synchronized (detectionLock) {
                    for (CameraInfo camera : availableCameras) {
                        if (camera.getCameraId() == cameraId) {
                            availableCameras.removeIf(c -> c.getCameraId() == cameraId);
                            String description = getCameraResolution(cameraId);
                            availableCameras.add(new CameraInfo(cameraId, "Camera " + cameraId, description, true));
                            break;
                        }
                    }
                }
                cameraExists = true;
            } else {
                System.err.println("Camera " + cameraId + " is not available after retry");
                return false;
            }
        }
        
        boolean wasActive = cameraActive.get();
        if (wasActive) {
            System.out.println("Stopping current camera " + this.currentCameraId + " before switching");
            stopCamera();
            
            // Wait for complete cleanup and ensure camera is fully stopped
            int waitCount = 0;
            while (cameraActive.get() && waitCount < 10) {
                try {
                    Thread.sleep(100);
                    waitCount++;
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            
            if (cameraActive.get()) {
                System.err.println("Warning: Camera did not stop cleanly within timeout");
            } else {
                System.out.println("Camera stopped successfully");
            }
            
        }
        
        System.out.println("Switching to camera " + cameraId);
        this.currentCameraId = cameraId;
        configService.setSelectedCameraId(cameraId);
        
        if (wasActive) {
            System.out.println("Starting new camera " + cameraId);
            startCamera();
        }
        
        return true;
    }
    
    /**
     * Gets the currently selected camera ID.
     */
    public int getCurrentCameraId() {
        return currentCameraId;
    }

    /**
     * Gets information about the currently selected camera.
     */
    public CameraInfo getCurrentCameraInfo() {
        return availableCameras.stream()
                .filter(camera -> camera.getCameraId() == currentCameraId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Refreshes the list of available cameras with extended wait time.
     */
    public void refreshAvailableCameras() {
        synchronized (detectionLock) {
            camerasDetected = false;
            availableCameras.clear();
            
            // Give cameras time to become available after system events
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
            
            detectAvailableCameras();
            camerasDetected = true;
        }
    }

    /**
     * Starts the camera feed.
     */
    public void startCamera() {
        if (cameraActive.get()) {
            return;
        }

        cameraActive.set(true);
        notifyStatusChange("Initializing camera...");

        cameraThread = new Thread(this::cameraLoop);
        cameraThread.setDaemon(true);
        cameraThread.setName("CameraThread-" + currentCameraId);
        cameraThread.start();
    }

    /**
     * Detects all available resolutions for the current camera.
     * WARNING: This method should only be called when the camera is not active,
     * as it creates temporary camera instances which can interfere with active capture.
     * @return List of available resolutions sorted by pixel count (highest first)
     */
    private List<CameraResolution> detectAvailableResolutions() {
        List<CameraResolution> availableResolutions = new ArrayList<>();
        
        if (cameraActive.get()) {
            System.out.println("Cannot detect resolutions while camera is active");
            return availableResolutions;
        }
        
        // Common camera resolutions to test
        CameraResolution[] testResolutions = {
            new CameraResolution(1920, 1080), // Full HD
            new CameraResolution(1280, 720),  // HD
            new CameraResolution(640, 480),   // VGA
        };
        
        System.out.println("Testing available resolutions for camera " + currentCameraId + "...");
        
        for (CameraResolution testRes : testResolutions) {
            try (VideoInputFrameGrabber testGrabber = new VideoInputFrameGrabber(currentCameraId)) {
                testGrabber.setImageWidth(testRes.getWidth());
                testGrabber.setImageHeight(testRes.getHeight());
                testGrabber.start();
                
                // Quick test, try to get image dimensions
                int actualWidth = testGrabber.getImageWidth();
                int actualHeight = testGrabber.getImageHeight();
                
                if (actualWidth == testRes.getWidth() && actualHeight == testRes.getHeight()) {
                    availableResolutions.add(testRes);
                    System.out.println(testRes + " - Supported");
                } else {
                    System.out.println(testRes + " - Not supported (got " + actualWidth + "x" + actualHeight + ")");
                }
                
                testGrabber.stop();
                
                // Add delay to prevent rapid camera access
                Thread.sleep(100);
                
            } catch (Exception e) {
                System.out.println(testRes + " - Error: " + e.getMessage());
            }
        }
        
        // Sort by pixel count (highest first)
        availableResolutions.sort((r1, r2) -> Integer.compare(r2.getPixelCount(), r1.getPixelCount()));
        
        System.out.println("Found " + availableResolutions.size() + " supported resolutions");
        return availableResolutions;
    }

    /**
     * Gets all available resolutions for display to the user.
     * NOTE: This method requires the camera to be stopped first.
     * @return List of available resolutions as strings for UI display
     */
    public List<String> getAvailableResolutionsForDisplay() {
        if (currentCameraId == -1) {
            return new ArrayList<>();
        }
        
        if (cameraActive.get()) {
            System.out.println("Cannot detect resolutions while camera is active. Stop camera first.");
            return new ArrayList<>();
        }
        
        List<CameraResolution> resolutions = detectAvailableResolutions();
        List<String> displayResolutions = new ArrayList<>();
        for (CameraResolution resolution : resolutions) {
            displayResolutions.add(resolution.toString());
        }
        return displayResolutions;
    }

    /**
     * Sets a specific resolution for the camera.
     * This method can only be used when the camera is stopped.
     * @param resolutionString Resolution in format "WIDTHxHEIGHT" (e.g., "1920x1080")
     * @return true if the resolution was set successfully, false otherwise
     */
    public boolean setSpecificResolution(String resolutionString) {
        if (cameraActive.get()) {
            System.err.println("Cannot change resolution while camera is active. Stop camera first.");
            return false;
        }
        
        try {
            String[] parts = resolutionString.split("x");
            if (parts.length != 2) {
                System.err.println("Invalid resolution format. Expected format: WIDTHxHEIGHT");
                return false;
            }
            
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            CameraResolution targetResolution = new CameraResolution(width, height);
            
            // Verify this resolution is available
            List<CameraResolution> availableResolutions = detectAvailableResolutions();
            if (!availableResolutions.contains(targetResolution)) {
                System.err.println("Resolution " + resolutionString + " is not supported by this camera");
                System.out.println("Available resolutions: " + availableResolutions);
                return false;
            }
            
            System.out.println("Resolution " + resolutionString + " is supported and can be set when camera restarts");
            return true;
            
        } catch (NumberFormatException e) {
            System.err.println("Invalid resolution format: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets display name for current camera.
     */
    private String getCameraDisplayName() {
        if (cameraConfig != null) {
            return cameraConfig.getName();
        }
        return "Camera " + currentCameraId;
    }
    
    /**
     * Gets the camera configuration if in multi-camera mode.
     */
    public CameraConfiguration getCameraConfiguration() {
        return cameraConfig;
    }
    
    /**
     * Gets the camera ID for this service instance.
     */
    public String getCameraId() {
        if (cameraConfig != null) {
            return cameraConfig.getId();
        }
        return String.valueOf(currentCameraId);
    }
    
    /**
     * Main camera loop running in background thread.
     */
    private void cameraLoop() {
        try {
            String cameraName = getCameraDisplayName();
            System.out.println("Initializing camera: " + cameraName);
            
            // Create frame grabber based on camera type
            if (cameraConfig != null) {
                // Multi-camera mode with configuration
                if (cameraConfig.getType() == CameraConfiguration.CameraType.LOCAL) {
                    frameGrabber = new VideoInputFrameGrabber(cameraConfig.getLocalId());
                } else {
                    // For IP cameras, use FFmpegFrameGrabber with robust RTSP configuration
                    FFmpegFrameGrabber ipGrabber = new FFmpegFrameGrabber(cameraConfig.getConnectionString());
                    
                    // Basic reliable RTSP settings
                    ipGrabber.setOption("rtsp_transport", "tcp"); // Use TCP for reliability
                    ipGrabber.setOption("stimeout", "10000000"); // 10 second timeout (in microseconds)
                    ipGrabber.setOption("max_delay", "500000"); // 0.5 second max delay
                    
                    // Enhanced error recovery and stream stability
                    ipGrabber.setOption("fflags", "+genpts+discardcorrupt+igndts"); // Generate timestamps, discard corrupt packets
                    ipGrabber.setOption("flags", "+low_delay+global_header");
                    ipGrabber.setOption("err_detect", "ignore_err"); // Ignore all decoding errors
                    ipGrabber.setOption("skip_frame", "nokey"); // Skip frames until keyframe for stability
                    
                    // H.264 specific error concealment
                    ipGrabber.setVideoOption("ec", "favor_inter+deblock"); // Error concealment
                    ipGrabber.setVideoOption("error_concealment", "3"); // Maximum error concealment
                    
                    System.out.println("Configuring IP camera with robust RTSP settings: " + cameraConfig.getConnectionString());
                    frameGrabber = ipGrabber;
                }
            } else {
                // Single camera mode with local camera
                frameGrabber = new VideoInputFrameGrabber(currentCameraId);
            }
            
            try {
                // Add timeout mechanism for IP camera initialization
                if (cameraConfig != null && cameraConfig.getType() == CameraConfiguration.CameraType.IP_CAMERA) {
                    // Use a separate thread with timeout for IP camera initialization
                    final Exception[] initException = new Exception[1];
                    final boolean[] initComplete = new boolean[1];
                    
                    Thread initThread = new Thread(() -> {
                        try {
                            frameGrabber.start();
                            initComplete[0] = true;
                        } catch (Exception e) {
                            initException[0] = e;
                        }
                    });
                    
                    initThread.setDaemon(true);
                    initThread.start();
                    
                    // Wait for initialization with timeout
                    initThread.join(15000); // 15 second timeout
                    
                    if (!initComplete[0]) {
                        initThread.interrupt();
                        throw new RuntimeException("IP camera initialization timed out after 15 seconds");
                    }
                    
                    if (initException[0] != null) {
                        throw initException[0];
                    }
                } else {
                    // Local cameras use direct initialization
                    frameGrabber.start();
                }
                
                // Test if we can grab a frame
                Frame testFrame = frameGrabber.grab();
                if (testFrame == null || testFrame.image == null) {
                    throw new Exception("Unable to capture test frame");
                }
                
                // Get the native resolution and settings
                int actualWidth = frameGrabber.getImageWidth();
                int actualHeight = frameGrabber.getImageHeight();
                double actualFPS = frameGrabber.getFrameRate();
                
                System.out.println("Camera " + cameraName + " initialized:");
                System.out.println("  Resolution: " + actualWidth + "x" + actualHeight);
                System.out.println("  Frame Rate: " + (actualFPS > 0 ? actualFPS : "Unknown") + " FPS");
                
                notifyStatusChange("Active (" + actualWidth + "x" + actualHeight + ")");
                
            } catch (Exception e) {
                System.err.println("Failed to initialize camera " + cameraName + ": " + e.getMessage());
                e.printStackTrace(); // Add stack trace for better debugging
                notifyStatusChange("Failed to initialize: " + e.getMessage());
                cameraActive.set(false);
                return;
            }

            // Main capture loop
            int consecutiveErrors = 0;
            int networkErrors = 0;
            final int MAX_CONSECUTIVE_ERRORS = 10; // Increased for IP cameras
            final int MAX_NETWORK_ERRORS = 20; // Allow more network-related errors
            
            while (cameraActive.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Frame frame = frameGrabber.grab();
                    if (frame != null && frame.image != null) {
                        // Validate frame quality for IP cameras to prevent displaying heavily corrupted frames
                        boolean frameValid = true;
                        if (cameraConfig != null && cameraConfig.getType() == CameraConfiguration.CameraType.IP_CAMERA) {
                            // Basic frame validation - check if dimensions are reasonable
                            if (frame.imageWidth <= 0 || frame.imageHeight <= 0) {
                                frameValid = false;
                            }
                        }
                        
                        if (frameValid) {
                            consecutiveErrors = 0; // Reset error counter on successful frame
                            networkErrors = 0; // Reset network error count on successful frame
                            lastFrame = frame.clone();
                            
                            // Convert frame to JavaFX Image with enhanced error handling
                            try {
                                BufferedImage bufferedImage = imageConverter.convert(frame);
                                if (bufferedImage != null && bufferedImage.getWidth() > 0 && bufferedImage.getHeight() > 0) {
                                    Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                                    Platform.runLater(() -> {
                                        if (frameListener != null) {
                                            frameListener.onFrameUpdate(fxImage);
                                        }
                                    });
                                } else {
                                    // Skip frame with invalid BufferedImage
                                    consecutiveErrors++;
                                }
                            } catch (Exception conversionException) {
                                // Silently skip corrupted frames from H.264 decoding errors
                                consecutiveErrors++;
                                if (consecutiveErrors % 50 == 0) {
                                    System.err.println("Frame conversion errors for " + cameraName + ": " + consecutiveErrors + " consecutive errors");
                                }
                            }
                            
                            // Record frame if recording
                            if (recording.get() && frameRecorder != null) {
                                try {
                                    frameRecorder.record(frame);
                                    recordedFrameCount++;
                                } catch (Exception recordingException) {
                                    System.err.println("Error recording frame: " + recordingException.getMessage());
                                    // Continue capturing even if recording fails
                                }
                            }
                        } else {
                            // Skip invalid frame
                            consecutiveErrors++;
                        }
                    } else {
                        consecutiveErrors++;
                        if (cameraConfig != null && cameraConfig.getType() == CameraConfiguration.CameraType.IP_CAMERA) {
                            // For IP cameras, be more lenient with null frames due to network issues
                            if (consecutiveErrors >= MAX_NETWORK_ERRORS) {
                                System.err.println("Too many consecutive null frames for IP camera " + cameraName);
                                break;
                            }
                        } else {
                            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                                System.err.println("Too many consecutive null frames, stopping camera " + cameraName);
                                break;
                            }
                        }
                    }
                    
                    // Dynamic frame delay - increase delay when experiencing errors
                    int frameDelay = FRAME_DELAY_MS;
                    if (consecutiveErrors > 5) {
                        frameDelay = FRAME_DELAY_MS * 2; // Slow down when errors occur
                    }
                    Thread.sleep(frameDelay);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    consecutiveErrors++;
                    networkErrors++;
                    
                    String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    boolean isNetworkError = errorMsg.contains("timeout") || 
                                           errorMsg.contains("connection") || 
                                           errorMsg.contains("network") ||
                                           errorMsg.contains("rtsp") ||
                                           errorMsg.contains("rtp");
                    
                    if (isNetworkError && cameraConfig != null && cameraConfig.getType() == CameraConfiguration.CameraType.IP_CAMERA) {
                        System.err.println("Network error for IP camera " + cameraName + 
                                         " (attempt " + networkErrors + "): " + e.getMessage());
                        
                        if (networkErrors >= MAX_NETWORK_ERRORS) {
                            System.err.println("Too many network errors, stopping IP camera " + cameraName);
                            break;
                        }
                        
                        // Longer delay for network errors to allow recovery
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            break;
                        }
                    } else {
                        System.err.println("Error capturing frame from camera " + cameraName + ": " + e.getMessage());
                        
                        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            System.err.println("Too many consecutive errors, stopping camera " + cameraName);
                            break;
                        }
                        
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Camera initialization failed: " + e.getMessage());
            Platform.runLater(() -> notifyStatusChange("Camera initialization failed: " + e.getMessage()));
        } finally {
            cleanup();
        }
    }

    /**
     * Stops the camera feed.
     */
    public void stopCamera() {
        if (!cameraActive.get()) {
            return;
        }

        System.out.println("Stopping camera " + currentCameraId);
        cameraActive.set(false);
        
        if (cameraThread != null && cameraThread.isAlive()) {
            cameraThread.interrupt();
            try {
                cameraThread.join(2000); // Wait up to 2 seconds for thread to finish
            } catch (InterruptedException ignored) {}
        }
        
        Platform.runLater(() -> {
            if (frameListener != null) {
                frameListener.onFrameUpdate(null);
            }
            notifyStatusChange("Camera stopped");
        });
    }

    /**
     * Cleans up camera resources.
     */
    private void cleanup() {
        if (recording.get()) {
            stopRecording();
        }
        
        if (frameGrabber != null) {
            try {
                frameGrabber.stop();
                frameGrabber.release();
            } catch (Exception e) {
                System.err.println("Error releasing frame grabber: " + e.getMessage());
            }
            frameGrabber = null;
        }
    }

    /**
     * Starts video recording.
     */
    public boolean startRecording() {
        if (recording.get() || !cameraActive.get()) {
            return false;
        }

        // Check if frameGrabber is available
        if (frameGrabber == null) {
            System.err.println("Cannot start recording: Camera not initialized");
            return false;
        }

        try {
            String recordingPath = configService.getRecordingsPath();
            File recordingDir = new File(recordingPath);
            if (!recordingDir.exists()) {
                recordingDir.mkdirs();
            }

            String filename = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String cameraNameForFile = getCameraDisplayName().replaceAll("[^a-zA-Z0-9]", "_");
            currentRecordingPath = new File(recordingDir, 
                "recording_" + cameraNameForFile + "_" + filename + ".mp4").getAbsolutePath();

            int width = frameGrabber.getImageWidth();
            int height = frameGrabber.getImageHeight();
            double frameRate = frameGrabber.getFrameRate();
            
            // Validate dimensions
            if (width <= 0 || height <= 0) {
                System.err.println("Cannot start recording: Invalid camera dimensions (" + width + "x" + height + ")");
                return false;
            }
            
            // Ensure we have a valid frame rate
            if (frameRate <= 0) {
                frameRate = DEFAULT_FPS;
            }

            System.out.println("Starting recording for camera " + getCameraDisplayName() + " with dimensions: " + width + "x" + height + " at " + frameRate + " FPS");
            
            frameRecorder = new FFmpegFrameRecorder(currentRecordingPath, width, height);
            frameRecorder.setFormat("mp4");
            frameRecorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
            frameRecorder.setFrameRate(frameRate);
            frameRecorder.setVideoBitrate(1000000); // Lower bitrate for compatibility
            frameRecorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
            
            // Add timeout and error recovery options
            frameRecorder.setOption("fflags", "+genpts"); // Generate timestamps
            frameRecorder.setOption("avoid_negative_ts", "make_zero");

            frameRecorder.start();

            recording.set(true);
            recordedFrameCount = 0; // Reset frame counter
            notifyStatusChange("Recording started");
            System.out.println("Recording successfully started for camera " + getCameraId() + " -> " + currentRecordingPath);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to start recording: " + e.getMessage());
            return false;
        }
    }

    /**
     * Stops video recording.
     */
    public void stopRecording() {
        if (!recording.get()) {
            return;
        }

        recording.set(false);
        String recordingPath = currentRecordingPath;

        if (frameRecorder != null) {
            try {
                System.out.println("Stopping recording for camera " + getCameraDisplayName() + " - Total frames recorded: " + recordedFrameCount);
                frameRecorder.stop();
                frameRecorder.release();
                frameRecorder = null;
                
                Platform.runLater(() -> {
                    if (frameListener != null) {
                        frameListener.onRecordingSaveComplete(true, recordingPath);
                    }
                });
                notifyStatusChange("Recording saved");
            } catch (Exception e) {
                System.err.println("Error stopping recording: " + e.getMessage());
                Platform.runLater(() -> {
                    if (frameListener != null) {
                        frameListener.onRecordingSaveComplete(false, recordingPath);
                    }
                });
            }
        }
        
        currentRecordingPath = null;
    }

    /**
     * Saves a screenshot of the current frame.
     */
    public boolean saveScreenshot() {
        if (lastFrame == null) {
            return false;
        }

        try {
            String screenshotPath = configService.getScreenshotsPath();
            File screenshotDir = new File(screenshotPath);
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }

            String filename = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String cameraNameForFile = getCameraDisplayName().replaceAll("[^a-zA-Z0-9]", "_");
            String filePath = new File(screenshotDir, 
                "screenshot_" + cameraNameForFile + "_" + filename + ".png").getAbsolutePath();

            // Convert frame to Mat and save
            Mat mat = matConverter.convert(lastFrame);
            boolean success = opencv_imgcodecs.imwrite(filePath, mat);
            mat.release();

            if (success) {
                notifyStatusChange("Screenshot saved");
            }
            return success;
        } catch (Exception e) {
            System.err.println("Failed to save screenshot: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the camera is currently active.
     */
    public boolean isCameraActive() {
        return cameraActive.get();
    }

    /**
     * Checks if currently recording.
     */
    public boolean isRecording() {
        return recording.get();
    }

    /**
     * Gets the current recording file path.
     */
    public String getCurrentRecordingPath() {
        return currentRecordingPath;
    }

    /**
     * Gets the configuration service.
     */
    public ConfigurationService getConfigurationService() {
        return configService;
    }

    /**
     * Notifies the listener about status changes.
     */
    private void notifyStatusChange(String status) {
        Platform.runLater(() -> {
            if (frameListener != null) {
                frameListener.onCameraStatusChange(status);
            }
        });
    }
}
