package org.kgames.client.service;

import org.kgames.client.model.AccessLog;
import org.kgames.client.model.User;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class for database operations.
 * Handles all CRUD operations for users, RFID cards, and access logs.
 */
public class DatabaseService {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/securitysystem";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root";

    private Connection connection;

    /**
     * Establishes connection to the MySQL database.
     * @throws SQLException if connection fails
     * @throws ClassNotFoundException if MySQL driver is not found
     */
    public void connect() throws SQLException, ClassNotFoundException {
        if (connection != null && !connection.isClosed()) {
            return; // Already connected
        }
        
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        
        // Test the connection
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Failed to establish database connection");
        }
    }
    
    /**
     * Checks if the database connection is established and valid.
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Closes the database connection.
     */
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
        }
    }

    /**
     * Retrieves all users from the database with their primary RFID card.
     * @return List of all users
     * @throws SQLException if database query fails
     */
    public List<User> getAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = """
            SELECT u.user_id, u.name, u.role, u.security_level, u.contact_info, u.created_at,
                   (SELECT card_uid FROM rfid_cards WHERE user_id = u.user_id AND active = TRUE ORDER BY issued_at ASC LIMIT 1) AS rfid_uid
            FROM users u
            ORDER BY u.user_id
            """;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                LocalDateTime createdAt = null;
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) {
                    createdAt = ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                }
                users.add(new User(
                        rs.getInt("user_id"),
                        rs.getString("name"),
                        rs.getString("role"),
                        rs.getInt("security_level"),
                        rs.getString("contact_info"),
                        rs.getString("rfid_uid"),
                        createdAt
                ));
            }
        }
        return users;
    }

    /**
     * Adds a new user to the database.
     * @param user The user to add
     * @return The generated user ID
     * @throws SQLException if database operation fails
     */
    public int addUser(User user) throws SQLException {
        String sql = "INSERT INTO users (name, role, security_level, contact_info) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getName());
            stmt.setString(2, user.getRole());
            stmt.setInt(3, user.getSecurityLevel());
            stmt.setString(4, user.getContactInfo());
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);
                    if (user.getRfidUid() != null && !user.getRfidUid().isBlank()) {
                        addRfidCard(userId, user.getRfidUid());
                    }
                    return userId;
                }
            }
        }
        throw new SQLException("Failed to get generated user ID");
    }

    /**
     * Updates an existing user in the database.
     * @param user The user with updated information
     * @throws SQLException if database operation fails
     */
    public void updateUser(User user) throws SQLException {
        String sql = "UPDATE users SET name=?, role=?, security_level=?, contact_info=? WHERE user_id=?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user.getName());
            stmt.setString(2, user.getRole());
            stmt.setInt(3, user.getSecurityLevel());
            stmt.setString(4, user.getContactInfo());
            stmt.setInt(5, user.getUserId());
            stmt.executeUpdate();
        }
    }

    /**
     * Deletes a user from the database.
     * @param userId The ID of the user to delete
     * @throws SQLException if database operation fails
     */
    public void deleteUser(int userId) throws SQLException {
        String sql = "DELETE FROM users WHERE user_id=?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    /**
     * Adds an RFID card for a user.
     * @param userId The user ID
     * @param cardUid The card UID
     * @param cardSecret The card secret (16 hex characters)
     * @throws SQLException if database operation fails
     */
    public void addRfidCard(int userId, String cardUid, String cardSecret) throws SQLException {
        String sql = "INSERT INTO rfid_cards (card_uid, card_secret, user_id, active) VALUES (?, ?, ?, TRUE)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, cardUid);
            stmt.setString(2, cardSecret);
            stmt.setInt(3, userId);
            stmt.executeUpdate();
        }
    }

    /**
     * Adds an RFID card for a user with automatically generated secret.
     * @param userId The user ID
     * @param cardUid The card UID
     * @throws SQLException if database operation fails
     */
    public void addRfidCard(int userId, String cardUid) throws SQLException {
        String cardSecret = generateUniqueCardSecret();
        addRfidCard(userId, cardUid, cardSecret);
    }

    /**
     * Generates a unique 16-character hexadecimal secret for RFID cards.
     * Ensures the generated secret is not already in use in the database.
     * @return A unique 16-character hex string
     * @throws SQLException if database query fails
     */
    public String generateUniqueCardSecret() throws SQLException {
        String secret;
        boolean isUnique = false;
        int attempts = 0;
        final int MAX_ATTEMPTS = 100;

        do {
            secret = generateRandomHexString(16);

            // Check if this secret already exists
            String checkSql = "SELECT COUNT(*) FROM rfid_cards WHERE card_secret = ?";
            try (PreparedStatement stmt = connection.prepareStatement(checkSql)) {
                stmt.setString(1, secret);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        isUnique = true;
                    }
                }
            }

            attempts++;
            if (attempts >= MAX_ATTEMPTS) {
                throw new SQLException("Failed to generate unique card secret after " + MAX_ATTEMPTS + " attempts");
            }
        } while (!isUnique);

        return secret;
    }

    /**
     * Generates a random hexadecimal string of specified length.
     * @param length The length of the hex string to generate
     * @return A random hex string
     */
    private String generateRandomHexString(int length) {
        String chars = "0123456789ABCDEF";
        StringBuilder result = new StringBuilder();
        java.util.Random random = new java.util.Random();

        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }

        return result.toString();
    }

    /**
     * Updates the primary RFID card for a user.
     * Deactivates all existing cards and adds a new one if provided.
     * @param userId The user ID
     * @param newCardUid The new card UID (null to remove all cards)
     * @throws SQLException if database operation fails
     */
    public void updatePrimaryRfidCard(int userId, String newCardUid) throws SQLException {
        // Deactivate all cards for this user
        String deactivateSql = "UPDATE rfid_cards SET active=FALSE WHERE user_id=?";
        try (PreparedStatement stmt = connection.prepareStatement(deactivateSql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }

        // Add new card if provided
        if (newCardUid != null && !newCardUid.isBlank()) {
            addRfidCard(userId, newCardUid);
        }
    }

    /**
     * Gets the active RFID card secret for a user.
     * @param userId The user ID to get the secret for
     * @return The RFID card secret, or null if no active card found
     * @throws SQLException if database query fails
     */
    public String getActiveRfidSecretForUser(int userId) throws SQLException {
        String sql = """
            SELECT card_secret 
            FROM rfid_cards 
            WHERE user_id = ? AND active = TRUE 
            ORDER BY issued_at ASC 
            LIMIT 1
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("card_secret");
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all access logs from the database with user and device information.
     * @return List of all access logs ordered by timestamp (newest first)
     * @throws SQLException if database query fails
     */
    public List<AccessLog> getAllAccessLogs() throws SQLException {
        List<AccessLog> accessLogs = new ArrayList<>();
        String sql = """
            SELECT al.log_id, al.timestamp, al.user_id, al.card_id, al.device_id, 
                   al.access_result, al.reason,
                   u.name as user_name,
                   rc.card_uid,
                   d.location as device_location
            FROM access_logs al
            LEFT JOIN users u ON al.user_id = u.user_id
            LEFT JOIN rfid_cards rc ON al.card_id = rc.card_id
            LEFT JOIN devices d ON al.device_id = d.device_id
            ORDER BY al.timestamp DESC
            """;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                LocalDateTime timestamp = null;
                Timestamp ts = rs.getTimestamp("timestamp");
                if (ts != null) {
                    timestamp = ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                }

                AccessLog.AccessResult result = AccessLog.AccessResult.valueOf(rs.getString("access_result").toUpperCase());

                accessLogs.add(new AccessLog(
                        rs.getInt("log_id"),
                        timestamp,
                        rs.getObject("user_id", Integer.class),
                        rs.getString("user_name"),
                        rs.getObject("card_id", Integer.class),
                        rs.getString("card_uid"),
                        rs.getObject("device_id", Integer.class),
                        rs.getString("device_location"),
                        result,
                        rs.getString("reason")
                ));
            }
        }
        return accessLogs;
    }

    /**
     * Adds a new access log entry to the database.
     * @param userId The user ID (can be null for unknown users)
     * @param cardId The RFID card ID (can be null)
     * @param deviceId The device ID (can be null)
     * @param accessResult The result of the access attempt
     * @param reason The reason for the access result
     * @throws SQLException if database operation fails
     */
    public void addAccessLog(Integer userId, Integer cardId, Integer deviceId,
                           AccessLog.AccessResult accessResult, String reason) throws SQLException {
        String sql = "INSERT INTO access_logs (user_id, card_id, device_id, access_result, reason) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (userId != null) {
                stmt.setInt(1, userId);
            } else {
                stmt.setNull(1, Types.INTEGER);
            }

            if (cardId != null) {
                stmt.setInt(2, cardId);
            } else {
                stmt.setNull(2, Types.INTEGER);
            }

            if (deviceId != null) {
                stmt.setInt(3, deviceId);
            } else {
                stmt.setNull(3, Types.INTEGER);
            }

            stmt.setString(4, accessResult.name());
            stmt.setString(5, reason);
            stmt.executeUpdate();
        }
    }

    /**
     * Retrieves access logs for a specific user.
     * @param userId The user ID to filter by
     * @return List of access logs for the specified user
     * @throws SQLException if database query fails
     */
    public List<AccessLog> getAccessLogsByUser(int userId) throws SQLException {
        List<AccessLog> accessLogs = new ArrayList<>();
        String sql = """
            SELECT al.log_id, al.timestamp, al.user_id, al.card_id, al.device_id, 
                   al.access_result, al.reason,
                   u.name as user_name,
                   rc.card_uid,
                   d.location as device_location
            FROM access_logs al
            LEFT JOIN users u ON al.user_id = u.user_id
            LEFT JOIN rfid_cards rc ON al.card_id = rc.card_id
            LEFT JOIN devices d ON al.device_id = d.device_id
            WHERE al.user_id = ?
            ORDER BY al.timestamp DESC
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime timestamp = null;
                    Timestamp ts = rs.getTimestamp("timestamp");
                    if (ts != null) {
                        timestamp = ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    }

                    AccessLog.AccessResult result = AccessLog.AccessResult.valueOf(rs.getString("access_result").toUpperCase());

                    accessLogs.add(new AccessLog(
                            rs.getInt("log_id"),
                            timestamp,
                            rs.getObject("user_id", Integer.class),
                            rs.getString("user_name"),
                            rs.getObject("card_id", Integer.class),
                            rs.getString("card_uid"),
                            rs.getObject("device_id", Integer.class),
                            rs.getString("device_location"),
                            result,
                            rs.getString("reason")
                    ));
                }
            }
        }
        return accessLogs;
    }

    /**
     * Retrieves recent access logs (last N entries).
     * @param limit The maximum number of logs to retrieve
     * @return List of recent access logs
     * @throws SQLException if database query fails
     */
    public List<AccessLog> getRecentAccessLogs(int limit) throws SQLException {
        List<AccessLog> accessLogs = new ArrayList<>();
        String sql = """
            SELECT al.log_id, al.timestamp, al.user_id, al.card_id, al.device_id, 
                   al.access_result, al.reason,
                   u.name as user_name,
                   rc.card_uid,
                   d.location as device_location
            FROM access_logs al
            LEFT JOIN users u ON al.user_id = u.user_id
            LEFT JOIN rfid_cards rc ON al.card_id = rc.card_id
            LEFT JOIN devices d ON al.device_id = d.device_id
            ORDER BY al.timestamp DESC
            LIMIT ?
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime timestamp = null;
                    Timestamp ts = rs.getTimestamp("timestamp");
                    if (ts != null) {
                        timestamp = ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    }

                    AccessLog.AccessResult result = AccessLog.AccessResult.valueOf(rs.getString("access_result").toUpperCase());

                    accessLogs.add(new AccessLog(
                            rs.getInt("log_id"),
                            timestamp,
                            rs.getObject("user_id", Integer.class),
                            rs.getString("user_name"),
                            rs.getObject("card_id", Integer.class),
                            rs.getString("card_uid"),
                            rs.getObject("device_id", Integer.class),
                            rs.getString("device_location"),
                            result,
                            rs.getString("reason")
                    ));
                }
            }
        }
        return accessLogs;
    }

    /**
     * Gets the card ID for a given card UID.
     * @param cardUid The card UID to look up
     * @return The card ID, or null if not found
     * @throws SQLException if database query fails
     */
    public Integer getCardIdByUid(String cardUid) throws SQLException {
        String sql = "SELECT card_id FROM rfid_cards WHERE card_uid = ? AND active = TRUE";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, cardUid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("card_id");
                }
            }
        }
        return null;
    }

    /**
     * Gets the user ID for a given card UID.
     * @param cardUid The card UID to look up
     * @return The user ID, or null if not found
     * @throws SQLException if database query fails
     */
    public Integer getUserIdByCardUid(String cardUid) throws SQLException {
        String sql = "SELECT user_id FROM rfid_cards WHERE card_uid = ? AND active = TRUE";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, cardUid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_id");
                }
            }
        }
        return null;
    }
}
