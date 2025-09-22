package org.kgames.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.kgames.client.controller.MainController;

import java.io.IOException;

public class MainFrame extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainFrame.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1280, 720);
        
        scene.getStylesheets().add(MainFrame.class.getResource("dark-theme.css").toExternalForm());
        
        stage.setTitle("Security System GUI");
        stage.setScene(scene);
        stage.getIcons().add(new javafx.scene.image.Image(MainFrame.class.getResourceAsStream("/org/kgames/client/app_icon.png")));
        stage.setMinWidth(1000);
        stage.setMinHeight(700);

        MainController controller = fxmlLoader.getController();
        stage.setOnCloseRequest(_ -> {
            controller.shutdown();
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}