package cloud.server;

import cloud.common.CloudMsgDecoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class CloudServer {
    //CloudMsgDecoder ожидает, пока придет bytebuf, содержащий все сообщение,
    // считывает первые 4 байта из него(длина сообщения) и передает остальной bytebuf хендлеру
    public CloudServer() {
        EventLoopGroup auth = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(auth, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) throws Exception {
                            channel.pipeline().addLast(new CloudMsgDecoder(), new CloudServerHandler());
                        }
                    });
            ChannelFuture future = bootstrap.bind(8189).sync();
            future.channel().closeFuture().sync(); // block
        } catch (InterruptedException e) {
            System.out.println(e);
        } finally {
            auth.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        new CloudServer();
    }
}