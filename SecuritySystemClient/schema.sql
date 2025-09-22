-- Security System Database Schema

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    role ENUM('Admin','Security','Guest') NOT NULL,
    security_level INT NOT NULL DEFAULT 1 CHECK (security_level >= 1 AND security_level <= 5),
    contact_info VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. RFID Cards Table
CREATE TABLE IF NOT EXISTS rfid_cards (
    card_id INT AUTO_INCREMENT PRIMARY KEY,
    card_uid VARCHAR(50) NOT NULL UNIQUE,
    card_secret VARCHAR(16) NOT NULL UNIQUE,
    user_id INT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_card_secret (card_secret),
    INDEX idx_card_uid (card_uid),
    INDEX idx_active (active)
);

-- 3. Devices Table
CREATE TABLE IF NOT EXISTS devices (
    device_id INT AUTO_INCREMENT PRIMARY KEY,
    device_type ENUM('RFID Reader','Camera','PIR Sensor','Alarm','Remote Access') NOT NULL,
    location VARCHAR(100),
    ip_address VARCHAR(45),
    status ENUM('Online','Offline','Maintenance') DEFAULT 'Online',
    installed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Access Logs Table
CREATE TABLE IF NOT EXISTS access_logs (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id INT NULL,
    card_id INT NULL,
    device_id INT NULL,
    access_result ENUM('Granted','Denied') NOT NULL,
    reason VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    FOREIGN KEY (card_id) REFERENCES rfid_cards(card_id) ON DELETE SET NULL,
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE SET NULL,
    INDEX idx_access_logs_timestamp (timestamp),
    INDEX idx_access_result (access_result)
);

-- 5. Motion Events Table
CREATE TABLE IF NOT EXISTS motion_events (
    event_id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    device_id INT NOT NULL,
    detected BOOLEAN DEFAULT TRUE,
    sensitivity INT,
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
    INDEX idx_motion_events_timestamp (timestamp)
);

-- 6. Camera Records Table
CREATE TABLE IF NOT EXISTS camera_records (
    record_id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    device_id INT NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    triggered_by ENUM('Manual','Motion','Alarm') NOT NULL,
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
    INDEX idx_camera_records_timestamp (timestamp)
);

-- 7. Alarm Events Table
CREATE TABLE IF NOT EXISTS alarm_events (
    alarm_id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    device_id INT NOT NULL,
    triggered_by ENUM('Motion','Manual','Remote','RFID Failure') NOT NULL,
    duration_sec INT,
    resolved BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
    INDEX idx_alarm_events_timestamp (timestamp)
);

-- 8. Remote Commands Table
CREATE TABLE IF NOT EXISTS remote_commands (
    command_id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    issued_by INT NULL,
    device_id INT NOT NULL,
    command VARCHAR(100) NOT NULL,
    status ENUM('Pending','Executed','Failed') DEFAULT 'Pending',
    FOREIGN KEY (issued_by) REFERENCES users(user_id) ON DELETE SET NULL,
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
    INDEX idx_remote_commands_timestamp (timestamp)
);

-- Sample Data
-- Insert a default RFID reader device
INSERT INTO devices (device_type, location, status) VALUES
('RFID Reader', 'Main Entrance', 'Online')
ON DUPLICATE KEY UPDATE device_type=device_type;

-- Sample users for testing
INSERT INTO users (name, role, security_level, contact_info) VALUES
('Admin User', 'Admin', 5, 'admin@security.local'),
('John Doe', 'Security', 3, 'john.doe@company.com'),
('Jane Smith', 'Guest', 1, 'jane.smith@company.com')
ON DUPLICATE KEY UPDATE name=name;

-- Sample RFID cards with 16-character hex secrets for testing
INSERT INTO rfid_cards (card_uid, card_secret, user_id, active) VALUES
('ADMIN001', '1234567890ABCDEF', 1, TRUE),
('USER001', 'ABCDEF1234567890', 2, TRUE),
('GUEST001', 'FEDCBA0987654321', 3, TRUE)
ON DUPLICATE KEY UPDATE card_uid=card_uid;
