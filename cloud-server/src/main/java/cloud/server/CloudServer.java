package cloud.server;

import cloud.common.CloudMsgDecoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class CloudServer {
    private static final Logger LOG = LoggerFactory.getLogger(CloudServer.class);
    //CloudMsgDecoder ожидает, пока придет bytebuf, содержащий все сообщение,
    // считывает первые 4 байта из него(длина сообщения) и передает остальной bytebuf хендлеру
    public CloudServer() {
        EventLoopGroup auth = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            SqlClient.connect();
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
            LOG.info("Server started");
            future.channel().closeFuture().sync();
        } catch (InterruptedException | SQLException e) {
            LOG.error("e = ", e);
        } finally {
            auth.shutdownGracefully();
            worker.shutdownGracefully();
            try {
                SqlClient.disconnect();
            } catch (SQLException e) {
                LOG.error("e = ", e);
            }
        }
    }

    public static void main(String[] args) {
        new CloudServer();
    }
}
