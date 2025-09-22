package org.kgames;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Configuration class for AuthServer settings.
 * Loads configuration from properties file in Documents\SecuritySystem folder or creates default values.
 */
public class AuthServerConfig {

    private static final Logger logger = Logger.getLogger(AuthServerConfig.class.getName());

    // Default values
    private static final String DEFAULT_MQTT_BROKER = "tcp://localhost:1883";
    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/securitysystem";
    private static final String DEFAULT_DB_USERNAME = "root";
    private static final String DEFAULT_DB_PASSWORD = "root";
    private static final String DEFAULT_AUTH_REQUEST_TOPIC = "home/arduino/auth_requests";
    private static final String DEFAULT_AUTH_RESPONSE_TOPIC = "home/arduino/auth_response";

    private static final String CONFIG_DIR = "SecuritySystem";
    private static final String CONFIG_FILE = "auth-server.properties";

    private final Properties properties;
    private final Path configFilePath;

    public AuthServerConfig() {
        this.properties = new Properties();
        this.configFilePath = getConfigFilePath();
        loadConfiguration();
    }

    /**
     * Gets the configuration file path in the user's Documents folder.
     * 
     * @return Path to the configuration file
     */
    private Path getConfigFilePath() {
        String userHome = System.getProperty("user.home");
        Path documentsPath = Paths.get(userHome, "Documents", CONFIG_DIR);
        
        try {
            Files.createDirectories(documentsPath);
        } catch (IOException e) {
            logger.warning("Could not create config directory: " + e.getMessage());
        }
        
        return documentsPath.resolve(CONFIG_FILE);
    }

    /**
     * Loads configuration from auth-server.properties file or creates default configuration.
     */
    private void loadConfiguration() {
        File configFile = configFilePath.toFile();
        
        if (configFile.exists()) {
            try (FileInputStream input = new FileInputStream(configFile)) {
                properties.load(input);
                logger.info("Configuration loaded from: " + configFilePath);
            } catch (IOException e) {
                logger.warning("Error loading configuration from " + configFilePath + ": " + e.getMessage());
                createDefaultConfiguration();
            }
        } else {
            logger.info("Configuration file not found at " + configFilePath + ". Creating default configuration.");
            createDefaultConfiguration();
        }

        // Log the configuration being used
        logger.info("Using configuration:");
        logger.info("  Database URL: " + getDbUrl());
        logger.info("  Database User: " + getDbUsername());
        logger.info("  MQTT Broker: " + getMqttBroker());
        logger.info("  Auth Request Topic: " + getAuthRequestTopic());
        logger.info("  Auth Response Topic: " + getAuthResponseTopic());
    }

    /**
     * Creates a default configuration file with default values.
     */
    private void createDefaultConfiguration() {
        properties.setProperty("mqtt.broker", DEFAULT_MQTT_BROKER);
        properties.setProperty("mqtt.topic.auth.request", DEFAULT_AUTH_REQUEST_TOPIC);
        properties.setProperty("mqtt.topic.auth.response", DEFAULT_AUTH_RESPONSE_TOPIC);
        properties.setProperty("database.url", DEFAULT_DB_URL);
        properties.setProperty("database.username", DEFAULT_DB_USERNAME);
        properties.setProperty("database.password", DEFAULT_DB_PASSWORD);
        properties.setProperty("auth.max.retries", "5");
        properties.setProperty("auth.retry.timeout.seconds", "1");

        saveConfiguration();
    }

    /**
     * Saves the current configuration to the properties file.
     */
    private void saveConfiguration() {
        try (FileOutputStream output = new FileOutputStream(configFilePath.toFile())) {
            properties.store(output, "AuthServer Configuration");
            logger.info("Configuration saved to: " + configFilePath);
        } catch (IOException e) {
            logger.warning("Error saving configuration to " + configFilePath + ": " + e.getMessage());
        }
    }

    public String getMqttBroker() {
        return properties.getProperty("mqtt.broker", DEFAULT_MQTT_BROKER);
    }

    public String getDbUrl() {
        return properties.getProperty("database.url", DEFAULT_DB_URL);
    }

    public String getDbUsername() {
        return properties.getProperty("database.username", DEFAULT_DB_USERNAME);
    }

    public String getDbPassword() {
        return properties.getProperty("database.password", DEFAULT_DB_PASSWORD);
    }

    public String getAuthRequestTopic() {
        return properties.getProperty("mqtt.topic.auth.request", DEFAULT_AUTH_REQUEST_TOPIC);
    }

    public String getAuthResponseTopic() {
        return properties.getProperty("mqtt.topic.auth.response", DEFAULT_AUTH_RESPONSE_TOPIC);
    }

    public int getMaxRetries() {
        return Integer.parseInt(properties.getProperty("auth.max.retries", "5"));
    }

    public int getRetryTimeoutSeconds() {
        return Integer.parseInt(properties.getProperty("auth.retry.timeout.seconds", "1"));
    }
}
