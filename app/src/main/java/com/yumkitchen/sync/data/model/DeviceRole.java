package com.yumkitchen.sync.data.model;

public enum DeviceRole {
    WAITER("Waiter", "Takes orders from tables"),
    KITCHEN("Kitchen", "Receives and prepares orders"),
    MANAGER("Manager", "Monitors operations");

    private final String displayName;
    private final String description;

    DeviceRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
