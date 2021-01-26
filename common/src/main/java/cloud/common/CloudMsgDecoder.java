package cloud.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class CloudMsgDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < Integer.BYTES) {
            return;
        }
        int len = in.getInt(in.readerIndex());
        if (in.readableBytes() < Integer.BYTES + len) {
            return;
        }
        in.skipBytes(Integer.BYTES);
        out.add(in.readBytes(len));
    }
}
