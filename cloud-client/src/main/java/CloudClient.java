import cloud.common.ProtocolDict;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CloudClient {
    private SocketChannel channel;

    private static final String HOST = "localhost";
    private static final int PORT = 8189;
    private boolean isAuthorized;

    public CloudClient() {
        isAuthorized = false;

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
                                socketChannel.pipeline().addLast(new ClientHandler());
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

    public boolean authorize(String login, String password) {
        if (login.equals("katty") && password.equals("123")) {
            isAuthorized = true;
            return true;
        } else {
            return false;
        }
    }

    public void register(String name, String email, String login, String password) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.REGISTRATION);

        byte[] bName = name.getBytes(StandardCharsets.UTF_8);
        msg.writeByte(bName.length);
        msg.writeBytes(bName);

        byte[] bEmail = email.getBytes(StandardCharsets.UTF_8);
        msg.writeByte(bEmail.length);
        msg.writeBytes(bEmail);

        byte[] bLogin = login.getBytes(StandardCharsets.UTF_8);
        msg.writeByte(bLogin.length);
        msg.writeBytes(bLogin);

        byte[] bPass = password.getBytes(StandardCharsets.UTF_8);
        msg.writeByte(bPass.length);
        msg.writeBytes(bPass);

        ByteBuf msgLength = Unpooled.buffer(Integer.BYTES);
        msgLength.writeInt(msg.writerIndex());

        channel.write(msgLength);
        channel.writeAndFlush(msg);
    }

    public static void writeSmallFile(DataOutputStream out, File f) throws IOException {
        byte[] data;
        try {
            data = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            System.out.println("Exception caught when reading the file " + f.getName());
            System.out.println(e.getMessage());
            return;
        }
        out.writeShort(ProtocolDict.LOAD);
        byte[] name = f.getName().getBytes(StandardCharsets.UTF_8);
        out.writeInt(name.length);
        out.write(name);
        out.writeInt(data.length);
        out.write(data);
    }
}
