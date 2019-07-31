package com.constantine.communication.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Message decoder for all netty messages for the direct access client/server communication.
 */
public class SizedMessageDecoder extends ByteToMessageDecoder
{
    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out)
    {
        if (in.readableBytes() < 4)
        {
            return;
        }
        in.markReaderIndex();

        final byte flag = in.readByte();

        if (flag == SizedMessage.FLAG)
        {
            final int id = in.readInt();
            final int length = in.readInt();
            if (in.readableBytes() < length)
            {
                in.resetReaderIndex();
                return;
            }

            final SizedMessage message = new SizedMessage(new byte[length], id);
            in.readBytes(message.buffer);
            out.add(message);
        }
        else
        {
            final int id = in.readInt();
            final int length = in.readInt();
            if (in.readableBytes() < length)
            {
                in.resetReaderIndex();
                return;
            }

            final byte[] msg = new byte[length];
            in.readBytes(msg);

            final int lengthSig = in.readInt();
            if (in.readableBytes() < length)
            {
                in.resetReaderIndex();
                return;
            }

            final byte[] sig = new byte[lengthSig];
            in.readBytes(sig);
            out.add(new SignedSizedMessage(msg, id, sig));
        }
    }
}