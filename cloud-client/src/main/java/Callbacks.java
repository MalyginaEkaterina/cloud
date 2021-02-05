import cloud.common.FileDir;
import cloud.common.User;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

class Callbacks {
    private Consumer<Short> onRegStatusCallback;
    private BiConsumer<Short, User> onAuthStatusCallback;
    private BiConsumer<Short, TreeDirectory> onDirStructureCallback;
    private BiConsumer<Short, FileDir> onCreateNewDirCallback;
    private BiConsumer<Short, FileDir> onUploadFileCallback;
    private BiConsumer<Short, FileDir> onStartUploadFileCallback;
    private BiConsumer<Short, FileDir> onEndUploadFileCallback;
    private Consumer<Short> onRenameStatusCallback;
    private BiConsumer<Short, Long> onDeleteStatusCallback;
    private BiConsumer<Short, String> onDownloadStatusCallback;
    private BiConsumer<Long, byte[]> onProcDownloadStatusCallback;
    private BiConsumer<Short, Long> onEndDownloadStatusCallback;

    Consumer<Short> getOnRegStatusCallback() {
        return onRegStatusCallback;
    }

    void setOnRegStatusCallback(Consumer<Short> onRegStatusCallback) {
        this.onRegStatusCallback = onRegStatusCallback;
    }

    BiConsumer<Short, User> getOnAuthStatusCallback() {
        return onAuthStatusCallback;
    }

    void setOnAuthStatusCallback(BiConsumer<Short, User> onAuthStatusCallback) {
        this.onAuthStatusCallback = onAuthStatusCallback;
    }

    public BiConsumer<Short, TreeDirectory> getOnDirStructureCallback() {
        return onDirStructureCallback;
    }

    public void setOnDirStructureCallback(BiConsumer<Short, TreeDirectory> onDirStructureCallback) {
        this.onDirStructureCallback = onDirStructureCallback;
    }

    public BiConsumer<Short, FileDir> getOnCreateNewDirCallback() {
        return onCreateNewDirCallback;
    }

    public void setOnCreateNewDirCallback(BiConsumer<Short, FileDir> onCreateNewDirCallback) {
        this.onCreateNewDirCallback = onCreateNewDirCallback;
    }

    public BiConsumer<Short, FileDir> getOnUploadFileCallback() {
        return onUploadFileCallback;
    }

    public void setOnUploadFileCallback(BiConsumer<Short, FileDir> onUploadFileCallback) {
        this.onUploadFileCallback = onUploadFileCallback;
    }

    public BiConsumer<Short, FileDir> getOnStartUploadFileCallback() {
        return onStartUploadFileCallback;
    }

    public void setOnStartUploadFileCallback(BiConsumer<Short, FileDir> onStartUploadFileCallback) {
        this.onStartUploadFileCallback = onStartUploadFileCallback;
    }

    public BiConsumer<Short, FileDir> getOnEndUploadFileCallback() {
        return onEndUploadFileCallback;
    }

    public void setOnEndUploadFileCallback(BiConsumer<Short, FileDir> onEndUploadFileCallback) {
        this.onEndUploadFileCallback = onEndUploadFileCallback;
    }

    public Consumer<Short> getOnRenameStatusCallback() {
        return onRenameStatusCallback;
    }

    public void setOnRenameStatusCallback(Consumer<Short> onRenameStatusCallback) {
        this.onRenameStatusCallback = onRenameStatusCallback;
    }

    public BiConsumer<Short, Long> getOnDeleteStatusCallback() {
        return onDeleteStatusCallback;
    }

    public void setOnDeleteStatusCallback(BiConsumer<Short, Long> onDeleteStatusCallback) {
        this.onDeleteStatusCallback = onDeleteStatusCallback;
    }

    public BiConsumer<Short, String> getOnDownloadStatusCallback() {
        return onDownloadStatusCallback;
    }

    public void setOnDownloadStatusCallback(BiConsumer<Short, String> onDownloadStatusCallback) {
        this.onDownloadStatusCallback = onDownloadStatusCallback;
    }

    public BiConsumer<Long, byte[]> getOnProcDownloadStatusCallback() {
        return onProcDownloadStatusCallback;
    }

    public void setOnProcDownloadStatusCallback(BiConsumer<Long, byte[]> onProcDownloadStatusCallback) {
        this.onProcDownloadStatusCallback = onProcDownloadStatusCallback;
    }

    public BiConsumer<Short, Long> getOnEndDownloadStatusCallback() {
        return onEndDownloadStatusCallback;
    }

    public void setOnEndDownloadStatusCallback(BiConsumer<Short, Long> onEndDownloadStatusCallback) {
        this.onEndDownloadStatusCallback = onEndDownloadStatusCallback;
    }
}
