package org.kgames.client.model;

/**
 * Model class representing information about an available camera.
 */
public class CameraInfo {
    private final int cameraId;
    private final String name;
    private final String description;
    private final boolean isAvailable;

    public CameraInfo(int cameraId, String name, String description, boolean isAvailable) {
        this.cameraId = cameraId;
        this.name = name;
        this.description = description;
        this.isAvailable = isAvailable;
    }

    public int getCameraId() {
        return cameraId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", name, description);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CameraInfo that = (CameraInfo) obj;
        return cameraId == that.cameraId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(cameraId);
    }
}
