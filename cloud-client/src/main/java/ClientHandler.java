import cloud.common.FileDir;
import cloud.common.Protocol;
import cloud.common.ProtocolDict;
import cloud.common.User;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class ClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHandler.class);
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
                    LOG.error("unexpected message received, msgType={}", msgType);
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
                    LOG.error("unexpected message received, msgType={}", msgType);
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
                    LOG.error("unexpected message received, msgType={}", msgType);
                }
            } else if (msgType == ProtocolDict.CREATE_NEW_DIRECTORY_STATUS) {
                short status = m.readShort();
                FileDir newDir = null;
                if (status == ProtocolDict.STATUS_OK) {
                    newDir = Protocol.readFileDirStr(m);
                }
                if (callbacks.getOnCreateNewDirCallback() != null) {
                    callbacks.getOnCreateNewDirCallback().accept(status, newDir);
                } else {
                    LOG.error("unexpected message received, msgType={}", msgType);
                }
            } else if (msgType == ProtocolDict.START_UPLOAD_FILE_STATUS) {
                short status = m.readShort();
                FileDir f = null;
                if (status == ProtocolDict.STATUS_OK) {
                    f = Protocol.readFileDirStr(m);
                }
                if (callbacks.getOnStartUploadFileCallback() != null) {
                    callbacks.getOnStartUploadFileCallback().accept(status, f);
                } else {
                    LOG.error("unexpected message received, msgType={}", msgType);
                }
            } else if (msgType == ProtocolDict.END_UPLOAD_FILE_STATUS) {
                short status = m.readShort();
                FileDir f = null;
                if (status == ProtocolDict.STATUS_OK) {
                    f = Protocol.readFileDirStr(m);
                }
                if (callbacks.getOnEndUploadFileCallback() != null) {
                    callbacks.getOnEndUploadFileCallback().accept(status, f);
                } else {
                    LOG.error("unexpected message received, msgType={}", msgType);
                }
            } else if (msgType == ProtocolDict.RENAME_STATUS) {
                short status = m.readShort();
                if (callbacks.getOnRenameStatusCallback() != null) {
                    callbacks.getOnRenameStatusCallback().accept(status);
                } else {
                    LOG.error("unexpected message received, msgType={}", msgType);
                }
            } else {
                LOG.error("unknown message received msgType={}", msgType);
            }
        } finally {
            m.release();
        }
    }

    public TreeDirectory getTreeDirectoryFromBuf(ByteBuf m) {
        TreeDirectory treeDirectory = new TreeDirectory();
        ArrayList<FileDir> arrFiles = new ArrayList<>();
        while (m.isReadable()) {
            arrFiles.add(Protocol.readFileDirStr(m));
        }
        for (FileDir f : arrFiles) {
            treeDirectory.insert(f);
        }
        return treeDirectory;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        LOG.error("e = ", e);
        ctx.close();
    }
}
