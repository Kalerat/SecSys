package org.kgames.client.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.kgames.client.model.User;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the User Management tab.
 * Handles user CRUD operations, filtering, and RFID card management.
 */
public class UserManagementTabController extends BaseFilterableTabController<User> implements Initializable {

    // User Table and Columns
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, Integer> userIdColumn;
    @FXML private TableColumn<User, String> userNameColumn;
    @FXML private TableColumn<User, String> userRoleColumn;
    @FXML private TableColumn<User, Integer> userSecurityLevelColumn;
    @FXML private TableColumn<User, String> userContactInfoColumn;
    @FXML private TableColumn<User, String> userRfidUidColumn;
    @FXML private TableColumn<User, LocalDateTime> userCreatedAtColumn;

    // Filter Controls
    @FXML private TextField userSearchField;
    @FXML private ComboBox<String> roleFilterComboBox;
    @FXML private ComboBox<String> securityLevelFilterComboBox;
    @FXML private Label filterStatusLabel;
    @FXML private Label userCountLabel;

    // Button Controls
    @FXML private Button addUserButton;
    @FXML private Button editUserButton;
    @FXML private Button removeUserButton;
    @FXML private Button exportUsersButton;
    @FXML private Button refreshUsersButton;
    @FXML private Button clearFiltersButton;

    // RFID Encoder Controls
    @FXML private RadioButton manualSecretRadio;
    @FXML private RadioButton databaseUserRadio;
    @FXML private TextField manualSecretField;
    @FXML private Label selectedUserLabel;
    @FXML private Label selectedUserDetailsLabel;
    @FXML private Label selectedSecretLabel;
    @FXML private Button initializeWriteButton;
    @FXML private Button clearStatusButton;
    @FXML private TextArea statusArea;
    @FXML private ProgressIndicator progressIndicator;

    // RFID Encoder Controller
    private RfidEncoderController rfidEncoderController;
    
    // User Management Service
    private UserManagementController userManagementController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeFilterableData();
        setupUserTable();
        setupFilterControls();
        setupEventHandlers();
    }

    @Override
    protected void onServicesInitialized() {
        super.onServicesInitialized();
        initializeUserManagementController();
        setupRfidEncoder();
    }

    @Override
    public void onTabActivated() {
        super.onTabActivated();
        refreshUserList();
    }

    /**
     * Initializes the user management controller if not already done.
     */
    private void initializeUserManagementController() {
        if (userManagementController == null && databaseService != null) {
            userManagementController = new UserManagementController(databaseService);
        }
    }

    /**
     * Sets up the user table columns and properties.
     */
    private void setupUserTable() {
        userIdColumn.setCellValueFactory(cellData -> 
            javafx.beans.binding.Bindings.createObjectBinding(() -> cellData.getValue().getUserId()));
        userNameColumn.setCellValueFactory(cellData -> 
            javafx.beans.binding.Bindings.createStringBinding(() -> cellData.getValue().getName()));
        userRoleColumn.setCellValueFactory(cellData -> 
            javafx.beans.binding.Bindings.createStringBinding(() -> cellData.getValue().getRole()));
        userSecurityLevelColumn.setCellValueFactory(cellData -> 
            javafx.beans.binding.Bindings.createObjectBinding(() -> cellData.getValue().getSecurityLevel()));
        userContactInfoColumn.setCellValueFactory(cellData -> 
            javafx.beans.binding.Bindings.createStringBinding(() -> cellData.getValue().getContactInfo()));
        userRfidUidColumn.setCellValueFactory(cellData -> 
            javafx.beans.binding.Bindings.createStringBinding(() -> cellData.getValue().getRfidUid()));
        userCreatedAtColumn.setCellValueFactory(cellData -> 
            javafx.beans.binding.Bindings.createObjectBinding(() -> cellData.getValue().getCreatedAt()));

        bindTableToData(userTable);
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        javafx.application.Platform.runLater(() -> {
            if (userTable.getParent() != null) {
                userTable.prefWidthProperty().bind(
                    ((javafx.scene.layout.Region) userTable.getParent()).widthProperty()
                );
            }
        });
        
        userTable.setRowFactory(_ -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onEditUser();
                }
            });
            return row;
        });

        // Setup selection listener
        userTable.getSelectionModel().selectedItemProperty().addListener((_, _, newSelection) -> {
            boolean hasSelection = newSelection != null;
            editUserButton.setDisable(!hasSelection);
            removeUserButton.setDisable(!hasSelection);
        });
    }

    /**
     * Sets up filter controls with default values.
     */
    private void setupFilterControls() {
        // Setup role filter
        roleFilterComboBox.setItems(FXCollections.observableArrayList(
            "All Roles", "Admin", "Security", "Guest"
        ));
        roleFilterComboBox.setValue("All Roles");

        // Setup security level filter
        securityLevelFilterComboBox.setItems(FXCollections.observableArrayList(
            "All Levels", "1", "2", "3", "4", "5"
        ));
        securityLevelFilterComboBox.setValue("All Levels");

        // Initialize status labels
        filterStatusLabel.setText("Showing all users");
        userCountLabel.setText("(0 users)");
    }

    /**
     * Sets up event handlers for UI controls.
     */
    private void setupEventHandlers() {
        userSearchField.textProperty().addListener((_, _, _) -> onUserSearchChanged());
        roleFilterComboBox.valueProperty().addListener((_, _, _) -> onRoleFilterChanged());
        securityLevelFilterComboBox.valueProperty().addListener((_, _, _) -> onSecurityLevelFilterChanged());
    }

    // Button Event Handlers

    @FXML
    private void onAddUser() {
        if (userManagementController != null) {
            userManagementController.addUser();
            refreshUserList();
        }
    }

    @FXML
    private void onEditUser() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected != null && userManagementController != null) {
            userManagementController.editUser(selected);
            refreshUserList();
        }
    }

    @FXML
    private void onRemoveUser() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected != null && userManagementController != null) {
            userManagementController.removeUser(selected);
            refreshUserList();
        }
    }

    @FXML
    private void onExportUsers() {
        if (userManagementController != null) {
            userManagementController.exportToCSV();
        }
    }

    @FXML
    private void onRefreshUsers() {
        refreshUserList();
    }

    @FXML
    private void onClearFilters() {
        userSearchField.clear();
        roleFilterComboBox.setValue("All Roles");
        securityLevelFilterComboBox.setValue("All Levels");
        applyFilters();
    }

    // Filter Event Handlers

    private void onUserSearchChanged() {
        applyFilters();
    }

    private void onRoleFilterChanged() {
        applyFilters();
    }

    private void onSecurityLevelFilterChanged() {
        applyFilters();
    }

    // Data Management Methods

    /**
     * Refreshes the user list from the database.
     */
    public void refreshUserList() {
        refreshData();
    }

    /**
     * Refreshes the data from the database.
     */
    @Override
    public void refreshData() {
        if (databaseService == null) return;

        try {
            List<User> users = databaseService.getAllUsers();
            allItems.setAll(users);
            applyFilters();
        } catch (Exception e) {
            showError("Database Error", "Failed to load users: " + e.getMessage());
        }
    }

    /**
     * Applies current filters to the user list.
     */
    @Override
    protected void applyFilters() {
        String searchText = userSearchField.getText().toLowerCase().trim();
        String roleFilter = roleFilterComboBox.getValue();
        String levelFilter = securityLevelFilterComboBox.getValue();

        filteredItems.setPredicate(user -> {
            boolean matchesSearch = searchText.isEmpty() ||
                user.getName().toLowerCase().contains(searchText) ||
                user.getContactInfo().toLowerCase().contains(searchText);

            boolean matchesRole = roleFilter.equals("All Roles") ||
                user.getRole().equals(roleFilter);

            boolean matchesLevel = levelFilter.equals("All Levels") ||
                String.valueOf(user.getSecurityLevel()).equals(levelFilter);

            return matchesSearch && matchesRole && matchesLevel;
        });

        updateFilterStatus();
    }

    /**
     * Updates the filter status display.
     */
    @Override
    protected void updateFilterStatus() {
        StringBuilder filterDescription = new StringBuilder();
        
        String searchText = userSearchField.getText().trim();
        String roleFilter = roleFilterComboBox.getValue();
        String levelFilter = securityLevelFilterComboBox.getValue();
        
        if (!searchText.isEmpty()) {
            filterDescription.append("Search: '").append(searchText).append("'");
        }
        
        if (!"All Roles".equals(roleFilter)) {
            if (filterDescription.length() > 0) filterDescription.append(", ");
            filterDescription.append("Role: ").append(roleFilter);
        }
        
        if (!"All Levels".equals(levelFilter)) {
            if (filterDescription.length() > 0) filterDescription.append(", ");
            filterDescription.append("Level: ").append(levelFilter);
        }
        
        updateFilterStatusLabel(filterStatusLabel, filterDescription.toString());
        updateCountLabel(userCountLabel, "users");
    }

    /**
     * Sets up the RFID encoder functionality.
     */
    private void setupRfidEncoder() {
        if (databaseService != null && mqttService != null) {
            // Initialize RFID encoder controller
            rfidEncoderController = new RfidEncoderController();
            rfidEncoderController.setServices(databaseService, mqttService);
            rfidEncoderController.initializeWithControls(
                manualSecretRadio, databaseUserRadio, manualSecretField,
                selectedSecretLabel, initializeWriteButton, statusArea, progressIndicator
            );
            
            // Setup user selection listener for RFID encoder
            userTable.getSelectionModel().selectedItemProperty().addListener((_, _, newSelection) -> {
                if (rfidEncoderController != null) {
                    rfidEncoderController.setSelectedUser(newSelection);
                    updateSelectedUserDisplay(newSelection);
                }
            });
        }
    }

    /**
     * Updates the selected user display for RFID encoder.
     */
    private void updateSelectedUserDisplay(User user) {
        if (user != null) {
            selectedUserLabel.setText(user.getName());
            selectedUserLabel.setStyle("-fx-text-fill: green;");
            selectedUserDetailsLabel.setText("Role: " + user.getRole() + 
                " | Security Level: " + user.getSecurityLevel() + 
                " | User ID: " + user.getUserId());
        } else {
            selectedUserLabel.setText("No user selected");
            selectedUserLabel.setStyle("-fx-text-fill: orange;");
            selectedUserDetailsLabel.setText("Select a user from the table to encode RFID card");
        }
    }

    // RFID Encoder Event Handlers
    @FXML
    private void onInitializeWrite() {
        if (rfidEncoderController != null) {
            rfidEncoderController.onInitializeWrite();
        }
    }

    @FXML
    private void onClearStatus() {
        if (rfidEncoderController != null) {
            rfidEncoderController.onClearStatus();
        }
    }

    /**
     * Gets the currently selected user.
     * @return Selected user or null if none selected
     */
    public User getSelectedUser() {
        return userTable.getSelectionModel().getSelectedItem();
    }

    /**
     * Sets the selection to a specific user.
     * @param user User to select
     */
    public void selectUser(User user) {
        userTable.getSelectionModel().select(user);
    }

    /**
     * Handles MQTT messages for RFID encoder functionality.
     * This method should be called from the main MQTT message handler.
     */
    public void handleMqttMessage(String topic, String message) {
        if (rfidEncoderController != null) {
            rfidEncoderController.handleMqttMessage(topic, message);
        }
    }
}