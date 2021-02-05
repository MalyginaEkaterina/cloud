package cloud.server;

import cloud.common.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class CloudServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(CloudServerHandler.class);
    private static final String CLOUD_PATH = "cloudServerStorage";
    private static final long DEFAULT_MEM_SIZE = 1024 * 1024 * 1024;


    private User user = new User();
    private HashMap<Long, FileLoaded> filesLoaded = new HashMap<>();


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        user = null;
        LOG.info("client connected");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf m = (ByteBuf) msg; // (1)
        try {
            int msgType = m.readShort();
            if (msgType != ProtocolDict.UPLOAD_FILE) {
                LOG.info("received message (userid={}, type={})", (user == null) ? "null" : user.getId(), msgType);
            }
            if (msgType == ProtocolDict.REGISTRATION) {
                registration(ctx, m);
            } else if (msgType == ProtocolDict.AUTHORIZATION) {
                authorization(ctx, m);
            } else if (msgType == ProtocolDict.GET_DIR_STRUCTURE) {
                getDirStructure(ctx, m);
            } else if (msgType == ProtocolDict.CREATE_NEW_DIRECTORY) {
                createNewDirectory(ctx, m);
            } else if (msgType == ProtocolDict.START_UPLOAD_FILE) {
                startUploadFile(ctx, m);
            } else if (msgType == ProtocolDict.UPLOAD_FILE) {
                uploadFile(ctx, m);
            } else if (msgType == ProtocolDict.END_UPLOAD_FILE) {
                endUploadFile(ctx, m);
            } else if (msgType == ProtocolDict.RENAME) {
                rename(ctx, m);
            } else if (msgType == ProtocolDict.DELETE) {
                delete(ctx, m);
            } else if (msgType == ProtocolDict.DOWNLOAD) {
                downloadFile(ctx, m);
            } else {
                LOG.error("unknown message received, msg_type={}", msgType);
            }
        } finally {
            m.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        LOG.error("e = ", e);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOG.info("client disconnected");
    }

    private void getDirStructure(ChannelHandlerContext ctx, ByteBuf msg) {
        if (user == null) {
            sendStatus(ctx, ProtocolDict.GET_DIR_STRUCTURE_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("unknown user");
            return;
        }
        try {
            sendGetStructureStatusAndInfo(ctx, SqlClient.getStructureByID(user.getId()));
        } catch (RuntimeException e) {
            sendStatus(ctx, ProtocolDict.GET_DIR_STRUCTURE_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("e = ", e);
        }

    }

    private void createNewDirectory(ChannelHandlerContext ctx, ByteBuf msg) {
        if (user == null) {
            sendStatus(ctx, ProtocolDict.CREATE_NEW_DIRECTORY_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("unknown user");
            return;
        }
        FileDir newDir = Protocol.readFileDirStr(msg);
        try {
            newDir.setId(SqlClient.createNewDir(user.getId(), newDir));
            sendOKStatusAndFileDir(ctx, newDir, ProtocolDict.CREATE_NEW_DIRECTORY_STATUS);
            LOG.info("folder (id={}) was created", newDir.getId());
        } catch (RuntimeException e) {
            sendStatus(ctx, ProtocolDict.CREATE_NEW_DIRECTORY_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("e = ", e);
        }
    }

    //на сервере все файлы клиента сохраняются в папке с названием=id клиента из БД (с названиями = id файла из БД).
    //перед загрузкой проверяется, что у клиента достаточно места для данного файла.
    private void startUploadFile(ChannelHandlerContext ctx, ByteBuf m) {
        if (user == null) {
            sendStatus(ctx, ProtocolDict.START_UPLOAD_FILE_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("unknown user");
            return;
        }
        FileDir f = Protocol.readFileDirStr(m);
        if (SqlClient.getFreeMemSize(user.getId()) < f.getSize()) {
            sendStatus(ctx, ProtocolDict.START_UPLOAD_FILE_STATUS, ProtocolDict.STATUS_NOT_ENOUGH_MEM);
            LOG.error("there is not enough free space to upload file, filename={}", f.getName());
            return;
        }
        Long fid = null;
        try {
            fid = SqlClient.startUploadFile(user.getId(), f);
            if (fid != null) {
                f.setId(fid);
                File userDir = new File(CLOUD_PATH + "/" + user.getId());
                userDir.mkdirs();
                File newFile = new File(userDir, Long.toString(fid));
                FileLoaded fLoaded = new FileLoaded(f, newFile);
                filesLoaded.put(fid, fLoaded);
                sendOKStatusAndFileDir(ctx, f, ProtocolDict.START_UPLOAD_FILE_STATUS);
                LOG.info("start upload file id={}", fid);
            }
        } catch (RuntimeException | FileNotFoundException e) {
            if (fid != null) {
                SqlClient.endUploadFileWithError(user.getId(), fid, f.getSize());
            }
            sendStatus(ctx, ProtocolDict.START_UPLOAD_FILE_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("e = ", e);
        }
    }

    private void uploadFile(ChannelHandlerContext ctx, ByteBuf m) {
        Long fid = m.readLong();
        FileLoaded fl = filesLoaded.get(fid);
        if (fl.getFailed()) {
            return;
        }
        try {
            byte[] b = new byte[m.writerIndex() - m.readerIndex()];
            m.readBytes(b);
            fl.getOutputStream().write(b);
        } catch (IOException e) {
            fl.setFailed(true);
            LOG.error("unable to upload file id={}", fid);
        }
    }

    private void endUploadFile(ChannelHandlerContext ctx, ByteBuf m) {
        Long fid = m.readLong();
        FileLoaded fl = filesLoaded.get(fid);
        try {
            fl.getOutputStream().close();
            if (fl.getFailed()) {
                fl.getFile().delete();
                SqlClient.endUploadFileWithError(user.getId(), fid, fl.getFileDir().getSize());
                sendStatus(ctx, ProtocolDict.END_UPLOAD_FILE_STATUS, ProtocolDict.STATUS_ERROR);
                LOG.info("upload file (id={}) was failed, file was deleted", fid);
            } else {
                SqlClient.endUploadFile(user.getId(), fid);
                sendOKStatusAndFileDir(ctx, fl.getFileDir(), ProtocolDict.END_UPLOAD_FILE_STATUS);
                LOG.info("file (id={}) was uploaded", fid);
            }
            filesLoaded.remove(fid);
        } catch (RuntimeException | IOException e) {
            sendStatus(ctx, ProtocolDict.END_UPLOAD_FILE_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("e = ", e);
        }
    }

    private void downloadFile(ChannelHandlerContext ctx, ByteBuf m) {
        Long fid = m.readLong();
        if (user == null) {
            sendEndDownload(ctx, ProtocolDict.STATUS_ERROR, fid);
            LOG.error("unknown user");
            return;
        }
        File file = new File(CLOUD_PATH + "/" + user.getId() + "/" + fid);
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            byte[] b = new byte[10 * 1024];
            int i = 0;
            while ((i = fileInputStream.read(b)) >= 0) {
                ByteBuf msg = Unpooled.buffer();
                msg.writeShort(ProtocolDict.PROCESS_DOWNLOAD);
                msg.writeLong(fid);
                msg.writeBytes(b, 0, i);
                writeMsg(ctx.channel(), msg);
            }
            sendEndDownload(ctx, ProtocolDict.STATUS_OK, fid);
        } catch (IOException e) {
            sendEndDownload(ctx, ProtocolDict.STATUS_ERROR, fid);
            LOG.error("e = " + e);
        }
    }

    private void rename(ChannelHandlerContext ctx, ByteBuf m) {
        if (user == null) {
            sendStatus(ctx, ProtocolDict.RENAME_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("unknown user");
            return;
        }
        String newName = Protocol.readString(m);
        FileDir f = Protocol.readFileDirStr(m);
        try {
            SqlClient.rename(user.getId(), newName, f);
            sendStatus(ctx, ProtocolDict.RENAME_STATUS, ProtocolDict.STATUS_OK);
        } catch (RuntimeException e) {
            sendStatus(ctx, ProtocolDict.RENAME_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("e = ", e);
        }
    }

    private void delete(ChannelHandlerContext ctx, ByteBuf m) {
        if (user == null) {
            sendStatus(ctx, ProtocolDict.DELETE_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("unknown user");
            return;
        }
        Short emptyFlag = m.readShort();
        FileDir f = Protocol.readFileDirStr(m);
        try {
            ArrayList<Long> deletedFiles = SqlClient.delete(user.getId(), f, emptyFlag);
            for (Long fid : deletedFiles) {
                File file = new File(CLOUD_PATH + "/" + user.getId() + "/" + fid);
                file.delete();
            }
            sendStatusDeleteAndFreeSize(ctx, SqlClient.getFreeMemSize(user.getId()));
        } catch (RuntimeException e) {
            sendStatus(ctx, ProtocolDict.DELETE_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("e = ", e);
        }
    }

    private void authorization(ChannelHandlerContext ctx, ByteBuf msg) {
        String login = Protocol.readString(msg);
        String pass = Protocol.readString(msg);
        try {
            user = SqlClient.checkLoginPass(login, pass);
            if (user != null) {
                sendAuthStatusAndInfo(ctx);
            } else {
                sendStatus(ctx, ProtocolDict.AUTHORIZATION_STATUS, ProtocolDict.STATUS_LOGIN_FAIL);
            }
        } catch (RuntimeException e) {
            sendStatus(ctx, ProtocolDict.AUTHORIZATION_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("e = ", e);
        }
    }

    public void registration(ChannelHandlerContext ctx, ByteBuf msg) {
        User newUser = new User(Protocol.readString(msg), Protocol.readString(msg), Protocol.readString(msg), Protocol.readString(msg));
        newUser.setMemSize(DEFAULT_MEM_SIZE);
        newUser.setFreeMemSize(DEFAULT_MEM_SIZE);
        try {
            if (SqlClient.checkLogin(newUser.getLogin())) {
                sendStatus(ctx, ProtocolDict.REGISTRATION_STATUS, ProtocolDict.STATUS_LOGIN_USED);
                return;
            }
            SqlClient.regUser(newUser);
            sendStatus(ctx, ProtocolDict.REGISTRATION_STATUS, ProtocolDict.STATUS_OK);
        } catch (RuntimeException e) {
            sendStatus(ctx, ProtocolDict.REGISTRATION_STATUS, ProtocolDict.STATUS_ERROR);
            LOG.error("e = ", e);
        }
    }

    public void sendStatus(ChannelHandlerContext ctx, short command, short status) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(command);
        msg.writeShort(status);
        writeMsg(ctx.channel(), msg);
        LOG.info("sent status: msg_type={}, status={}", command, status);
    }

    public void sendEndDownload(ChannelHandlerContext ctx, short status, long fid) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.END_DOWNLOAD);
        msg.writeShort(status);
        msg.writeLong(fid);
        writeMsg(ctx.channel(), msg);
        LOG.info("sent download end: file_if={}, status={}", fid, status);
    }

    private void sendStatusDeleteAndFreeSize(ChannelHandlerContext ctx, Long freeMemSize) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.DELETE_STATUS);
        msg.writeShort(ProtocolDict.STATUS_OK);
        msg.writeLong(freeMemSize);
        writeMsg(ctx.channel(), msg);
        LOG.info("sent OK status on delete, new free size={}", freeMemSize);
    }

    public void sendGetStructureStatusAndInfo(ChannelHandlerContext ctx, ArrayList<FileDir> arr) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(ProtocolDict.GET_DIR_STRUCTURE_STATUS);
        msg.writeShort(ProtocolDict.STATUS_OK);
        for (FileDir f : arr) {
            Protocol.putFileDir(msg, f);
        }
        writeMsg(ctx.channel(), msg);
        LOG.info("sent directory structure for user id={}", user.getId());
    }

    private void sendOKStatusAndFileDir(ChannelHandlerContext ctx, FileDir fileDir, short command) {
        ByteBuf msg = Unpooled.buffer();
        msg.writeShort(command);
        msg.writeShort(ProtocolDict.STATUS_OK);
        Protocol.putFileDir(msg, fileDir);
        writeMsg(ctx.channel(), msg);
        LOG.info("sent msg_type={} with status OK and file info id={}", command, fileDir.getId());
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
        LOG.info("sent auth user info, user_id={}", user.getId());
    }

    public void writeMsg(Channel channel, ByteBuf msg) {
        ByteBuf msgLength = Unpooled.buffer(Integer.BYTES);
        msgLength.writeInt(msg.writerIndex());
        channel.write(msgLength);
        channel.writeAndFlush(msg);
    }
}
