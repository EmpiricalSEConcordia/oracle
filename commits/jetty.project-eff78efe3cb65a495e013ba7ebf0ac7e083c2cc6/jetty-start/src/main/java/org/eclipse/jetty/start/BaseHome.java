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

package org.eclipse.jetty.start;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jetty.start.config.CommandLineConfigSource;
import org.eclipse.jetty.start.config.ConfigSource;
import org.eclipse.jetty.start.config.ConfigSources;
import org.eclipse.jetty.start.config.DirConfigSource;

/**
 * File access for <code>${jetty.home}</code>, <code>${jetty.base}</code>, directories.
 * <p>
 * By default, both <code>${jetty.home}</code> and <code>${jetty.base}</code> are the same directory, but they can point at different directories.
 * <p>
 * The <code>${jetty.home}</code> directory is where the main Jetty binaries and default configuration is housed.
 * <p>
 * The <code>${jetty.base}</code> directory is where the execution specific configuration and webapps are obtained from.
 */
public class BaseHome
{
    public static class SearchDir
    {
        private Path dir;
        private String name;

        public SearchDir(String name)
        {
            this.name = name;
        }

        public Path getDir()
        {
            return dir;
        }

        public Path resolve(Path subpath)
        {
            return dir.resolve(subpath);
        }

        public Path resolve(String subpath)
        {
            return dir.resolve(FS.separators(subpath));
        }

        public SearchDir setDir(File path)
        {
            if (path != null)
            {
                return setDir(path.toPath());
            }
            return this;
        }

        public SearchDir setDir(Path path)
        {
            if (path != null)
            {
                this.dir = path.toAbsolutePath();
            }
            return this;
        }

        public SearchDir setDir(String path)
        {
            if (path != null)
            {
                return setDir(FS.toPath(path));
            }
            return this;
        }

        public String toShortForm(Path path)
        {
            Path relative = dir.relativize(path);
            return String.format("${%s}%c%s",name,File.separatorChar,relative.toString());
        }
    }
    private static final String JETTY_BASE = "jetty.base";

    private static final String JETTY_HOME = "jetty.home";;
    private final static EnumSet<FileVisitOption> SEARCH_VISIT_OPTIONS = EnumSet.of(FileVisitOption.FOLLOW_LINKS);

    private final static int MAX_SEARCH_DEPTH = Integer.getInteger("org.eclipse.jetty.start.searchDepth",10);

    private Path homeDir;
    private ConfigSources sources;
    private Path baseDir;

    public BaseHome()
    {
        try
        {
            // find ${jetty.base} and ${jetty.home} from environment.

            // overrides from command line (and the like) come later.
            // in the .initialize() step

            // default is ${user.dir}
            this.baseDir = FS.toPath(System.getProperty("user.dir","."));

            // if ${jetty.base} declared, use it
            String jettyBase = System.getProperty(JETTY_BASE);
            if (jettyBase != null)
            {
                this.baseDir = FS.toPath(jettyBase);
            }

            // find ${jetty.home}

            // default location is based on lookup for BaseHome (from jetty's start.jar)
            URL jarfile = this.getClass().getClassLoader().getResource("org/eclipse/jetty/start/BaseHome.class");
            if (jarfile != null)
            {
                Matcher m = Pattern.compile("jar:(file:.*)!/org/eclipse/jetty/start/BaseHome.class").matcher(jarfile.toString());
                if (m.matches())
                {
                    // ${jetty.home} is relative to found BaseHome class
                    this.homeDir = new File(new URI(m.group(1))).getParentFile().toPath();
                }
            }

            // if we can't locate BaseHome, then assume home == base
            if (this.homeDir == null)
            {
                this.homeDir = baseDir.toAbsolutePath();
            }

            // if ${jetty.home} declared, use it
            String jettyHome = System.getProperty(JETTY_HOME);
            if (jettyHome != null)
            {
                this.homeDir = FS.toPath(jettyHome);
            }

            // Resolve to absolute paths
            this.homeDir = this.homeDir.toAbsolutePath();
            this.baseDir = this.baseDir.toAbsolutePath();
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    public BaseHome(File homeDir, File baseDir)
    {
        Objects.requireNonNull(homeDir,"Home Dir cannot be null");

        this.homeDir = homeDir.toPath();
        this.baseDir = homeDir.toPath(); // default
        if (baseDir != null)
        {
            this.baseDir = baseDir.toPath();
        }

        // Resolve to absolute paths
        this.homeDir = this.homeDir.toAbsolutePath();
        this.baseDir = this.baseDir.toAbsolutePath();
    }

    public String getBase()
    {
        if (baseDir == null)
        {
            return null;
        }
        return baseDir.toString();
    }

    /**
     * @deprecated use {@link #getBasePath()}
     */
    @Deprecated
    public File getBaseDir()
    {
        return baseDir.toFile();
    }

    /**
     * Create a file reference to some content in <code>"${jetty.base}"</code>
     * 
     * @param path
     *            the path to reference
     * @return the file reference
     * @deprecated use {@link #getBasePath(String)}
     */
    @Deprecated
    public File getBaseFile(String path)
    {
        return baseDir.resolve(path).toFile();
    }

    public Path getBasePath()
    {
        return baseDir;
    }

    public Path getBasePath(String path)
    {
        return baseDir.resolve(path);
    }

    public ConfigSources getConfigSources()
    {
        return this.sources;
    }

    /**
     * Get a specific file reference.
     * <p>
     * File references go through 3 possibly scenarios.
     * <ol>
     * <li>If exists relative to <code>${jetty.base}</code>, return that reference</li>
     * <li>If exists relative to <code>${jetty.home}</code>, return that reference</li>
     * <li>Otherwise return absolute path reference (standard java logic)</li>
     * </ol>
     * 
     * @param path
     *            the path to get.
     * @return the file reference.
     * @deprecated use {@link #getPath(String)}
     */
    @Deprecated
    public File getFile(String path)
    {
        return getPath(path).toAbsolutePath().toFile();
    }

    public String getHome()
    {
        return homeDir.toString();
    }

    /**
     * @deprecated use {@link #getHomePath()}
     */
    @Deprecated
    public File getHomeDir()
    {
        return homeDir.toFile();
    }

    public Path getHomePath()
    {
        return homeDir;
    }

    /**
     * Get a specific path reference.
     * <p>
     * Path references are searched based on the config source search order.
     * <ol>
     * <li>If provided path is an absolute reference., and exists, return that reference</li>
     * <li>If exists relative to <code>${jetty.base}</code>, return that reference</li>
     * <li>If exists relative to and <code>extra-start-dir</code> locations, return that reference</li>
     * <li>If exists relative to <code>${jetty.home}</code>, return that reference</li>
     * <li>Return standard {@link Path} reference obtained from {@link java.nio.file.FileSystem#getPath(String, String...)} (no exists check performed)</li>
     * </ol>
     * 
     * @param path
     *            the path to get.
     * @return the path reference.
     */
    public Path getPath(final String path)
    {
        Path apath = FS.toPath(path);

        if (apath.isAbsolute())
        {
            if (FS.exists(apath))
            {
                return apath;
            }
        }

        for (ConfigSource source : sources)
        {
            if (source instanceof DirConfigSource)
            {
                DirConfigSource dirsource = (DirConfigSource)source;
                Path file = dirsource.getDir().resolve(apath);
                if (FS.exists(file))
                {
                    return file;
                }
            }
        }

        // Finally, as an anonymous path
        return FS.toPath(path);
    }

    /**
     * Search specified Path with pattern and return hits
     * 
     * @param dir
     *            the path to a directory to start search from
     * @param searchDepth
     *            the number of directories deep to perform the search
     * @param pattern
     *            the raw pattern to use for the search (must be relative)
     * @return the list of Paths found
     * @throws IOException
     *             if unable to search the path
     */
    public List<Path> getPaths(Path dir, int searchDepth, String pattern) throws IOException
    {
        if (PathMatchers.isAbsolute(pattern))
        {
            throw new RuntimeException("Pattern cannot be absolute: " + pattern);
        }

        List<Path> hits = new ArrayList<>();
        if (FS.isValidDirectory(dir))
        {
            PathMatcher matcher = PathMatchers.getMatcher(pattern);
            PathFinder finder = new PathFinder();
            finder.setFileMatcher(matcher);
            finder.setBase(dir);
            Files.walkFileTree(dir,SEARCH_VISIT_OPTIONS,searchDepth,finder);
            hits.addAll(finder.getHits());
            Collections.sort(hits,new NaturalSort.Paths());
        }
        return hits;
    }

    /**
     * Get a List of {@link Path}s from a provided pattern.
     * <p>
     * Resolution Steps:
     * <ol>
     * <li>If the pattern starts with "regex:" or "glob:" then a standard {@link PathMatcher} is built using
     * {@link java.nio.file.FileSystem#getPathMatcher(String)} as a file search.</li>
     * <li>If pattern starts with a known filesystem root (using information from {@link java.nio.file.FileSystem#getRootDirectories()}) then this is assumed to
     * be a absolute file system pattern.</li>
     * <li>All other patterns are treated as relative to BaseHome information:
     * <ol>
     * <li>Search ${jetty.home} first</li>
     * <li>Search ${jetty.base} for overrides</li>
     * </ol>
     * </li>
     * </ol>
     * <p>
     * Pattern examples:
     * <dl>
     * <dt><code>lib/logging/*.jar</code></dt>
     * <dd>Relative pattern, not recursive, search <code>${jetty.home}</code> then <code>${jetty.base}</code> for lib/logging/*.jar content</dd>
     * 
     * <dt><code>lib/**&#47;*-dev.jar</code></dt>
     * <dd>Relative pattern, recursive search <code>${jetty.home}</code> then <code>${jetty.base}</code> for files under <code>lib</code> ending in
     * <code>-dev.jar</code></dd>
     * </dl>
     * 
     * <dt><code>etc/jetty.xml</code></dt>
     * <dd>Relative pattern, no glob, search for <code>${jetty.home}/etc/jetty.xml</code> then <code>${jetty.base}/etc/jetty.xml</code></dd>
     * 
     * <dt><code>glob:/opt/app/common/*-corp.jar</code></dt>
     * <dd>PathMapper pattern, glob, search <code>/opt/app/common/</code> for <code>*-corp.jar</code></code></dd>
     * 
     * </dl>
     * 
     * <p>
     * Notes:
     * <ul>
     * <li>FileSystem case sensitivity is implementation specific (eg: linux is case-sensitive, windows is case-insensitive).<br/>
     * See {@link java.nio.file.FileSystem#getPathMatcher(String)} for more details</li>
     * <li>Pattern slashes are implementation neutral (use '/' always and you'll be fine)</li>
     * <li>Recursive searching is limited to 30 levels deep (not configurable)</li>
     * <li>File System loops are detected and skipped</li>
     * </ul>
     * 
     * @param pattern
     *            the pattern to search.
     * @return the collection of paths found
     * @throws IOException
     *             if error during search operation
     */
    public List<Path> getPaths(String pattern) throws IOException
    {
        StartLog.debug("getPaths('%s')",pattern);
        List<Path> hits = new ArrayList<>();

        if (PathMatchers.isAbsolute(pattern))
        {
            // Perform absolute path pattern search

            // The root to start search from
            Path root = PathMatchers.getSearchRoot(pattern);
            // The matcher for file hits
            PathMatcher matcher = PathMatchers.getMatcher(pattern);

            if (FS.isValidDirectory(root))
            {
                PathFinder finder = new PathFinder();
                finder.setIncludeDirsInResults(true);
                finder.setFileMatcher(matcher);
                finder.setBase(root);
                Files.walkFileTree(root,SEARCH_VISIT_OPTIONS,MAX_SEARCH_DEPTH,finder);
                hits.addAll(finder.getHits());
            }
        }
        else
        {
            // Perform relative path pattern search
            Path relativePath = PathMatchers.getSearchRoot(pattern);
            PathMatcher matcher = PathMatchers.getMatcher(pattern);
            PathFinder finder = new PathFinder();
            finder.setIncludeDirsInResults(true);
            finder.setFileMatcher(matcher);

            // walk config sources backwards ...
            ListIterator<ConfigSource> iter = sources.reverseListIterator();
            while (iter.hasPrevious())
            {
                ConfigSource source = iter.previous();
                if (source instanceof DirConfigSource)
                {
                    DirConfigSource dirsource = (DirConfigSource)source;
                    Path dir = dirsource.getDir();
                    Path deepDir = dir.resolve(relativePath);
                    if (FS.isValidDirectory(deepDir))
                    {
                        finder.setBase(dir);
                        Files.walkFileTree(deepDir,SEARCH_VISIT_OPTIONS,MAX_SEARCH_DEPTH,finder);
                    }
                }
            }

            hits.addAll(finder.getHits());
        }

        Collections.sort(hits,new NaturalSort.Paths());
        return hits;
    }

    public void initialize(ConfigSources config)
    {
        CommandLineConfigSource cmdLine = config.getCommandLineSource();
        if (cmdLine != null)
        {
            this.homeDir = cmdLine.getHomePath();
            this.baseDir = cmdLine.getBasePath();
        }

        this.sources = config;
    }

    public boolean isBaseDifferent()
    {
        return homeDir.compareTo(baseDir) != 0;
    }

    /**
     * @deprecated use {@link #setBaseDir(Path)}
     */
    @Deprecated
    public void setBaseDir(File dir)
    {
        setBaseDir(dir.toPath());
    }

    public void setBaseDir(Path dir)
    {
        this.baseDir = dir.toAbsolutePath();
        System.setProperty(JETTY_BASE,dir.toString());
    }

    /**
     * @deprecated use {@link #setHomeDir(Path)}
     */
    @Deprecated
    public void setHomeDir(File dir)
    {
        setHomeDir(dir.toPath());
    }

    public void setHomeDir(Path dir)
    {
        this.homeDir = dir.toAbsolutePath();
        System.setProperty(JETTY_HOME,dir.toString());
    }

    /**
     * Convenience method for <code>toShortForm(file.toPath())</code>
     */
    public String toShortForm(final File path)
    {
        return toShortForm(path.toPath());
    }

    /**
     * Replace/Shorten arbitrary path with property strings <code>"${jetty.home}"</code> or <code>"${jetty.base}"</code> where appropriate.
     * 
     * @param path
     *            the path to shorten
     * @return the potentially shortened path
     */
    public String toShortForm(final Path path)
    {
        Path apath = path.toAbsolutePath();

        for (ConfigSource source : sources)
        {
            if (source instanceof DirConfigSource)
            {
                DirConfigSource dirsource = (DirConfigSource)source;
                Path dir = dirsource.getDir();
                if (apath.startsWith(dir))
                {
                    if (dirsource.isPropertyBased())
                    {
                        Path relative = dir.relativize(apath);
                        return String.format("%s%c%s",dirsource.getId(),File.separatorChar,relative.toString());
                    }
                    else
                    {
                        return apath.toString();
                    }
                }
            }
        }

        return apath.toString();
    }

    /**
     * Replace/Shorten arbitrary path with property strings <code>"${jetty.home}"</code> or <code>"${jetty.base}"</code> where appropriate.
     * 
     * @param path
     *            the path to shorten
     * @return the potentially shortened path
     */
    public String toShortForm(final String path)
    {
        if (path == null)
        {
            return path;
        }

        return toShortForm(FS.toPath(path));
    }
}
