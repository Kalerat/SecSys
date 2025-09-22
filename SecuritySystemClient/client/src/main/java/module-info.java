module org.kgames.client {
    requires transitive javafx.graphics;
    requires transitive javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.media;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires eu.hansolo.tilesfx;
    requires org.eclipse.paho.client.mqttv3;
    requires org.bytedeco.opencv;
    requires org.bytedeco.javacv;
    requires org.bytedeco.ffmpeg;
    requires javafx.swing;
    requires java.sql;
    requires java.desktop;

    exports org.kgames.client;
    exports org.kgames.client.config;
    exports org.kgames.client.controller;
    exports org.kgames.client.model;
    exports org.kgames.client.service;
    opens org.kgames.client;
    opens org.kgames.client.controller;
    opens org.kgames.client.model;
}