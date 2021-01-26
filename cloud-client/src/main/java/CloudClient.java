import cloud.common.CloudMsgDecoder;
import cloud.common.Protocol;
import cloud.common.ProtocolDict;
import cloud.common.User;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CloudClient {
    private SocketChannel channel;
    private Callbacks callbacks;

    private static final String HOST = "localhost";
    private static final int PORT = 8189;
    private boolean isAuthorized;

    //CloudMsgDecoder ожидает, пока придет bytebuf, содержащий все сообщение,
    // считывает первые 4 байта из него(длина сообщения) и передает остальной bytebuf хендлеру
    public CloudClient() {
        isAuthorized = false;
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
                e.printStackTrace();
            } finally {
                workerGroup.shutdownGracefully();
            }
        });
        t.start();
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
    }

    public void writeMsg(ByteBuf msg) {
        //добавляем длину всего сообщения перед сообщением(4 байта)
        ByteBuf msgLength = Unpooled.buffer(Integer.BYTES);
        msgLength.writeInt(msg.writerIndex());
        channel.write(msgLength);
        channel.writeAndFlush(msg);
    }

//    public static void writeSmallFile(DataOutputStream out, File f) throws IOException {
//        byte[] data;
//        try {
//            data = Files.readAllBytes(f.toPath());
//        } catch (IOException e) {
//            System.out.println("Exception caught when reading the file " + f.getName());
//            System.out.println(e.getMessage());
//            return;
//        }
//        out.writeShort(ProtocolDict.LOAD);
//        byte[] name = f.getName().getBytes(StandardCharsets.UTF_8);
//        out.writeInt(name.length);
//        out.write(name);/
//        out.writeInt(data.length);
//        out.write(data);
//    }
}
