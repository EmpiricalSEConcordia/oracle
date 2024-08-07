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

package org.eclipse.jetty.websocket.jsr356.endpoints.samples;

import javax.websocket.PongMessage;
import javax.websocket.WebSocketClient;
import javax.websocket.WebSocketMessage;

import org.eclipse.jetty.websocket.jsr356.endpoints.TrackingSocket;

@WebSocketClient
public class BasicPongMessageSocket extends TrackingSocket
{
    @WebSocketMessage
    public void onPong(PongMessage pong)
    {
        addEvent("onPong(%s)",pong);
        dataLatch.countDown();
    }
}
