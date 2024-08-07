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

package org.eclipse.jetty.http.client;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.fcgi.client.http.HttpClientTransportOverFCGI;
import org.eclipse.jetty.fcgi.server.ServerFCGIConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.toolchain.test.TestTracker;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class AbstractTest
{
    @Parameterized.Parameters(name = "transport: {0}")
    public static Object[] parameters() throws Exception
    {
        return Transport.values();
    }

    @Rule
    public final TestTracker tracker = new TestTracker();

    protected final Transport transport;
    protected SslContextFactory sslContextFactory;
    protected Server server;
    protected ServerConnector connector;
    protected HttpClient client;

    public AbstractTest(Transport transport)
    {
        this.transport = transport;
    }

    public void start(Handler handler) throws Exception
    {
        startServer(handler);
        startClient();
    }

    protected void startServer(Handler handler) throws Exception
    {
        sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath("src/test/resources/keystore.jks");
        sslContextFactory.setKeyStorePassword("storepwd");
        sslContextFactory.setTrustStorePath("src/test/resources/truststore.jks");
        sslContextFactory.setTrustStorePassword("storepwd");
        sslContextFactory.setUseCipherSuitesOrder(true);
        sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
        QueuedThreadPool serverThreads = new QueuedThreadPool();
        serverThreads.setName("server");
        server = new Server(serverThreads);
        connector = new ServerConnector(server, provideServerConnectionFactory(transport));
        server.addConnector(connector);
        server.setHandler(handler);
        server.start();
    }

    private void startClient() throws Exception
    {
        QueuedThreadPool clientThreads = new QueuedThreadPool();
        clientThreads.setName("client");
        client = newHttpClient(provideClientTransport(transport), sslContextFactory);
        client.setExecutor(clientThreads);
        client.start();
    }

    private ConnectionFactory[] provideServerConnectionFactory(Transport transport)
    {
        List<ConnectionFactory> result = new ArrayList<>();
        switch (transport)
        {
            case HTTP:
            {
                result.add(new HttpConnectionFactory(new HttpConfiguration()));
                break;
            }
            case HTTPS:
            {
                HttpConfiguration configuration = new HttpConfiguration();
                configuration.addCustomizer(new SecureRequestCustomizer());
                HttpConnectionFactory http = new HttpConnectionFactory(configuration);
                SslConnectionFactory ssl = new SslConnectionFactory(sslContextFactory, http.getProtocol());
                result.add(ssl);
                result.add(http);
                break;
            }
            case H2C:
            {
                result.add(new HTTP2CServerConnectionFactory(new HttpConfiguration()));
                break;
            }
            case H2:
            {
                HttpConfiguration configuration = new HttpConfiguration();
                configuration.addCustomizer(new SecureRequestCustomizer());
                HTTP2ServerConnectionFactory h2 = new HTTP2ServerConnectionFactory(configuration);
                ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory("h2");
                SslConnectionFactory ssl = new SslConnectionFactory(sslContextFactory, alpn.getProtocol());
                result.add(ssl);
                result.add(alpn);
                result.add(h2);
                break;
            }
            case FCGI:
            {
                result.add(new ServerFCGIConnectionFactory(new HttpConfiguration()));
                break;
            }
            default:
            {
                throw new IllegalArgumentException();
            }
        }
        return result.toArray(new ConnectionFactory[result.size()]);
    }

    private HttpClientTransport provideClientTransport(Transport transport)
    {
        switch (transport)
        {
            case HTTP:
            case HTTPS:
            {
                return new HttpClientTransportOverHTTP(1);
            }
            case H2C:
            case H2:
            {
                HTTP2Client http2Client = newHTTP2Client();
                return new HttpClientTransportOverHTTP2(http2Client);
            }
            case FCGI:
            {
                return new HttpClientTransportOverFCGI(1, false, "");
            }
            default:
            {
                throw new IllegalArgumentException();
            }
        }
    }

    protected HttpClient newHttpClient(HttpClientTransport transport, SslContextFactory sslContextFactory)
    {
        return new HttpClient(transport, sslContextFactory);
    }

    protected HTTP2Client newHTTP2Client()
    {
        HTTP2Client http2Client = new HTTP2Client();
        http2Client.setSelectors(1);
        return http2Client;
    }

    protected String newURI()
    {
        switch (transport)
        {
            case HTTP:
            case H2C:
            case FCGI:
                return "http://localhost:" + connector.getLocalPort();
            case HTTPS:
            case H2:
                return "https://localhost:" + connector.getLocalPort();
            default:
                throw new IllegalArgumentException();
        }
    }

    @After
    public void stop() throws Exception
    {
        if (client != null)
            client.stop();
        if (server != null)
            server.stop();
    }

    protected enum Transport
    {
        HTTP, HTTPS, H2C, H2, FCGI
    }
}
