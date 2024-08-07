//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.http2.parser;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http2.frames.GoAwayFrame;

public class GoAwayBodyParser extends BodyParser
{
    private State state = State.PREPARE;
    private int cursor;
    private int length;
    private int lastStreamId;
    private int error;
    private byte[] payload;

    public GoAwayBodyParser(HeaderParser headerParser, Parser.Listener listener)
    {
        super(headerParser, listener);
    }

    private void reset()
    {
        state = State.PREPARE;
        cursor = 0;
        length = 0;
        lastStreamId = 0;
        error = 0;
        payload = null;
    }

    @Override
    public Result parse(ByteBuffer buffer)
    {
        while (buffer.hasRemaining())
        {
            switch (state)
            {
                case PREPARE:
                {
                    state = State.LAST_STREAM_ID;
                    length = getBodyLength();
                    break;
                }
                case LAST_STREAM_ID:
                {
                    if (buffer.remaining() >= 4)
                    {
                        lastStreamId = buffer.getInt();
                        lastStreamId &= 0x7F_FF_FF_FF;
                        state = State.ERROR;
                        length -= 4;
                        if (length <= 0)
                        {
                            return notifyConnectionFailure(ErrorCode.PROTOCOL_ERROR, "invalid_go_away_frame");
                        }
                    }
                    else
                    {
                        state = State.LAST_STREAM_ID_BYTES;
                        cursor = 4;
                    }
                    break;
                }
                case LAST_STREAM_ID_BYTES:
                {
                    int currByte = buffer.get() & 0xFF;
                    --cursor;
                    lastStreamId += currByte << (8 * cursor);
                    --length;
                    if (cursor > 0 && length <= 0)
                    {
                        return notifyConnectionFailure(ErrorCode.PROTOCOL_ERROR, "invalid_go_away_frame");
                    }
                    if (cursor == 0)
                    {
                        lastStreamId &= 0x7F_FF_FF_FF;
                        state = State.ERROR;
                        if (length == 0)
                        {
                            return notifyConnectionFailure(ErrorCode.PROTOCOL_ERROR, "invalid_go_away_frame");
                        }
                    }
                    break;
                }
                case ERROR:
                {
                    if (buffer.remaining() >= 4)
                    {
                        error = buffer.getInt();
                        state = State.PAYLOAD;
                        length -= 4;
                        if (length < 0)
                        {
                            return notifyConnectionFailure(ErrorCode.PROTOCOL_ERROR, "invalid_go_away_frame");
                        }
                        if (length == 0)
                        {
                            return onGoAway(lastStreamId, error, null);
                        }
                    }
                    else
                    {
                        state = State.ERROR_BYTES;
                        cursor = 4;
                    }
                    break;
                }
                case ERROR_BYTES:
                {
                    int currByte = buffer.get() & 0xFF;
                    --cursor;
                    error += currByte << (8 * cursor);
                    --length;
                    if (cursor > 0 && length <= 0)
                    {
                        return notifyConnectionFailure(ErrorCode.PROTOCOL_ERROR, "invalid_go_away_frame");
                    }
                    if (cursor == 0)
                    {
                        state = State.PAYLOAD;
                        if (length == 0)
                        {
                            return onGoAway(lastStreamId, error, null);
                        }
                    }
                    break;
                }
                case PAYLOAD:
                {
                    payload = new byte[length];
                    if (buffer.remaining() >= length)
                    {
                        buffer.get(payload);
                        return onGoAway(lastStreamId, error, payload);
                    }
                    else
                    {
                        state = State.PAYLOAD_BYTES;
                        cursor = length;
                    }
                    break;
                }
                case PAYLOAD_BYTES:
                {
                    payload[payload.length - cursor] = buffer.get();
                    --cursor;
                    if (cursor == 0)
                    {
                        return onGoAway(lastStreamId, error, payload);
                    }
                    break;
                }
                default:
                {
                    throw new IllegalStateException();
                }
            }
        }
        return Result.PENDING;
    }

    private Result onGoAway(int lastStreamId, int error, byte[] payload)
    {
        GoAwayFrame frame = new GoAwayFrame(lastStreamId, error, payload);
        reset();
        return notifyGoAway(frame) ? Result.ASYNC : Result.COMPLETE;
    }

    private enum State
    {
        PREPARE, LAST_STREAM_ID, LAST_STREAM_ID_BYTES, ERROR, ERROR_BYTES, PAYLOAD, PAYLOAD_BYTES
    }
}
