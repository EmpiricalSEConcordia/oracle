//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.server;

import java.io.IOException;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.SharedBlockingCallback;
import org.eclipse.jetty.util.SharedBlockingCallback.Blocker;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class HttpInputOverHTTP extends HttpInput implements Callback
{
    private static final Logger LOG = Log.getLogger(HttpInputOverHTTP.class);

    private final HttpConnection _httpConnection;
    private Content _content;
    private final SharedBlockingCallback _readBlocker;

    public HttpInputOverHTTP(HttpConnection httpConnection)
    {
        _httpConnection = httpConnection;
        _readBlocker = new SharedBlockingCallback();
    }

    @Override
    public void recycle()
    {
        synchronized (lock())
        {
            super.recycle();
            _content=null;
        }
    }

    @Override
    protected void blockForContent() throws IOException
    {
        while(true)
        {
            try (Blocker blocker=_readBlocker.acquire())
            {            
                _httpConnection.fillInterested(blocker);
                if (LOG.isDebugEnabled())
                    LOG.debug("{} block readable on {}",this,blocker);
                blocker.block();
            }

            Object content=getNextContent();
            if (content!=null || isFinished())
                break;
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x",getClass().getSimpleName(),hashCode());
    }

    @Override
    protected Content nextContent() throws IOException
    {
        // If we have some content available, return it
        if (_content!=null && _content.hasContent())
            return _content;

        // No - then we are going to need to parse some more content
        _content=null;
        _httpConnection.parseContent();
        
        // If we have some content available, return it
        if (_content!=null && _content.hasContent())
            return _content;

        return null;
    }

    @Override
    public void content(Content item)
    {
        if (_content!=null && _content.hasContent())
            throw new IllegalStateException();
        _content=item;
    }

    @Override
    protected void unready()
    {
        _httpConnection.fillInterested(this);
    }

    @Override
    public void succeeded()
    {
        _httpConnection.getHttpChannel().getState().onReadPossible();
    }

    @Override
    public void failed(Throwable x)
    {
        super.failed(x);
        _httpConnection.getHttpChannel().getState().onReadPossible();
    }
}
