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

package org.eclipse.jetty.websocket.common.extensions.fragment;


import java.nio.ByteBuffer;
import java.util.Queue;

import org.eclipse.jetty.util.ConcurrentArrayQueue;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.extensions.ExtensionConfig;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.OpCode;
import org.eclipse.jetty.websocket.common.extensions.AbstractExtension;
import org.eclipse.jetty.websocket.common.frames.DataFrame;

/**
 * Fragment Extension
 */
public class FragmentExtension extends AbstractExtension
{
    private static final Logger LOG = Log.getLogger(FragmentExtension.class);

    private final Queue<FrameEntry> entries = new ConcurrentArrayQueue<>();
    private final IteratingCallback flusher = new Flusher();
    private int maxLength;

    @Override
    public String getName()
    {
        return "fragment";
    }

    @Override
    public void incomingFrame(Frame frame)
    {
        nextIncomingFrame(frame);
    }

    @Override
    public void outgoingFrame(Frame frame, WriteCallback callback, FlushMode flushMode)
    {
        ByteBuffer payload = frame.getPayload();
        int length = payload != null ? payload.remaining() : 0;
        if (OpCode.isControlFrame(frame.getOpCode()) || maxLength <= 0 || length <= maxLength)
        {
            nextOutgoingFrame(frame, callback, flushMode);
            return;
        }

        FrameEntry entry = new FrameEntry(frame, callback, flushMode);
        LOG.debug("Queuing {}", entry);
        entries.offer(entry);
        flusher.iterate();
    }

    @Override
    public void setConfig(ExtensionConfig config)
    {
        super.setConfig(config);
        maxLength = config.getParameter("maxLength", -1);
    }

    private static class FrameEntry
    {
        private final Frame frame;
        private final WriteCallback callback;
        private final FlushMode flushMode;

        private FrameEntry(Frame frame, WriteCallback callback, FlushMode flushMode)
        {
            this.frame = frame;
            this.callback = callback;
            this.flushMode = flushMode;
        }

        @Override
        public String toString()
        {
            return frame.toString();
        }
    }

    private class Flusher extends IteratingCallback implements WriteCallback
    {
        private FrameEntry current;
        private boolean finished = true;

        @Override
        protected Action process() throws Exception
        {
            if (finished)
            {
                current = entries.poll();
                LOG.debug("Processing {}", current);
                if (current == null)
                    return Action.IDLE;
                fragment(current, true);
            }
            else
            {
                fragment(current, false);
            }
            return Action.SCHEDULED;
        }

        private void fragment(FrameEntry entry, boolean first)
        {
            Frame frame = entry.frame;
            ByteBuffer payload = frame.getPayload();
            int remaining = payload.remaining();
            int length = Math.min(remaining, maxLength);
            finished = length == remaining;

            boolean continuation = frame.getType().isContinuation() || !first;
            DataFrame fragment = new DataFrame(frame, continuation);
            boolean fin = frame.isFin() && finished;
            fragment.setFin(fin);

            int limit = payload.limit();
            int newLimit = payload.position() + length;
            payload.limit(newLimit);
            ByteBuffer payloadFragment = payload.slice();
            payload.limit(limit);
            fragment.setPayload(payloadFragment);
            LOG.debug("Fragmented {}->{}", frame, fragment);
            payload.position(newLimit);

            nextOutgoingFrame(fragment, this, entry.flushMode);
        }

        @Override
        protected void completed()
        {
            // This IteratingCallback never completes.
        }

        @Override
        public void writeSuccess()
        {
            // Notify first then call succeeded(), otherwise
            // write callbacks may be invoked out of order.
            notifyCallbackSuccess(current.callback);
            succeeded();
        }

        @Override
        public void writeFailed(Throwable x)
        {
            // Notify first, the call succeeded() to drain the queue.
            // We don't want to call failed(x) because that will put
            // this flusher into a final state that cannot be exited,
            // and the failure of a frame may not mean that the whole
            // connection is now invalid.
            notifyCallbackFailure(current.callback, x);
            succeeded();
        }

        private void notifyCallbackSuccess(WriteCallback callback)
        {
            try
            {
                if (callback != null)
                    callback.writeSuccess();
            }
            catch (Throwable x)
            {
                LOG.debug("Exception while notifying success of callback " + callback, x);
            }
        }

        private void notifyCallbackFailure(WriteCallback callback, Throwable failure)
        {
            try
            {
                if (callback != null)
                    callback.writeFailed(failure);
            }
            catch (Throwable x)
            {
                LOG.debug("Exception while notifying failure of callback " + callback, x);
            }
        }
    }
}
