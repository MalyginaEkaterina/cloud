package cloud.common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class FileLoaded {
    FileDir fileDir;
    File file;
    FileOutputStream outputStream;
    Boolean isFailed;

    public FileLoaded(FileDir fileDir, File file) throws FileNotFoundException {
        this.fileDir = fileDir;
        this.file = file;
        outputStream = new FileOutputStream(file, true);
        isFailed = false;
    }

    public FileOutputStream getOutputStream() {
        return outputStream;
    }

    public FileDir getFileDir() {
        return fileDir;
    }

    public Boolean getFailed() {
        return isFailed;
    }

    public void setFailed(Boolean failed) {
        isFailed = failed;
    }

    public File getFile() {
        return file;
    }
}
