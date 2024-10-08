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

package org.eclipse.jetty.start.config;

import static org.eclipse.jetty.start.UsageException.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.start.FS;
import org.eclipse.jetty.start.Props;
import org.eclipse.jetty.start.Props.Prop;
import org.eclipse.jetty.start.UsageException;

/**
 * Weighted List of ConfigSources.
 * <p>
 */
public class ConfigSources implements Iterable<ConfigSource>
{
    private static class WeightedConfigSourceComparator implements Comparator<ConfigSource>
    {
        @Override
        public int compare(ConfigSource o1, ConfigSource o2)
        {
            return o1.getWeight() - o2.getWeight();
        }
    }

    private LinkedList<ConfigSource> sources = new LinkedList<>();
    private Props props = new Props();
    private AtomicInteger xtraSourceWeight = new AtomicInteger(1);

    public void add(ConfigSource source) throws IOException
    {
        if (sources.contains(source))
        {
            // TODO: needs a better/more clear error message
            throw new UsageException(ERR_BAD_ARG,"Duplicate Configuration Source Reference: " + source);
        }
        sources.add(source);

        Collections.sort(sources,new WeightedConfigSourceComparator());

        updateProps();

        // look for --extra-start-dir entries
        for (String arg : source.getArgs())
        {
            if (arg.startsWith("--extra-start-dir"))
            {
                String ref = getValue(arg);
                String dirName = props.expand(ref);
                Path dir = FS.toPath(dirName);
                DirConfigSource dirsource = new DirConfigSource(ref,dir,xtraSourceWeight.incrementAndGet(),true);
                add(dirsource);
            }
        }
    }

    public Prop getProp(String key)
    {
        return props.getProp(key);
    }

    public Props getProps()
    {
        return props;
    }

    private String getValue(String arg)
    {
        int idx = arg.indexOf('=');
        if (idx == (-1))
        {
            throw new UsageException(ERR_BAD_ARG,"Argument is missing a required value: %s",arg);
        }
        String value = arg.substring(idx + 1).trim();
        if (value.length() <= 0)
        {
            throw new UsageException(ERR_BAD_ARG,"Argument is missing a required value: %s",arg);
        }
        return value;
    }

    @Override
    public Iterator<ConfigSource> iterator()
    {
        return sources.iterator();
    }

    private void updateProps()
    {
        props.reset();

        // add all properties from config sources (in reverse order)
        ListIterator<ConfigSource> iter = sources.listIterator(sources.size());
        while (iter.hasPrevious())
        {
            ConfigSource source = iter.previous();
            props.addAll(source.getProps());
        }
    }
}
