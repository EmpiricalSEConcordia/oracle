//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.jsr356.server;

import java.net.URI;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.toolchain.test.TestingDir;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.jsr356.server.samples.echo.LargeEchoSocket;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test Echo of Large messages, targeting the {@link javax.websocket.Session#setMaxTextMessageBufferSize(int)} functionality
 */
public class LargeAnnotatedTest
{
    @Rule
    public TestingDir testdir = new TestingDir();

    @Test
    public void testEcho() throws Exception
    {
        WSServer wsb = new WSServer(testdir,"app");
        wsb.createWebInf();
        wsb.copyEndpoint(LargeEchoSocket.class);

        try
        {
            wsb.start();
            URI uri = wsb.getServerBaseURI();

            WebAppContext webapp = wsb.createWebAppContext();
            wsb.deployWebapp(webapp);
            // wsb.dump();

            WebSocketClient client = new WebSocketClient();
            try
            {
                client.getPolicy().setMaxTextMessageSize(128*1024);
                client.start();
                JettyEchoSocket clientEcho = new JettyEchoSocket();
                Future<Session> foo = client.connect(clientEcho,uri.resolve("echo/large"));
                // wait for connect
                foo.get(1,TimeUnit.SECONDS);
                // The message size should be bigger than default, but smaller than the limit that LargeEchoSocket specifies
                byte txt[] = new byte[100 * 1024];
                Arrays.fill(txt,(byte)'o');
                String msg = new String(txt,StringUtil.__UTF8_CHARSET);
                clientEcho.sendMessage(msg);
                Queue<String> msgs = clientEcho.awaitMessages(1);
                Assert.assertEquals("Expected message",msg,msgs.poll());
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            wsb.stop();
        }
    }
}
