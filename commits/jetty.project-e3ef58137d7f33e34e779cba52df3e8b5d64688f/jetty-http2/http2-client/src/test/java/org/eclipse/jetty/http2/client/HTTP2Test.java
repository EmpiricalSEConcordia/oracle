//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.http2.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HostPortHttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.ErrorCode;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.GoAwayFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.SettingsFrame;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.FuturePromise;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.Promise;
import org.junit.Assert;
import org.junit.Test;

public class HTTP2Test extends AbstractTest
{
    @Test
    public void testRequestNoContentResponseNoContent() throws Exception
    {
        start(new EmptyHttpServlet());

        Session session = newClient(new Session.Listener.Adapter());

        HttpFields fields = new HttpFields();
        MetaData.Request metaData = newRequest("GET", fields);
        HeadersFrame frame = new HeadersFrame(metaData, null, true);
        final CountDownLatch latch = new CountDownLatch(1);
        session.newStream(frame, new Promise.Adapter<>(), new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                Assert.assertTrue(stream.getId() > 0);

                Assert.assertTrue(frame.isEndStream());
                Assert.assertEquals(stream.getId(), frame.getStreamId());
                Assert.assertTrue(frame.getMetaData().isResponse());
                MetaData.Response response = (MetaData.Response)frame.getMetaData();
                Assert.assertEquals(200, response.getStatus());

                latch.countDown();
            }
        });

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testRequestNoContentResponseContent() throws Exception
    {
        final byte[] content = "Hello World!".getBytes(StandardCharsets.UTF_8);
        start(new HttpServlet()
        {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
            {
                resp.getOutputStream().write(content);
            }
        });

        Session session = newClient(new Session.Listener.Adapter());

        HttpFields fields = new HttpFields();
        MetaData.Request metaData = newRequest("GET", fields);
        HeadersFrame frame = new HeadersFrame(metaData, null, true);
        final CountDownLatch latch = new CountDownLatch(2);
        session.newStream(frame, new Promise.Adapter<>(), new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                Assert.assertTrue(stream.getId() > 0);

                Assert.assertFalse(frame.isEndStream());
                Assert.assertEquals(stream.getId(), frame.getStreamId());
                Assert.assertTrue(frame.getMetaData().isResponse());
                MetaData.Response response = (MetaData.Response)frame.getMetaData();
                Assert.assertEquals(200, response.getStatus());

                latch.countDown();
            }

            @Override
            public void onData(Stream stream, DataFrame frame, Callback callback)
            {
                Assert.assertTrue(frame.isEndStream());
                Assert.assertEquals(ByteBuffer.wrap(content), frame.getData());

                callback.succeeded();
                latch.countDown();
            }
        });

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testMultipleRequests() throws Exception
    {
        final String downloadBytes = "X-Download";
        start(new HttpServlet()
        {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
            {
                int download = request.getIntHeader(downloadBytes);
                byte[] content = new byte[download];
                new Random().nextBytes(content);
                response.getOutputStream().write(content);
            }
        });

        int requests = 20;
        Session session = newClient(new Session.Listener.Adapter());

        Random random = new Random();
        HttpFields fields = new HttpFields();
        fields.putLongField(downloadBytes, random.nextInt(128 * 1024));
        fields.put("User-Agent", "HTTP2Client/" + Jetty.VERSION);
        MetaData.Request metaData = newRequest("GET", fields);
        HeadersFrame frame = new HeadersFrame(metaData, null, true);
        final CountDownLatch latch = new CountDownLatch(requests);
        for (int i = 0; i < requests; ++i)
        {
            session.newStream(frame, new Promise.Adapter<>(), new Stream.Listener.Adapter()
            {
                @Override
                public void onData(Stream stream, DataFrame frame, Callback callback)
                {
                    callback.succeeded();
                    if (frame.isEndStream())
                        latch.countDown();
                }
            });
        }

        Assert.assertTrue(latch.await(requests, TimeUnit.SECONDS));
    }

    @Test
    public void testCustomResponseCode() throws Exception
    {
        final int status = 475;
        start(new HttpServlet()
        {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
            {
                response.setStatus(status);
            }
        });

        Session session = newClient(new Session.Listener.Adapter());
        HttpFields fields = new HttpFields();
        MetaData.Request metaData = newRequest("GET", fields);
        HeadersFrame frame = new HeadersFrame(metaData, null, true);
        final CountDownLatch latch = new CountDownLatch(1);
        session.newStream(frame, new Promise.Adapter<>(), new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                MetaData.Response response = (MetaData.Response)frame.getMetaData();
                Assert.assertEquals(status, response.getStatus());
                if (frame.isEndStream())
                    latch.countDown();
            }
        });

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testHostHeader() throws Exception
    {
        final String host = "fooBar";
        final int port = 1313;
        final String authority = host + ":" + port;
        start(new HttpServlet()
        {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
            {
                Assert.assertEquals(host, request.getServerName());
                Assert.assertEquals(port, request.getServerPort());
                Assert.assertEquals(authority, request.getHeader("Host"));
            }
        });

        Session session = newClient(new Session.Listener.Adapter());
        HostPortHttpField hostHeader = new HostPortHttpField(authority);
        MetaData.Request metaData = new MetaData.Request("GET", HttpScheme.HTTP, hostHeader, servletPath, HttpVersion.HTTP_2, new HttpFields());
        HeadersFrame frame = new HeadersFrame(metaData, null, true);
        final CountDownLatch latch = new CountDownLatch(1);
        session.newStream(frame, new Promise.Adapter<>(), new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                MetaData.Response response = (MetaData.Response)frame.getMetaData();
                Assert.assertEquals(200, response.getStatus());
                if (frame.isEndStream())
                    latch.countDown();
            }
        });

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testServerSendsGoAwayOnStop() throws Exception
    {
        start(new ServerSessionListener.Adapter());

        CountDownLatch closeLatch = new CountDownLatch(1);
        newClient(new Session.Listener.Adapter()
        {
            @Override
            public void onClose(Session session, GoAwayFrame frame)
            {
                closeLatch.countDown();
            }
        });

        Thread.sleep(1000);

        server.stop();

        Assert.assertTrue(closeLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testClientSendsGoAwayOnStop() throws Exception
    {
        CountDownLatch closeLatch = new CountDownLatch(1);
        start(new ServerSessionListener.Adapter()
        {
            @Override
            public void onClose(Session session, GoAwayFrame frame)
            {
                closeLatch.countDown();
            }
        });

        newClient(new Session.Listener.Adapter());

        Thread.sleep(1000);

        client.stop();

        Assert.assertTrue(closeLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testMaxConcurrentStreams() throws Exception
    {
        int maxStreams = 2;
        start(new ServerSessionListener.Adapter()
        {
            @Override
            public Map<Integer, Integer> onPreface(Session session)
            {
                Map<Integer, Integer> settings = new HashMap<>(1);
                settings.put(SettingsFrame.MAX_CONCURRENT_STREAMS, maxStreams);
                return settings;
            }

            @Override
            public Stream.Listener onNewStream(Stream stream, HeadersFrame frame)
            {
                MetaData.Response response = new MetaData.Response(HttpVersion.HTTP_2, HttpStatus.OK_200, new HttpFields(), 0);
                stream.headers(new HeadersFrame(stream.getId(), response, null, true), Callback.NOOP);
                return null;
            }
        });

        CountDownLatch settingsLatch = new CountDownLatch(1);
        Session session = newClient(new Session.Listener.Adapter()
        {
            @Override
            public void onSettings(Session session, SettingsFrame frame)
            {
                settingsLatch.countDown();
            }
        });
        Assert.assertTrue(settingsLatch.await(5, TimeUnit.SECONDS));

        MetaData.Request request1 = newRequest("GET", new HttpFields());
        FuturePromise<Stream> promise1 = new FuturePromise<>();
        CountDownLatch exchangeLatch1 = new CountDownLatch(2);
        session.newStream(new HeadersFrame(request1, null, false), promise1, new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                if (frame.isEndStream())
                    exchangeLatch1.countDown();
            }
        });
        Stream stream1 = promise1.get(5, TimeUnit.SECONDS);

        MetaData.Request request2 = newRequest("GET", new HttpFields());
        FuturePromise<Stream> promise2 = new FuturePromise<>();
        CountDownLatch exchangeLatch2 = new CountDownLatch(2);
        session.newStream(new HeadersFrame(request2, null, false), promise2, new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                if (frame.isEndStream())
                    exchangeLatch2.countDown();
            }
        });
        Stream stream2 = promise2.get(5, TimeUnit.SECONDS);

        // The third stream must not be created.
        MetaData.Request request3 = newRequest("GET", new HttpFields());
        CountDownLatch maxStreamsLatch = new CountDownLatch(1);
        session.newStream(new HeadersFrame(request3, null, false), new Promise.Adapter<Stream>()
        {
            @Override
            public void failed(Throwable x)
            {
                if (x instanceof IllegalStateException)
                    maxStreamsLatch.countDown();
            }
        }, new Stream.Listener.Adapter());

        Assert.assertTrue(maxStreamsLatch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(2, session.getStreams().size());

        // End the second stream.
        stream2.data(new DataFrame(stream2.getId(), BufferUtil.EMPTY_BUFFER, true), new Callback()
        {
            @Override
            public void succeeded()
            {
                exchangeLatch2.countDown();
            }
        });
        Assert.assertTrue(exchangeLatch2.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(1, session.getStreams().size());

        // Create a fourth stream.
        MetaData.Request request4 = newRequest("GET", new HttpFields());
        CountDownLatch exchangeLatch4 = new CountDownLatch(2);
        session.newStream(new HeadersFrame(request4, null, true), new Promise.Adapter<Stream>()
        {
            @Override
            public void succeeded(Stream result)
            {
                exchangeLatch4.countDown();
            }
        }, new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                if (frame.isEndStream())
                    exchangeLatch4.countDown();
            }
        });
        Assert.assertTrue(exchangeLatch4.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(1, session.getStreams().size());

        // End the first stream.
        stream1.data(new DataFrame(stream1.getId(), BufferUtil.EMPTY_BUFFER, true), new Callback()
        {
            @Override
            public void succeeded()
            {
                exchangeLatch1.countDown();
            }
        });
        Assert.assertTrue(exchangeLatch2.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(0, session.getStreams().size());
    }

    @Test
    public void testCleanGoAwayDoesNotTriggerFailureNotification() throws Exception
    {
        start(new ServerSessionListener.Adapter()
        {
            @Override
            public Stream.Listener onNewStream(Stream stream, HeadersFrame frame)
            {
                MetaData.Response metaData = new MetaData.Response(HttpVersion.HTTP_2, HttpStatus.OK_200, new HttpFields());
                HeadersFrame response = new HeadersFrame(stream.getId(), metaData, null, true);
                stream.headers(response, Callback.NOOP);
                // Close cleanly.
                stream.getSession().close(ErrorCode.NO_ERROR.code, null, Callback.NOOP);
                return null;
            }
        });

        CountDownLatch closeLatch = new CountDownLatch(1);
        CountDownLatch failureLatch = new CountDownLatch(1);
        Session session = newClient(new Session.Listener.Adapter()
        {
            @Override
            public void onClose(Session session, GoAwayFrame frame)
            {
                closeLatch.countDown();
            }

            @Override
            public void onFailure(Session session, Throwable failure)
            {
                failureLatch.countDown();
            }
        });
        MetaData.Request metaData = newRequest("GET", new HttpFields());
        HeadersFrame request = new HeadersFrame(metaData, null, true);
        session.newStream(request, new Promise.Adapter<>(), new Stream.Listener.Adapter());

        // Make sure onClose() is called.
        Assert.assertTrue(closeLatch.await(5, TimeUnit.SECONDS));
        Assert.assertFalse(failureLatch.await(1, TimeUnit.SECONDS));
    }
}
