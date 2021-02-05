import cloud.common.FileDir;
import cloud.common.ProtocolDict;
import cloud.common.User;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.HashMap;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class MainController implements Initializable {
    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);
    private CloudClient cloudClient;
    private User userInfo;
    private TreeDirectory treeDirectory;
    private String currentPath;
    private static final String CLOUD_DOWNLOADS = "cloudDownloads";

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
        updateViewSize();
        getDirStruct();
    }

    public void getDirStruct() {
        cloudClient.getDirectoryStructure((status, tree) -> {
            Platform.runLater(() -> {
                if (status == ProtocolDict.STATUS_ERROR) {
                    showAlertError("Не удалось получить структуру папок");
                } else if (status == ProtocolDict.STATUS_OK) {
                    treeDirectory = tree;
                    updateTable();
                }
            });
        });
    }


    private void updateViewSize() {
        if (userInfo.getMemSize() != 0) {
            freeSize.setProgress(1 - (float) userInfo.getFreeMemSize() / (float) userInfo.getMemSize());
        }
        lFreeSize.setText(String.format("Свободно %.2f мб из %.2f мб",
                userInfo.getFreeMemSize() / (1024.0f * 1024.0f), userInfo.getMemSize() / (1024.0f * 1024.0f)));
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
        fileSizeColumn.setPrefWidth(140);
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

    public void btnAddDirectory(ActionEvent actionEvent) {
        TextInputDialog dialog = new TextInputDialog();

        dialog.setTitle("Create folder");
        dialog.setHeaderText("Введите название папки:");
        dialog.setContentText("Название:");

        Pattern p = Pattern.compile("[^/\\.]*");
        dialog.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (!p.matcher(newValue).matches()) dialog.getEditor().setText(oldValue);
        });

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String dirName = result.get();
            if (treeDirectory.get(currentPath).getSetChild().containsKey(dirName)) {
                showAlertError("папка с таким именем уже существует");
                return;
            }
            if (!dirName.isEmpty()) {
                String dirPath = currentPath + dirName;
                FileDir newDir = new FileDir(ProtocolDict.TYPE_DIRECTORY, -1L, -1L, dirPath);
                cloudClient.createNewDir(newDir, (status, d) -> {
                    Platform.runLater(() -> {
                        if (status == ProtocolDict.STATUS_ERROR) {
                            showAlertError("Не получилось создать папку");
                        } else if (status == ProtocolDict.STATUS_OK) {
                            treeDirectory.insert(d);
                            updateTable();
                        }
                    });
                });
            }
        }
    }

    public void btnUploadFile(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Upload file");
        File selectedFile = fileChooser.showOpenDialog(((Node) actionEvent.getSource()).getScene().getWindow());
        if (selectedFile != null) {
            if (treeDirectory.get(currentPath).getSetChild().containsKey(selectedFile.getName())) {
                showAlertError("файл с таким именем уже существует");
                return;
            }
            FileDir newFile = new FileDir(ProtocolDict.TYPE_FILE, 1L, selectedFile.length(), currentPath + selectedFile.getName());
            cloudClient.startUploadFile(selectedFile, newFile, (status, f) -> {
                Platform.runLater(() -> {
                    if (status == ProtocolDict.STATUS_ERROR) {
                        showAlertError("Не получилось загрузить файл " + selectedFile.getName());
                    } else if (status == ProtocolDict.STATUS_NOT_ENOUGH_MEM) {
                        showAlertError("Недостаточно места для загрузки файла " + selectedFile.getName());
                    } else if (status == ProtocolDict.STATUS_OK) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("File uploaded");
                        alert.setHeaderText(null);
                        alert.setContentText("Файл " + selectedFile.getName() + " успешно загружен");
                        alert.showAndWait();
                        userInfo.setFreeMemSize(userInfo.getFreeMemSize() - f.getSize());
                        updateViewSize();
                        treeDirectory.insert(f);
                        updateTable();
                    }
                });
            });
        }
    }

    public void btnDownloadFile(ActionEvent actionEvent) {
        if (!filesTable.isFocused()) {
            return;
        }
        if (filesTable.getSelectionModel().isEmpty()) {
            return;
        }
        FileDir selectedFile = filesTable.getSelectionModel().getSelectedItem().getFileDir();
        if (selectedFile.getType() == ProtocolDict.TYPE_DIRECTORY) {
            return;
        }
        File downloadsDir = new File(CLOUD_DOWNLOADS);
        downloadsDir.mkdirs();
        File file = new File(downloadsDir, selectedFile.getName());
        if (file.exists()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Download File");
            alert.setHeaderText("Файл " + selectedFile.getName() + " уже загружен. Перазаписать?");
            alert.setContentText(null);
            Optional<ButtonType> option = alert.showAndWait();
            if (option.get() == ButtonType.CANCEL) {
                return;
            } else if (option.get() == ButtonType.OK) {
                file.delete();
            }
        }
        LOG.info("download file " + selectedFile.getName());
        try {
            cloudClient.downloadFile(file, selectedFile, (status, fname) -> {
                Platform.runLater(() -> {
                    if (status == ProtocolDict.STATUS_OK) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("File downloaded");
                        alert.setHeaderText(null);
                        alert.setContentText("Скачивание файла " + fname + " завершено");
                        alert.showAndWait();
                    } else {
                        showAlertError("Не получилось скачать файл " + fname);
                    }
                });
            });
        } catch (FileNotFoundException e) {
            file.delete();
            showAlertError("Не получилось скачать файл " + selectedFile.getName());
            LOG.error("e = ", e);
        }
    }

    public void btnDelete(ActionEvent actionEvent) {
        if (!filesTable.isFocused()) {
            return;
        }
        if (filesTable.getSelectionModel().isEmpty()) {
            return;
        }
        FileDir selectedFileDir = filesTable.getSelectionModel().getSelectedItem().getFileDir();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete File/Directory");
        alert.setHeaderText("Вы уверены, что хотите удалить выбранный файл/директорию?");
        alert.setContentText(selectedFileDir.getName());
        Optional<ButtonType> option = alert.showAndWait();

        if (option.get() == ButtonType.OK) {
            LOG.info("delete file/directory {}", selectedFileDir.getName());
            TreeNode selectedNode = treeDirectory.get(selectedFileDir.getPathStr());
            int emptyFlag = (selectedNode.getParent().getSetChild().size() == 1) ? 1 : 0;
            cloudClient.delete(selectedFileDir, emptyFlag, (status, freeSize) -> {
                Platform.runLater(() -> {
                    if (status == ProtocolDict.STATUS_OK) {
                        treeDirectory.delete(selectedFileDir);
                        updateTable();
                        userInfo.setFreeMemSize(freeSize);
                        updateViewSize();
                    } else {
                        showAlertError("Не получилось удалить " + selectedFileDir.getName());
                    }
                });
            });
        } else if (option.get() == ButtonType.CANCEL) {
            LOG.info("delete file/directory {} cancelled", selectedFileDir.getName());
        }
    }

    public void showAlertError(String text) {
        Alert alertError = new Alert(Alert.AlertType.ERROR);
        alertError.setTitle("Error alert");
        alertError.setHeaderText(text);
        alertError.setContentText("Error");
        alertError.showAndWait();
    }

    public void btnRename(ActionEvent actionEvent) {
        if (!filesTable.isFocused()) {
            return;
        }
        if (filesTable.getSelectionModel().isEmpty()) {
            return;
        }
        FileDir selectedFileDir = filesTable.getSelectionModel().getSelectedItem().getFileDir();

        TextInputDialog dialog = new TextInputDialog(selectedFileDir.getName());

        dialog.setTitle("Rename");
        dialog.setHeaderText("Введите новое название:");
        dialog.setContentText("Название:");

        Pattern p = Pattern.compile("[^/]*");
        dialog.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (!p.matcher(newValue).matches()) dialog.getEditor().setText(oldValue);
        });

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String newDirName = result.get();
            if (treeDirectory.get(currentPath).getSetChild().containsKey(newDirName)) {
                showAlertError("файл/папка с таким именем уже существует");
                return;
            }
            if (!newDirName.isEmpty()) {
                LOG.info("rename file/directory {}, new name {}", selectedFileDir.getName(), newDirName);
                cloudClient.rename(selectedFileDir, newDirName, status -> {
                    Platform.runLater(() -> {
                        if (status == ProtocolDict.STATUS_OK) {
                            getDirStruct();
                        } else {
                            showAlertError("Не получилось переименовать файл/директорию");
                        }
                    });
                });
            }
        }
    }
}
