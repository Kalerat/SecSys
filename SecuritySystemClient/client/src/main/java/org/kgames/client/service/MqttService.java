package org.kgames.client.service;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import java.io.File;

/**
 * Service class for MQTT communication.
 * Handles connection, subscription, and publishing to the MQTT broker.
 */
public class MqttService {
    private static final String CLIENT_ID = "SecuritySystemClient";

    private MqttClient client;
    private Thread mqttThread;
    private volatile boolean running = false;
    private MqttMessageListener messageListener;

    /**
     * Interface for handling received MQTT messages.
     */
    public interface MqttMessageListener {
        void onMessageReceived(String topic, String message);

        void onConnectionLost(String cause);

        void onConnectionSuccess();

        void onConnectionFailed(String error);
    }

    /**
     * Sets the message listener for MQTT events.
     * 
     * @param listener The listener to handle MQTT events
     */
    public void setMessageListener(MqttMessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * Connects to the MQTT broker and subscribes to a topic.
     * 
     * @param brokerUrl The MQTT broker URL
     * @param topic     The topic to subscribe to
     */
    public void connect(String brokerUrl, String topic) {
        if (running)
            return;

        // Create MQTT data directory
        File mqttDataDir = new File(System.getProperty("user.home"), ".securitysystem/mqtt");
        if (!mqttDataDir.exists()) {
            mqttDataDir.mkdirs();
        }
        // Use file persistence in dedicated directory
        MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence(mqttDataDir.getAbsolutePath());

        running = true;
        mqttThread = new Thread(() -> {
            try {
                if (client == null || !client.isConnected()) {
                    client = new MqttClient(brokerUrl, CLIENT_ID, persistence);
                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setAutomaticReconnect(true);
                    options.setCleanSession(true);

                    client.setCallback(new MqttCallback() {
                        @Override
                        public void connectionLost(Throwable cause) {
                            if (messageListener != null) {
                                messageListener.onConnectionLost(cause.getMessage());
                            }
                        }

                        @Override
                        public void messageArrived(String topic, MqttMessage message) {
                            if (messageListener != null) {
                                messageListener.onMessageReceived(topic, new String(message.getPayload()));
                            }
                        }

                        @Override
                        public void deliveryComplete(IMqttDeliveryToken token) {
                            // Not needed for now
                        }
                    });

                    client.connect(options);
                    client.subscribe(topic);

                    if (messageListener != null) {
                        messageListener.onConnectionSuccess();
                    }
                }

                // Keep thread alive to receive messages
                while (running && client.isConnected()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } catch (MqttException e) {
                if (messageListener != null) {
                    messageListener.onConnectionFailed(e.getMessage());
                }
            }
        });
        mqttThread.setDaemon(true);
        mqttThread.start();
    }

    /**
     * Disconnects from the MQTT broker.
     */
    public void disconnect() {
        running = false;
        if (mqttThread != null && mqttThread.isAlive()) {
            mqttThread.interrupt();
            try {
                mqttThread.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
            } catch (MqttException ignored) {
            }
        }
    }

    /**
     * Publishes a message to a specific topic.
     * 
     * @param topic   The topic to publish to
     * @param message The message to publish
     * @throws MqttException if publishing fails
     */
    public void publishMessage(String topic, String message) throws MqttException {
        if (client == null || !client.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        client.publish(topic, new MqttMessage(message.getBytes()));
    }

    /**
     * Checks if the MQTT client is connected.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return client != null && client.isConnected();
    }
}
