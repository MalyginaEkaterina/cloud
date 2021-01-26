import cloud.common.ProtocolDict;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class AuthController {
    private CloudClient cloudClient;

    private Parent registrationRoot;
    private Parent mainRoot;
    private MainController mainController;

    @FXML
    public TextField login;
    @FXML
    public TextField pass;
    @FXML
    public Label authFail;

    public void setCloudClient(CloudClient cloudClient) {
        this.cloudClient = cloudClient;
    }

    public void setRegistrationRoot(Parent registrationRoot) {
        this.registrationRoot = registrationRoot;
    }

    public void setMainRoot(Parent mainRoot) {
        this.mainRoot = mainRoot;
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void regBtnAction(ActionEvent actionEvent) throws IOException {
        login.clear();
        pass.clear();
        Stage stage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
        stage.setTitle("Registration");
        stage.setScene(new Scene(registrationRoot, 400, 450));
    }

    public void enterBtnAction(ActionEvent actionEvent) {
        if (login.getText().isEmpty() || pass.getText().isEmpty()) {
            authFail.setText("Введите логин и пароль");
            authFail.setVisible(true);
        } else {
            cloudClient.authorize(login.getText(), pass.getText(), (status, user) -> {
                if (status == ProtocolDict.STATUS_LOGIN_FAIL) {
                    Platform.runLater(() -> {
                        login.clear();
                        pass.clear();
                        authFail.setText("Неверный логин/пароль");
                        authFail.setVisible(true);
                    });
                } else if (status == ProtocolDict.STATUS_ERROR) {
                    Platform.runLater(() -> {
                        authFail.setText("Ошибка, попробуйте позднее");
                        authFail.setVisible(true);
                    });
                } else if (status == ProtocolDict.STATUS_OK) {
                    mainController.setUserInfo(user);
                    mainController.init();
                    Platform.runLater(() -> {
                        authFail.setVisible(false);
                        Stage stage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
                        stage.setTitle("Cloud storage");
                        stage.setScene(new Scene(mainRoot, 800, 600));
                    });
                }
            });
        }
    }
}
