import cloud.common.FileDir;
import cloud.common.ProtocolDict;
import cloud.common.User;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;

import java.net.URL;
import java.util.HashMap;
import java.util.ResourceBundle;

public class MainController implements Initializable {
    private CloudClient cloudClient;
    private User userInfo;
    private TreeDirectory treeDirectory;
    private String currentPath;

    @FXML
    public Label lName;
    @FXML
    public Label lFreeSize;
    @FXML
    public ProgressBar freeSize;
    @FXML
    public TableView<TreeNode> filesTable;
    @FXML
    public TextField pathField;

    public void setCloudClient(CloudClient cloudClient) {
        this.cloudClient = cloudClient;
    }

    public void setUserInfo(User userInfo) {
        this.userInfo = userInfo;
    }

    public void init() {
        currentPath = "";
        lName.setText(userInfo.getName());
        if (userInfo.getMemSize() != 0) {
            freeSize.setProgress(1 - (float) userInfo.getFreeMemSize() / (float) userInfo.getMemSize());
        }
        lFreeSize.setText(String.format("Свободно %.2f мб из %.2f мб",
                userInfo.getFreeMemSize() / (1024.0f * 1024.0f), userInfo.getMemSize() / (1024.0f * 1024.0f)));
        cloudClient.getDirectoryStructure((status, tree) -> {
            if (status == ProtocolDict.STATUS_ERROR) {
                //TODO Отобразить сообщение об ошибке
            } else if (status == ProtocolDict.STATUS_OK) {
                treeDirectory = tree;
                updateTable();
            }
        });
    }

    public void updateTable() {
        pathField.setText(currentPath);
        HashMap<String, TreeNode> fMap = treeDirectory.get(currentPath).getSetChild();
        filesTable.getItems().clear();
        filesTable.getItems().addAll(fMap.values());
        filesTable.sort();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        final Image fileImg = new Image("/images/file.png");
        final Image dirImg = new Image("/images/dir.png");

        TableColumn<TreeNode, ImageView> fileTypeColumn = new TableColumn<>();
        fileTypeColumn.setCellValueFactory(param -> {
            short type = param.getValue().getFileDir().getType();
            if (type == ProtocolDict.TYPE_DIRECTORY) {
                return new SimpleObjectProperty<>(new ImageView(dirImg));
            } else {
                return new SimpleObjectProperty<>(new ImageView(fileImg));
            }
        });
        fileTypeColumn.setPrefWidth(40);
        //fileTypeColumn.setStyle("-fx-alignment: CENTER-CENTER;");

        TableColumn<TreeNode, String> filenameColumn = new TableColumn<>("Имя");
        filenameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getFileDir().getName()));
        filenameColumn.setPrefWidth(240);
        filenameColumn.setStyle("-fx-alignment: CENTER-LEFT;");

        TableColumn<TreeNode, Long> fileSizeColumn = new TableColumn<>("Размер");
        fileSizeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getFileDir().getSize()));
        fileSizeColumn.setCellFactory(column -> {
            return new TableCell<TreeNode, Long>() {
                @Override
                protected void updateItem(Long item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setText(null);
                        setStyle("");
                    } else {
                        String text = String.format("%,d bytes", item);
                        if (item == -1L) {
                            text = "";
                        }
                        setText(text);
                    }
                }
            };
        });
        fileSizeColumn.setPrefWidth(120);
        fileSizeColumn.setStyle("-fx-alignment: CENTER-LEFT;");


        filesTable.getColumns().addAll(fileTypeColumn, filenameColumn, fileSizeColumn);
        filesTable.getSortOrder().add(fileSizeColumn);

        filesTable.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    FileDir fClick = filesTable.getSelectionModel().getSelectedItem().getFileDir();
                    if (fClick.getType() == ProtocolDict.TYPE_DIRECTORY) {
                        currentPath = currentPath + fClick.getName() + "/";
                        updateTable();
                    }
                }
            }
        });
    }

    public void btnPathUpAction(ActionEvent actionEvent) {
        if (currentPath.isEmpty()) {
            return;
        }
        String s = currentPath.substring(0, currentPath.length() - 1);
        if (s.contains("/")) {
            currentPath = s.substring(0, s.lastIndexOf("/") + 1);
        } else {
            currentPath = "";
        }
        updateTable();
    }
}
