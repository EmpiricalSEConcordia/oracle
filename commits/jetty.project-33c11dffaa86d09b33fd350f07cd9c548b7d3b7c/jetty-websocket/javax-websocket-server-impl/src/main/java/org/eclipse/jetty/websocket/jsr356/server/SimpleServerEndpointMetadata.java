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

import javax.websocket.Endpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.eclipse.jetty.websocket.jsr356.client.SimpleEndpointMetadata;

public class SimpleServerEndpointMetadata extends SimpleEndpointMetadata implements ServerEndpointMetadata
{
    private final ServerEndpointConfig config;
    
    public SimpleServerEndpointMetadata(Class<? extends Endpoint> endpointClass, ServerEndpointConfig config)
    {
        super(endpointClass);
        this.config = config;
    }

    @Override
    public ServerEndpointConfig getConfig()
    {
        return config;
    }

    @Override
    public String getPath()
    {
        return config.getPath();
    }
}
