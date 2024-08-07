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

package org.eclipse.jetty.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritePendingException;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.jetty.http.HttpGenerator;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * <p>A {@link Connection} that handles the HTTP protocol.</p>
 */
public class HttpConnection extends AbstractConnection implements Runnable, HttpTransport
{
    public static final String UPGRADE_CONNECTION_ATTRIBUTE = "org.eclipse.jetty.server.HttpConnection.UPGRADE";
    private static final boolean REQUEST_BUFFER_DIRECT=false;
    private static final boolean HEADER_BUFFER_DIRECT=false;
    private static final boolean CHUNK_BUFFER_DIRECT=false;
    private static final Logger LOG = Log.getLogger(HttpConnection.class);
    private static final ThreadLocal<HttpConnection> __currentConnection = new ThreadLocal<>();

    private final HttpConfiguration _config;
    private final Connector _connector;
    private final ByteBufferPool _bufferPool;
    private final HttpInput _input;
    private final HttpGenerator _generator;
    private final HttpChannelOverHttp _channel;
    private final HttpParser _parser;
    private volatile ByteBuffer _requestBuffer = null;
    private volatile ByteBuffer _chunk = null;
    private final BlockingReadCallback _blockingReadCallback = new BlockingReadCallback();
    private final AsyncReadCallback _asyncReadCallback = new AsyncReadCallback();
    private final SendCallback _sendCallback = new SendCallback();
    private final HttpInput.PoisonPillContent _recycleRequestBuffer = new RecycleBufferContent();

    /**
     * Get the current connection that this thread is dispatched to.
     * Note that a thread may be processing a request asynchronously and
     * thus not be dispatched to the connection.  
     * @return the current HttpConnection or null
     * @see Request#getAttribute(String) for a more general way to access the HttpConnection
     */
    public static HttpConnection getCurrentConnection()
    {
        return __currentConnection.get();
    }

    protected static HttpConnection setCurrentConnection(HttpConnection connection)
    {
        HttpConnection last=__currentConnection.get();
        if (connection==null)
            __currentConnection.remove();
        else 
            __currentConnection.set(connection);
        return last;
    }

    public HttpConnection(HttpConfiguration config, Connector connector, EndPoint endPoint)
    {
        super(endPoint, connector.getExecutor());
        _config = config;
        _connector = connector;
        _bufferPool = _connector.getByteBufferPool();
        _generator = newHttpGenerator();
        _input = newHttpInput();
        _channel = newHttpChannel(_input);
        _parser = newHttpParser();
        if (LOG.isDebugEnabled())
            LOG.debug("New HTTP Connection {}", this);
    }

    public HttpConfiguration getHttpConfiguration()
    {
        return _config;
    }

    protected HttpGenerator newHttpGenerator()
    {
        return new HttpGenerator(_config.getSendServerVersion(),_config.getSendXPoweredBy());
    }
    
    protected HttpInput newHttpInput()
    {
        return new HttpInputOverHTTP(this);
    }
    
    protected HttpChannelOverHttp newHttpChannel(HttpInput httpInput)
    {
        return new HttpChannelOverHttp(this, _connector, _config, getEndPoint(), this, httpInput);
    }
    
    protected HttpParser newHttpParser()
    {
        return new HttpParser(newRequestHandler(), getHttpConfiguration().getRequestHeaderSize());
    }

    protected HttpParser.RequestHandler newRequestHandler()
    {
        return _channel;
    }

    public Server getServer()
    {
        return _connector.getServer();
    }

    public Connector getConnector()
    {
        return _connector;
    }

    public HttpChannel getHttpChannel()
    {
        return _channel;
    }

    public HttpParser getParser()
    {
        return _parser;
    }

    public HttpGenerator getGenerator()
    {
        return _generator;
    }

    @Override
    public int getMessagesIn()
    {
        return getHttpChannel().getRequests();
    }

    @Override
    public int getMessagesOut()
    {
        return getHttpChannel().getRequests();
    }

    void releaseRequestBuffer()
    {
        if (_requestBuffer != null && !_requestBuffer.hasRemaining())
        {
            if (LOG.isDebugEnabled())
                LOG.debug("releaseRequestBuffer {}",this);
            ByteBuffer buffer=_requestBuffer;
            _requestBuffer=null;
            _bufferPool.release(buffer);
        }
    }
    
    public ByteBuffer getRequestBuffer()
    {
        if (_requestBuffer == null)
            _requestBuffer = _bufferPool.acquire(getInputBufferSize(), REQUEST_BUFFER_DIRECT);
        return _requestBuffer;
    }

    public boolean isRequestBufferEmpty()
    {
        return BufferUtil.isEmpty(_requestBuffer);
    }

    @Override
    public void onFillable()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{} onFillable enter {}", this, _channel.getState());

        HttpConnection last=setCurrentConnection(this);
        try
        {
            while (true)
            {
                // Fill the request buffer (if needed)
                int filled = fillRequestBuffer();
                
                // Parse the request buffer
                boolean handle = parseRequestBuffer();
                
                // Handle close parser
                if (_parser.isClose())
                {
                    close();
                    break;
                }
                
                // Handle channel event
                if (handle)
                {
                    boolean suspended = !_channel.handle();
                    
                    // We should break iteration if we have suspended or changed connection or this is not the handling thread.
                    if (suspended || getEndPoint().getConnection() != this)
                        break;
                }
                
                // Continue or break?
                else if (filled<=0)
                {
                    if (filled==0)
                        fillInterested();
                    break;
                }
            }
        }
        finally
        {                        
            setCurrentConnection(last);
            if (LOG.isDebugEnabled())
                LOG.debug("{} onFillable exit {}", this, _channel.getState());
        }
    }

    
    /* ------------------------------------------------------------ */
    /** Fill and parse data looking for content
     * @throws IOException
     */
    protected boolean parseContent() 
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{} parseContent",this);
        boolean handled=false;
        while (_parser.inContentState())
        {
            int filled = fillRequestBuffer();
            boolean handle = parseRequestBuffer();
            handled|=handle;
            if (handle || filled<=0)
                break;
        }
        return handled;
    }
    

    /* ------------------------------------------------------------ */
    private int fillRequestBuffer() 
    {
        if (BufferUtil.isEmpty(_requestBuffer))
        {
            // Can we fill?
            if(getEndPoint().isInputShutdown())
            {
                // No pretend we read -1
                _parser.atEOF();
                if (LOG.isDebugEnabled())
                    LOG.debug("{} filled -1",this);
                return -1;
            }
            
            // Get a buffer
            // We are not in a race here for the request buffer as we have not yet received a request,
            // so there are not an possible legal threads calling #parseContent or #completed.
            _requestBuffer = getRequestBuffer();

            // fill
            try
            {
                int filled = getEndPoint().fill(_requestBuffer);
                if (filled==0) // Do a retry on fill 0 (optimization for SSL connections)
                    filled = getEndPoint().fill(_requestBuffer);

                // tell parser
                if (filled < 0)
                    _parser.atEOF();
                
                if (LOG.isDebugEnabled())
                    LOG.debug("{} filled {}",this,filled);
                                
                return filled;
            }
            catch (IOException e)
            {
                LOG.debug(e);
                return -1;
            }
        }
        return 0;
    }

    /* ------------------------------------------------------------ */
    private boolean parseRequestBuffer() 
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{} parse {} {}",this,BufferUtil.toDetailString(_requestBuffer));
        
        boolean buffer_had_content=BufferUtil.hasContent(_requestBuffer);
        boolean handle = _parser.parseNext(_requestBuffer==null?BufferUtil.EMPTY_BUFFER:_requestBuffer);

        if (LOG.isDebugEnabled())
            LOG.debug("{} parsed {} {}",this,handle,_parser);
        
        // recycle buffer ?
        if (buffer_had_content)
        {
            if (BufferUtil.isEmpty(_requestBuffer))
            {
                if (_parser.inContentState())
                    _input.addContent(_recycleRequestBuffer);
                else
                    releaseRequestBuffer();
            }
        }
        else
        {
            releaseRequestBuffer();
        }
        return handle;
    }
    
    @Override
    public void onCompleted()
    {
        // Handle connection upgrades
        if (_channel.getResponse().getStatus() == HttpStatus.SWITCHING_PROTOCOLS_101)
        {
            Connection connection = (Connection)_channel.getRequest().getAttribute(UPGRADE_CONNECTION_ATTRIBUTE);
            if (connection != null)
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Upgrade from {} to {}", this, connection);
                onClose();
                getEndPoint().setConnection(connection);
                connection.onOpen();
                _channel.recycle();
                _parser.reset();
                _generator.reset();
                releaseRequestBuffer();
                return;
            }
        }
        
        // Finish consuming the request
        // If we are still expecting
        if (_channel.isExpecting100Continue())
        {
            // close to seek EOF
            _parser.close();
        }
        else if (_parser.inContentState() && _generator.isPersistent())
        {
            // If we are async, then we have problems to complete neatly
            if (_channel.getRequest().getHttpInput().isAsync())
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("unconsumed async input {}", this);
                _channel.abort(new IOException("unconsumed input"));
            }
            else
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("unconsumed input {}", this);
                // Complete reading the request
                if (!_channel.getRequest().getHttpInput().consumeAll())
                    _channel.abort(new IOException("unconsumed input"));
            }
        }

        // Reset the channel, parsers and generator
        _channel.recycle();
        if (_generator.isPersistent() && !_parser.isClosed())
            _parser.reset();
        else
            _parser.close();
        
        // Not in a race here with onFillable, because it has given up control before calling handle.
        // in a slight race with #completed, but not sure what to do with that anyway.
        if (_chunk!=null)
            _bufferPool.release(_chunk);
        _chunk=null;
        _generator.reset();

        // if we are not called from the onfillable thread, schedule completion
        if (getCurrentConnection()!=this)
        {
            // If we are looking for the next request
            if (_parser.isStart())
            {
                // if the buffer is empty
                if (BufferUtil.isEmpty(_requestBuffer))
                {
                    // look for more data
                    fillInterested();
                }
                // else if we are still running
                else if (getConnector().isRunning())
                {
                    // Dispatched to handle a pipelined request
                    try
                    {
                        getExecutor().execute(this);
                    }
                    catch (RejectedExecutionException e)
                    {
                        if (getConnector().isRunning())
                            LOG.warn(e);
                        else
                            LOG.ignore(e);
                        getEndPoint().close();
                    }
                }
                else
                {
                    getEndPoint().close();
                }
            }
            // else the parser must be closed, so seek the EOF if we are still open 
            else if (getEndPoint().isOpen())
                fillInterested();
        }
    }

    @Override
    protected void onFillInterestedFailed(Throwable cause)
    {
        _parser.close();
        super.onFillInterestedFailed(cause);
    }

    @Override
    public void onOpen()
    {
        super.onOpen();
        fillInterested();
    }

    @Override
    public void onClose()
    {
        _sendCallback.close();
        super.onClose();
    }

    @Override
    public void run()
    {
        onFillable();
    }

    @Override
    public void send(MetaData.Response info, boolean head, ByteBuffer content, boolean lastContent, Callback callback)
    {
        if (info == null)
        {
            if (!lastContent && BufferUtil.isEmpty(content))
            {
                callback.succeeded();
                return;
            }
        }
        else
        {
            // If we are still expecting a 100 continues when we commit
            if (_channel.isExpecting100Continue())
                // then we can't be persistent
                _generator.setPersistent(false);
        }
            
        if(_sendCallback.reset(info,head,content,lastContent,callback))
            _sendCallback.iterate();
    }

    private final class RecycleBufferContent extends HttpInput.PoisonPillContent
    {
        private RecycleBufferContent()
        {
            super("RECYCLE");
        }

        @Override
        public void succeeded()
        {
            releaseRequestBuffer();
        }

        @Override
        public void failed(Throwable x)
        {
            succeeded();
        }
    }

    private class BlockingReadCallback implements Callback
    {
        @Override
        public void succeeded()
        {
            _input.unblock();
        }

        @Override
        public void failed(Throwable x)
        {
            _input.failed(x);
        }
        
    }

    private class AsyncReadCallback implements Callback
    {
        @Override
        public void succeeded()
        {
            if (parseContent())
                _channel.handle();
            else
                asyncReadFillInterested();
        }

        @Override
        public void failed(Throwable x)
        {
            _input.failed(x);
            _channel.handle();
        }
    }
    
    
    private class SendCallback extends IteratingCallback
    {
        private MetaData.Response _info;
        private boolean _head;
        private ByteBuffer _content;
        private boolean _lastContent;
        private Callback _callback;
        private ByteBuffer _header;
        private boolean _shutdownOut;

        private SendCallback()
        {
            super(true);
        }

        private boolean reset(MetaData.Response info, boolean head, ByteBuffer content, boolean last, Callback callback)
        {
            if (reset())
            {
                _info = info;
                _head = head;
                _content = content;
                _lastContent = last;
                _callback = callback;
                _header = null;
                _shutdownOut = false;
                return true;
            }
            
            if (isClosed())
                callback.failed(new EofException());
            else
                callback.failed(new WritePendingException());
            return false;
        }

        @Override
        public Action process() throws Exception
        {
            if (_callback==null)
                throw new IllegalStateException();
            
            ByteBuffer chunk = _chunk;
            while (true)
            {
                HttpGenerator.Result result = _generator.generateResponse(_info, _head, _header, chunk, _content, _lastContent);
                if (LOG.isDebugEnabled())
                    LOG.debug("{} generate: {} ({},{},{})@{}",
                        this,
                        result,
                        BufferUtil.toSummaryString(_header),
                        BufferUtil.toSummaryString(_content),
                        _lastContent,
                        _generator.getState());

                switch (result)
                {
                    case NEED_HEADER:
                    {
                        // Look for optimisation to avoid allocating a _header buffer
                        /*
                         Cannot use this optimisation unless we work out how not to overwrite data in user passed arrays.
                        if (_lastContent && _content!=null && !_content.isReadOnly() && _content.hasArray() && BufferUtil.space(_content)>_config.getResponseHeaderSize() )
                        {
                            // use spare space in content buffer for header buffer
                            int p=_content.position();
                            int l=_content.limit();
                            _content.position(l);
                            _content.limit(l+_config.getResponseHeaderSize());
                            _header=_content.slice();
                            _header.limit(0);
                            _content.position(p);
                            _content.limit(l);
                        }
                        else
                        */
                            _header = _bufferPool.acquire(_config.getResponseHeaderSize(), HEADER_BUFFER_DIRECT);
                            
                        continue;
                    }
                    case NEED_CHUNK:
                    {
                        chunk = _chunk = _bufferPool.acquire(HttpGenerator.CHUNK_SIZE, CHUNK_BUFFER_DIRECT);
                        continue;
                    }
                    case FLUSH:
                    {
                        // Don't write the chunk or the content if this is a HEAD response, or any other type of response that should have no content
                        if (_head || _generator.isNoContent())
                        {
                            BufferUtil.clear(chunk);
                            BufferUtil.clear(_content);
                        }

                        // If we have a header
                        if (BufferUtil.hasContent(_header))
                        {
                            if (BufferUtil.hasContent(_content))
                            {
                                if (BufferUtil.hasContent(chunk))
                                    getEndPoint().write(this, _header, chunk, _content);
                                else
                                    getEndPoint().write(this, _header, _content);
                            }
                            else
                                getEndPoint().write(this, _header);
                        }
                        else if (BufferUtil.hasContent(chunk))
                        {
                            if (BufferUtil.hasContent(_content))
                                getEndPoint().write(this, chunk, _content);
                            else
                                getEndPoint().write(this, chunk);
                        }
                        else if (BufferUtil.hasContent(_content))
                        {
                            getEndPoint().write(this, _content);
                        }
                        else
                        {
                            succeeded(); // nothing to write
                        }
                        return Action.SCHEDULED;
                    }
                    case SHUTDOWN_OUT:
                    {
                        _shutdownOut=true;
                        continue;
                    }
                    case DONE:
                    {
                        return Action.SUCCEEDED;
                    }
                    case CONTINUE:
                    {
                        break;
                    }
                    default:
                    {
                        throw new IllegalStateException("generateResponse="+result);
                    }
                }
            }
        }

        private void releaseHeader()
        {
            ByteBuffer h=_header;
            _header=null;
            if (h!=null)
                _bufferPool.release(h);
        }
        
        @Override
        protected void onCompleteSuccess()
        {
            releaseHeader();
            _callback.succeeded();
            if (_shutdownOut)
                getEndPoint().shutdownOutput();
        }

        @Override
        public void onCompleteFailure(final Throwable x)
        {
            releaseHeader();
            failedCallback(_callback,x);
            if (_shutdownOut)
                getEndPoint().shutdownOutput();
        }
        
        @Override
        public String toString()
        {
            return String.format("%s[i=%s,cb=%s]",super.toString(),_info,_callback);
        }
    }

    @Override
    public void abort(Throwable failure)
    {
        // Do a direct close of the output, as this may indicate to a client that the 
        // response is bad either with RST or by abnormal completion of chunked response.
        getEndPoint().close();
    }

    @Override
    public boolean isPushSupported()
    {
        return false;
    }
    
    /**
     * @see org.eclipse.jetty.server.HttpTransport#push(org.eclipse.jetty.http.MetaData.Request)
     */
    @Override
    public void push(org.eclipse.jetty.http.MetaData.Request request)
    {   
        LOG.debug("ignore push in {}",this);
    }

    public void asyncReadFillInterested()
    {
        getEndPoint().fillInterested(_asyncReadCallback);
        
    }

    public void blockingReadFillInterested()
    {
        getEndPoint().fillInterested(_blockingReadCallback);        
    }
}
