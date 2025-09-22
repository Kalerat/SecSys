package org.kgames.client.service;

import javafx.scene.image.Image;
import org.kgames.client.model.CameraConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for coordinating multiple camera feeds simultaneously.
 * Acts as a coordinator that manages multiple CameraService instances.
 * Supports both local and IP cameras with individual recording capabilities.
 */
public class MultiCameraService {
    
    /**
     * Interface for handling multi-camera events.
     */
    public interface MultiCameraFrameListener {
        void onCameraFrameUpdate(String cameraId, Image frame);
        void onCameraStatusChange(String cameraId, String status);
        void onCameraRecordingComplete(String cameraId, boolean success, String filePath);
    }
    
    private final Map<String, CameraService> cameraServices = new ConcurrentHashMap<>();
    private final ConfigurationService configService;
    private MultiCameraFrameListener frameListener;
    
    public MultiCameraService(ConfigurationService configService) {
        this.configService = configService;
        
        // Set FFmpeg log level to QUIET to completely suppress console output
        org.bytedeco.ffmpeg.global.avutil.av_log_set_level(org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET);
        
        loadConfiguredCameras();
    }
    
    /**
     * Sets the frame listener for camera events.
     */
    public void setFrameListener(MultiCameraFrameListener listener) {
        this.frameListener = listener;
    }
    
    /**
     * Loads all configured cameras from the configuration service.
     */
    public void loadConfiguredCameras() {
        List<CameraConfiguration> configs = configService.getConfiguredCameras();
        for (CameraConfiguration config : configs) {
            addCamera(config);
        }
    }
    
    /**
     * Adds a new camera to the multi-camera system.
     */
    public void addCamera(CameraConfiguration config) {
        CameraService cameraService = new CameraService(configService, config);
        
        // Set up frame listener to relay events
        cameraService.setFrameListener(new CameraService.CameraFrameListener() {
            @Override
            public void onFrameUpdate(Image frame) {
                if (frameListener != null) {
                    frameListener.onCameraFrameUpdate(config.getId(), frame);
                }
            }
            
            @Override
            public void onCameraStatusChange(String status) {
                if (frameListener != null) {
                    frameListener.onCameraStatusChange(config.getId(), status);
                }
            }
            
            @Override
            public void onRecordingSaveComplete(boolean success, String filePath) {
                if (frameListener != null) {
                    frameListener.onCameraRecordingComplete(config.getId(), success, filePath);
                }
            }
        });
        
        cameraServices.put(config.getId(), cameraService);
        // Only add to configuration if it's a new camera (not during loading)
        // The loadConfiguredCameras method shouldn't trigger saves
    }
    
    /**
     * Adds a new camera to the multi-camera system and saves to configuration.
     * Use this method when adding cameras through the UI.
     */
    public void addNewCamera(CameraConfiguration config) {
        addCamera(config);
        configService.addCameraConfiguration(config);
    }
    
    /**
     * Removes a camera from the multi-camera system.
     */
    public void removeCamera(String cameraId) {
        CameraService cameraService = cameraServices.get(cameraId);
        if (cameraService != null) {
            stopCamera(cameraId);
            cameraServices.remove(cameraId);
            configService.removeCameraConfiguration(cameraId);
        }
    }
    
    /**
     * Starts a specific camera.
     */
    public void startCamera(String cameraId) {
        CameraService cameraService = cameraServices.get(cameraId);
        if (cameraService == null || cameraService.isCameraActive()) {
            return;
        }
        
        cameraService.startCamera();
    }
    
    /**
     * Stops a specific camera.
     */
    public void stopCamera(String cameraId) {
        CameraService cameraService = cameraServices.get(cameraId);
        if (cameraService == null || !cameraService.isCameraActive()) {
            return;
        }
        
        cameraService.stopCamera();
    }
    
    /**
     * Starts recording for a specific camera.
     */
    public boolean startRecording(String cameraId) {
        CameraService cameraService = cameraServices.get(cameraId);
        
        if (cameraService == null || !cameraService.isCameraActive()) {
            return false;
        }
        
        return cameraService.startRecording();
    }
    
    /**
     * Stops recording for a specific camera.
     */
    public void stopRecording(String cameraId) {
        CameraService cameraService = cameraServices.get(cameraId);
        if (cameraService == null || !cameraService.isRecording()) {
            return;
        }
        
        cameraService.stopRecording();
    }
    
    /**
     * Checks if a specific camera is currently recording.
     */
    public boolean isRecording(String cameraId) {
        CameraService cameraService = cameraServices.get(cameraId);
        return cameraService != null && cameraService.isRecording();
    }
    
    /**
     * Takes a screenshot for a specific camera.
     */
    public boolean saveScreenshot(String cameraId) {
        CameraService cameraService = cameraServices.get(cameraId);
        if (cameraService == null) {
            return false;
        }
        
        return cameraService.saveScreenshot();
    }
    
    /**
     * Gets all configured camera configurations in the order they were added.
     */
    public List<CameraConfiguration> getAllCameras() {
        // Return cameras from config service to preserve order, not from HashMap
        return configService.getConfiguredCameras();
    }
    
    /**
     * Gets a specific camera service.
     */
    public CameraService getCameraService(String cameraId) {
        return cameraServices.get(cameraId);
    }
    
    /**
     * Stops all cameras and cleans up resources.
     */
    public void shutdown() {
        for (String cameraId : new ArrayList<>(cameraServices.keySet())) {
            stopCamera(cameraId);
        }
        cameraServices.clear();
    }
}
