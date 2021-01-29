import cloud.common.FileDir;
import cloud.common.Protocol;
import cloud.common.ProtocolDict;
import cloud.common.User;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.ArrayList;
import java.util.function.Consumer;

public class ClientHandler extends ChannelInboundHandlerAdapter {
    private Callbacks callbacks;

    public ClientHandler(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf m = (ByteBuf) msg; // (1)
        try {
            int msgType = m.readShort();
            if (msgType == ProtocolDict.REGISTRATION_STATUS) {
                short status = m.readShort();
                if (callbacks.getOnRegStatusCallback() != null) {
                    callbacks.getOnRegStatusCallback().accept(status);
                } else {
                    // TODO: log
                }
            } else if (msgType == ProtocolDict.AUTHORIZATION_STATUS) {
                short status = m.readShort();
                User user = null;
                if (status == ProtocolDict.STATUS_OK) {
                    user = new User(Protocol.readString(m), Protocol.readString(m), Protocol.readString(m));
                    user.setMemSize(m.readLong());
                    user.setFreeMemSize(m.readLong());
                }
                if (callbacks.getOnAuthStatusCallback() != null) {
                    callbacks.getOnAuthStatusCallback().accept(status, user);
                } else {
                    // TODO: log
                }
            } else if (msgType == ProtocolDict.GET_DIR_STRUCTURE_STATUS) {
                short status = m.readShort();
                TreeDirectory treeDirectory = null;
                if (status == ProtocolDict.STATUS_OK) {
                    treeDirectory = getTreeDirectoryFromBuf(m);
                }
                if (callbacks.getOnDirStructureCallback() != null) {
                    callbacks.getOnDirStructureCallback().accept(status, treeDirectory);
                } else {
                    // TODO: log
                }
            } else {
                // TODO: log
            }
        } finally {
            m.release();
        }
    }

    public TreeDirectory getTreeDirectoryFromBuf(ByteBuf m) {
        TreeDirectory treeDirectory = new TreeDirectory();
        ArrayList<FileDir> arrFiles = new ArrayList<>();
        while (m.isReadable()) {
            arrFiles.add(Protocol.readFileDir(m));
        }
        for (FileDir f : arrFiles) {
            treeDirectory.insert(f);
        }
        return treeDirectory;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
