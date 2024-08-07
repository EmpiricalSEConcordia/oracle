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

package org.eclipse.jetty.websocket.jsr356.annotations;

import java.lang.reflect.Method;
import java.util.Map;

import javax.websocket.OnError;
import javax.websocket.Session;

/**
 * Callable for {@link OnError} annotated methods
 */
public class OnErrorCallable extends JsrCallable
{
    private int idxThrowable = -1;

    public OnErrorCallable(Class<?> pojo, Method method)
    {
        super(pojo,method);
    }

    public void call(Object endpoint, Throwable cause)
    {
        // Throwable is a mandatory parameter
        super.args[idxThrowable] = cause;
        super.call(endpoint,super.args);
    }

    public OnErrorCallable copy()
    {
        OnErrorCallable copy = new OnErrorCallable(pojo,method);
        super.copyTo(copy);
        copy.idxThrowable = this.idxThrowable;
        return copy;
    }

    @Override
    public void init(Session session, Map<String, String> pathParams)
    {
        idxThrowable = findIndexForRole(Param.Role.ERROR_CAUSE);
        super.init(session,pathParams);
    }
}
