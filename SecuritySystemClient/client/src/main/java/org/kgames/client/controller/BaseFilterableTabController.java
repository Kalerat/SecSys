package org.kgames.client.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

/**
 * Base controller for tabs that need filtering and sorting capabilities.
 * Provides common functionality for data tables with filters.
 * @param <T> The type of objects being managed in the filterable table
 */
public abstract class BaseFilterableTabController<T> extends BaseTabController {
    
    protected ObservableList<T> allItems = FXCollections.observableArrayList();
    protected FilteredList<T> filteredItems;
    protected SortedList<T> sortedItems;
    
    /**
     * Initializes the filterable data structures.
     * Call this method in the initialize() method of the subclass.
     */
    protected void initializeFilterableData() {
        filteredItems = new FilteredList<>(allItems);
        sortedItems = new SortedList<>(filteredItems);
        setupDefaultFiltering();
    }
    
    /**
     * Sets up the table with filtered and sorted data.
     * @param table The table view to bind to the filtered data
     */
    protected void bindTableToData(TableView<T> table) {
        table.setItems(sortedItems);
        sortedItems.comparatorProperty().bind(table.comparatorProperty());
    }
    
    /**
     * Gets the filtered and sorted items list for UI binding.
     * @return The observable list of filtered items
     */
    public ObservableList<T> getFilteredItems() {
        return sortedItems;
    }
    
    /**
     * Gets the total count of all items (before filtering).
     * @return Total item count
     */
    protected int getTotalItemCount() {
        return allItems.size();
    }
    
    /**
     * Gets the count of filtered items.
     * @return Filtered item count
     */
    protected int getFilteredItemCount() {
        return filteredItems.size();
    }
    
    /**
     * Updates filter status label with current filter information.
     * Subclasses must implement this to update their specific UI elements.
     */
    protected abstract void updateFilterStatus();
    
    /**
     * Applies current filters to the data.
     * Subclasses must implement this to define their filtering logic.
     */
    protected abstract void applyFilters();
    
    /**
     * Sets up default filtering behavior.
     * Subclasses can override this to set up initial filter predicates.
     */
    protected void setupDefaultFiltering() {
        // Default implementation does nothing - show all items
        filteredItems.setPredicate(null);
    }
    
    /**
     * Clears all active filters.
     * Subclasses can override this to reset filter-specific controls.
     */
    protected void clearAllFilters() {
        filteredItems.setPredicate(null);
        updateFilterStatus();
    }
    
    /**
     * Refreshes the data from the source.
     * Subclasses must implement this to reload their specific data.
     */
    public abstract void refreshData();
    
    /**
     * Updates a filter status label with formatted text.
     * @param statusLabel The label to update
     * @param filterDescription Description of active filters
     */
    protected void updateFilterStatusLabel(Label statusLabel, String filterDescription) {
        if (statusLabel != null) {
            String statusText;
            if (filterDescription.isEmpty()) {
                statusText = String.format("Showing all %d items", getTotalItemCount());
            } else {
                statusText = String.format("Showing %d of %d items (%s)", 
                    getFilteredItemCount(), getTotalItemCount(), filterDescription);
            }
            statusLabel.setText(statusText);
        }
    }
    
    /**
     * Updates a count label with the current item count.
     * @param countLabel The label to update
     * @param itemType The type of items being counted (e.g., "users", "files")
     */
    protected void updateCountLabel(Label countLabel, String itemType) {
        if (countLabel != null) {
            countLabel.setText(String.format("%d %s", getFilteredItemCount(), itemType));
        }
    }
}
