import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        //загружаем окно авторизации
        FXMLLoader authLoader = new FXMLLoader(getClass().getResource("/auth.fxml"));
        Parent authRoot = authLoader.load();
        AuthController authController = authLoader.getController();
        //загружаем окно регистрации
        FXMLLoader registrationLoader = new FXMLLoader(getClass().getResource("/registration.fxml"));
        Parent registrationRoot = registrationLoader.load();
        RegistrationController registrationController = registrationLoader.getController();
        //загружаем основное рабочее окно
        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/main.fxml"));
        Parent mainRoot = mainLoader.load();
        MainController mainController = mainLoader.getController();

        CloudClient cloudClient = new CloudClient();
        authController.setCloudClient(cloudClient);
        authController.setRegistrationRoot(registrationRoot);
        authController.setMainRoot(mainRoot);

        Scene authScene = new Scene(authRoot, 400, 400);

        registrationController.setCloudClient(cloudClient);
        registrationController.setAuthScene(authScene);
        //отображаем окно авторизации
        primaryStage.setTitle("Auth");
        primaryStage.setScene(authScene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
