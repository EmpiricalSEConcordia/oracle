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

package org.eclipse.jetty.http2;

import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.Frame;
import org.eclipse.jetty.http2.frames.WindowUpdateFrame;
import org.eclipse.jetty.util.Callback;

public interface ISession extends Session
{
    @Override
    public IStream getStream(int streamId);

    public void control(IStream stream, Callback callback, Frame frame, Frame... frames);

    public void data(IStream stream, Callback callback, DataFrame frame);

    public int updateSendWindow(int delta);

    public int updateRecvWindow(int delta);

    public void onWindowUpdate(IStream stream, WindowUpdateFrame frame);

    public void shutdown();

    public void disconnect();
}
