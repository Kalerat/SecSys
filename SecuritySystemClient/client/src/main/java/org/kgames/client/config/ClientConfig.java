package org.kgames.client.config;

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
 * Configuration class for SecuritySystem Client settings.
 * Loads configuration from properties file in Documents\SecuritySystem folder or creates default values.
 */
public class ClientConfig {

    private static final Logger logger = Logger.getLogger(ClientConfig.class.getName());

    // Default values
    private static final String DEFAULT_MQTT_BROKER = "tcp://localhost:1883";
    private static final String DEFAULT_TOPIC_PUB = "home/arduino/events";
    private static final String DEFAULT_TOPIC_SUB = "home/arduino/command";
    private static final String DEFAULT_TOPIC_AUTH_REQUEST = "home/arduino/auth_requests";
    private static final String DEFAULT_TOPIC_AUTH_RESPONSE = "home/arduino/auth_response";

    private static final String CONFIG_DIR = "SecuritySystem";
    private static final String CONFIG_FILE = "security-system-config.properties";

    private final Properties properties;
    private final Path configFilePath;

    public ClientConfig() {
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
     * Loads configuration from security-system-config.properties file or creates default configuration.
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
        logger.info("Using client configuration:");
        logger.info("  MQTT Broker: " + getMqttBroker());
        logger.info("  Publish Topic: " + getTopicPub());
        logger.info("  Subscribe Topic: " + getTopicSub());
        logger.info("  Auth Request Topic: " + getTopicAuthRequest());
        logger.info("  Auth Response Topic: " + getTopicAuthResponse());
    }

    /**
     * Creates a default configuration file with default values.
     */
    private void createDefaultConfiguration() {
        properties.setProperty("mqtt.broker", DEFAULT_MQTT_BROKER);
        properties.setProperty("mqtt.topic.pub", DEFAULT_TOPIC_PUB);
        properties.setProperty("mqtt.topic.sub", DEFAULT_TOPIC_SUB);
        properties.setProperty("mqtt.topic.auth.request", DEFAULT_TOPIC_AUTH_REQUEST);
        properties.setProperty("mqtt.topic.auth.response", DEFAULT_TOPIC_AUTH_RESPONSE);

        saveConfiguration();
    }

    /**
     * Saves the current configuration to the properties file.
     */
    private void saveConfiguration() {
        try (FileOutputStream output = new FileOutputStream(configFilePath.toFile())) {
            properties.store(output, "SecuritySystem Client Configuration");
            logger.info("Configuration saved to: " + configFilePath);
        } catch (IOException e) {
            logger.warning("Error saving configuration to " + configFilePath + ": " + e.getMessage());
        }
    }

    public String getMqttBroker() {
        return properties.getProperty("mqtt.broker", DEFAULT_MQTT_BROKER);
    }

    public String getTopicPub() {
        return properties.getProperty("mqtt.topic.pub", DEFAULT_TOPIC_PUB);
    }

    public String getTopicSub() {
        return properties.getProperty("mqtt.topic.sub", DEFAULT_TOPIC_SUB);
    }

    public String getTopicAuthRequest() {
        return properties.getProperty("mqtt.topic.auth.request", DEFAULT_TOPIC_AUTH_REQUEST);
    }

    public String getTopicAuthResponse() {
        return properties.getProperty("mqtt.topic.auth.response", DEFAULT_TOPIC_AUTH_RESPONSE);
    }

    /**
     * Updates a configuration property and saves the file.
     * 
     * @param key The property key
     * @param value The property value
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
        saveConfiguration();
    }
}