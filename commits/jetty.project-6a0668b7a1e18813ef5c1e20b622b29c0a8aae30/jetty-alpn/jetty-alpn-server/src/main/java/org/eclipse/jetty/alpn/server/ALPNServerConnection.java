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

package org.eclipse.jetty.alpn.server;

import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.alpn.ALPN;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NegotiatingServerConnection;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ALPNServerConnection extends NegotiatingServerConnection implements ALPN.ServerProvider
{
    private static final Logger LOG = Log.getLogger(ALPNServerConnection.class);

    public ALPNServerConnection(Connector connector, EndPoint endPoint, SSLEngine engine, List<String> protocols, String defaultProtocol)
    {
        super(connector, endPoint, engine, protocols, defaultProtocol);
        ALPN.put(engine, this);
    }

    @Override
    public void unsupported()
    {
        select(Collections.<String>emptyList());
    }

    @Override
    public String select(List<String> clientProtocols)
    {
        List<String> serverProtocols = getProtocols();
        String negotiated = null;
        for (String clientProtocol : clientProtocols)
        {
            if (serverProtocols.contains(clientProtocol))
            {
                negotiated = clientProtocol;
                break;
            }
        }
        if (negotiated == null)
        {
            negotiated = getDefaultProtocol();
        }
        if (LOG.isDebugEnabled())
            LOG.debug("{} protocol selected {}", this, negotiated);
        setProtocol(negotiated);
        ALPN.remove(getSSLEngine());
        return negotiated;
    }

    @Override
    public void close()
    {
        ALPN.remove(getSSLEngine());
        super.close();
    }
}
