import cloud.common.ProtocolDict;
import cloud.common.User;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RegistrationController {
    private CloudClient cloudClient;
    private Scene authScene;

    @FXML
    public TextField name;
    @FXML
    public TextField email;
    @FXML
    public TextField login;
    @FXML
    public PasswordField pass;
    @FXML
    public PasswordField pass2;
    @FXML
    public Label incorrectData;

    public void setCloudClient(CloudClient cloudClient) {
        this.cloudClient = cloudClient;
    }

    public void setAuthScene(Scene authScene) {
        this.authScene = authScene;
    }

    public void onBtnReg(ActionEvent actionEvent) {
        if (name.getText().isEmpty() || email.getText().isEmpty() || login.getText().isEmpty() || pass.getText().isEmpty()) {
            incorrectData.setText("Не все поля заполнены");
            incorrectData.setVisible(true);
        } else if (!pass.getText().equals(pass2.getText())) {
            incorrectData.setText("Некорректно введен пароль");
            incorrectData.setVisible(true);
        } else {
            cloudClient.register(new User(name.getText(), email.getText(), login.getText(), pass.getText()), status -> {
                Platform.runLater(() -> {
                    if (status == ProtocolDict.STATUS_LOGIN_USED) {
                        login.clear();
                        incorrectData.setText("Данный логин уже используется. Попробуйте другой");
                        incorrectData.setVisible(true);
                    } else if (status == ProtocolDict.STATUS_ERROR) {
                        login.clear();
                        incorrectData.setText("Ошибка. Попробуйте позже");
                        incorrectData.setVisible(true);
                    } else if (status == ProtocolDict.STATUS_OK) {
                        incorrectData.setVisible(false);
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Auth status");
                        alert.setHeaderText(null);
                        alert.setContentText("Регистрация прошла успешно");
                        alert.showAndWait();
                        Stage stage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
                        stage.setTitle("Auth");
                        stage.setScene(authScene);
                    }
                });
            });
        }
    }
}
