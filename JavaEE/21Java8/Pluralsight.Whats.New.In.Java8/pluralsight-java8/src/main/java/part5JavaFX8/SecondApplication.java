package part5JavaFX8;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class SecondApplication extends Application{
    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        File fxml = new File("/home/andrew/git/JavaRelated/JavaEE/Java8/Pluralsight.Whats.New.In.Java8/" +
                "pluralsight-java8/src/main/java/part5JavaFX8/ihn.fxml");
        URL url = fxml.toURI().toURL();
        try{
            FXMLLoader loader = new FXMLLoader(url); // instead of url can be smth like this getClass.getResource("ihn.fxml")
            Parent root = loader.load();
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}
