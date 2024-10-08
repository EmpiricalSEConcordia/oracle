// ========================================================================
// Copyright (c) 2004-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.eclipse.jetty.io;

import java.nio.ByteBuffer;
import java.text.DateFormatSymbols;
import java.util.Locale;

import org.eclipse.jetty.util.DateCache;

public class BufferDateCache extends DateCache
{
    ByteBuffer _buffer;
    String _last;
    
    public BufferDateCache()
    {
        super();
    }

    public BufferDateCache(String format, DateFormatSymbols s)
    {
        super(format,s);
    }

    public BufferDateCache(String format, Locale l)
    {
        super(format,l);
    }

    public BufferDateCache(String format)
    {
        super(format);
    }

    public synchronized ByteBuffer formatBuffer(long date)
    {
        String d = super.format(date);
        if (d==_last)
            return _buffer;
        _last=d;
        _buffer=BufferUtil.toBuffer(d);
        
        return _buffer;
    }
}
