package org.kgames;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Authentication Server that handles RFID card authentication via MQTT and MySQL database.
 * Listens for authentication requests from Arduino and responds based on database validation.
 */
public class AuthServer implements MqttCallback {

    private static final String CLIENT_ID = "AuthServer";

    // Authentication Protocol Messages
    private static final String AUTH_REQUEST_PREFIX = "AUTH_REQUEST:";
    private static final String ACK_AUTH_REQUEST = "ACK_AUTH_REQUEST";
    private static final String AUTH_SUCCESS = "AUTH_SUCCESS";
    private static final String AUTH_FAILED = "AUTH_FAILED";
    private static final String ACK_AUTH_SUCCESS = "ACK_AUTH_SUCCESS";
    private static final String ACK_AUTH_FAILED = "ACK_AUTH_FAILED";

    private final AuthServerConfig config;
    private MqttClient mqttClient;
    private Connection dbConnection;
    private ScheduledExecutorService scheduler;
    private Logger logger;

    // State tracking for pending authentication
    private String pendingAuthResponse;
    private int retryCount;
    private boolean waitingForAck;

    public AuthServer() {
        this.config = new AuthServerConfig();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.logger = Logger.getLogger(AuthServer.class.getName());
        this.retryCount = 0;
        this.waitingForAck = false;
    }

    /**
     * Initializes and starts the authentication server.
     * Establishes MQTT and database connections.
     */
    public void start() {
        try {
            initializeDatabase();
            initializeMqtt();
            logger.info("AuthServer started successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start AuthServer", e);
            throw new RuntimeException("Failed to start AuthServer", e);
        }
    }

    /**
     * Stops the authentication server and cleans up resources.
     */
    public void stop() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
            }
            scheduler.shutdown();
            logger.info("AuthServer stopped successfully");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error stopping AuthServer", e);
        }
    }

    /**
     * Initializes the database connection.
     */
    private void initializeDatabase() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySQL driver not found", e);
            throw new SQLException("MySQL driver not found", e);
        }

        dbConnection = DriverManager.getConnection(
            config.getDbUrl(),
            config.getDbUsername(),
            config.getDbPassword()
        );
        logger.info("Database connection established to: " + config.getDbUrl());
    }

    /**
     * Initializes the MQTT client and subscribes to authentication request topic.
     */
    private void initializeMqtt() throws MqttException {
        mqttClient = new MqttClient(config.getMqttBroker(), CLIENT_ID, new MemoryPersistence());
        mqttClient.setCallback(this);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        mqttClient.connect(options);
        mqttClient.subscribe(config.getAuthRequestTopic());

        logger.info("MQTT connection established and subscribed to " + config.getAuthRequestTopic());
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.log(Level.WARNING, "MQTT connection lost", cause);
        try {
            if (!mqttClient.isConnected()) {
                mqttClient.reconnect();
                mqttClient.subscribe(config.getAuthRequestTopic());
                logger.info("MQTT reconnected successfully");
            }
        } catch (MqttException e) {
            logger.log(Level.SEVERE, "Failed to reconnect to MQTT broker", e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String messageContent = new String(message.getPayload());
        logger.info("Received message on topic " + topic + ": " + messageContent);

        if (config.getAuthRequestTopic().equals(topic)) {
            handleAuthRequest(messageContent);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used for this implementation
    }

    /**
     * Handles incoming authentication requests from Arduino.
     * Processes AUTH_REQUEST messages and ACK responses.
     */
    private void handleAuthRequest(String messageContent) {
        if (messageContent.startsWith(AUTH_REQUEST_PREFIX)) {
            processNewAuthRequest(messageContent);
        } else if (ACK_AUTH_SUCCESS.equals(messageContent) || ACK_AUTH_FAILED.equals(messageContent)) {
            processAckResponse(messageContent);
        }
    }

    /**
     * Processes a new authentication request by extracting the secret number
     * and querying the database for validation.
     */
    private void processNewAuthRequest(String messageContent) {
        try {
            String secretNumber = extractSecretNumber(messageContent);
            if (secretNumber != null) {
                // Send acknowledgment
                publishMessage(config.getAuthResponseTopic(), ACK_AUTH_REQUEST);

                // Query database and send authentication result
                boolean isAuthenticated = checkSecretInDatabase(secretNumber);
                String authResponse = isAuthenticated ? AUTH_SUCCESS : AUTH_FAILED;

                // Log the authentication attempt
                logAuthenticationAttempt(secretNumber, isAuthenticated);

                sendAuthResponseWithRetry(authResponse);
            } else {
                logger.warning("Failed to extract secret number from: " + messageContent);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing auth request", e);
        }
    }

    /**
     * Extracts the 16-byte secret number from the authentication request message.
     * @param messageContent The full message content
     * @return The extracted secret number or null if extraction fails
     */
    private String extractSecretNumber(String messageContent) {
        if (messageContent.length() >= AUTH_REQUEST_PREFIX.length() + 16) {
            String secretNumber = messageContent.substring(AUTH_REQUEST_PREFIX.length()).trim();
            if (secretNumber.length() == 16 && secretNumber.matches("[A-Fa-f0-9]+")) {
                return secretNumber.toUpperCase(); // Normalize to uppercase
            }
        }
        return null;
    }

    /**
     * Checks if the provided RFID card secret exists in the database.
     * Uses the existing database schema with users and rfid_cards tables.
     * @param cardSecret The RFID card secret to validate
     * @return true if the card secret is found and user is active, false otherwise
     */
    private boolean checkSecretInDatabase(String cardSecret) {
        String query = """
            SELECT u.user_id, u.name, u.role, u.security_level, rc.card_id, rc.card_uid
            FROM users u 
            JOIN rfid_cards rc ON u.user_id = rc.user_id 
            WHERE rc.card_secret = ? AND rc.active = TRUE
            """;

        try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
            stmt.setString(1, cardSecret);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int userId = rs.getInt("user_id");
                    String userName = rs.getString("name");
                    String userRole = rs.getString("role");
                    int securityLevel = rs.getInt("security_level");
                    //int cardId = rs.getInt("card_id");
                    String cardUid = rs.getString("card_uid");

                    logger.info(String.format("Authentication successful for user: %s (ID: %d, Role: %s, Level: %d, Card: %s)",
                        userName, userId, userRole, securityLevel, cardUid));

                    return true;
                } else {
                    logger.info("Card secret " + cardSecret + " not found or card inactive");
                    return false;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error checking card secret", e);
        }

        return false;
    }

    /**
     * Logs the authentication attempt to the access_logs table.
     * Uses the existing database schema structure.
     * @param cardSecret The RFID card secret used
     * @param accessGranted Whether access was granted
     */
    private void logAuthenticationAttempt(String cardSecret, boolean accessGranted) {
        String insertQuery = """
            INSERT INTO access_logs (user_id, card_id, device_id, access_result, reason) 
            SELECT u.user_id, rc.card_id, 
                   (SELECT device_id FROM devices WHERE device_type = 'RFID Reader' LIMIT 1),
                   ?, 
                   CASE WHEN ? THEN 'Valid RFID card authentication' 
                        ELSE 'Invalid or inactive RFID card' END
            FROM users u 
            JOIN rfid_cards rc ON u.user_id = rc.user_id 
            WHERE rc.card_secret = ? AND rc.active = TRUE
            """;

        try (PreparedStatement stmt = dbConnection.prepareStatement(insertQuery)) {
            stmt.setString(1, accessGranted ? "Granted" : "Denied");
            stmt.setBoolean(2, accessGranted);
            stmt.setString(3, cardSecret);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected == 0 && !accessGranted) {
                // Log failed attempt even if card not found
                logUnknownCardAttempt(cardSecret);
            }

            logger.info("Authentication attempt logged for card secret: " + cardSecret + ", granted: " + accessGranted);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to log authentication attempt", e);
        }
    }

    /**
     * Logs authentication attempt for unknown/invalid card secrets.
     * @param cardSecret The unknown card secret
     */
    private void logUnknownCardAttempt(String cardSecret) {
        String insertQuery = """
            INSERT INTO access_logs (user_id, card_id, device_id, access_result, reason) 
            VALUES (NULL, NULL, 
                   (SELECT device_id FROM devices WHERE device_type = 'RFID Reader' LIMIT 1),
                   'Denied', 
                   CONCAT('Unknown card secret: ', ?))
            """;

        try (PreparedStatement stmt = dbConnection.prepareStatement(insertQuery)) {
            stmt.setString(1, cardSecret);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to log unknown card attempt", e);
        }
    }

    /**
     * Sends authentication response with retry mechanism.
     * Retries up to MAX_RETRIES times if no ACK is received within timeout.
     */
    private void sendAuthResponseWithRetry(String authResponse) {
        pendingAuthResponse = authResponse;
        retryCount = 0;
        waitingForAck = true;

        sendAuthResponseAttempt();
    }

    /**
     * Attempts to send authentication response and schedules retry if needed.
     */
    private void sendAuthResponseAttempt() {
        try {
            publishMessage(config.getAuthResponseTopic(), pendingAuthResponse);
            logger.info("Sent authentication response: " + pendingAuthResponse + " (attempt " + (retryCount + 1) + ")");

            // Schedule retry if maximum attempts not reached
            if (retryCount < config.getMaxRetries() - 1) {
                scheduler.schedule(() -> {
                    if (waitingForAck) {
                        retryCount++;
                        sendAuthResponseAttempt();
                    }
                }, config.getRetryTimeoutSeconds(), TimeUnit.SECONDS);
            } else {
                // Max retries reached
                scheduler.schedule(() -> {
                    if (waitingForAck) {
                        logger.warning("Max retries reached for authentication response: " + pendingAuthResponse);
                        resetAuthState();
                    }
                }, config.getRetryTimeoutSeconds(), TimeUnit.SECONDS);
            }
        } catch (MqttException e) {
            logger.log(Level.SEVERE, "Failed to send authentication response", e);
            resetAuthState();
        }
    }

    /**
     * Processes ACK responses from Arduino to stop retry mechanism.
     */
    private void processAckResponse(String ackMessage) {
        if (waitingForAck) {
            logger.info("Received ACK: " + ackMessage);
            resetAuthState();
        }
    }

    /**
     * Resets the authentication state after successful ACK or timeout.
     */
    private void resetAuthState() {
        waitingForAck = false;
        pendingAuthResponse = null;
        retryCount = 0;
    }

    /**
     * Publishes a message to the specified MQTT topic.
     */
    private void publishMessage(String topic, String message) throws MqttException {
        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
        mqttMessage.setQos(1); // At least once delivery
        mqttClient.publish(topic, mqttMessage);
    }

    /**
     * Main method to start the authentication server.
     */
    public static void main(String[] args) {
        AuthServer authServer = new AuthServer();

        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(authServer::stop));

        try {
            authServer.start();

            // Keep the application running
            Thread.currentThread().join();
        } catch (Exception e) {
            Logger.getLogger(AuthServer.class.getName()).log(Level.SEVERE, "AuthServer failed", e);
            System.exit(1);
        }
    }
}
