/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.squareup.okhttp;

import com.squareup.okhttp.internal.Platform;
import com.squareup.okhttp.internal.http.HttpAuthenticator;
import com.squareup.okhttp.internal.http.HttpEngine;
import com.squareup.okhttp.internal.http.HttpTransport;
import com.squareup.okhttp.internal.http.RawHeaders;
import com.squareup.okhttp.internal.http.SpdyTransport;
import com.squareup.okhttp.internal.spdy.SpdyConnection;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PROXY_AUTH;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import javax.net.ssl.SSLSocket;

/**
 * Holds the sockets and streams of an HTTP, HTTPS, or HTTPS+SPDY connection,
 * which may be used for multiple HTTP request/response exchanges. Connections
 * may be direct to the origin server or via a proxy.
 *
 * <p>Typically instances of this class are created, connected and exercised
 * automatically by the HTTP client. Applications may use this class to monitor
 * HTTP connections as members of a {@link ConnectionPool connection pool}.
 *
 * <p>Do not confuse this class with the misnamed {@code HttpURLConnection},
 * which isn't so much a connection as a single request/response exchange.
 *
 * <h3>Modern TLS</h3>
 * There are tradeoffs when selecting which options to include when negotiating
 * a secure connection to a remote host. Newer TLS options are quite useful:
 * <ul>
 *   <li>Server Name Indication (SNI) enables one IP address to negotiate secure
 *       connections for multiple domain names.
 *   <li>Next Protocol Negotiation (NPN) enables the HTTPS port (443) to be used
 *       for both HTTP and SPDY transports.
 * </ul>
 * Unfortunately, older HTTPS servers refuse to connect when such options are
 * presented. Rather than avoiding these options entirely, this class allows a
 * connection to be attempted with modern options and then retried without them
 * should the attempt fail.
 */
public final class Connection implements Closeable {
    private static final byte[] NPN_PROTOCOLS = new byte[] {
            6, 's', 'p', 'd', 'y', '/', '3',
            8, 'h', 't', 't', 'p', '/', '1', '.', '1',
    };
    private static final byte[] SPDY3 = new byte[] {
            's', 'p', 'd', 'y', '/', '3',
    };
    private static final byte[] HTTP_11 = new byte[] {
            'h', 't', 't', 'p', '/', '1', '.', '1',
    };

    private final Address address;
    private final Proxy proxy;
    private final InetSocketAddress inetSocketAddress;
    private final boolean modernTls;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private boolean recycled = false;
    private SpdyConnection spdyConnection;
    private int httpMinorVersion = 1; // Assume HTTP/1.1

    public Connection(Address address, Proxy proxy, InetSocketAddress inetSocketAddress,
            boolean modernTls) {
        if (address == null) throw new NullPointerException("address == null");
        if (proxy == null) throw new NullPointerException("proxy == null");
        if (inetSocketAddress == null) throw new NullPointerException("inetSocketAddress == null");
        this.address = address;
        this.proxy = proxy;
        this.inetSocketAddress = inetSocketAddress;
        this.modernTls = modernTls;
    }

    public void connect(int connectTimeout, int readTimeout, TunnelRequest tunnelRequest)
            throws IOException {
        socket = (proxy.type() != Proxy.Type.HTTP)
                ? new Socket(proxy)
                : new Socket();
        socket.connect(inetSocketAddress, connectTimeout);
        socket.setSoTimeout(readTimeout);
        in = socket.getInputStream();
        out = socket.getOutputStream();

        if (address.sslSocketFactory != null) {
            upgradeToTls(tunnelRequest);
        }

        // Buffer the socket stream to permit efficient parsing of HTTP headers and chunk sizes.
        if (!isSpdy()) {
            int bufferSize = 128;
            in = new BufferedInputStream(in, bufferSize);
        }
    }

    /**
     * Create an {@code SSLSocket} and perform the TLS handshake and certificate
     * validation.
     */
    private void upgradeToTls(TunnelRequest tunnelRequest) throws IOException {
        Platform platform = Platform.get();

        // Make an SSL Tunnel on the first message pair of each SSL + proxy connection.
        if (requiresTunnel()) {
            makeTunnel(tunnelRequest);
        }

        // Create the wrapper over connected socket.
        socket = address.sslSocketFactory.createSocket(
                socket, address.uriHost, address.uriPort, true /* autoClose */);
        SSLSocket sslSocket = (SSLSocket) socket;
        if (modernTls) {
            platform.enableTlsExtensions(sslSocket, address.uriHost);
        } else {
            platform.supportTlsIntolerantServer(sslSocket);
        }

        if (modernTls) {
            platform.setNpnProtocols(sslSocket, NPN_PROTOCOLS);
        }

        // Force handshake. This can throw!
        sslSocket.startHandshake();

        // Verify that the socket's certificates are acceptable for the target host.
        if (!address.hostnameVerifier.verify(address.uriHost, sslSocket.getSession())) {
            throw new IOException("Hostname '" + address.uriHost + "' was not verified");
        }

        out = sslSocket.getOutputStream();
        in = sslSocket.getInputStream();

        byte[] selectedProtocol;
        if (modernTls
                && (selectedProtocol = platform.getNpnSelectedProtocol(sslSocket)) != null) {
            if (Arrays.equals(selectedProtocol, SPDY3)) {
                sslSocket.setSoTimeout(0); // SPDY timeouts are set per-stream.
                spdyConnection = new SpdyConnection.Builder(true, in, out).build();
            } else if (!Arrays.equals(selectedProtocol, HTTP_11)) {
                throw new IOException("Unexpected NPN transport "
                        + new String(selectedProtocol, "ISO-8859-1"));
            }
        }
    }

    @Override public void close() throws IOException {
        socket.close();
    }

    /**
     * Returns the proxy that this connection is using.
     *
     * <strong>Warning:</strong> This may be different than the proxy returned
     * by {@link #getAddress}! That is the proxy that the user asked to be
     * connected to; this returns the proxy that they were actually connected
     * to. The two may disagree when a proxy selector selects a different proxy
     * for a connection.
     */
    public Proxy getProxy() {
        return proxy;
    }

    public Address getAddress() {
        return address;
    }

    public InetSocketAddress getSocketAddress() {
        return inetSocketAddress;
    }

    public boolean isModernTls() {
        return modernTls;
    }

    /**
     * Returns the socket that this connection uses, or null if the connection
     * is not currently connected.
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Returns true if this connection has been used to satisfy an earlier
     * HTTP request/response pair.
     *
     * <p>The HTTP client treats recycled and non-recycled connections
     * differently. I/O failures on recycled connections are often temporary:
     * the remote peer may have closed the socket because it was idle for an
     * extended period of time. When fresh connections suffer similar failures
     * the problem is fatal and the request is not retried.
     */
    public boolean isRecycled() {
        return recycled;
    }

    public void setRecycled() {
        this.recycled = true;
    }

    /**
     * Returns true if this connection is eligible to be reused for another
     * request/response pair.
     */
    protected boolean isEligibleForRecycling() {
        return !socket.isClosed() && !socket.isInputShutdown() && !socket.isOutputShutdown();
    }

    /**
     * Returns the transport appropriate for this connection.
     */
    public Object newTransport(HttpEngine httpEngine) throws IOException {
        return (spdyConnection != null)
                ? new SpdyTransport(httpEngine, spdyConnection)
                : new HttpTransport(httpEngine, out, in);
    }

    /**
     * Returns true if this is a SPDY connection. Such connections can be used
     * in multiple HTTP requests simultaneously.
     */
    public boolean isSpdy() {
        return spdyConnection != null;
    }

    /**
     * Returns the minor HTTP version that should be used for future requests on
     * this connection. Either 0 for HTTP/1.0, or 1 for HTTP/1.1. The default
     * value is 1 for new connections.
     */
    public int getHttpMinorVersion() {
        return httpMinorVersion;
    }

    public void setHttpMinorVersion(int httpMinorVersion) {
        this.httpMinorVersion = httpMinorVersion;
    }

    /**
     * Returns true if the HTTP connection needs to tunnel one protocol over
     * another, such as when using HTTPS through an HTTP proxy. When doing so,
     * we must avoid buffering bytes intended for the higher-level protocol.
     */
    public boolean requiresTunnel() {
        return address.sslSocketFactory != null && proxy != null && proxy.type() == Proxy.Type.HTTP;
    }

    /**
     * To make an HTTPS connection over an HTTP proxy, send an unencrypted
     * CONNECT request to create the proxy connection. This may need to be
     * retried if the proxy requires authorization.
     */
    private void makeTunnel(TunnelRequest tunnelRequest) throws IOException {
        RawHeaders requestHeaders = tunnelRequest.getRequestHeaders();
        while (true) {
            out.write(requestHeaders.toBytes());
            RawHeaders responseHeaders = RawHeaders.fromBytes(in);

            switch (responseHeaders.getResponseCode()) {
            case HTTP_OK:
                return;
            case HTTP_PROXY_AUTH:
                requestHeaders = new RawHeaders(requestHeaders);
                URL url = new URL("https", tunnelRequest.host, tunnelRequest.port, "/");
                boolean credentialsFound = HttpAuthenticator.processAuthHeader(HTTP_PROXY_AUTH,
                        responseHeaders, requestHeaders, proxy, url);
                if (credentialsFound) {
                    continue;
                } else {
                    throw new IOException("Failed to authenticate with proxy");
                }
            default:
                throw new IOException("Unexpected response code for CONNECT: "
                        + responseHeaders.getResponseCode());
            }
        }
    }
}
