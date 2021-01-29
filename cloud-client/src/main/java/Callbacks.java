import cloud.common.User;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

class Callbacks {
    private Consumer<Short> onRegStatusCallback;
    private BiConsumer<Short, User> onAuthStatusCallback;
    private BiConsumer<Short, TreeDirectory> onDirStructureCallback;

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
}
