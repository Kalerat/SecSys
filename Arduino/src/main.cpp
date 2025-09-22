#include <Arduino.h>
#include <SPI.h>
#include <MFRC522.h>
#include <SoftwareSerial.h>

// Debug configuration - set to 1 to enable debug output, 0 to disable
#define DEBUG_ENABLED 1

// Debug macro 
#if DEBUG_ENABLED
  #define DEBUG_PRINT(x) Serial.print(x)
  #define DEBUG_PRINTLN(x) Serial.println(x)
  #define DEBUG_PRINT_HEX(x) Serial.print(x, HEX)
#else
  #define DEBUG_PRINT(x)
  #define DEBUG_PRINTLN(x)
  #define DEBUG_PRINT_HEX(x)
#endif

// Protocol message codes for communication with Pico
enum MessageCode : uint8_t {
  // Arduino -> Pico messages
  MSG_STATUS_READY = 1,
  MSG_MOTION_DETECTED = 2,
  MSG_MOTION_STOPPED = 3,
  MSG_RFID_DETECTED = 4,
  MSG_BUTTON_PRESSED = 5,
  MSG_RFID_READ_SUCCESS = 6,
  MSG_RFID_READ_FAILED = 7,
  MSG_RFID_WRITE_SUCCESS = 8,
  MSG_RFID_WRITE_FAILED = 9,
  MSG_RFID_WRITE_COMPLETED = 10,
  MSG_STATUS_UPDATE = 11,      // General status update
  MSG_HEARTBEAT = 12,          // Periodic heartbeat to indicate Arduino is alive
  
  // Pico -> Arduino commands
  CMD_SET_LED_RGB = 20,        // Takes RGB data: "r,g,b" (0-255 each)
  CMD_SET_BUZZER_ON = 21,
  CMD_SET_BUZZER_OFF = 22,
  CMD_RFID_WRITE_PREPARE = 23, // Prepare for RFID write (store key but don't activate)
  CMD_RFID_WRITE_CONFIRM = 24, // Confirm and activate RFID write mode
  CMD_RFID_NORMAL_MODE = 25,
  CMD_ACK = 26,
  CMD_REQUEST_STATUS = 27     // Request status update
};

// Hardware pins
#define LED_PIN_RED 3
#define LED_PIN_BLUE 5
#define LED_PIN_GREEN 6
#define MOTION_SENSOR_PIN 7
#define BUZZER_PIN 8
#define REARM_BUTTON_PIN 2
#define SS_PIN 10
#define RST_PIN 9

// Hardware objects
SoftwareSerial picoSerial(A0, A1); // RX=A0, TX=A1
MFRC522 rfidReader(SS_PIN, RST_PIN);

// State variables
bool lastPirValue = LOW;
bool lastButtonState = HIGH;
bool rfidWriteMode = false;
bool rfidWritePrepared = false;
char rfidWriteKey[17] = ""; // For storing key to write

// Heartbeat and status variables
unsigned long lastHeartbeat = 0;
const unsigned long heartbeatInterval = 10000;
unsigned long lastMotionChange = 0;
unsigned long lastMotionStatusReport = 0;
const unsigned long motionStatusInterval = 5000;

// Function declarations
void sendMessage(MessageCode code);
void sendMessageWithData(MessageCode code, const char* data);
void processCommand(uint8_t cmd);
void setLEDColor(int red, int green, int blue);
void parseAndSetRGB(const char* rgbData);
void handleRFIDCard();
bool writeSecretKeyToRFID(const char* secretKey);
bool readSecretKeyFromRFID(char* secretKey);
void sendStatusUpdate();

void setup() {
  Serial.begin(9600);
  DEBUG_PRINTLN(F("=== Arduino Security System Starting ==="));

  // Initialize pins
  pinMode(LED_PIN_RED, OUTPUT);
  pinMode(LED_PIN_GREEN, OUTPUT);
  pinMode(LED_PIN_BLUE, OUTPUT);
  pinMode(MOTION_SENSOR_PIN, INPUT_PULLUP);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(REARM_BUTTON_PIN, INPUT_PULLUP);
  
  // Initialize communication
  picoSerial.begin(9600);
  SPI.begin();
  rfidReader.PCD_Init();
  
  
  // Turn off all outputs initially
  digitalWrite(BUZZER_PIN, LOW);
  analogWrite(LED_PIN_RED, 0);
  analogWrite(LED_PIN_GREEN, 0);
  analogWrite(LED_PIN_BLUE, 0);
  
  // Send ready status
  delay(1000); // Give Pico time to initialize
  sendMessage(MSG_STATUS_READY);
  DEBUG_PRINTLN(F("=== Arduino setup complete ==="));
}

void loop() {
  // Handle incoming commands from Pico
  while (picoSerial.available()) {
    uint8_t cmd = picoSerial.read();
    DEBUG_PRINT(F("Received command from Pico: "));
    DEBUG_PRINTLN(cmd);
    processCommand(cmd);
  }
  
  // Send periodic heartbeat
  unsigned long currentTime = millis();
  if (currentTime - lastHeartbeat >= heartbeatInterval) {
    sendMessage(MSG_HEARTBEAT);
    lastHeartbeat = currentTime;
    DEBUG_PRINTLN(F("Heartbeat sent"));
  }
  
  // Send periodic motion status report
  if (currentTime - lastMotionStatusReport >= motionStatusInterval) {
    sendStatusUpdate();
    lastMotionStatusReport = currentTime;
    DEBUG_PRINTLN(F("Motion status report sent"));
  }
  
  // If in RFID write mode, only handle RFID operations
  // This is to prevent other Alarm actions disturbing the write process which might result in a deadlock
  if (rfidWriteMode) {
    DEBUG_PRINTLN(F("In RFID write mode, checking for cards..."));
    if (rfidReader.PICC_IsNewCardPresent() && rfidReader.PICC_ReadCardSerial()) {
      DEBUG_PRINT(F("RFID card detected in write mode, writing key: "));
      DEBUG_PRINTLN(rfidWriteKey);
      
      if (writeSecretKeyToRFID(rfidWriteKey)) {
        DEBUG_PRINTLN(F("RFID write successful"));
        sendMessage(MSG_RFID_WRITE_SUCCESS);
      } else {
        DEBUG_PRINTLN(F("RFID write failed"));
        sendMessage(MSG_RFID_WRITE_FAILED);
      }
      rfidReader.PICC_HaltA();
      rfidReader.PCD_StopCrypto1();
      
      // Exit write mode after attempt
      rfidWriteMode = false;
      rfidWritePrepared = false;
      memset(rfidWriteKey, 0, sizeof(rfidWriteKey));
      sendMessage(MSG_RFID_WRITE_COMPLETED);
    }
    return; // Skip normal operation in write mode
  }
  
  // Motion sensor handling
  bool pirValue = digitalRead(MOTION_SENSOR_PIN);
  if (pirValue != lastPirValue) {
    lastMotionChange = currentTime;
    if (pirValue == HIGH) {
      DEBUG_PRINTLN(F("Motion detected! Sending MSG_MOTION_DETECTED"));
      sendMessage(MSG_MOTION_DETECTED);
    } else {
      DEBUG_PRINTLN(F("Motion stopped! Sending MSG_MOTION_STOPPED"));
      sendMessage(MSG_MOTION_STOPPED);
    }
    lastPirValue = pirValue;
  }
  
  // Button handling
  bool buttonState = digitalRead(REARM_BUTTON_PIN);
  if (lastButtonState == HIGH && buttonState == LOW) { // Button pressed
    DEBUG_PRINTLN(F("Rearm button pressed! Sending MSG_BUTTON_PRESSED"));
    sendMessage(MSG_BUTTON_PRESSED);
  }
  lastButtonState = buttonState;
  
  // RFID handling
  if (rfidReader.PICC_IsNewCardPresent() && rfidReader.PICC_ReadCardSerial()) {
    DEBUG_PRINTLN(F("RFID card detected! Processing card..."));
    handleRFIDCard();
    rfidReader.PICC_HaltA();
    rfidReader.PCD_StopCrypto1();
  }
  
  delay(50); // Small delay to prevent overwhelming the Pico
}

void sendMessage(MessageCode code) {
  DEBUG_PRINT(F("Sending message to Pico: "));
  DEBUG_PRINTLN(code);
  picoSerial.write((uint8_t)code);
}

void sendMessageWithData(MessageCode code, const char* data) {
  picoSerial.write((uint8_t)code);
  picoSerial.print(':');
  picoSerial.print(data);
  picoSerial.write('\n');
}

void processCommand(uint8_t cmd) {
  DEBUG_PRINT(F("Processing command: "));
  DEBUG_PRINTLN(cmd);
  
  switch (cmd) {
    case CMD_SET_LED_RGB:
      DEBUG_PRINTLN(F("Setting LED RGB color, reading color data..."));
      // Read the RGB data from the next bytes until newline
      {
        char rgbData[16] = "";
        int i = 0;
        while (i < 15 && picoSerial.available()) {
          char c = picoSerial.read();
          if (c == '\n' || c == '\0') break;
          if (c == ':') continue; // Skip separator
          rgbData[i++] = c;
        }
        rgbData[i] = '\0';
        DEBUG_PRINT(F("RGB data received: "));
        DEBUG_PRINTLN(rgbData);
        parseAndSetRGB(rgbData);
      }
      break;
      
    case CMD_SET_BUZZER_ON:
      digitalWrite(BUZZER_PIN, HIGH);
      break;
      
    case CMD_SET_BUZZER_OFF:
      digitalWrite(BUZZER_PIN, LOW);
      break;
      
    case CMD_RFID_WRITE_PREPARE:
      DEBUG_PRINTLN(F("Preparing for RFID write mode, reading secret key..."));
      // Read the secret key from the next bytes until newline
      {
        int i = 0;
        while (i < 16 && picoSerial.available()) {
          char c = picoSerial.read();
          if (c == '\n' || c == '\0') break;
          if (c == ':') continue; // Skip separator
          rfidWriteKey[i++] = c;
        }
        rfidWriteKey[i] = '\0';
        rfidWritePrepared = true;
        rfidWriteMode = false; // Not yet in active write mode
        DEBUG_PRINT(F("RFID write prepared with key: "));
        DEBUG_PRINTLN(rfidWriteKey);
      }
      break;
      
    case CMD_RFID_WRITE_CONFIRM:
      DEBUG_PRINTLN(F("Confirming RFID write mode - entering active write mode"));
      if (rfidWritePrepared) {
        rfidWriteMode = true;
        DEBUG_PRINTLN(F("RFID write mode activated"));
      } else {
        DEBUG_PRINTLN(F("ERROR: RFID write not prepared - cannot confirm"));
      }
      break;
      
    case CMD_RFID_NORMAL_MODE:
      DEBUG_PRINTLN(F("Exiting RFID write mode"));
      rfidWriteMode = false;
      rfidWritePrepared = false;
      memset(rfidWriteKey, 0, sizeof(rfidWriteKey));
      break;
      
    case CMD_ACK:
      DEBUG_PRINTLN(F("Received ACK command"));
      // Just acknowledge - no action needed
      break;
      
    case CMD_REQUEST_STATUS:
      DEBUG_PRINTLN(F("Status request received, sending status update"));
      sendStatusUpdate();
      break;
      
    default:
      DEBUG_PRINT(F("Unknown command received: "));
      DEBUG_PRINTLN(cmd);
      break;
  }
}

void handleRFIDCard() {
  DEBUG_PRINTLN(F("Handling RFID card..."));
  sendMessage(MSG_RFID_DETECTED);
  
  char secretKey[17];
  if (readSecretKeyFromRFID(secretKey)) {
    DEBUG_PRINT(F("RFID read successful, secret key: "));
    DEBUG_PRINTLN(secretKey);
    sendMessageWithData(MSG_RFID_READ_SUCCESS, secretKey);
  } else {
    DEBUG_PRINTLN(F("RFID read failed"));
    sendMessage(MSG_RFID_READ_FAILED);
  }
}

bool readSecretKeyFromRFID(char* secretKey) {
  DEBUG_PRINTLN(F("Starting RFID authentication..."));
  
  // Define default MIFARE key (factory default)
  MFRC522::MIFARE_Key key;
  for (byte i = 0; i < 6; i++) {
    key.keyByte[i] = 0xFF;
  }
  
  // Authenticate sector 1, block 0 (block 4)
  byte block = 4;
  byte trailerBlock = 7;

  DEBUG_PRINT(F("Authenticating with sector 1, trailer block "));
  DEBUG_PRINTLN(trailerBlock);
  
  MFRC522::StatusCode status = rfidReader.PCD_Authenticate(
    MFRC522::PICC_CMD_MF_AUTH_KEY_A, trailerBlock, &key, &(rfidReader.uid)
  );
  
  if (status != MFRC522::STATUS_OK) {
    DEBUG_PRINT(F("Authentication failed: "));
    DEBUG_PRINTLN(rfidReader.GetStatusCodeName(status));
    return false;
  }

  DEBUG_PRINTLN(F("Authentication successful, reading block 4..."));
  
  // Read block 4
  byte buffer[18];
  byte bufferSize = sizeof(buffer);
  status = rfidReader.MIFARE_Read(block, buffer, &bufferSize);
  
  if (status != MFRC522::STATUS_OK) {
    DEBUG_PRINT(F("Read failed: "));
    DEBUG_PRINTLN(rfidReader.GetStatusCodeName(status));
    return false;
  }

  DEBUG_PRINTLN(F("Block read successful, extracting secret key..."));

  // Extract secret key
  for (int i = 0; i < 16; i++) {
    secretKey[i] = (char)buffer[i];
  }
  secretKey[16] = '\0';

  DEBUG_PRINT(F("Secret key extracted: "));
  for (int i = 0; i < 16; i++) {
    if (secretKey[i] >= 32 && secretKey[i] <= 126) {
      DEBUG_PRINT(secretKey[i]);
    } else {
      DEBUG_PRINT(".");
    }
  }
  DEBUG_PRINTLN("");
  
  return true;
}

bool writeSecretKeyToRFID(const char* secretKey) {
  DEBUG_PRINT(F("Starting RFID write operation with key: "));
  DEBUG_PRINTLN(secretKey);
  
  // Define default MIFARE key (factory default)
  MFRC522::MIFARE_Key key;
  for (byte i = 0; i < 6; i++) {
    key.keyByte[i] = 0xFF;
  }
  
  // Authenticate sector 1, block 0 (block 4)
  byte block = 4;
  byte trailerBlock = 7;

  DEBUG_PRINTLN(F("Authenticating for write operation..."));

  MFRC522::StatusCode status = rfidReader.PCD_Authenticate(
    MFRC522::PICC_CMD_MF_AUTH_KEY_A, trailerBlock, &key, &(rfidReader.uid)
  );
  
  if (status != MFRC522::STATUS_OK) {
    DEBUG_PRINT(F("Write authentication failed: "));
    DEBUG_PRINTLN(rfidReader.GetStatusCodeName(status));
    return false;
  }
  
  DEBUG_PRINTLN(F("Write authentication successful, preparing data..."));
  
  // Prepare data buffer
  byte dataBuffer[16];
  memset(dataBuffer, 0, 16);
  
  // Copy secret key to buffer
  int keyLen = strlen(secretKey);
  DEBUG_PRINT(F("Copying secret key (length "));
  DEBUG_PRINT(keyLen);
  DEBUG_PRINTLN(F(") to buffer..."));

  for (int i = 0; i < keyLen && i < 16; i++) {
    dataBuffer[i] = (byte)secretKey[i];
  }

  DEBUG_PRINTLN(F("Writing data to block 4..."));
  
  // Write to block 4
  status = rfidReader.MIFARE_Write(block, dataBuffer, 16);
  
  if (status == MFRC522::STATUS_OK) {
    DEBUG_PRINTLN(F("RFID write operation successful!"));
    return true;
  } else {
    DEBUG_PRINT(F("RFID write operation failed: "));
    DEBUG_PRINTLN(rfidReader.GetStatusCodeName(status));
    return false;
  }
}

void setLEDColor(int red, int green, int blue) {
  // Ensure values are within valid range (0-255)
  red = constrain(red, 0, 255);
  green = constrain(green, 0, 255);
  blue = constrain(blue, 0, 255);

  DEBUG_PRINT(F("Setting LED color - R:"));
  DEBUG_PRINT(red);
  DEBUG_PRINT(" G:");
  DEBUG_PRINT(green);
  DEBUG_PRINT(" B:");
  DEBUG_PRINTLN(blue);
  
  analogWrite(LED_PIN_RED, red);
  analogWrite(LED_PIN_GREEN, green);
  analogWrite(LED_PIN_BLUE, blue);
}

void parseAndSetRGB(const char* rgbData) {
  // Parse RGB data in format "r,g,b" or "r g b" or "rrggbb" (hex)
  int red = 0, green = 0, blue = 0;
  
  // Check if it's hex format (6 characters)
  if (strlen(rgbData) == 6) {
    // Parse as hex: "RRGGBB"
    char hexStr[3] = {0};
    
    hexStr[0] = rgbData[0]; hexStr[1] = rgbData[1];
    red = strtol(hexStr, NULL, 16);
    
    hexStr[0] = rgbData[2]; hexStr[1] = rgbData[3];
    green = strtol(hexStr, NULL, 16);
    
    hexStr[0] = rgbData[4]; hexStr[1] = rgbData[5];
    blue = strtol(hexStr, NULL, 16);

    DEBUG_PRINT(F("Parsed hex RGB: "));
    DEBUG_PRINT(red);
    DEBUG_PRINT(",");
    DEBUG_PRINT(green);
    DEBUG_PRINT(",");
    DEBUG_PRINTLN(blue);
  } else {
    // Parse as comma or space separated: "r,g,b" or "r g b"
    char* rgbCopy = strdup(rgbData);
    char* token;
    
    // Try comma separator first
    token = strtok(rgbCopy, ",");
    if (token) {
      red = atoi(token);
      token = strtok(NULL, ",");
      if (token) {
        green = atoi(token);
        token = strtok(NULL, ",");
        if (token) {
          blue = atoi(token);
        }
      }
    } else {
      // Try space separator
      free(rgbCopy);
      rgbCopy = strdup(rgbData);
      token = strtok(rgbCopy, " ");
      if (token) {
        red = atoi(token);
        token = strtok(NULL, " ");
        if (token) {
          green = atoi(token);
          token = strtok(NULL, " ");
          if (token) {
            blue = atoi(token);
          }
        }
      }
    }
    
    free(rgbCopy);

    DEBUG_PRINT(F("Parsed decimal RGB: "));
    DEBUG_PRINT(red);
    DEBUG_PRINT(",");
    DEBUG_PRINT(green);
    DEBUG_PRINT(",");
    DEBUG_PRINTLN(blue);
  }
  
  setLEDColor(red, green, blue);
}

void sendStatusUpdate() {
  // Send status update with current sensor states
  char statusData[64];
  bool currentPirValue = digitalRead(MOTION_SENSOR_PIN);
  unsigned long timeSinceLastChange = millis() - lastMotionChange;
  
  snprintf(statusData, sizeof(statusData), "MOTION:%s,TIME:%lu", 
           currentPirValue ? "ACTIVE" : "INACTIVE", timeSinceLastChange);
  
  sendMessageWithData(MSG_STATUS_UPDATE, statusData);
  DEBUG_PRINT(F("Status update sent: "));
  DEBUG_PRINTLN(statusData);
}
