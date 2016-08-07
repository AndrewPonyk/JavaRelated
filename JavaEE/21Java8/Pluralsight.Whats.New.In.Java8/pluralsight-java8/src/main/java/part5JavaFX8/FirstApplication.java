package part5JavaFX8;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class FirstApplication extends Application{
    public static void main(String[] args) {
        System.out.println("FirstApplication");
        launch();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Label label = new Label("Hello world");
        label.setFont(Font.font(12));

        primaryStage.setScene(new Scene(label));
        primaryStage.setTitle("Hello");
        primaryStage.show();
    }
}
