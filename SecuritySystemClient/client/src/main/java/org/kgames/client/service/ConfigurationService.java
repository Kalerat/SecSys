package org.kgames.client.service;

import org.kgames.client.model.CameraConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

/**
 * Service for managing application configuration.
 * Handles loading, saving, and providing default values for configuration settings.
 */
public class ConfigurationService {
    
    private static final String CONFIG_FILE_NAME = "security-system-config.properties";
    private static final String BASE_DIR_NAME = "SecuritySystem";
    private static final String MEDIA_DIR_NAME = "Media";
    
    // Configuration keys
    private static final String KEY_RECORDINGS_PATH = "media.recordings.path";
    private static final String KEY_SCREENSHOTS_PATH = "media.screenshots.path";
    private static final String KEY_CUSTOM_PATHS_ENABLED = "media.custom.paths.enabled";
    private static final String KEY_SELECTED_CAMERA_ID = "camera.selected.id";
    private static final String KEY_CAMERAS_COUNT = "cameras.count";
    private static final String KEY_CAMERA_PREFIX = "camera.";
    private static final String KEY_CAMERA_NAME_SUFFIX = ".name";
    private static final String KEY_CAMERA_TYPE_SUFFIX = ".type";
    private static final String KEY_CAMERA_LOCAL_ID_SUFFIX = ".localId";
    private static final String KEY_CAMERA_IP_ADDRESS_SUFFIX = ".ipAddress";
    private static final String KEY_CAMERA_PORT_SUFFIX = ".port";
    private static final String KEY_CAMERA_USERNAME_SUFFIX = ".username";
    private static final String KEY_CAMERA_PASSWORD_SUFFIX = ".password";
    private static final String KEY_CAMERA_RTSP_PATH_SUFFIX = ".rtspPath";
    
    private Properties properties;
    private File configFile;
    private File baseDirectory;
    private File mediaDirectory;
    
    /**
     * Initializes the configuration service and loads existing configuration.
     */
    public ConfigurationService() {
        this.properties = new Properties();
        
        String userHome = System.getProperty("user.home");
        String documentsPath = Paths.get(userHome, "Documents").toString();
        this.baseDirectory = new File(documentsPath, BASE_DIR_NAME);
        this.mediaDirectory = new File(baseDirectory, MEDIA_DIR_NAME);
        this.configFile = new File(baseDirectory, CONFIG_FILE_NAME);
        
        loadConfiguration();
        ensureDefaultConfiguration();
    }
    
    /**
     * Loads configuration from the properties file.
     */
    private void loadConfiguration() {
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
                System.out.println("Configuration loaded from: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Failed to load configuration: " + e.getMessage());
                // Continue with default values
            }
        } else {
            System.out.println("No existing configuration found, using defaults");
        }
    }
    
    /**
     * Ensures that default configuration values are set if not already present.
     */
    private void ensureDefaultConfiguration() {
        // Create base directory structure if it doesn't exist
        createDirectoryIfNotExists(baseDirectory.getAbsolutePath());
        createDirectoryIfNotExists(mediaDirectory.getAbsolutePath());
        
        boolean needsSave = false;
        
        // Set default paths if not configured
        if (!properties.containsKey(KEY_RECORDINGS_PATH) || !properties.containsKey(KEY_SCREENSHOTS_PATH)) {
            setDefaultMediaPathsInternal();
            needsSave = true;
        }
        
        // Set default custom paths setting
        if (!properties.containsKey(KEY_CUSTOM_PATHS_ENABLED)) {
            properties.setProperty(KEY_CUSTOM_PATHS_ENABLED, "false");
            needsSave = true;
        }
        
        // Set default selected camera ID
        if (!properties.containsKey(KEY_SELECTED_CAMERA_ID)) {
            properties.setProperty(KEY_SELECTED_CAMERA_ID, "0");
            needsSave = true;
        }
        
        // Only save if we actually changed something
        if (needsSave) {
            saveConfiguration();
        }
    }
    
    /**
     * Sets default media storage paths based on the operating system.
     */
    private void setDefaultMediaPaths() {
        setDefaultMediaPathsInternal();
        saveConfiguration();
    }
    
    /**
     * Sets default media storage paths without saving configuration.
     */
    private void setDefaultMediaPathsInternal() {
        String defaultBasePath = getDefaultMediaBasePath();
        
        String defaultRecordingsPath = defaultBasePath + File.separator + "Recordings";
        String defaultScreenshotsPath = defaultBasePath + File.separator + "Screenshots";
        
        properties.setProperty(KEY_RECORDINGS_PATH, defaultRecordingsPath);
        properties.setProperty(KEY_SCREENSHOTS_PATH, defaultScreenshotsPath);
        
        // Create the directories if they don't exist
        createDirectoryIfNotExists(defaultRecordingsPath);
        createDirectoryIfNotExists(defaultScreenshotsPath);
    }
    
    /**
     * Gets the default base path for media storage based on the operating system.
     * @return The default base path for media storage
     */
    private String getDefaultMediaBasePath() {
        String osName = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        
        if (osName.contains("windows")) {
            // Windows: Use Documents/SecuritySystem/Media folder
            return userHome + File.separator + "Documents" + File.separator + BASE_DIR_NAME + File.separator + MEDIA_DIR_NAME;
        } else if (osName.contains("mac")) {
            // macOS: Use Documents/SecuritySystem/Media folder
            return userHome + File.separator + "Documents" + File.separator + BASE_DIR_NAME + File.separator + MEDIA_DIR_NAME;
        } else {
            // Linux and others: Use home/SecuritySystem/Media directory
            return userHome + File.separator + BASE_DIR_NAME + File.separator + MEDIA_DIR_NAME;
        }
    }
    
    /**
     * Creates a directory if it doesn't exist.
     * @param path The directory path to create
     */
    private void createDirectoryIfNotExists(String path) {
        try {
            Path dirPath = Paths.get(path);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                System.out.println("Created directory: " + path);
            }
        } catch (IOException e) {
            System.err.println("Failed to create directory " + path + ": " + e.getMessage());
        }
    }
    
    /**
     * Saves the current configuration to the properties file.
     */
    public void saveConfiguration() {
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            properties.store(fos, "Security System Configuration");
            System.out.println("Configuration saved to: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save configuration: " + e.getMessage());
        }
    }
    
    /**
     * Gets the recordings directory path.
     * @return The path to the recordings directory
     */
    public String getRecordingsPath() {
        return properties.getProperty(KEY_RECORDINGS_PATH);
    }
    
    /**
     * Sets the recordings directory path.
     * @param path The new recordings directory path
     */
    public void setRecordingsPath(String path) {
        String currentPath = getRecordingsPath();
        if (!Objects.equals(currentPath, path)) {
            properties.setProperty(KEY_RECORDINGS_PATH, path);
            createDirectoryIfNotExists(path);
            saveConfiguration();
        }
    }
    
    /**
     * Gets the screenshots directory path.
     * @return The path to the screenshots directory
     */
    public String getScreenshotsPath() {
        return properties.getProperty(KEY_SCREENSHOTS_PATH);
    }
    
    /**
     * Sets the screenshots directory path.
     * @param path The new screenshots directory path
     */
    public void setScreenshotsPath(String path) {
        String currentPath = getScreenshotsPath();
        if (!Objects.equals(currentPath, path)) {
            properties.setProperty(KEY_SCREENSHOTS_PATH, path);
            createDirectoryIfNotExists(path);
            saveConfiguration();
        }
    }
    
    /**
     * Checks if custom paths are enabled.
     * @return true if custom paths are enabled, false if using defaults
     */
    public boolean isCustomPathsEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_CUSTOM_PATHS_ENABLED, "false"));
    }
    
    /**
     * Sets whether custom paths are enabled.
     * @param enabled true to enable custom paths, false to use defaults
     */
    public void setCustomPathsEnabled(boolean enabled) {
        boolean currentEnabled = isCustomPathsEnabled();
        if (currentEnabled != enabled) {
            properties.setProperty(KEY_CUSTOM_PATHS_ENABLED, String.valueOf(enabled));
            
            if (!enabled) {
                // Reset to default paths without triggering additional saves
                setDefaultMediaPathsInternal();
            }
            
            saveConfiguration();
        }
    }
    
    /**
     * Gets all media paths for validation or display.
     * @return Array containing [recordingsPath, screenshotsPath]
     */
    public String[] getAllMediaPaths() {
        return new String[]{
            getRecordingsPath(),
            getScreenshotsPath()
        };
    }
    
    /**
     * Validates that the configured paths exist and are writable.
     * @return true if all paths are valid and writable, false otherwise
     */
    public boolean validateMediaPaths() {
        String[] paths = getAllMediaPaths();
        
        for (String path : paths) {
            if (path == null || path.trim().isEmpty()) {
                return false;
            }
            
            File dir = new File(path);
            if (!dir.exists()) {
                try {
                    if (!dir.mkdirs()) {
                        System.err.println("Failed to create directory: " + path);
                        return false;
                    }
                } catch (SecurityException e) {
                    System.err.println("No permission to create directory: " + path);
                    return false;
                }
            }
            
            if (!dir.canWrite()) {
                System.err.println("Directory is not writable: " + path);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Resets all configuration to default values.
     */
    public void resetToDefaults() {
        properties.clear();
        ensureDefaultConfiguration();
    }
    
    /**
     * Gets a human-readable summary of the current configuration.
     * @return Configuration summary string
     */
    public String getConfigurationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Security System Media Configuration\n");
        summary.append("==================================\n");
        summary.append("Recordings Path: ").append(getRecordingsPath()).append("\n");
        summary.append("Screenshots Path: ").append(getScreenshotsPath()).append("\n");
        summary.append("Custom Paths Enabled: ").append(isCustomPathsEnabled()).append("\n");
        summary.append("Selected Camera ID: ").append(getSelectedCameraId()).append("\n");
        summary.append("Config File: ").append(configFile.getAbsolutePath()).append("\n");
        summary.append("Paths Valid: ").append(validateMediaPaths()).append("\n");
        return summary.toString();
    }
    
    /**
     * Gets the selected camera ID.
     * @return The ID of the selected camera
     */
    public int getSelectedCameraId() {
        return Integer.parseInt(properties.getProperty(KEY_SELECTED_CAMERA_ID, "0"));
    }
    
    /**
     * Sets the selected camera ID.
     * @param cameraId The ID of the camera to select
     */
    public void setSelectedCameraId(int cameraId) {
        int currentCameraId = getSelectedCameraId();
        if (currentCameraId != cameraId) {
            properties.setProperty(KEY_SELECTED_CAMERA_ID, String.valueOf(cameraId));
            saveConfiguration();
        }
    }
    
    // Camera Configuration Management
    
    /**
     * Gets all configured cameras.
     * @return List of camera configurations
     */
    public List<CameraConfiguration> getConfiguredCameras() {
        List<CameraConfiguration> cameras = new ArrayList<>();
        int count = Integer.parseInt(properties.getProperty(KEY_CAMERAS_COUNT, "0"));
        
        for (int i = 0; i < count; i++) {
            String prefix = KEY_CAMERA_PREFIX + i;
            String id = properties.getProperty(prefix + ".id");
            String name = properties.getProperty(prefix + KEY_CAMERA_NAME_SUFFIX);
            String typeStr = properties.getProperty(prefix + KEY_CAMERA_TYPE_SUFFIX);
            
            if (id != null && name != null && typeStr != null) {
                CameraConfiguration.CameraType type = CameraConfiguration.CameraType.valueOf(typeStr);
                
                if (type == CameraConfiguration.CameraType.LOCAL) {
                    int localId = Integer.parseInt(properties.getProperty(prefix + KEY_CAMERA_LOCAL_ID_SUFFIX, "0"));
                    cameras.add(new CameraConfiguration(id, name, localId));
                } else {
                    String ipAddress = properties.getProperty(prefix + KEY_CAMERA_IP_ADDRESS_SUFFIX);
                    int port = Integer.parseInt(properties.getProperty(prefix + KEY_CAMERA_PORT_SUFFIX, "8080"));
                    String username = properties.getProperty(prefix + KEY_CAMERA_USERNAME_SUFFIX, "");
                    String password = properties.getProperty(prefix + KEY_CAMERA_PASSWORD_SUFFIX, "");
                    String rtspPath = properties.getProperty(prefix + KEY_CAMERA_RTSP_PATH_SUFFIX, "");
                    
                    cameras.add(new CameraConfiguration(id, name, ipAddress, port, username, password, rtspPath));
                }
            }
        }
        
        return cameras;
    }
    
    /**
     * Adds a new camera configuration.
     * @param camera The camera configuration to add
     */
    public void addCameraConfiguration(CameraConfiguration camera) {
        List<CameraConfiguration> cameras = getConfiguredCameras();
        
        // Generate unique ID if not set
        if (camera.getId() == null || camera.getId().isEmpty()) {
            camera.setId(UUID.randomUUID().toString());
        }
        
        // Check if camera already exists to avoid duplicates
        boolean exists = cameras.stream().anyMatch(existing -> 
            existing.getId().equals(camera.getId()));
        
        if (!exists) {
            cameras.add(camera);
            saveCameraConfigurations(cameras);
        }
    }
    
    /**
     * Removes a camera configuration.
     * @param cameraId The ID of the camera to remove
     */
    public void removeCameraConfiguration(String cameraId) {
        List<CameraConfiguration> cameras = getConfiguredCameras();
        cameras.removeIf(camera -> camera.getId().equals(cameraId));
        saveCameraConfigurations(cameras);
    }
    
    /**
     * Updates an existing camera configuration.
     * @param camera The updated camera configuration
     */
    public void updateCameraConfiguration(CameraConfiguration camera) {
        List<CameraConfiguration> cameras = getConfiguredCameras();
        for (int i = 0; i < cameras.size(); i++) {
            if (cameras.get(i).getId().equals(camera.getId())) {
                cameras.set(i, camera);
                break;
            }
        }
        saveCameraConfigurations(cameras);
    }
    
    /**
     * Saves all camera configurations to properties.
     * @param cameras List of camera configurations to save
     */
    private void saveCameraConfigurations(List<CameraConfiguration> cameras) {
        // Clear existing camera properties
        properties.entrySet().removeIf(entry -> 
            entry.getKey().toString().startsWith(KEY_CAMERA_PREFIX));
        
        // Save camera count
        properties.setProperty(KEY_CAMERAS_COUNT, String.valueOf(cameras.size()));
        
        // Save each camera configuration
        for (int i = 0; i < cameras.size(); i++) {
            CameraConfiguration camera = cameras.get(i);
            String prefix = KEY_CAMERA_PREFIX + i;
            
            properties.setProperty(prefix + ".id", camera.getId());
            properties.setProperty(prefix + KEY_CAMERA_NAME_SUFFIX, camera.getName());
            properties.setProperty(prefix + KEY_CAMERA_TYPE_SUFFIX, camera.getType().toString());
            
            if (camera.getType() == CameraConfiguration.CameraType.LOCAL) {
                properties.setProperty(prefix + KEY_CAMERA_LOCAL_ID_SUFFIX, String.valueOf(camera.getLocalId()));
            } else {
                properties.setProperty(prefix + KEY_CAMERA_IP_ADDRESS_SUFFIX, camera.getIpAddress());
                properties.setProperty(prefix + KEY_CAMERA_PORT_SUFFIX, String.valueOf(camera.getPort()));
                properties.setProperty(prefix + KEY_CAMERA_USERNAME_SUFFIX, camera.getUsername() != null ? camera.getUsername() : "");
                properties.setProperty(prefix + KEY_CAMERA_PASSWORD_SUFFIX, camera.getPassword() != null ? camera.getPassword() : "");
                properties.setProperty(prefix + KEY_CAMERA_RTSP_PATH_SUFFIX, camera.getRtspPath() != null ? camera.getRtspPath() : "");
            }
        }
        
        saveConfiguration();
    }
}
