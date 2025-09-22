package org.kgames.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Alert;
import org.kgames.client.model.AccessLog;
import org.kgames.client.service.DatabaseService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for access log operations.
 * Handles displaying and managing access logs from the security system.
 */
public class AccessLogController {
    private final DatabaseService databaseService;
    private final ObservableList<AccessLog> accessLogList = FXCollections.observableArrayList();
    private FilteredList<AccessLog> filteredAccessLogList;
    private SortedList<AccessLog> sortedAccessLogList;
    
    // Filter state
    private String searchFilter = "";
    private AccessLog.AccessResult accessResultFilter = null;
    private TimeRange timeRangeFilter = TimeRange.ALL_TIME;
    
    // Pagination state
    private int currentPage = 0;
    private final int pageSize = 50;
    private boolean hasMorePages = false;
    private int totalFilteredCount = 0;
    
    /**
     * Enum for time range filtering options.
     */
    public enum TimeRange {
        ALL_TIME("All Time"),
        LAST_HOUR("Last Hour"), 
        LAST_24_HOURS("Last 24 Hours"),
        LAST_WEEK("Last Week"),
        LAST_MONTH("Last Month");
        
        private final String displayName;
        
        TimeRange(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Creates a new AccessLogController with the specified database service.
     * @param databaseService The database service to use for access log operations
     */
    public AccessLogController(DatabaseService databaseService) {
        this.databaseService = databaseService;
        filteredAccessLogList = new FilteredList<>(accessLogList);
        sortedAccessLogList = new SortedList<>(filteredAccessLogList);
    }
    

    /**
     * Gets the observable list of access logs for the TableView.
     * Returns the sorted and filtered list.
     * @return The sorted and filtered access log list
     */
    public ObservableList<AccessLog> getAccessLogList() {
        return sortedAccessLogList;
    }
    
    /**
     * Gets the raw unfiltered access log list.
     * @return The raw access log list
     */
    public ObservableList<AccessLog> getRawAccessLogList() {
        return accessLogList;
    }
    
    /**
     * Applies sorting to the access log table.
     * @param tableView The TableView to apply sorting to
     */
    public void applySorting(javafx.scene.control.TableView<AccessLog> tableView) {
        sortedAccessLogList.comparatorProperty().bind(tableView.comparatorProperty());
    }
    
    /**
     * Filters access logs by search text.
     * @param searchText The search text
     */
    public void filterBySearch(String searchText) {
        this.searchFilter = searchText != null ? searchText : "";
        applyFiltersAndReload();
    }
    
    /**
     * Filters access logs by access result.
     * @param result The access result to filter by (null for all)
     */
    public void filterByAccessResult(AccessLog.AccessResult result) {
        this.accessResultFilter = result;
        applyFiltersAndReload();
    }
    
    /**
     * Filters access logs by time range.
     * @param timeRange The time range to filter by
     */
    public void filterByTimeRange(TimeRange timeRange) {
        this.timeRangeFilter = timeRange != null ? timeRange : TimeRange.ALL_TIME;
        applyFiltersAndReload();
    }
    
    /**
     * Clears all filters and resets to first page.
     */
    public void clearFilters() {
        this.searchFilter = "";
        this.accessResultFilter = null;
        this.timeRangeFilter = TimeRange.ALL_TIME;
        currentPage = 0;
        loadAccessLogsPage(0);
    }

    /**
     * Loads the next page of results.
     */
    public void loadNextPage() {
        if (hasMorePages) {
            loadAccessLogsPage(currentPage + 1);
        }
    }

    /**
     * Loads the previous page of results.
     */
    public void loadPreviousPage() {
        if (currentPage > 0) {
            loadAccessLogsPage(currentPage - 1);
        }
    }

    /**
     * Gets the current page number.
     * @return The current page number
     */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Gets the page size.
     * @return The number of items per page
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Checks if there are more pages available.
     * @return true if there are more pages, false otherwise
     */
    public boolean hasMorePages() {
        return hasMorePages;
    }

    /**
     * Checks if there is a previous page available.
     * @return true if current page > 0, false otherwise
     */
    public boolean hasPreviousPage() {
        return currentPage > 0;
    }

    /**
     * Gets the total count of filtered records.
     * @return The total number of records matching current filters
     */
    public int getTotalFilteredCount() {
        return totalFilteredCount;
    }

    /**
     * Updates the filter and reloads from the first page.
     */
    private void applyFiltersAndReload() {
        currentPage = 0;
        loadAccessLogsPage(0);
    }
    
    /**
     * Gets the current filter status description.
     * @return A description of the current filters
     */
    public String getFilterStatusDescription() {
        StringBuilder status = new StringBuilder();
        boolean hasFilters = false;
        
        if (!searchFilter.isEmpty()) {
            status.append("Search: \"").append(searchFilter).append("\"");
            hasFilters = true;
        }
        
        if (accessResultFilter != null) {
            if (hasFilters) status.append(" | ");
            status.append("Result: ").append(accessResultFilter.getDisplayName());
            hasFilters = true;
        }
        
        if (timeRangeFilter != TimeRange.ALL_TIME) {
            if (hasFilters) status.append(" | ");
            status.append("Time: ").append(timeRangeFilter.getDisplayName());
            hasFilters = true;
        }
        
        return hasFilters ? status.toString() : "Showing all logs";
    }
    
    /**
     * Gets the current count of visible (filtered) access logs.
     * @return The count of visible logs
     */
    public int getVisibleLogCount() {
        return filteredAccessLogList.size();
    }
    
    /**
     * Gets the total count of access logs (unfiltered).
     * @return The total count of logs
     */
    public int getTotalLogCount() {
        return accessLogList.size();
    }

    /**
     * Loads all access logs from the database into the observable list.
     */
    public void loadAccessLogs() {
        try {
            List<AccessLog> logs = databaseService.getAllAccessLogs();
            accessLogList.clear();
            accessLogList.addAll(logs);
        } catch (SQLException e) {
            showAlert("Database Error", "Could not load access logs: " + e.getMessage());
        }
    }

    /**
     * Loads access logs with pagination and filtering.
     * @param page The page number to load (0-based)
     */
    public void loadAccessLogsPage(int page) {
        try {
            currentPage = page;
            
            List<AccessLog> allLogs = databaseService.getRecentAccessLogs(500);
            
            List<AccessLog> filteredLogs = applyLocalFiltering(allLogs);
            
            // Calculate pagination
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, filteredLogs.size());
            
            List<AccessLog> pageData = new ArrayList<>();
            if (startIndex < filteredLogs.size()) {
                pageData = filteredLogs.subList(startIndex, endIndex);
            }
            
            // Check if there are more pages
            hasMorePages = endIndex < filteredLogs.size();
            totalFilteredCount = filteredLogs.size();
            
            accessLogList.clear();
            accessLogList.addAll(pageData);
            
        } catch (SQLException e) {
            showAlert("Database Error", "Could not load access logs: " + e.getMessage());
        }
    }

    /**
     * Applies local filtering to the access logs.
     * @param logs The list of logs to filter
     * @return The filtered list
     */
    private List<AccessLog> applyLocalFiltering(List<AccessLog> logs) {
        return logs.stream()
            .filter(log -> {
                // Search filter
                if (searchFilter != null && !searchFilter.trim().isEmpty()) {
                    String search = searchFilter.toLowerCase();
                    boolean matches = false;
                    if (log.getUserName() != null && log.getUserName().toLowerCase().contains(search)) {
                        matches = true;
                    }
                    if (log.getReason() != null && log.getReason().toLowerCase().contains(search)) {
                        matches = true;
                    }
                    if (!matches) return false;
                }
                
                // Access result filter
                if (accessResultFilter != null && !accessResultFilter.equals(log.getAccessResult())) {
                    return false;
                }
                
                // Time range filter
                if (timeRangeFilter != null && timeRangeFilter != TimeRange.ALL_TIME) {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime cutoff = switch (timeRangeFilter) {
                        case LAST_HOUR -> now.minusHours(1);
                        case LAST_24_HOURS -> now.minusHours(24);
                        case LAST_WEEK -> now.minusWeeks(1);
                        case LAST_MONTH -> now.minusMonths(1);
                        default -> now.minusYears(100);
                    };
                    if (log.getTimestamp().isBefore(cutoff)) {
                        return false;
                    }
                }
                
                return true;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Loads recent access logs (limited number) from the database.
     * @param limit The maximum number of logs to load
     * @deprecated Use loadAccessLogsPage(0) instead for paginated loading
     */
    @Deprecated
    public void loadRecentAccessLogs(int limit) {
        try {
            List<AccessLog> logs = databaseService.getRecentAccessLogs(limit);
            accessLogList.clear();
            accessLogList.addAll(logs);
        } catch (SQLException e) {
            showAlert("Database Error", "Could not load recent access logs: " + e.getMessage());
        }
    }

    /**
     * Loads access logs for a specific user.
     * @param userId The user ID to filter by
     */
    public void loadAccessLogsByUser(int userId) {
        try {
            List<AccessLog> logs = databaseService.getAccessLogsByUser(userId);
            accessLogList.clear();
            accessLogList.addAll(logs);
        } catch (SQLException e) {
            showAlert("Database Error", "Could not load access logs for user: " + e.getMessage());
        }
    }

    /**
     * Adds a new access log entry to the database and refreshes the list.
     * This method is typically called when processing MQTT messages from the security system.
     *
     * @param cardUid The RFID card UID (can be null)
     * @param accessResult The result of the access attempt
     * @param reason The reason for the access result
     */
    public void addAccessLog(String cardUid, AccessLog.AccessResult accessResult, String reason) {
        try {
            Integer userId = null;
            Integer cardId = null;

            // Look up user and card IDs if card UID is provided
            if (cardUid != null && !cardUid.isBlank()) {
                userId = databaseService.getUserIdByCardUid(cardUid);
                cardId = databaseService.getCardIdByUid(cardUid);
            }

            // Device Management has not yet been implemented due to time constraints
            // Because of this we simply set the deviceId to null
            // This will be updated once device management is in place
            Integer deviceId = null;

            databaseService.addAccessLog(userId, cardId, deviceId, accessResult, reason);

            // Refresh the access log list to show the new entry
            // Reset to first page to show the newest entry
            loadAccessLogsPage(0);
        } catch (SQLException e) {
            showAlert("Database Error", "Could not add access log: " + e.getMessage());
        }
    }

    /**
     * Processes MQTT messages related to access control and creates access log entries.
     * This method parses MQTT messages from the Arduino/Raspberry Pi and creates appropriate log entries.
     *
     * @param topic The MQTT topic
     * @param message The MQTT message content
     */
    public void processMqttAccessMessage(String topic, String message) {
        try {
            // Parse different types of access-related messages
            if (message.startsWith("ACCESS_GRANTED:")) {
                String cardUid = message.substring("ACCESS_GRANTED:".length()).trim();
                addAccessLog(cardUid, AccessLog.AccessResult.GRANTED, "Valid RFID card scanned");
            } else if (message.startsWith("ACCESS_DENIED:")) {
                String[] parts = message.substring("ACCESS_DENIED:".length()).trim().split(":", 2);
                String cardUid = parts.length > 0 ? parts[0] : null;
                String reason = parts.length > 1 ? parts[1] : "Unknown card";
                addAccessLog(cardUid, AccessLog.AccessResult.DENIED, reason);
            } else if (message.startsWith("INVALID_CARD:")) {
                String cardUid = message.substring("INVALID_CARD:".length()).trim();
                addAccessLog(cardUid, AccessLog.AccessResult.DENIED, "Invalid or unregistered card");
            } else if (message.startsWith("CARD_SCAN:")) {
                // This has not yet been implemented and should never be reached
                // For now we simply add a log entry for the card scan should we end up here for some reason
                String cardUid = message.substring("CARD_SCAN:".length()).trim();
                addAccessLog(cardUid, AccessLog.AccessResult.DENIED, "Card scanned - processing");
            }
        } catch (Exception e) {
            System.err.println("Error processing MQTT access message: " + e.getMessage());
        }
    }

    /**
     * Refreshes the access log list by reloading from the database.
     */
    public void refreshAccessLogs() {
        // Preserve current page when refreshing
        int currentPage = getCurrentPage();
        loadAccessLogsPage(currentPage);
    }

    /**
     * Clears the access log list (for testing or refresh purposes).
     */
    public void clearAccessLogs() {
        accessLogList.clear();
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
}
