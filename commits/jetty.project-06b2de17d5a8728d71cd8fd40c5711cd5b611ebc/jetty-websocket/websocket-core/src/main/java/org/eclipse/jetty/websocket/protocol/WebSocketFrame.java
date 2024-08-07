//
//  ========================================================================
//  Copyright (c) 1995-2012 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.protocol;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.websocket.api.ProtocolException;

/**
 * A Base Frame as seen in <a href="https://tools.ietf.org/html/rfc6455#section-5.2">RFC 6455. Sec 5.2</a>
 * 
 * <pre>
 *    0                   1                   2                   3
 *    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *   +-+-+-+-+-------+-+-------------+-------------------------------+
 *   |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
 *   |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
 *   |N|V|V|V|       |S|             |   (if payload len==126/127)   |
 *   | |1|2|3|       |K|             |                               |
 *   +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 *   |     Extended payload length continued, if payload len == 127  |
 *   + - - - - - - - - - - - - - - - +-------------------------------+
 *   |                               |Masking-key, if MASK set to 1  |
 *   +-------------------------------+-------------------------------+
 *   | Masking-key (continued)       |          Payload Data         |
 *   +-------------------------------- - - - - - - - - - - - - - - - +
 *   :                     Payload Data continued ...                :
 *   + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
 *   |                     Payload Data continued ...                |
 *   +---------------------------------------------------------------+
 * </pre>
 */
public class WebSocketFrame implements Frame
{
    /** Maximum size of Control frame, per RFC 6455 */
    public static final int MAX_CONTROL_PAYLOAD = 125;

    public static WebSocketFrame binary()
    {
        return new WebSocketFrame(OpCode.BINARY);
    }

    public static WebSocketFrame binary(byte buf[])
    {
        return new WebSocketFrame(OpCode.BINARY).setPayload(buf);
    }

    public static WebSocketFrame ping()
    {
        return new WebSocketFrame(OpCode.PING);
    }

    public static WebSocketFrame pong()
    {
        return new WebSocketFrame(OpCode.PONG);
    }

    public static WebSocketFrame text()
    {
        return new WebSocketFrame(OpCode.TEXT);
    }

    public static WebSocketFrame text(String msg)
    {
        return new WebSocketFrame(OpCode.TEXT).setPayload(msg);
    }

    private boolean fin = true;
    private boolean rsv1 = false;
    private boolean rsv2 = false;
    private boolean rsv3 = false;
    private byte opcode = -1;
    private boolean masked = false;
    private byte mask[];
    /**
     * The payload data.
     * <p>
     * It is assumed to always be in FLUSH mode (ready to read) in this object.
     */
    private ByteBuffer data;
    private int payloadLength = 0;
    /** position of start of data within a fresh payload */
    private int payloadStart = -1;

    private boolean continuation = false;
    private int continuationIndex = 0;

    /**
     * Default constructor
     */
    public WebSocketFrame()
    {
        this(OpCode.UNDEFINED);
    }

    /**
     * Construct form opcode
     */
    public WebSocketFrame(byte opcode)
    {
        reset();
        this.opcode = opcode;
    }

    /**
     * Copy constructor for the websocket frame.
     * <p>
     * Note: the underlying payload is merely a {@link ByteBuffer#slice()} of the input frame.
     * 
     * @param copy
     *            the websocket to copy.
     */
    public WebSocketFrame(WebSocketFrame copy)
    {
        fin = copy.fin;
        rsv1 = copy.rsv2;
        rsv2 = copy.rsv2;
        rsv3 = copy.rsv3;
        opcode = copy.opcode;
        masked = copy.masked;
        mask = null;
        if (copy.mask != null)
        {
            mask = new byte[copy.mask.length];
            System.arraycopy(copy.mask,0,mask,0,mask.length);
        }
        payloadLength = copy.payloadLength;
        payloadStart = copy.payloadStart;
        if (copy.data != null) // deal with empty payloads
        {
            data = copy.data.slice();
        }
        continuationIndex = copy.continuationIndex;
        continuation = copy.continuation;
    }

    public void assertValid()
    {
        if (OpCode.isControlFrame(opcode))
        {
            if (getPayloadLength() > WebSocketFrame.MAX_CONTROL_PAYLOAD)
            {
                throw new ProtocolException("Desired payload length [" + getPayloadLength() + "] exceeds maximum control payload length ["
                        + MAX_CONTROL_PAYLOAD + "]");
            }

            if (fin == false)
            {
                throw new ProtocolException("Cannot have FIN==false on Control frames");
            }

            if (rsv1 == true)
            {
                throw new ProtocolException("Cannot have RSV1==true on Control frames");
            }

            if (rsv2 == true)
            {
                throw new ProtocolException("Cannot have RSV2==true on Control frames");
            }

            if (rsv3 == true)
            {
                throw new ProtocolException("Cannot have RSV3==true on Control frames");
            }

            if (isContinuation())
            {
                throw new ProtocolException("Control frames cannot be Continuations");
            }
        }
    }

    /**
     * The number of fragments this frame consists of.
     * <p>
     * For every {@link OpCode#CONTINUATION} opcode encountered, this increments by one.
     * <p>
     * Note: Not part of the Base Framing Protocol / header information.
     * 
     * @return the number of continuation fragments encountered.
     */
    public int getContinuationIndex()
    {
        return continuationIndex;
    }

    @Override
    public byte[] getMask()
    {
        if (!masked)
        {
            throw new IllegalStateException("Frame is not masked");
        }
        return mask;
    }

    @Override
    public final byte getOpCode()
    {
        return opcode;
    }

    /**
     * Get the payload ByteBuffer. possible null.
     * <p>
     * 
     * @return A {@link ByteBuffer#slice()} of the payload buffer (to prevent modification of the buffer state). Possibly null if no payload present.
     *         <p>
     *         Note: this method is exposed via the immutable {@link Frame#getPayload()} method.
     */
    @Override
    public ByteBuffer getPayload()
    {
        if (data != null)
        {
            return data;
        }
        else
        {
            return null;
        }
    }

    public String getPayloadAsUTF8()
    {
        if (data == null)
        {
            return null;
        }
        return BufferUtil.toUTF8String(data);
    }

    @Override
    public int getPayloadLength()
    {
        if (data == null)
        {
            return 0;
        }
        return payloadLength;
    }

    public int getPayloadStart()
    {
        if (data == null)
        {
            return -1;
        }
        return payloadStart;
    }

    public boolean hasPayload()
    {
        return ((data != null) && (payloadLength > 0));
    }

    public boolean isContinuation()
    {
        return continuation;
    }

    public boolean isControlFrame()
    {
        return OpCode.isControlFrame(opcode);
    }

    public boolean isDataFrame()
    {
        return OpCode.isDataFrame(opcode);
    }

    @Override
    public boolean isFin()
    {
        return fin;
    }

    public boolean isLastFrame()
    {
        return fin;
    }

    @Override
    public boolean isMasked()
    {
        return masked;
    }

    @Override
    public boolean isRsv1()
    {
        return rsv1;
    }

    @Override
    public boolean isRsv2()
    {
        return rsv2;
    }

    @Override
    public boolean isRsv3()
    {
        return rsv3;
    }

    /**
     * Get the position currently within the payload data.
     * <p>
     * Used by flow control, generator and window sizing.
     * 
     * @return the number of bytes remaining in the payload data that has not yet been written out to Network ByteBuffers.
     */
    public int position()
    {
        if (data == null)
        {
            return -1;
        }
        return data.position();
    }

    /**
     * Get the number of bytes remaining to write out to the Network ByteBuffer.
     * <p>
     * Used by flow control, generator and window sizing.
     * 
     * @return the number of bytes remaining in the payload data that has not yet been written out to Network ByteBuffers.
     */
    public int remaining()
    {
        if (data == null)
        {
            return 0;
        }
        return data.remaining();
    }

    public void reset()
    {
        fin = true;
        rsv1 = false;
        rsv2 = false;
        rsv3 = false;
        opcode = -1;
        masked = false;
        data = null;
        payloadLength = 0;
        mask = null;
        continuationIndex = 0;
        continuation = false;
    }

    public WebSocketFrame setContinuation(boolean continuation)
    {
        this.continuation = continuation;
        return this;
    }

    public WebSocketFrame setContinuationIndex(int continuationIndex)
    {
        this.continuationIndex = continuationIndex;
        return this;
    }

    public WebSocketFrame setFin(boolean fin)
    {
        this.fin = fin;
        return this;
    }

    public WebSocketFrame setMask(byte[] maskingKey)
    {
        this.mask = maskingKey;
        this.masked = (mask != null);
        return this;
    }

    public WebSocketFrame setMasked(boolean mask)
    {
        this.masked = mask;
        return this;
    }

    public WebSocketFrame setOpCode(byte op)
    {
        this.opcode = op;
        return this;
    }

    /**
     * Set the data and payload length.
     * 
     * @param buf
     *            the bytebuffer to set
     */
    public WebSocketFrame setPayload(byte buf[])
    {
        if (buf == null)
        {
            data = null;
            return this;
        }

        if (OpCode.isControlFrame(opcode))
        {
            if (buf.length > WebSocketFrame.MAX_CONTROL_PAYLOAD)
            {
                throw new ProtocolException("Control Payloads can not exceed 125 bytes in length.");
            }
        }

        data = BufferUtil.toBuffer(buf);
        payloadStart = data.position();
        payloadLength = data.limit();
        return this;
    }

    /**
     * Set the data and payload length.
     * 
     * @param buf
     *            the bytebuffer to set
     */
    public WebSocketFrame setPayload(byte buf[], int offset, int len)
    {
        if (buf == null)
        {
            data = null;
            return this;
        }

        if (OpCode.isControlFrame(opcode))
        {
            if (len > WebSocketFrame.MAX_CONTROL_PAYLOAD)
            {
                throw new ProtocolException("Control Payloads can not exceed 125 bytes in length.");
            }
        }

        data = BufferUtil.toBuffer(buf,offset,len);
        payloadStart = data.position();
        payloadLength = data.limit();
        return this;
    }

    /**
     * Set the data payload.
     * <p>
     * The provided buffer will be used as is, no copying of bytes performed.
     * <p>
     * The provided buffer should be flipped and ready to READ from.
     * 
     * @param buf
     *            the bytebuffer to set
     */
    public WebSocketFrame setPayload(ByteBuffer buf)
    {
        if (buf == null)
        {
            data = null;
            return this;
        }

        if (OpCode.isControlFrame(opcode))
        {
            if (buf.remaining() > WebSocketFrame.MAX_CONTROL_PAYLOAD)
            {
                throw new ProtocolException("Control Payloads can not exceed 125 bytes in length.");
            }
        }

        data = buf.slice();
        payloadStart = data.position();
        payloadLength = data.limit();
        return this;
    }

    public WebSocketFrame setPayload(String str)
    {
        setPayload(BufferUtil.toBuffer(str,StringUtil.__UTF8_CHARSET));
        return this;
    }

    public WebSocketFrame setRsv1(boolean rsv1)
    {
        this.rsv1 = rsv1;
        return this;
    }

    public WebSocketFrame setRsv2(boolean rsv2)
    {
        this.rsv2 = rsv2;
        return this;
    }

    public WebSocketFrame setRsv3(boolean rsv3)
    {
        this.rsv3 = rsv3;
        return this;
    }

    @Override
    public String toString()
    {
        StringBuilder b = new StringBuilder();
        b.append(OpCode.name(opcode));
        b.append('[');
        b.append("len=").append(payloadLength);
        b.append(",fin=").append(fin);
        b.append(",masked=").append(masked);
        b.append(",continuation=").append(continuation);
        b.append(']');
        return b.toString();
    }
}
