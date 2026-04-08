package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

// clasa principala care porneste aplicatia
public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // incarcare fxml
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        // setam rezolutie tip telefon (latime x inaltime)
        Scene scene = new Scene(fxmlLoader.load(), 400, 800);
        // aplicam fisierul de stil css
        scene.getStylesheets().add(getClass().getResource("/fxml/style.css").toExternalForm());

        stage.setTitle("Gym App");
        stage.setResizable(false); // nu lasam fereastra sa fie redimensionata
        stage.setScene(scene);
        stage.show();
    }

    // metoda main
    public static void main(String[] args) {
        launch();
    }
}
