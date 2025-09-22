package org.kgames.client.model;

/**
 * Represents a camera configuration that can be either local or IP-based.
 */
public class CameraConfiguration {
    
    public enum CameraType {
        LOCAL, IP_CAMERA
    }
    
    private String id;
    private String name;
    private CameraType type;
    
    // Local camera properties
    private int localId;
    
    // IP camera properties
    private String ipAddress;
    private int port;
    private String username;
    private String password;
    private String rtspPath;
    
    /**
     * Creates a local camera configuration.
     */
    public CameraConfiguration(String id, String name, int localId) {
        this.id = id;
        this.name = name;
        this.type = CameraType.LOCAL;
        this.localId = localId;
    }
    
    /**
     * Creates an IP camera configuration.
     */
    public CameraConfiguration(String id, String name, String ipAddress, int port, 
                             String username, String password, String rtspPath) {
        this.id = id;
        this.name = name;
        this.type = CameraType.IP_CAMERA;
        this.ipAddress = ipAddress;
        this.port = port;
        this.username = username;
        this.password = password;
        this.rtspPath = rtspPath;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public CameraType getType() { return type; }
    public void setType(CameraType type) { this.type = type; }
    
    public int getLocalId() { return localId; }
    public void setLocalId(int localId) { this.localId = localId; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getRtspPath() { return rtspPath; }
    public void setRtspPath(String rtspPath) { this.rtspPath = rtspPath; }
    
    /**
     * Gets the connection string for this camera based on its type.
     */
    public String getConnectionString() {
        if (type == CameraType.LOCAL) {
            return String.valueOf(localId);
        } else {
            // Build RTSP URL for IP camera
            String auth = (username != null && !username.isEmpty()) ? 
                username + ":" + password + "@" : "";
            String portStr = (port > 0 && port != 554) ? ":" + port : "";
            return "rtsp://" + auth + ipAddress + portStr + "/" + (rtspPath != null ? rtspPath : "");
        }
    }
    
    @Override
    public String toString() {
        return name + " (" + type + ")";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CameraConfiguration)) return false;
        CameraConfiguration other = (CameraConfiguration) obj;
        return id != null && id.equals(other.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
