import network
import time
import ubinascii
import machine
from machine import UART
import gc

# Try to import MQTT, handle if not available
try:
    from umqtt.simple import MQTTClient # type: ignore
    MQTT_AVAILABLE = True
except ImportError:
    print("MQTT library not available - running in debug mode")
    MQTT_AVAILABLE = False

# WiFi credentials
ssid = 'NETWORK_NAME_HERE' # Replace with your WiFi SSID
password = 'NETWORK_PASSWORD_HERE' # Replace with your WiFi password

# MQTT Broker IP (your Windows PC IP)
mqtt_server = '0.0.0.0'  # Replace with PC's local IP
mqtt_port = 1883

client_id = ubinascii.hexlify(machine.unique_id())
topic_pub = b'home/arduino/events'
topic_sub = b'home/arduino/command'

topic_auth_request = b'home/arduino/auth_requests'
topic_auth_response = b'home/arduino/auth_response'

# Message codes from Arduino
MSG_STATUS_READY = 1
MSG_MOTION_DETECTED = 2
MSG_MOTION_STOPPED = 3
MSG_RFID_DETECTED = 4
MSG_BUTTON_PRESSED = 5
MSG_RFID_READ_SUCCESS = 6
MSG_RFID_READ_FAILED = 7
MSG_RFID_WRITE_SUCCESS = 8
MSG_RFID_WRITE_FAILED = 9
MSG_RFID_WRITE_COMPLETED = 10
MSG_STATUS_UPDATE = 11       # General status update
MSG_HEARTBEAT = 12          # Periodic heartbeat from Arduino

# Commands to Arduino
CMD_SET_LED_RGB = 20          # Takes RGB data: "r,g,b" or "RRGGBB"
CMD_SET_BUZZER_ON = 21
CMD_SET_BUZZER_OFF = 22
CMD_RFID_WRITE_PREPARE = 23   # Prepare for RFID write (store key but don't activate)
CMD_RFID_WRITE_CONFIRM = 24   # Confirm and activate RFID write mode
CMD_RFID_NORMAL_MODE = 25
CMD_ACK = 26
CMD_REQUEST_STATUS = 27       # Request status update

# Predefined LED colors
LED_OFF = (0, 0, 0)
LED_RED = (255, 0, 0)
LED_GREEN = (0, 255, 0)
LED_BLUE = (0, 0, 255)
LED_YELLOW = (255, 255, 0)
LED_PURPLE = (255, 0, 255)
LED_CYAN = (0, 255, 255)
LED_WHITE = (255, 255, 255)
LED_ORANGE = (255, 165, 0)

# Security system state
class SecurityState:
    READY = "READY"
    MOTION_DETECTED = "MOTION_DETECTED"
    ALARM_ACTIVE = "ALARM_ACTIVE"
    ALARM_DISABLED = "ALARM_DISABLED"
    RFID_WRITE_MODE = "RFID_WRITE_MODE"

# State variables
current_state = SecurityState.READY
motion_start_time = 0
alarm_disabled_time = 0
alarm_disable_duration = 60000  # 60 seconds in milliseconds
motion_grace_period = 5000      # 5 seconds in milliseconds
current_rfid_secret = None
authenticated_keys = set()

# Alarm control variables
manually_activated = False      # Track if alarm was manually activated
alarm_disable_end_time = 0     # When timed disable ends
alarm_disable_permanent = False # If alarm is permanently disabled

# Arduino heartbeat monitoring
last_arduino_heartbeat = 0
arduino_timeout = 30000  # 30 seconds without heartbeat = communication error
arduino_connected = False

# LED blinking state for async operation
led_blink_active = False
led_blink_count = 0
led_blink_max_count = 0
led_blink_last_time = 0
led_blink_interval = 200  # 200ms for each on/off phase
led_blink_is_on = False
led_blink_color = LED_OFF  # Current blink color

# Pico heartbeat for client communication
last_pico_heartbeat = 0
pico_heartbeat_interval = 15000  # Send heartbeat every 15 seconds

def connect_wifi():
    """Connect to WiFi with error handling"""
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    
    if wlan.isconnected():
        print('Already connected to WiFi:', wlan.ifconfig())
        return wlan
    
    print(f'Connecting to WiFi network: {ssid}')
    wlan.connect(ssid, password)
    
    max_wait = 20
    while max_wait > 0:
        if wlan.status() < 0 or wlan.status() >= 3:
            break
        max_wait -= 1
        print(f'Waiting for connection... ({max_wait})')
        time.sleep(1)
    
    if wlan.status() != 3:
        print(f'WiFi connection failed. Status: {wlan.status()}')
        return None
    else:
        print('WiFi connected successfully!')
        status = wlan.ifconfig()
        print(f'IP: {status[0]}')
        return wlan

# MQTT Callback - Handle commands from server
def sub_cb(topic, msg):
    print('MQTT Message received:', topic, msg)
    global current_state
    
    try:
        msg_str = msg.decode('utf-8')
        
        # Handle authentication responses
        if topic == topic_auth_response:
            if msg_str == "AUTH_SUCCESS":
                handle_auth_success()
            elif msg_str == "AUTH_FAILED":
                handle_auth_failed()
            return
            
        # Handle other commands
        if msg_str == "CMD_DISABLE_ALARM":
            disable_alarm()
        elif msg_str == "CMD_ACTIVATE_ALARM":
            activate_alarm_manual()
        elif msg_str == "CMD_RESET_ALARM":
            reset_alarm()
        elif msg_str == "CMD_DISABLE_ALARM_PERMANENT":
            disable_alarm_permanent()
        elif msg_str.startswith("CMD_DISABLE_ALARM_TIMED:"):
            minutes = int(msg_str[25:])  # Extract minutes after "CMD_DISABLE_ALARM_TIMED:"
            disable_alarm_timed(minutes)
        elif msg_str == "CMD_ENABLE_ALARM":
            enable_alarm()
        elif msg_str.startswith("CMD_RFID_WRITE_PREPARE:"):
            secret_key = msg_str[23:]  # Extract key after "CMD_RFID_WRITE_PREPARE:"
            prepare_rfid_write_mode(secret_key)
        elif msg_str == "CMD_RFID_WRITE_CONFIRM":
            confirm_rfid_write_mode()
        elif msg_str == "CMD_ABORT":
            abort_operation()
        elif msg_str == "CMD_RFID_WRITE_INITALIZE":
            initialize_rfid_write()
            
    except Exception as e:
        print("Error processing MQTT message:", e)

def connect_mqtt():
    """Connect to MQTT broker with error handling"""
    if not MQTT_AVAILABLE:
        print("MQTT not available - running without MQTT")
        return None
    
    try:
        print(f"MQTT Configuration:")
        print(f"  Server: {mqtt_server}:{mqtt_port}")
        print(f"  Client ID: {client_id}")
        print(f"  Publishing to: {topic_pub}")
        print(f"  Subscribing to: {topic_sub}, {topic_auth_response}")
        
        client = MQTTClient(client_id, mqtt_server, port=mqtt_port, keepalive=60)
        client.set_callback(sub_cb)
        
        print(f'Connecting to MQTT broker at {mqtt_server}:{mqtt_port}')
        client.connect()
        
        client.subscribe(topic_sub)
        client.subscribe(topic_auth_response)
        
        print('MQTT Connected & Subscribed successfully!')
        return client
        
    except OSError as e:
        print(f'MQTT connection failed: {e}')
        print('Check if MQTT broker is running and accessible')
        return None
    except Exception as e:
        print(f'Unexpected MQTT error: {e}')
        return None

# Connect to WiFi
wlan = connect_wifi()
if not wlan:
    print("Failed to connect to WiFi - stopping")
    raise Exception("WiFi connection failed")

# Connect to MQTT
client = connect_mqtt()

# UART to Arduino
uart = UART(0, baudrate=9600, tx=0, rx=1)  # GP0=TX, GP1=RX

# Color constants
LED_OFF = (0, 0, 0)
LED_RED = (255, 0, 0)
LED_GREEN = (0, 255, 0)
LED_BLUE = (0, 0, 255)
LED_YELLOW = (255, 255, 0)
LED_PURPLE = (255, 0, 255)
LED_CYAN = (0, 255, 255)
LED_WHITE = (255, 255, 255)
LED_ORANGE = (255, 165, 0)

def safe_mqtt_publish(topic, message):
    """Safely publish MQTT message with error handling"""
    if client is None:
        print(f"MQTT not available - would publish: {topic}: {message}")
        return False
    
    try:
        client.publish(topic, message)
        print(f"MQTT published: {topic.decode()} -> {message}")
        return True
    except Exception as e:
        print(f"MQTT publish failed: {e}")
        return False

def safe_mqtt_check():
    """Safely check MQTT messages with error handling"""
    if client is None:
        return
    
    try:
        client.check_msg()
    except OSError as e:
        if e.errno == -1:  # no data available, not an error
            pass  # This is normal, just means no MQTT messages waiting
        else:
            print(f"MQTT OSError: {e}")
    except Exception as e:
        print(f"MQTT check_msg failed: {e}")

def send_uart_command(cmd):
    """Send a command code to Arduino"""
    uart.write(bytes([cmd]))

def send_uart_command_with_data(cmd, data):
    """Send a command with data to Arduino"""
    uart.write(bytes([cmd]))
    uart.write(b':')
    uart.write(data.encode('utf-8'))
    uart.write(b'\n')

def set_led_color(color):
    """Set LED color - flexible function that accepts:
    
    Args:
        color: Can be:
            - Predefined color constants: LED_RED, LED_GREEN, LED_BLUE, LED_OFF, etc.
            - RGB tuple: (r, g, b) where each value is 0-255
            - String: "r,g,b" or hex "RRGGBB"
    
    Examples:
        set_led_color(LED_RED)           # Use predefined constant
        set_led_color((255, 128, 0))     # Orange using RGB tuple
        set_led_color("255,0,255")       # Purple using string
        set_led_color("FF00FF")          # Purple using hex
    """
    if isinstance(color, tuple) and len(color) == 3:
        # RGB tuple: (r, g, b)
        red, green, blue = color
        # Ensure values are within valid range
        red = max(0, min(255, int(red)))
        green = max(0, min(255, int(green)))
        blue = max(0, min(255, int(blue)))
        rgb_data = f"{red},{green},{blue}"
    elif isinstance(color, str):
        # String format: "r,g,b" or "RRGGBB"
        rgb_data = color
    else:
        # Invalid format, default to off
        print(f"Invalid color format: {color}, using LED_OFF")
        red, green, blue = LED_OFF
        rgb_data = f"{red},{green},{blue}"
    
    send_uart_command_with_data(CMD_SET_LED_RGB, rgb_data)

def start_led_blink(color, blink_count):
    """Start asynchronous LED blinking
    
    Args:
        color: LED color to blink (e.g., LED_RED)
        blink_count: Number of times to blink (each blink = on+off)
    """
    global led_blink_active, led_blink_count, led_blink_max_count
    global led_blink_last_time, led_blink_is_on, led_blink_color
    
    led_blink_active = True
    led_blink_count = 0
    led_blink_max_count = blink_count * 2  # *2 because each blink has on+off phases
    led_blink_last_time = time.ticks_ms()
    led_blink_is_on = False
    led_blink_color = color
    
    # Start with LED on
    set_led_color(color)
    led_blink_is_on = True

def update_led_blink():
    """Update LED blinking state - call this in main loop"""
    global led_blink_active, led_blink_count, led_blink_last_time, led_blink_is_on
    
    if not led_blink_active:
        return
        
    current_time = time.ticks_ms()
    
    # Check if it's time to toggle the LED
    if time.ticks_diff(current_time, led_blink_last_time) >= led_blink_interval:
        led_blink_count += 1
        led_blink_last_time = current_time
        
        # Check if we've completed all blinks
        if led_blink_count >= led_blink_max_count:
            # Finished blinking - turn off LED and stop
            set_led_color(LED_OFF)
            led_blink_active = False
            led_blink_is_on = False
            return
        
        # Toggle LED state
        if led_blink_is_on:
            set_led_color(LED_OFF)
            led_blink_is_on = False
        else:
            set_led_color(led_blink_color)
            led_blink_is_on = True

def handle_motion_detected():
    """Handle motion sensor activation"""
    global motion_start_time, current_state
    
    if current_state == SecurityState.ALARM_DISABLED:
        # Send motion status but don't change state when alarm is disabled
        safe_mqtt_publish(topic_pub, "MOTION_DETECTED")
        print("Motion detected (alarm disabled)")
        return
    
    # Always send MQTT message for motion status
    safe_mqtt_publish(topic_pub, "MOTION_DETECTED")
    
    if current_state == SecurityState.ALARM_ACTIVE:
        # Motion detected during active alarm - just report it, don't change state
        print("Motion detected (alarm already active)")
        return
    
    # Only change state and start timer if we're in READY state
    if current_state == SecurityState.READY:
        motion_start_time = time.ticks_ms()
        current_state = SecurityState.MOTION_DETECTED

        # Turn on orange LED to indicate motion
        set_led_color(LED_ORANGE)
        print("Motion detected - starting 5 second grace period")

def handle_motion_stopped():
    """Handle motion sensor deactivation"""
    global current_state
    
    safe_mqtt_publish(topic_pub, "MOTION_STOPPED")
    
    if current_state == SecurityState.ALARM_DISABLED:
        print("Motion stopped (alarm disabled)")
        return
    
    if current_state == SecurityState.ALARM_ACTIVE:
        # Motion stopped during active alarm - just report it, don't change alarm state
        print("Motion stopped (alarm remains active)")
        return
    
    if current_state == SecurityState.MOTION_DETECTED:
        # Motion stopped during grace period - cancel alarm trigger and return to ready
        current_state = SecurityState.READY
        set_led_color(LED_OFF)
        print("Motion stopped - alarm trigger cancelled")
        return
    
    # For any other state, just log
    print("Motion stopped")

def check_motion_timeout():
    """Check if motion has been active long enough to trigger alarm"""
    global current_state
    
    if current_state == SecurityState.MOTION_DETECTED:
        if time.ticks_diff(time.ticks_ms(), motion_start_time) > motion_grace_period:
            activate_alarm()

def activate_alarm():
    """Activate the alarm system (automatic - triggered by motion timeout)"""
    global current_state, manually_activated
    
    if current_state == SecurityState.ALARM_DISABLED:
        return
        
    current_state = SecurityState.ALARM_ACTIVE
    manually_activated = False
    send_uart_command(CMD_SET_BUZZER_ON)
    set_led_color(LED_RED)
    
    safe_mqtt_publish(topic_pub, "ALARM_TRIGGERED")
    print("ALARM ACTIVATED - Motion detected for more than 5 seconds")

def handle_rfid_detected(secret_key):
    """Handle RFID card detection"""
    global current_rfid_secret
    
    current_rfid_secret = secret_key
    
    # Send authentication request to server
    auth_request = f"AUTH_REQUEST:{secret_key}"
    safe_mqtt_publish(topic_auth_request, auth_request)
    print(f"RFID authentication request sent: {secret_key}")

def handle_auth_success():
    """Handle successful authentication"""
    global current_state, alarm_disabled_time
    
    # RFID cannot disable manually activated alarms
    if manually_activated and current_state == SecurityState.ALARM_ACTIVE:
        print("Authentication successful but alarm is manually activated - RFID disable blocked")
        safe_mqtt_publish(topic_auth_request, "ACK_AUTH_SUCCESS")
        safe_mqtt_publish(topic_pub, "AUTH_SUCCESS_BLOCKED")
        return
    
    print("Authentication successful - disabling alarm")
    current_state = SecurityState.ALARM_DISABLED
    alarm_disabled_time = time.ticks_ms()
    
    send_uart_command(CMD_SET_BUZZER_OFF)
    set_led_color(LED_GREEN)

    safe_mqtt_publish(topic_auth_request, "ACK_AUTH_SUCCESS")
    safe_mqtt_publish(topic_pub, "ALARM_DISABLED_RFID")
    

def handle_auth_failed():
    """Handle failed authentication"""
    print("Authentication failed")
    
    # Start asynchronous red LED blinking (3 times) to indicate authentication failure
    start_led_blink(LED_RED, 3)
    
    safe_mqtt_publish(topic_auth_request, "ACK_AUTH_FAILED")
    safe_mqtt_publish(topic_pub, "AUTH_FAILED")

def handle_button_pressed():
    """Handle rearm button press - same as reset alarm from app"""
    print("Rearm button pressed - resetting alarm")
    reset_alarm()

def disable_alarm():
    """Disable alarm via MQTT command"""
    global current_state
    
    print("Alarm disabled via MQTT command")
    current_state = SecurityState.ALARM_DISABLED
    
    send_uart_command(CMD_SET_BUZZER_OFF)
    set_led_color(LED_GREEN)
    
    safe_mqtt_publish(topic_pub, "ACK_CMD_DISABLE_ALARM")

def activate_alarm_manual():
    """Manually activate the alarm system (cannot be disabled by RFID)"""
    global current_state, manually_activated, alarm_disable_permanent, alarm_disable_end_time
    
    print("Alarm manually activated - RFID disable blocked")
    current_state = SecurityState.ALARM_ACTIVE
    manually_activated = True
    alarm_disable_permanent = False
    alarm_disable_end_time = 0
    
    send_uart_command(CMD_SET_BUZZER_ON)
    set_led_color(LED_RED)
    
    safe_mqtt_publish(topic_pub, "ALARM_TRIGGERED")
    safe_mqtt_publish(topic_pub, "ACK_CMD_ACTIVATE_ALARM")

def disable_alarm_permanent():
    """Permanently disable alarm until manually reactivated"""
    global current_state, manually_activated, alarm_disable_permanent, alarm_disable_end_time
    
    print("Alarm permanently disabled")
    current_state = SecurityState.ALARM_DISABLED
    manually_activated = False
    alarm_disable_permanent = True
    alarm_disable_end_time = 0
    
    send_uart_command(CMD_SET_BUZZER_OFF)
    set_led_color(LED_GREEN)
    
    safe_mqtt_publish(topic_pub, "ACK_CMD_DISABLE_ALARM")

def disable_alarm_timed(minutes):
    """Disable alarm for specified number of minutes"""
    global current_state, manually_activated, alarm_disable_permanent, alarm_disable_end_time
    
    print(f"Alarm disabled for {minutes} minutes")
    current_state = SecurityState.ALARM_DISABLED
    manually_activated = False
    alarm_disable_permanent = False
    alarm_disable_end_time = time.ticks_ms() + (minutes * 60 * 1000)
    
    send_uart_command(CMD_SET_BUZZER_OFF)
    set_led_color(LED_GREEN)
    
    safe_mqtt_publish(topic_pub, "ACK_CMD_DISABLE_ALARM")

def enable_alarm():
    """Re-enable alarm system"""
    global current_state, manually_activated, alarm_disable_permanent, alarm_disable_end_time
    
    print("Alarm re-enabled")
    current_state = SecurityState.READY
    manually_activated = False
    alarm_disable_permanent = False
    alarm_disable_end_time = 0
    
    send_uart_command(CMD_SET_BUZZER_OFF)
    set_led_color(LED_OFF)
    
    safe_mqtt_publish(topic_pub, "SECURITY_STATE:READY")

def reset_alarm():
    """Reset/rearm alarm - stop active alarm and return to ready state immediately"""
    global current_state, manually_activated, alarm_disable_permanent, alarm_disable_end_time
    
    print("Alarm reset and rearmed")
    current_state = SecurityState.READY
    manually_activated = False
    alarm_disable_permanent = False
    alarm_disable_end_time = 0
    
    send_uart_command(CMD_SET_BUZZER_OFF)
    set_led_color(LED_OFF)
    
    # Notify the client that alarm was reset
    safe_mqtt_publish(topic_pub, "ALARM_RESET")
    safe_mqtt_publish(topic_pub, "SECURITY_STATE:READY")

def prepare_rfid_write_mode(secret_key):
    """Prepare for RFID write mode (step 1 - store key but don't activate)"""
    global current_state
    
    print(f"Preparing RFID write mode with key: {secret_key}")
    current_state = SecurityState.RFID_WRITE_MODE
    
    # Send prepare command to Arduino - it will store the key but not activate write mode
    send_uart_command_with_data(CMD_RFID_WRITE_PREPARE, secret_key)
    
    # Indicate preparation is ready
    safe_mqtt_publish(topic_pub, f"STATUS_RFID_WRITE_PREPARED:{secret_key}")

def confirm_rfid_write_mode():
    """Confirm and activate RFID write mode (step 2 - actually enter write mode)"""
    if current_state == SecurityState.RFID_WRITE_MODE:
        print("Confirming RFID write mode - activating write mode")
        
        # Send confirm command to Arduino - it will now activate write mode
        send_uart_command(CMD_RFID_WRITE_CONFIRM)
        
        # Indicate write mode is now active
        safe_mqtt_publish(topic_pub, "STATUS_RFID_WRITE_ACTIVE")
    else:
        print("Error: Cannot confirm RFID write - not in prepared state")
        safe_mqtt_publish(topic_pub, "ERROR_RFID_WRITE_NOT_PREPARED")

def enter_rfid_write_mode(secret_key):
    """Legacy function - now calls the two-step process"""
    prepare_rfid_write_mode(secret_key)

def initialize_rfid_write():
    """Initialize RFID write operation"""
    if current_state == SecurityState.RFID_WRITE_MODE:
        print("RFID write initialized")
        safe_mqtt_publish(topic_pub, "ACK_CMD_RFID_WRITE_INITALIZE")

def abort_operation():
    """Abort current operation"""
    global current_state
    
    print("Aborting current operation")
    current_state = SecurityState.READY
    
    send_uart_command(CMD_RFID_NORMAL_MODE)
    set_led_color(LED_OFF)
    
    safe_mqtt_publish(topic_pub, "ACK_CMD_ABORT")

def check_alarm_timeout():
    """Check if alarm disable period has expired"""
    global current_state, alarm_disable_permanent, alarm_disable_end_time
    
    if current_state == SecurityState.ALARM_DISABLED:
        # Don't re-enable if permanently disabled
        if alarm_disable_permanent:
            return
            
        # Check if timed disable has expired
        if alarm_disable_end_time > 0 and time.ticks_ms() >= alarm_disable_end_time:
            print("Alarm re-enabled after timed disable")
            enable_alarm()
        # Legacy timeout check (for old RFID disable behavior)
        elif alarm_disable_end_time == 0 and time.ticks_diff(time.ticks_ms(), alarm_disabled_time) > alarm_disable_duration:
            print("Alarm re-enabled after legacy timeout")
            current_state = SecurityState.READY
            set_led_color(LED_OFF)
            safe_mqtt_publish(topic_pub, "ALARM_REARMED")

def handle_arduino_heartbeat():
    """Handle heartbeat message from Arduino"""
    global last_arduino_heartbeat, arduino_connected
    
    last_arduino_heartbeat = time.ticks_ms()
    
    # Always send Arduino heartbeat to client
    safe_mqtt_publish(topic_pub, "ARDUINO_HEARTBEAT")
    print("Arduino heartbeat received and relayed to client")
    
    if not arduino_connected:
        arduino_connected = True
        print("Arduino connection restored")
        safe_mqtt_publish(topic_pub, "ARDUINO_CONNECTED")

def handle_arduino_status_update():
    """Handle status update message from Arduino"""
    # This function is now handled in the UART message parsing section
    # where messages with data are processed
    print("Status update handled in main UART parser")

def check_arduino_connection():
    """Check if Arduino is still connected based on heartbeat"""
    global arduino_connected
    
    if arduino_connected:
        time_since_heartbeat = time.ticks_diff(time.ticks_ms(), last_arduino_heartbeat)
        if time_since_heartbeat > arduino_timeout:
            arduino_connected = False
            print("Arduino connection lost - no heartbeat")
            safe_mqtt_publish(topic_pub, "ARDUINO_DISCONNECTED")

def send_pico_heartbeat():
    """Send periodic heartbeat from Pico to indicate it's alive"""
    safe_mqtt_publish(topic_pub, "PICO_HEARTBEAT")
    print("Pico heartbeat sent")

def test_mqtt_publishing():
    """Test function to manually verify MQTT publishing works"""
    print("Testing MQTT publishing...")
    messages = [
        "TEST_MESSAGE_1",
        "PICO_HEARTBEAT", 
        "MOTION_DETECTED",
        "MOTION_STOPPED",
        "ARDUINO_CONNECTED"
    ]
    
    for msg in messages:
        print(f"Sending test message: {msg}")
        if safe_mqtt_publish(topic_pub, msg):
            print(f"✓ {msg} sent successfully")
        else:
            print(f"✗ Failed to send {msg}")
        time.sleep(1)  # Wait 1 second between messages

def process_arduino_message(msg_code):
    """Process message codes from Arduino"""
    
    if msg_code == MSG_STATUS_READY:
        print("Arduino ready")
        safe_mqtt_publish(topic_pub, "STATUS_READY")
        
    elif msg_code == MSG_MOTION_DETECTED:
        handle_motion_detected()
        
    elif msg_code == MSG_MOTION_STOPPED:
        handle_motion_stopped()
        
    elif msg_code == MSG_RFID_DETECTED:
        print("RFID card detected")
        
    elif msg_code == MSG_BUTTON_PRESSED:
        handle_button_pressed()
        
    elif msg_code == MSG_RFID_WRITE_SUCCESS:
        print("RFID write successful")
        safe_mqtt_publish(topic_pub, "STATUS_RFID_WRITE_SUCCESS")
        
    elif msg_code == MSG_RFID_WRITE_FAILED:
        print("RFID write failed")
        safe_mqtt_publish(topic_pub, "STATUS_RFID_WRITE_FAILED")
        
    elif msg_code == MSG_RFID_WRITE_COMPLETED:
        print("RFID write completed")
        safe_mqtt_publish(topic_pub, "STATUS_RFID_WRITE_COMPLETED")
        current_state = SecurityState.READY
        set_led_color(LED_OFF)
        
    elif msg_code == MSG_HEARTBEAT:
        handle_arduino_heartbeat()
        
    elif msg_code == MSG_STATUS_UPDATE:
        handle_arduino_status_update()
        
    else:
        print(f"Unknown message code from Arduino: {msg_code}")

# Buffer for UART data
uart_buffer = b''

# Connection monitoring
last_mqtt_check = time.ticks_ms()
mqtt_check_interval = 30000  # Check MQTT connection every 30 seconds

print("Security system initialized")

# Send initial status to indicate Pico is ready
safe_mqtt_publish(topic_pub, "PICO_READY")

# Main loop
while True:
    current_time = time.ticks_ms()
    
    # Send Pico heartbeat every 15 seconds
    if time.ticks_diff(current_time, last_pico_heartbeat) > pico_heartbeat_interval:
        send_pico_heartbeat()
        last_pico_heartbeat = current_time
    
    # Periodic MQTT connection check
    if client and time.ticks_diff(current_time, last_mqtt_check) > mqtt_check_interval:
        try:
            client.ping()
            last_mqtt_check = current_time
        except Exception as e:
            print(f"MQTT ping failed: {e} - attempting reconnect")
            client = connect_mqtt()
            last_mqtt_check = current_time
    
    # Check MQTT messages
    safe_mqtt_check()
    
    # Check timeouts
    check_motion_timeout()
    check_alarm_timeout()
    check_arduino_connection()
    
    # Update LED blinking (non-blocking)
    update_led_blink()
    
    # Process UART data from Arduino
    while uart.any():
        c = uart.read(1)
        if c:
            byte_val = c[0]
            print(f"Received UART data: {byte_val} (0x{byte_val:02x})")
            
            if c == b'\n':
                # End of multi-byte message with data
                if uart_buffer and b':' in uart_buffer:
                    # Message with data (format: "code:data")
                    print("Received UART message with data:", uart_buffer)
                    try:
                        parts = uart_buffer.split(b':', 1)
                        if len(parts) == 2 and len(parts[0]) >= 1:
                            msg_code = parts[0][0]  # First byte is the message code
                            data = parts[1].decode('utf-8').strip()
                            
                            print(f"Parsed: msg_code={msg_code}, data='{data}'")
                            
                            if msg_code == MSG_RFID_READ_SUCCESS:
                                handle_rfid_detected(data)
                            elif msg_code == MSG_RFID_READ_FAILED:
                                print("RFID read failed")
                                safe_mqtt_publish(topic_pub, "RFID_READ_FAILED")
                            elif msg_code == MSG_STATUS_UPDATE:
                                print(f"Arduino status update: {data}")
                                safe_mqtt_publish(topic_pub, f"ARDUINO_STATUS:{data}")
                            elif msg_code == MSG_HEARTBEAT:
                                handle_arduino_heartbeat()
                            else:
                                print(f"Unknown message code with data: {msg_code}")
                        else:
                            print(f"Invalid message format: {uart_buffer}")
                    except Exception as e:
                        print(f"Error parsing message: {e}, buffer: {uart_buffer}")
                
                uart_buffer = b''
                
            else:
                uart_buffer += c
                
                # Check if this is a potential single-byte message
                if len(uart_buffer) == 1 and 1 <= byte_val <= 28:
                    # This could be a single-byte message or start of multi-byte message
                    # Wait a short time to see if ':' follows
                    start_time = time.ticks_ms()
                    timeout = 10  # 10ms timeout
                    
                    while time.ticks_diff(time.ticks_ms(), start_time) < timeout:
                        if uart.any():
                            # More data is available, peek at it
                            next_byte = uart.read(1)
                            if next_byte:
                                if next_byte == b':':
                                    # This is a multi-byte message, add ':' to buffer and continue
                                    uart_buffer += next_byte
                                    print(f"Multi-byte message detected: {byte_val}:")
                                    break
                                else:
                                    # Put the byte back in buffer and process as single-byte
                                    uart_buffer += next_byte
                                    print(f"Received single-byte message: {byte_val}")
                                    process_arduino_message(byte_val)
                                    uart_buffer = next_byte  # Keep the next byte for next iteration
                                    break
                        time.sleep(0.001)
                    else:
                        # Timeout - no more data, this is a single-byte message
                        print(f"Received single-byte message: {byte_val}")
                        process_arduino_message(byte_val)
                        uart_buffer = b''
    
    time.sleep(0.001)