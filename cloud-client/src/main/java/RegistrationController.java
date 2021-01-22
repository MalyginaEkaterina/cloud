import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
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
            cloudClient.register(name.getText(), email.getText(), login.getText(), pass.getText());
            incorrectData.setVisible(false);
            Stage stage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
            stage.setTitle("Auth");
            stage.setScene(authScene);
        }
    }
}
