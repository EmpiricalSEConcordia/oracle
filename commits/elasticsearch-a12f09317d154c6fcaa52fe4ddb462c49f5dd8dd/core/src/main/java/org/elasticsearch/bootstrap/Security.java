/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.bootstrap;

import org.elasticsearch.SecureSM;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.SuppressForbidden;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.http.HttpTransportSettings;
import org.elasticsearch.plugins.PluginInfo;
import org.elasticsearch.transport.TransportSettings;

import java.io.FilePermission;
import java.io.IOException;
import java.net.SocketPermission;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.Permissions;
import java.security.Policy;
import java.security.URIParameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Initializes SecurityManager with necessary permissions.
 * <br>
 * <h1>Initialization</h1>
 * The JVM is not initially started with security manager enabled,
 * instead we turn it on early in the startup process. This is a tradeoff
 * between security and ease of use:
 * <ul>
 *   <li>Assigns file permissions to user-configurable paths that can
 *       be specified from the command-line or {@code elasticsearch.yml}.</li>
 *   <li>Allows for some contained usage of native code that would not
 *       otherwise be permitted.</li>
 * </ul>
 * <br>
 * <h1>Permissions</h1>
 * Permissions use a policy file packaged as a resource, this file is
 * also used in tests. File permissions are generated dynamically and
 * combined with this policy file.
 * <p>
 * For each configured path, we ensure it exists and is accessible before
 * granting permissions, otherwise directory creation would require
 * permissions to parent directories.
 * <p>
 * In some exceptional cases, permissions are assigned to specific jars only,
 * when they are so dangerous that general code should not be granted the
 * permission, but there are extenuating circumstances.
 * <p>
 * Scripts (groovy, javascript, python) are assigned minimal permissions. This does not provide adequate
 * sandboxing, as these scripts still have access to ES classes, and could
 * modify members, etc that would cause bad things to happen later on their
 * behalf (no package protections are yet in place, this would need some
 * cleanups to the scripting apis). But still it can provide some defense for users
 * that enable dynamic scripting without being fully aware of the consequences.
 * <br>
 * <h1>Disabling Security</h1>
 * SecurityManager can be disabled completely with this setting:
 * <pre>
 * es.security.manager.enabled = false
 * </pre>
 * <br>
 * <h1>Debugging Security</h1>
 * A good place to start when there is a problem is to turn on security debugging:
 * <pre>
 * ES_JAVA_OPTS="-Djava.security.debug=access,failure" bin/elasticsearch
 * </pre>
 * <p>
 * When running tests you have to pass it to the test runner like this:
 * <pre>
 * gradle test -Dtests.jvm.argline="-Djava.security.debug=access,failure" ...
 * </pre>
 * See <a href="https://docs.oracle.com/javase/7/docs/technotes/guides/security/troubleshooting-security.html">
 * Troubleshooting Security</a> for information.
 */
final class Security {
    /** no instantiation */
    private Security() {}

    /**
     * Initializes SecurityManager for the environment
     * Can only happen once!
     * @param environment configuration for generating dynamic permissions
     * @param filterBadDefaults true if we should filter out bad java defaults in the system policy.
     */
    static void configure(Environment environment, boolean filterBadDefaults) throws IOException, NoSuchAlgorithmException {

        // enable security policy: union of template and environment-based paths, and possibly plugin permissions
        Policy.setPolicy(new ESPolicy(createPermissions(environment), getPluginPermissions(environment), filterBadDefaults));

        // enable security manager
        System.setSecurityManager(new SecureSM(new String[] { "org.elasticsearch.bootstrap.", "org.elasticsearch.cli" }));

        // do some basic tests
        selfTest();
    }

    /**
     * Sets properties (codebase URLs) for policy files.
     * we look for matching plugins and set URLs to fit
     */
    @SuppressForbidden(reason = "proper use of URL")
    static Map<String,Policy> getPluginPermissions(Environment environment) throws IOException, NoSuchAlgorithmException {
        Map<String,Policy> map = new HashMap<>();
        // collect up lists of plugins and modules
        List<Path> pluginsAndModules = new ArrayList<>();
        if (Files.exists(environment.pluginsFile())) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(environment.pluginsFile())) {
                for (Path plugin : stream) {
                    pluginsAndModules.add(plugin);
                }
            }
        }
        if (Files.exists(environment.modulesFile())) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(environment.modulesFile())) {
                for (Path plugin : stream) {
                    pluginsAndModules.add(plugin);
                }
            }
        }
        // now process each one
        for (Path plugin : pluginsAndModules) {
            Path policyFile = plugin.resolve(PluginInfo.ES_PLUGIN_POLICY);
            if (Files.exists(policyFile)) {
                // first get a list of URLs for the plugins' jars:
                // we resolve symlinks so map is keyed on the normalize codebase name
                List<URL> codebases = new ArrayList<>();
                try (DirectoryStream<Path> jarStream = Files.newDirectoryStream(plugin, "*.jar")) {
                    for (Path jar : jarStream) {
                        codebases.add(jar.toRealPath().toUri().toURL());
                    }
                }

                // parse the plugin's policy file into a set of permissions
                Policy policy = readPolicy(policyFile.toUri().toURL(), codebases.toArray(new URL[codebases.size()]));

                // consult this policy for each of the plugin's jars:
                for (URL url : codebases) {
                    if (map.put(url.getFile(), policy) != null) {
                        // just be paranoid ok?
                        throw new IllegalStateException("per-plugin permissions already granted for jar file: " + url);
                    }
                }
            }
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * Reads and returns the specified {@code policyFile}.
     * <p>
     * Resources (e.g. jar files and directories) listed in {@code codebases} location
     * will be provided to the policy file via a system property of the short name:
     * e.g. <code>${codebase.joda-convert-1.2.jar}</code> would map to full URL.
     */
    @SuppressForbidden(reason = "accesses fully qualified URLs to configure security")
    static Policy readPolicy(URL policyFile, URL codebases[]) {
        try {
            try {
                // set codebase properties
                for (URL url : codebases) {
                    String shortName = PathUtils.get(url.toURI()).getFileName().toString();
                    System.setProperty("codebase." + shortName, url.toString());
                }
                return Policy.getInstance("JavaPolicy", new URIParameter(policyFile.toURI()));
            } finally {
                // clear codebase properties
                for (URL url : codebases) {
                    String shortName = PathUtils.get(url.toURI()).getFileName().toString();
                    System.clearProperty("codebase." + shortName);
                }
            }
        } catch (NoSuchAlgorithmException | URISyntaxException e) {
            throw new IllegalArgumentException("unable to parse policy file `" + policyFile + "`", e);
        }
    }

    /** returns dynamic Permissions to configured paths and bind ports */
    static Permissions createPermissions(Environment environment) throws IOException {
        Permissions policy = new Permissions();
        addClasspathPermissions(policy);
        addFilePermissions(policy, environment);
        addBindPermissions(policy, environment.settings());
        return policy;
    }

    /** Adds access to classpath jars/classes for jar hell scan, etc */
    @SuppressForbidden(reason = "accesses fully qualified URLs to configure security")
    static void addClasspathPermissions(Permissions policy) throws IOException {
        // add permissions to everything in classpath
        // really it should be covered by lib/, but there could be e.g. agents or similar configured)
        for (URL url : JarHell.parseClassPath()) {
            Path path;
            try {
                path = PathUtils.get(url.toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            // resource itself
            policy.add(new FilePermission(path.toString(), "read,readlink"));
            // classes underneath
            if (Files.isDirectory(path)) {
                policy.add(new FilePermission(path.toString() + path.getFileSystem().getSeparator() + "-", "read,readlink"));
            }
        }
    }

    /**
     * Adds access to all configurable paths.
     */
    static void addFilePermissions(Permissions policy, Environment environment) {
        // read-only dirs
        addPath(policy, Environment.PATH_HOME_SETTING.getKey(), environment.binFile(), "read,readlink");
        addPath(policy, Environment.PATH_HOME_SETTING.getKey(), environment.libFile(), "read,readlink");
        addPath(policy, Environment.PATH_HOME_SETTING.getKey(), environment.modulesFile(), "read,readlink");
        addPath(policy, Environment.PATH_HOME_SETTING.getKey(), environment.pluginsFile(), "read,readlink");
        addPath(policy, Environment.PATH_CONF_SETTING.getKey(), environment.configFile(), "read,readlink");
        addPath(policy, Environment.PATH_SCRIPTS_SETTING.getKey(), environment.scriptsFile(), "read,readlink");
        // read-write dirs
        addPath(policy, "java.io.tmpdir", environment.tmpFile(), "read,readlink,write,delete");
        addPath(policy, Environment.PATH_LOGS_SETTING.getKey(), environment.logsFile(), "read,readlink,write,delete");
        if (environment.sharedDataFile() != null) {
            addPath(policy, Environment.PATH_SHARED_DATA_SETTING.getKey(), environment.sharedDataFile(), "read,readlink,write,delete");
        }
        for (Path path : environment.dataFiles()) {
            addPath(policy, Environment.PATH_DATA_SETTING.getKey(), path, "read,readlink,write,delete");
        }
        for (Path path : environment.repoFiles()) {
            addPath(policy, Environment.PATH_REPO_SETTING.getKey(), path, "read,readlink,write,delete");
        }
        if (environment.pidFile() != null) {
            // we just need permission to remove the file if its elsewhere.
            policy.add(new FilePermission(environment.pidFile().toString(), "delete"));
        }
    }

    /**
     * Add dynamic {@link SocketPermission}s based on HTTP and transport settings.
     *
     * @param policy the {@link Permissions} instance to apply the dynamic {@link SocketPermission}s to.
     * @param settings the {@link Settings} instance to read the HTTP and transport settings from
     */
    static void addBindPermissions(Permissions policy, Settings settings) {
        addSocketPermissionForHttp(policy, settings);
        // transport is waaaay overengineered
        Map<String, Settings> profiles = TransportSettings.TRANSPORT_PROFILES_SETTING.get(settings).getAsGroups();
        if (!profiles.containsKey(TransportSettings.DEFAULT_PROFILE)) {
            profiles = new HashMap<>(profiles);
            profiles.put(TransportSettings.DEFAULT_PROFILE, Settings.EMPTY);
        }

        // loop through all profiles and add permissions for each one, if its valid.
        // (otherwise Netty transports are lenient and ignores it)
        for (Map.Entry<String, Settings> entry : profiles.entrySet()) {
            Settings profileSettings = entry.getValue();
            String name = entry.getKey();

            // a profile is only valid if its the default profile, or if it has an actual name and specifies a port
            boolean valid = TransportSettings.DEFAULT_PROFILE.equals(name) || (Strings.hasLength(name) && profileSettings.get("port") != null);
            if (valid) {
                addSocketPermissionForTransportProfile(policy, profileSettings, settings);
            }
        }

        final Map<String, Settings> tribeNodesSettings = new HashMap<>(settings.getGroups("tribe", true));
        for (final Settings tribeNodeSettings : tribeNodesSettings.values()) {
            // tribe nodes have HTTP disabled by default, so we check if HTTP is enabled before granting
            if (NetworkModule.HTTP_ENABLED.exists(tribeNodeSettings) && NetworkModule.HTTP_ENABLED.get(tribeNodeSettings)) {
                addSocketPermissionForHttp(policy, tribeNodeSettings);
            }
            addSocketPermissionForTransport(policy, tribeNodeSettings);
        }
    }

    /**
     * Add dynamic {@link SocketPermission} based on HTTP settings.
     *
     * @param policy the {@link Permissions} instance to apply the dynamic {@link SocketPermission}s to.
     * @param settings the {@link Settings} instance to read the HTTP settingsfrom
     */
    private static void addSocketPermissionForHttp(final Permissions policy, final Settings settings) {
        // http is simple
        final String httpRange = HttpTransportSettings.SETTING_HTTP_PORT.get(settings).getPortRangeString();
        addSocketPermissionForPortRange(policy, httpRange);
    }

    /**
     * Add dynamic {@link SocketPermission} based on transport settings. This method will first check if there is a port range specified in
     * the transport profile specified by {@code profileSettings} and will fall back to {@code settings}.
     *
     * @param policy          the {@link Permissions} instance to apply the dynamic {@link SocketPermission}s to
     * @param profileSettings the {@link Settings} to read the transport profile from
     * @param settings        the {@link Settings} instance to read the transport settings from
     */
    private static void addSocketPermissionForTransportProfile(
        final Permissions policy,
        final Settings profileSettings,
        final Settings settings) {
        final String transportRange = profileSettings.get("port");
        if (transportRange != null) {
            addSocketPermissionForPortRange(policy, transportRange);
        } else {
            addSocketPermissionForTransport(policy, settings);
        }
    }

    /**
     * Add dynamic {@link SocketPermission} based on transport settings.
     *
     * @param policy          the {@link Permissions} instance to apply the dynamic {@link SocketPermission}s to
     * @param settings        the {@link Settings} instance to read the transport settings from
     */
    private static void addSocketPermissionForTransport(final Permissions policy, final Settings settings) {
        final String transportRange = TransportSettings.PORT.get(settings);
        addSocketPermissionForPortRange(policy, transportRange);
    }

    /**
     * Add dynamic {@link SocketPermission} for the specified port range.
     *
     * @param policy the {@link Permissions} instance to apply the dynamic {@link SocketPermission} to.
     * @param portRange the port range
     */
    private static void addSocketPermissionForPortRange(final Permissions policy, final String portRange) {
        // listen is always called with 'localhost' but use wildcard to be sure, no name service is consulted.
        // see SocketPermission implies() code
        policy.add(new SocketPermission("*:" + portRange, "listen,resolve"));
    }

    /**
     * Add access to path (and all files underneath it)
     * @param policy current policy to add permissions to
     * @param configurationName the configuration name associated with the path (for error messages only)
     * @param path the path itself
     * @param permissions set of filepermissions to grant to the path
     */
    static void addPath(Permissions policy, String configurationName, Path path, String permissions) {
        // paths may not exist yet, this also checks accessibility
        try {
            ensureDirectoryExists(path);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to access '" + configurationName + "' (" + path + ")", e);
        }

        // add each path twice: once for itself, again for files underneath it
        policy.add(new FilePermission(path.toString(), permissions));
        policy.add(new FilePermission(path.toString() + path.getFileSystem().getSeparator() + "-", permissions));
    }

    /**
     * Add access to a directory iff it exists already
     * @param policy current policy to add permissions to
     * @param configurationName the configuration name associated with the path (for error messages only)
     * @param path the path itself
     * @param permissions set of filepermissions to grant to the path
     */
    static void addPathIfExists(Permissions policy, String configurationName, Path path, String permissions) {
        if (Files.isDirectory(path)) {
            // add each path twice: once for itself, again for files underneath it
            policy.add(new FilePermission(path.toString(), permissions));
            policy.add(new FilePermission(path.toString() + path.getFileSystem().getSeparator() + "-", permissions));
            try {
                path.getFileSystem().provider().checkAccess(path.toRealPath(), AccessMode.READ);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to access '" + configurationName + "' (" + path + ")", e);
            }
        }
    }


    /**
     * Ensures configured directory {@code path} exists.
     * @throws IOException if {@code path} exists, but is not a directory, not accessible, or broken symbolic link.
     */
    static void ensureDirectoryExists(Path path) throws IOException {
        // this isn't atomic, but neither is createDirectories.
        if (Files.isDirectory(path)) {
            // verify access, following links (throws exception if something is wrong)
            // we only check READ as a sanity test
            path.getFileSystem().provider().checkAccess(path.toRealPath(), AccessMode.READ);
        } else {
            // doesn't exist, or not a directory
            try {
                Files.createDirectories(path);
            } catch (FileAlreadyExistsException e) {
                // convert optional specific exception so the context is clear
                IOException e2 = new NotDirectoryException(path.toString());
                e2.addSuppressed(e);
                throw e2;
            }
        }
    }

    /** Simple checks that everything is ok */
    @SuppressForbidden(reason = "accesses jvm default tempdir as a self-test")
    static void selfTest() throws IOException {
        // check we can manipulate temporary files
        try {
            Path p = Files.createTempFile(null, null);
            try {
                Files.delete(p);
            } catch (IOException ignored) {
                // potentially virus scanner
            }
        } catch (SecurityException problem) {
            throw new SecurityException("Security misconfiguration: cannot access java.io.tmpdir", problem);
        }
    }
}
