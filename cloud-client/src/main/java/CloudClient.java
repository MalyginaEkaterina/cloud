import cloud.common.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CloudClient {
    private static final Logger LOG = LoggerFactory.getLogger(CloudClient.class);

    private SocketChannel channel;
    private Callbacks callbacks;

    private static final String HOST = "localhost";
    private static final int PORT = 8189;

    private HashMap<Long, FileLoaded> filesLoaded = new HashMap<>();

    // CloudMsgDecoder ожидает, пока придет bytebuf, содержащий все сообщение,
    // считывает первые 4 байта из него(длина сообщения) и передает остальной bytebuf хендлеру
    public CloudClient() {
        callbacks = new Callbacks();

        Thread t = new Thread(() -> {
            NioEventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                Bootstrap b = new Bootstrap();
                b.group(workerGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) throws Exception {
                                channel = socketChannel;
                                socketChannel.pipeline().addLast(new CloudMsgDecoder(), new ClientHandler(callbacks));
                            }
                        });
                ChannelFuture future = b.connect(HOST, PORT).sync();
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                LOG.error("e = ", e);
            } finally {
                workerGroup.shutdownGracefully();
            }
        });
        t.start();
        LOG.info("Client started");
    }

    public void close() {
        channel.close();
    }
    //сообщение отправляется в формате:
    // [4 байта - длина всего сообщения][2 байта - тип сообщения]
    // [4б - длина логина][логин][4б - длина пароля][пароль]
    public void authorize(String login, String password, BiConsumer<Short, User> callback) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.AUTHORIZATION);
        Protocol.putString(msg, login);
        Protocol.putString(msg, password);
        callbacks.setOnAuthStatusCallback(callback);
        writeMsg(msg);
        LOG.info("Sent authorize for {}", login);
    }

    public void register(User user, Consumer<Short> callback) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.REGISTRATION);
        Protocol.putString(msg, user.getName());
        Protocol.putString(msg, user.getEmail());
        Protocol.putString(msg, user.getLogin());
        Protocol.putString(msg, user.getPass());
        callbacks.setOnRegStatusCallback(callback);
        writeMsg(msg);
        LOG.info("Sent register for {}", user.getLogin());
    }

    //запрос структуры папок с сервера.
    //В ответ от сервера приходит список объектов FileDir, который собирается из БД из всех файлов клиента и всех пустых папок клиента.
    //По этому списку собирается дерево для отображения в TableView.
    public void getDirectoryStructure(BiConsumer<Short, TreeDirectory> callback) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.GET_DIR_STRUCTURE);
        callbacks.setOnDirStructureCallback(callback);
        writeMsg(msg);
        LOG.info("Sent request for directory structure");
    }

    //создание новой папки
    public void createNewDir(FileDir d, BiConsumer<Short, FileDir> callback) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.CREATE_NEW_DIRECTORY);
        Protocol.putFileDir(msg, d);
        callbacks.setOnCreateNewDirCallback(callback);
        writeMsg(msg);
        LOG.info("Sent request for creating new folder {}", d.getName());
    }

    //Загрузка файла на сервер состоит из трех команд:
    //начало загрузки: на сервер передается информация о файле, в ответе от сервера возвращается id файла(из БД)
    //загрузка файла частями: команда+id файла+байты
    //сообщение об окончании загрузки: команда+id файла
    public void startUploadFile(File file, FileDir newFile, BiConsumer<Short, FileDir> callback) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.START_UPLOAD_FILE);
        Protocol.putFileDir(msg, newFile);
        callbacks.setOnUploadFileCallback(callback);
        callbacks.setOnStartUploadFileCallback((status, f) -> {
            if (status == ProtocolDict.STATUS_ERROR || status == ProtocolDict.STATUS_NOT_ENOUGH_MEM) {
                callbacks.getOnUploadFileCallback().accept(status, f);
            } else if (status == ProtocolDict.STATUS_OK) {
                try {
                    uploadFile(file, f);
                } catch (RuntimeException e) {
                    callbacks.getOnUploadFileCallback().accept(ProtocolDict.STATUS_ERROR, f);
                    LOG.error("e = ", e);
                }
            }
        });
        writeMsg(msg);
        LOG.info("Sent request for file upload start {}", newFile.getName());
    }

    public void uploadFile(File file, FileDir f) {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            byte[] b = new byte[10 * 1024];
            int i = 0;
            while ((i = fileInputStream.read(b)) >= 0) {
                ByteBuf msg = Unpooled.buffer();
                msg.writeShort(ProtocolDict.UPLOAD_FILE);
                msg.writeLong(f.getId());
                msg.writeBytes(b, 0, i);
                writeMsg(msg);
            }
            endUploadFile(f.getId());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void endUploadFile(long id) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.END_UPLOAD_FILE);
        msg.writeLong(id);
        callbacks.setOnEndUploadFileCallback((status, f) -> {
            callbacks.getOnUploadFileCallback().accept(status, f);
        });
        writeMsg(msg);
        LOG.info("Sent request for file upload end {}", id);
    }

    //запрос на сервер: команда+id файла
    //Скачиваемые файлы хранятся в мапе filesLoaded с ключом = id файла.
    //С сервера файл приходит частями: id файла+байты.
    //После отправки файла сервер отправляет сообщение об окончании передачи.
    public void downloadFile(File file, FileDir f, BiConsumer<Short, String> callback) throws FileNotFoundException {
        FileLoaded fileLoaded = new FileLoaded(f, file);
        filesLoaded.put(f.getId(), fileLoaded);
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.DOWNLOAD);
        msg.writeLong(f.getId());
        callbacks.setOnDownloadStatusCallback(callback);
        callbacks.setOnEndDownloadStatusCallback((status, fid) -> {
            FileLoaded fl = filesLoaded.remove(fid);
            if (fl == null) {
                return;
            }
            try {
                fl.getOutputStream().close();
                if (status == ProtocolDict.STATUS_ERROR) {
                    fl.getFile().delete();
                }
                callbacks.getOnDownloadStatusCallback().accept(status, fl.getFileDir().getName());
            } catch (IOException e) {
                LOG.error("e = ", e);
                fl.getFile().delete();
                callbacks.getOnDownloadStatusCallback().accept(ProtocolDict.STATUS_ERROR, fl.getFileDir().getName());
            }
        });
        callbacks.setOnProcDownloadStatusCallback((fid, bytes) -> {
            FileLoaded fl = filesLoaded.get(fid);
            if (fl == null) {
                return;
            }
            try {
                fl.getOutputStream().write(bytes);
            } catch (IOException e) {
                LOG.error("e = ", e);
                filesLoaded.remove(fid);
                fl.getFile().delete();
                callbacks.getOnDownloadStatusCallback().accept(ProtocolDict.STATUS_ERROR, fl.getFileDir().getName());
            }
        });
        writeMsg(msg);
        LOG.info("Sent request for file download {}", f.getId());
    }

    //переименование файла или папки.
    public void rename(FileDir fileDir, String newName, Consumer<Short> callback) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.RENAME);
        Protocol.putString(msg, newName);
        Protocol.putFileDir(msg, fileDir);
        callbacks.setOnRenameStatusCallback(callback);
        writeMsg(msg);
        LOG.info("Sent rename request for {}, new_name={}",fileDir.getName(),newName);
    }

    //Удаление файла или целиком папки.
    // Если после удаления из текущей папки она станет пустой, то это необходимо обновить в БД,
    // для чего передается параметр emptyFlag.
    // После удаления в интерфейсе обновляется значение свободного места(второй параметр в колбэке).
    public void delete(FileDir fileDir, int emptyFlag, BiConsumer<Short, Long> callback) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.DELETE);
        msg.writeShort(emptyFlag);
        Protocol.putFileDir(msg, fileDir);
        callbacks.setOnDeleteStatusCallback(callback);
        writeMsg(msg);
        LOG.info("Sent delete request for {}",fileDir.getName());
    }

    public void writeMsg(ByteBuf msg) {
        // добавляем длину всего сообщения перед сообщением(4 байта)
        ByteBuf msgLength = Unpooled.buffer(Integer.BYTES);
        msgLength.writeInt(msg.writerIndex());
        channel.write(msgLength);
        channel.writeAndFlush(msg);
    }
}
