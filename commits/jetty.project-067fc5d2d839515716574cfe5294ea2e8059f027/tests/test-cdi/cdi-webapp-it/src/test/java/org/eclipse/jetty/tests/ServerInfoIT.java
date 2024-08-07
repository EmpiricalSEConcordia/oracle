//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.tests;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.net.URI;

import org.eclipse.jetty.toolchain.test.SimpleRequest;
import org.junit.Test;

public class ServerInfoIT
{
    @Test
    public void testGET() throws Exception {
        URI serverURI = new URI("http://localhost:58080/cdi-webapp/");
        SimpleRequest req = new SimpleRequest(serverURI);
        assertThat(req.getString("serverinfo"),is("Hello World"));
    }
}
