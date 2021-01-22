package nio;

import cloud.common.ProtocolDict;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.charset.StandardCharsets;

public class CloudServerHandler extends ChannelInboundHandlerAdapter {
    private Boolean isAuthorized;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        isAuthorized = false;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf m = (ByteBuf) msg; // (1)
        try {
            int msgType = m.readShort();
            if (msgType == ProtocolDict.REGISTRATION) {
                registration(m);
            }
        } finally {
            m.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    public void registration(ByteBuf msg) {
        int nameLen = msg.readByte();
        String name = msg.readBytes(nameLen).toString(StandardCharsets.UTF_8);
        int emailLen = msg.readByte();
        String email = msg.readBytes(emailLen).toString(StandardCharsets.UTF_8);
        int loginLen = msg.readByte();
        String login = msg.readBytes(loginLen).toString(StandardCharsets.UTF_8);
        int passLen = msg.readByte();
        String pass = msg.readBytes(passLen).toString(StandardCharsets.UTF_8);
        System.out.println(name + " " + email + " " + login + " " + pass);
    }

}
