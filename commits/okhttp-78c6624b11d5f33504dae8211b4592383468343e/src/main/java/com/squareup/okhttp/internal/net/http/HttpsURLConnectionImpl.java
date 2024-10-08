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
package com.squareup.okhttp.internal.net.http;

import com.squareup.okhttp.Connection;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.TunnelRequest;
import com.squareup.okhttp.internal.util.Libcore;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CacheResponse;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ResponseCache;
import java.net.SecureCacheResponse;
import java.net.URL;
import java.security.Permission;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class HttpsURLConnectionImpl extends HttpsURLConnection {

    /** HttpUrlConnectionDelegate allows reuse of HttpURLConnectionImpl. */
    private final HttpUrlConnectionDelegate delegate;

    public HttpsURLConnectionImpl(URL url, int defaultPort, Proxy proxy,
            ProxySelector proxySelector, CookieHandler cookieHandler, ResponseCache responseCache,
            ConnectionPool connectionPool) {
        super(url);
        delegate = new HttpUrlConnectionDelegate(url, defaultPort, proxy, proxySelector,
                cookieHandler, responseCache, connectionPool);
    }

    private void checkConnected() {
        if (delegate.getSSLSocket() == null) {
            throw new IllegalStateException("Connection has not yet been established");
        }
    }

    HttpEngine getHttpEngine() {
        return delegate.getHttpEngine();
    }

    @Override
    public String getCipherSuite() {
        SecureCacheResponse cacheResponse = delegate.getCacheResponse();
        if (cacheResponse != null) {
            return cacheResponse.getCipherSuite();
        }
        checkConnected();
        return delegate.getSSLSocket().getSession().getCipherSuite();
    }

    @Override
    public Certificate[] getLocalCertificates() {
        SecureCacheResponse cacheResponse = delegate.getCacheResponse();
        if (cacheResponse != null) {
            List<Certificate> result = cacheResponse.getLocalCertificateChain();
            return result != null ? result.toArray(new Certificate[result.size()]) : null;
        }
        checkConnected();
        return delegate.getSSLSocket().getSession().getLocalCertificates();
    }

    @Override
    public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
        SecureCacheResponse cacheResponse = delegate.getCacheResponse();
        if (cacheResponse != null) {
            List<Certificate> result = cacheResponse.getServerCertificateChain();
            return result != null ? result.toArray(new Certificate[result.size()]) : null;
        }
        checkConnected();
        return delegate.getSSLSocket().getSession().getPeerCertificates();
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        SecureCacheResponse cacheResponse = delegate.getCacheResponse();
        if (cacheResponse != null) {
            return cacheResponse.getPeerPrincipal();
        }
        checkConnected();
        return delegate.getSSLSocket().getSession().getPeerPrincipal();
    }

    @Override
    public Principal getLocalPrincipal() {
        SecureCacheResponse cacheResponse = delegate.getCacheResponse();
        if (cacheResponse != null) {
            return cacheResponse.getLocalPrincipal();
        }
        checkConnected();
        return delegate.getSSLSocket().getSession().getLocalPrincipal();
    }

    @Override
    public void disconnect() {
        delegate.disconnect();
    }

    @Override
    public InputStream getErrorStream() {
        return delegate.getErrorStream();
    }

    @Override
    public String getRequestMethod() {
        return delegate.getRequestMethod();
    }

    @Override
    public int getResponseCode() throws IOException {
        return delegate.getResponseCode();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return delegate.getResponseMessage();
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        delegate.setRequestMethod(method);
    }

    @Override
    public boolean usingProxy() {
        return delegate.usingProxy();
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return delegate.getInstanceFollowRedirects();
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        delegate.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public void connect() throws IOException {
        connected = true;
        delegate.connect();
    }

    @Override
    public boolean getAllowUserInteraction() {
        return delegate.getAllowUserInteraction();
    }

    @Override
    public Object getContent() throws IOException {
        return delegate.getContent();
    }

    @SuppressWarnings("unchecked") // Spec does not generify
    @Override
    public Object getContent(Class[] types) throws IOException {
        return delegate.getContent(types);
    }

    @Override
    public String getContentEncoding() {
        return delegate.getContentEncoding();
    }

    @Override
    public int getContentLength() {
        return delegate.getContentLength();
    }

    @Override
    public String getContentType() {
        return delegate.getContentType();
    }

    @Override
    public long getDate() {
        return delegate.getDate();
    }

    @Override
    public boolean getDefaultUseCaches() {
        return delegate.getDefaultUseCaches();
    }

    @Override
    public boolean getDoInput() {
        return delegate.getDoInput();
    }

    @Override
    public boolean getDoOutput() {
        return delegate.getDoOutput();
    }

    @Override
    public long getExpiration() {
        return delegate.getExpiration();
    }

    @Override
    public String getHeaderField(int pos) {
        return delegate.getHeaderField(pos);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return delegate.getHeaderFields();
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return delegate.getRequestProperties();
    }

    @Override
    public void addRequestProperty(String field, String newValue) {
        delegate.addRequestProperty(field, newValue);
    }

    @Override
    public String getHeaderField(String key) {
        return delegate.getHeaderField(key);
    }

    @Override
    public long getHeaderFieldDate(String field, long defaultValue) {
        return delegate.getHeaderFieldDate(field, defaultValue);
    }

    @Override
    public int getHeaderFieldInt(String field, int defaultValue) {
        return delegate.getHeaderFieldInt(field, defaultValue);
    }

    @Override
    public String getHeaderFieldKey(int posn) {
        return delegate.getHeaderFieldKey(posn);
    }

    @Override
    public long getIfModifiedSince() {
        return delegate.getIfModifiedSince();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return delegate.getInputStream();
    }

    @Override
    public long getLastModified() {
        return delegate.getLastModified();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }

    @Override
    public Permission getPermission() throws IOException {
        return delegate.getPermission();
    }

    @Override
    public String getRequestProperty(String field) {
        return delegate.getRequestProperty(field);
    }

    @Override
    public URL getURL() {
        return delegate.getURL();
    }

    @Override
    public boolean getUseCaches() {
        return delegate.getUseCaches();
    }

    @Override
    public void setAllowUserInteraction(boolean newValue) {
        delegate.setAllowUserInteraction(newValue);
    }

    @Override
    public void setDefaultUseCaches(boolean newValue) {
        delegate.setDefaultUseCaches(newValue);
    }

    @Override
    public void setDoInput(boolean newValue) {
        delegate.setDoInput(newValue);
    }

    @Override
    public void setDoOutput(boolean newValue) {
        delegate.setDoOutput(newValue);
    }

    @Override
    public void setIfModifiedSince(long newValue) {
        delegate.setIfModifiedSince(newValue);
    }

    @Override
    public void setRequestProperty(String field, String newValue) {
        delegate.setRequestProperty(field, newValue);
    }

    @Override
    public void setUseCaches(boolean newValue) {
        delegate.setUseCaches(newValue);
    }

    @Override
    public void setConnectTimeout(int timeoutMillis) {
        delegate.setConnectTimeout(timeoutMillis);
    }

    @Override
    public int getConnectTimeout() {
        return delegate.getConnectTimeout();
    }

    @Override
    public void setReadTimeout(int timeoutMillis) {
        delegate.setReadTimeout(timeoutMillis);
    }

    @Override
    public int getReadTimeout() {
        return delegate.getReadTimeout();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
        delegate.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setChunkedStreamingMode(int chunkLength) {
        delegate.setChunkedStreamingMode(chunkLength);
    }

    private final class HttpUrlConnectionDelegate extends HttpURLConnectionImpl {
        private HttpUrlConnectionDelegate(URL url, int defaultPort, Proxy proxy,
                ProxySelector proxySelector, CookieHandler cookieHandler,
                ResponseCache responseCache, ConnectionPool connectionPool) {
            super(url, defaultPort, proxy, proxySelector, cookieHandler, responseCache,
                    connectionPool);
        }

        @Override protected HttpEngine newHttpEngine(String method, RawHeaders requestHeaders,
                Connection connection, RetryableOutputStream requestBody) throws IOException {
            return new HttpsEngine(this, method, requestHeaders, connection, requestBody,
                    HttpsURLConnectionImpl.this);
        }

        public SecureCacheResponse getCacheResponse() {
            HttpsEngine engine = (HttpsEngine) httpEngine;
            return engine != null ? (SecureCacheResponse) engine.getCacheResponse() : null;
        }

        public SSLSocket getSSLSocket() {
            HttpsEngine engine = (HttpsEngine) httpEngine;
            return engine != null ? engine.sslSocket : null;
        }
    }

    private static final class HttpsEngine extends HttpEngine {
        /**
         * Stash of HttpsEngine.connection.socket to implement requests like
         * {@link #getCipherSuite} even after the connection has been recycled.
         */
        private SSLSocket sslSocket;

        private final HttpsURLConnectionImpl enclosing;

        /**
         * @param policy the HttpURLConnectionImpl with connection configuration
         * @param enclosing the HttpsURLConnection with HTTPS features
         */
        private HttpsEngine(HttpURLConnectionImpl policy, String method, RawHeaders requestHeaders,
                Connection connection, RetryableOutputStream requestBody,
                HttpsURLConnectionImpl enclosing) throws IOException {
            super(policy, method, requestHeaders, connection, requestBody);
            this.sslSocket = connection != null ? (SSLSocket) connection.getSocket() : null;
            this.enclosing = enclosing;
        }

        @Override protected void connected(Connection connection) {
            this.sslSocket = (SSLSocket) connection.getSocket();
        }

        @Override protected boolean acceptCacheResponseType(CacheResponse cacheResponse) {
            return cacheResponse instanceof SecureCacheResponse;
        }

        @Override protected boolean includeAuthorityInRequestLine() {
            // Even if there is a proxy, it isn't involved. Always request just the file.
            return false;
        }

        @Override protected SSLSocketFactory getSslSocketFactory() {
            return enclosing.getSSLSocketFactory();
        }

        @Override protected HostnameVerifier getHostnameVerifier() {
            return enclosing.getHostnameVerifier();
        }

        @Override protected HttpURLConnection getHttpConnectionToCache() {
            return enclosing;
        }

        @Override protected TunnelRequest getTunnelConfig() {
            String userAgent = requestHeaders.getUserAgent();
            if (userAgent == null) {
                userAgent = HttpEngine.getDefaultUserAgent();
            }

            URL url = policy.getURL();
            return new TunnelRequest(url.getHost(), Libcore.getEffectivePort(url), userAgent,
                    requestHeaders.getProxyAuthorization());
        }
    }
}
