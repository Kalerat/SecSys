package org.kgames.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import org.kgames.client.model.User;
import org.kgames.client.service.DatabaseService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for user management operations.
 * Handles CRUD operations for users through the database service.
 * Automatically generates RFID card secrets for new users.
 */
public class UserManagementController {
    private final DatabaseService databaseService;
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final FilteredList<User> filteredUserList;
    private final SortedList<User> sortedUserList;

    // Filter state
    private String searchFilter = "";
    private String roleFilter = "All Roles";
    private String securityLevelFilter = "All Levels";

    // Constants for default card UID generation
    private static final String DEFAULT_CARD_UID_PREFIX = "CARD";

    public UserManagementController(DatabaseService databaseService) {
        this.databaseService = databaseService;
        
        this.filteredUserList = new FilteredList<>(userList);
        this.sortedUserList = new SortedList<>(filteredUserList);
        
        updateFilterPredicate();
    }
    /**
     * Gets the observable list of users for the TableView.
     * @return The filtered and sorted user list
     */
    public ObservableList<User> getUserList() {
        return sortedUserList;
    }

    /**
     * Loads all users from the database into the observable list.
     */
    public void loadUsers() {
        try {
            List<User> users = databaseService.getAllUsers();
            userList.clear();
            userList.addAll(users);
        } catch (SQLException e) {
            showAlert("Database Error", "Could not load users: " + e.getMessage());
        }
    }

    /**
     * Adds a new user through a dialog interface.
     * Automatically generates RFID card with unique secret.
     */
    public void addUser() {
        User user = showUserDialog(null);
        if (user == null) return;

        try {
            int userId = databaseService.addUser(user);

            // Generate default card UID and automatically create RFID card with secret
            String defaultCardUid = generateDefaultCardUid(userId);
            String cardSecret = databaseService.generateUniqueCardSecret();

            databaseService.addRfidCard(userId, defaultCardUid, cardSecret);

            showInfoAlert("User Created Successfully",
                String.format("User '%s' has been created successfully.\n\n" +
                             "RFID Card Information:\n" +
                             "Card UID: %s\n" +
                             "Card Secret: %s\n\n" +
                             "The card secret has been automatically generated and can be used for RFID encoding.",
                             user.getName(), defaultCardUid, cardSecret));

            loadUsers(); // Refresh the list
        } catch (SQLException e) {
            showAlert("Database Error", "Could not add user: " + e.getMessage());
        }
    }

    /**
     * Edits an existing user through a dialog interface.
     * Note: RFID card information cannot be changed through edit - use separate RFID management for that.
     * @param selectedUser The user to edit
     */
    public void editUser(User selectedUser) {
        if (selectedUser == null) {
            showAlert("No Selection", "Please select a user to edit.");
            return;
        }

        User editedUser = showUserDialog(selectedUser);
        if (editedUser == null) return;

        try {
            databaseService.updateUser(editedUser);
            loadUsers(); // Refresh the list

            showInfoAlert("User Updated", "User information has been updated successfully.\n\n" +
                         "Note: RFID card information is managed separately through the RFID Encoder tab.");
        } catch (SQLException e) {
            showAlert("Database Error", "Could not edit user: " + e.getMessage());
        }
    }

    /**
     * Removes a user from the database.
     * This will also remove all associated RFID cards due to foreign key constraints.
     * @param selectedUser The user to remove
     */
    public void removeUser(User selectedUser) {
        if (selectedUser == null) {
            showAlert("No Selection", "Please select a user to remove.");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm User Deletion");
        confirmDialog.setHeaderText("Delete User: " + selectedUser.getName());
        confirmDialog.setContentText("Are you sure you want to delete this user?\n\n" +
                                   "This will also remove all associated RFID cards and cannot be undone.");

        if (confirmDialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            databaseService.deleteUser(selectedUser.getUserId());
            loadUsers(); // Refresh the list
            showInfoAlert("User Deleted", "User '" + selectedUser.getName() + "' has been deleted successfully.");
        } catch (SQLException e) {
            showAlert("Database Error", "Could not remove user: " + e.getMessage());
        }
    }

    /**
     * Shows a dialog for adding or editing a user.
     * For editing, RFID card information is shown but cannot be modified.
     * @param user The user to edit, or null to add a new user
     * @return The user with updated information, or null if cancelled
     */
    private User showUserDialog(User user) {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle(user == null ? "Add New User" : "Edit User");
        dialog.setHeaderText(user == null ? "Enter user information" : "Edit user information");

        TextField nameField = new TextField(user == null ? "" : user.getName());
        nameField.setPromptText("Full name");

        ComboBox<String> roleBox = new ComboBox<>(FXCollections.observableArrayList("Admin", "Security", "Guest"));
        roleBox.setValue(user == null ? "Guest" : user.getRole());

        Spinner<Integer> levelSpinner = new Spinner<>(1, 5, user == null ? 1 : user.getSecurityLevel());
        levelSpinner.setEditable(true);

        TextField contactField = new TextField(user == null ? "" : user.getContactInfo());
        contactField.setPromptText("Email or phone number");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Role:"), 0, 1);
        grid.add(roleBox, 1, 1);
        grid.add(new Label("Security Level:"), 0, 2);
        grid.add(levelSpinner, 1, 2);
        grid.add(new Label("Contact Info:"), 0, 3);
        grid.add(contactField, 1, 3);

        // For editing, show current RFID card info (read-only)
        if (user != null) {
            Label rfidInfoLabel = new Label("Current RFID Card:");
            Label rfidValueLabel = new Label(user.getRfidUid() != null ? user.getRfidUid() : "No card assigned");
            rfidValueLabel.setStyle("-fx-text-fill: #666666;");

            grid.add(rfidInfoLabel, 0, 4);
            grid.add(rfidValueLabel, 1, 4);

            Label noteLabel = new Label("Note: RFID card management is available in the RFID Encoder tab.");
            noteLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 10px;");
            grid.add(noteLabel, 0, 5, 2, 1);
        } else {
            Label rfidInfoLabel = new Label("RFID Card will be automatically generated after user creation.");
            rfidInfoLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 10px;");
            grid.add(rfidInfoLabel, 0, 4, 2, 1);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Enable/disable OK button based on required fields
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);

        // Validation: name is required
        nameField.textProperty().addListener((_, _, newValue) -> {
            okButton.setDisable(newValue == null || newValue.trim().isEmpty());
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new User(
                        user == null ? 0 : user.getUserId(),
                        nameField.getText().trim(),
                        roleBox.getValue(),
                        levelSpinner.getValue(),
                        contactField.getText().trim(),
                        user == null ? null : user.getRfidUid(), // Keep existing RFID UID for edits
                        user == null ? LocalDateTime.now() : user.getCreatedAt()
                );
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    /**
     * Generates a default card UID for a new user.
     * Format: CARD_{userId}_{timestamp}
     * @param userId The user ID
     * @return A default card UID
     */
    private String generateDefaultCardUid(int userId) {
        long timestamp = System.currentTimeMillis() % 100000; // Last 5 digits for brevity
        return String.format("%s_%03d_%05d", DEFAULT_CARD_UID_PREFIX, userId, timestamp);
    }

    /**
     * Shows an error alert dialog.
     * @param title The alert title
     * @param message The alert message
     */
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Shows an information alert dialog.
     * @param title The alert title
     * @param message The alert message
     */
    private void showInfoAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }


    /**
     * Updates the filter predicate based on current filter settings.
     */
    private void updateFilterPredicate() {
        filteredUserList.setPredicate(user -> {
            // Search filter (name or contact info)
            if (!searchFilter.isEmpty()) {
                String lowerCaseFilter = searchFilter.toLowerCase();
                if (!user.getName().toLowerCase().contains(lowerCaseFilter) &&
                    !user.getContactInfo().toLowerCase().contains(lowerCaseFilter)) {
                    return false;
                }
            }

            // Role filter
            if (!"All Roles".equals(roleFilter)) {
                if (!user.getRole().equals(roleFilter)) {
                    return false;
                }
            }

            // Security level filter
            if (!"All Levels".equals(securityLevelFilter)) {
                String levelNumber = securityLevelFilter.substring(0, 1);
                if (!String.valueOf(user.getSecurityLevel()).equals(levelNumber)) {
                    return false;
                }
            }

            return true;
        });
    }

    /**
     * Filters users by search text (name or contact info).
     * @param searchText The text to search for
     */
    public void filterBySearch(String searchText) {
        this.searchFilter = searchText != null ? searchText : "";
        updateFilterPredicate();
    }

    /**
     * Filters users by role.
     * @param role The role to filter by
     */
    public void filterByRole(String role) {
        this.roleFilter = role != null ? role : "All Roles";
        updateFilterPredicate();
    }

    /**
     * Filters users by security level.
     * @param securityLevel The security level to filter by
     */
    public void filterBySecurityLevel(String securityLevel) {
        this.securityLevelFilter = securityLevel != null ? securityLevel : "All Levels";
        updateFilterPredicate();
    }

    /**
     * Clears all filters and shows all users.
     */
    public void clearAllFilters() {
        this.searchFilter = "";
        this.roleFilter = "All Roles";
        this.securityLevelFilter = "All Levels";
        updateFilterPredicate();
    }

    /**
     * Applies sorting to the user table based on current sort settings.
     * @param userTable The table view to apply sorting to
     */
    public void applySorting(TableView<User> userTable) {
        sortedUserList.comparatorProperty().bind(userTable.comparatorProperty());
    }

    /**
     * Refreshes the user list from the database.
     */
    public void refreshUserList() {
        loadUsers();
    }

    /**
     * Exports the current filtered user list to a CSV file.
     */
    public void exportToCSV() {
        Platform.runLater(() -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Users to CSV");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv")
            );
            fileChooser.setInitialFileName("users_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");

            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                try (FileWriter writer = new FileWriter(file)) {
                    // Write CSV header
                    writer.append("User ID,Name,Role,Security Level,Contact Info,RFID Card UID,Created At\n");
                    
                    // Write user data
                    for (User user : filteredUserList) {
                        writer.append(String.valueOf(user.getUserId())).append(",");
                        writer.append(escapeCsvField(user.getName())).append(",");
                        writer.append(escapeCsvField(user.getRole())).append(",");
                        writer.append(String.valueOf(user.getSecurityLevel())).append(",");
                        writer.append(escapeCsvField(user.getContactInfo())).append(",");
                        writer.append(escapeCsvField(user.getRfidUid())).append(",");
                        writer.append(user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        writer.append("\n");
                    }
                    
                    showInfoAlert("Export Successful", 
                        "Users exported successfully to: " + file.getAbsolutePath() + 
                        "\nExported " + filteredUserList.size() + " users.");
                        
                } catch (IOException e) {
                    showAlert("Export Error", "Failed to export users: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Escapes CSV field content to handle commas and quotes.
     * @param field The field to escape
     * @return The escaped field
     */
    private String escapeCsvField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
