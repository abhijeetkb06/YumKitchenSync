package com.kitchensync.data.model;

/**
 * Enum of QSR device roles used for device identification in the P2P mesh.
 * Each role has a display name and description shown on the role selection screen.
 */
public enum DeviceRole {
    KIOSK("Kiosk", "Self-order station"),
    KITCHEN("Kitchen", "Receives and prepares orders"),
    STORE_MANAGER("Store Manager", "Monitor store operations");

    private final String displayName;
    private final String description;

    DeviceRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
