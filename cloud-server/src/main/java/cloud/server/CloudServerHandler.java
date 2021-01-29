package cloud.server;

import cloud.common.FileDir;
import cloud.common.Protocol;
import cloud.common.ProtocolDict;
import cloud.common.User;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.ArrayList;

public class CloudServerHandler extends ChannelInboundHandlerAdapter {
    private Boolean isAuthorized;
    private User user = new User();

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
                registration(ctx, m);
            } else if (msgType == ProtocolDict.AUTHORIZATION) {
                authorization(ctx, m);
            } else if (msgType == ProtocolDict.GET_DIR_STRUCTURE) {
                getDirStructure(ctx, m);
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

    private void getDirStructure(ChannelHandlerContext ctx, ByteBuf msg) {
        if (!isAuthorized) {
            sendGetStructureError(ctx);
            return;
        }
        SqlClient sql = new SqlClient();
        try {
            //TODO Переписать с использованием пула коннектов к БД
            sql.connect();
            sendGetStructureStatusAndInfo(ctx, sql.getStructureByID(user.getId()));
            sql.disconnect();
        } catch (RuntimeException e) {
            sendGetStructureError(ctx);
        }

    }

    private void authorization(ChannelHandlerContext ctx, ByteBuf msg) {
        String login = Protocol.readString(msg);
        String pass = Protocol.readString(msg);
        SqlClient sql = new SqlClient();
        try {
            //TODO Переписать с использованием пула коннектов к БД
            sql.connect();
            if (sql.checkLoginPass(login, pass, user)) {
                isAuthorized = true;
                sendAuthStatusAndInfo(ctx);
            } else {
                sendAuthStatus(ctx, ProtocolDict.STATUS_LOGIN_FAIL);
            }
            sql.disconnect();
        } catch (RuntimeException e) {
            sendAuthStatus(ctx, ProtocolDict.STATUS_ERROR);
        }
    }

    public void registration(ChannelHandlerContext ctx, ByteBuf msg) {
        User u = new User(Protocol.readString(msg), Protocol.readString(msg), Protocol.readString(msg), Protocol.readString(msg));
        SqlClient sql = new SqlClient();
        //TODO Переписать с использованием пула коннектов к БД
        try {
            sql.connect();
            if (sql.checkLogin(u.getLogin())) {
                //System.out.println("Login already used");
                sendRegStatus(ctx, ProtocolDict.STATUS_LOGIN_USED);
                sql.disconnect();
                return;
            }
            sql.regUser(u);
            sql.disconnect();
            sendRegStatus(ctx, ProtocolDict.STATUS_OK);
        } catch (RuntimeException e) {
            sendRegStatus(ctx, ProtocolDict.STATUS_ERROR);
        }
    }

    public void sendGetStructureError(ChannelHandlerContext ctx) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.GET_DIR_STRUCTURE_STATUS);
        msg.writeShort(ProtocolDict.STATUS_ERROR);
        writeMsg(ctx.channel(), msg);
    }

    public void sendGetStructureStatusAndInfo(ChannelHandlerContext ctx, ArrayList<FileDir> arr) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.GET_DIR_STRUCTURE_STATUS);
        msg.writeShort(ProtocolDict.STATUS_OK);
        for (FileDir f : arr) {
            Protocol.putFileDir(msg, f);
        }
        writeMsg(ctx.channel(), msg);
    }

    public void sendRegStatus(ChannelHandlerContext ctx, short status) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.REGISTRATION_STATUS);
        msg.writeShort(status);
        writeMsg(ctx.channel(), msg);
    }

    public void sendAuthStatus(ChannelHandlerContext ctx, short status) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.AUTHORIZATION_STATUS);
        msg.writeShort(status);
        writeMsg(ctx.channel(), msg);
    }

    public void sendAuthStatusAndInfo(ChannelHandlerContext ctx) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.AUTHORIZATION_STATUS);
        msg.writeShort(ProtocolDict.STATUS_OK);
        Protocol.putString(msg, user.getName());
        Protocol.putString(msg, user.getEmail());
        Protocol.putString(msg, user.getLogin());
        msg.writeLong(user.getMemSize());
        msg.writeLong(user.getFreeMemSize());
        writeMsg(ctx.channel(), msg);
    }

    public void writeMsg(Channel channel, ByteBuf msg) {
        ByteBuf msgLength = Unpooled.buffer(Integer.BYTES);
        msgLength.writeInt(msg.writerIndex());
        channel.write(msgLength);
        channel.writeAndFlush(msg);
    }

}
