import cloud.common.User;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

public class MainController {
    private User userInfo;

    @FXML
    public Label lName;
    @FXML
    public Label lFreeSize;
    @FXML
    public ProgressBar freeSize;

    public void setUserInfo(User userInfo) {
        this.userInfo = userInfo;
    }

    public void init() {
        lName.setText(userInfo.getName());
        if (userInfo.getMemSize() != 0) {
            freeSize.setProgress(1 - (float) userInfo.getFreeMemSize() / (float) userInfo.getMemSize());
        }
        lFreeSize.setText(String.format("Свободно %.2f мб из %.2f мб", userInfo.getFreeMemSize()/(1024.0f*1024.0f), userInfo.getMemSize()/(1024.0f*1024.0f)));
    }
}
