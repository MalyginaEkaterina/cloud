package cloud.common;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class Protocol {
    public static void putString(ByteBuf out, String str) {
        byte[] b = str.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.writeBytes(b);
    }
    public static String readString(ByteBuf in) {
        int len = in.readInt();
        ByteBuf b = in.readBytes(len);
        try {
            return b.toString(StandardCharsets.UTF_8);
        } finally {
            b.release();
        }
    }

    public static FileDir readFileDirStr(ByteBuf in) {
        return new FileDir(in.readShort(), in.readLong(), in.readLong(), readString(in));
    }

    public static void putFileDir(ByteBuf out, FileDir f) {
        out.writeShort(f.getType());
        out.writeLong(f.getId());
        out.writeLong(f.getSize());
        putString(out, f.getPathStr());
    }
}
