/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.bootstrap.cli.runner;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.springframework.bootstrap.cli.compiler.GroovyCompiler;

/**
 * Compiles Groovy code running the resulting classes using a {@code SpringApplication}.
 * Takes care of threading and class-loading issues and can optionally monitor files for
 * changes.
 * 
 * @author Phillip Webb
 * @author Dave Syer
 */
public class BootstrapRunner {

	// FIXME logging

	private BootstrapRunnerConfiguration configuration;

	private final File[] files;

	private final String[] args;

	private final GroovyCompiler compiler;

	private RunThread runThread;

	private FileWatchThread fileWatchThread;

	/**
	 * Create a new {@link BootstrapRunner} instance.
	 * @param configuration the configuration
	 * @param files the files to compile/watch
	 * @param args input arguments
	 */
	public BootstrapRunner(final BootstrapRunnerConfiguration configuration,
			File[] files, String... args) {
		this.configuration = configuration;
		this.files = files;
		this.args = args;
		this.compiler = new GroovyCompiler(configuration);
		if (configuration.getLogLevel().intValue() <= Level.FINE.intValue()) {
			System.setProperty("groovy.grape.report.downloads", "true");
		}
	}

	/**
	 * Compile and run the application. This method is synchronized as it can be called by
	 * file monitoring threads.
	 * @throws Exception on error
	 */
	public synchronized void compileAndRun() throws Exception {
		try {

			stop();

			// Compile
			Object[] sources = this.compiler.sources(this.files);
			if (sources.length == 0) {
				throw new RuntimeException("No classes found in '" + this.files + "'");
			}

			// Run in new thread to ensure that the context classloader is setup
			this.runThread = new RunThread(sources);
			this.runThread.start();
			this.runThread.join();

			// Start monitoring for changes
			if (this.fileWatchThread == null
					&& this.configuration.isWatchForFileChanges()) {
				this.fileWatchThread = new FileWatchThread();
				this.fileWatchThread.start();
			}

		} catch (Exception ex) {
			if (this.fileWatchThread == null) {
				throw ex;
			} else {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Thread used to launch the Spring Application with the correct context classloader.
	 */
	private class RunThread extends Thread {

		private final Object[] sources;

		private Object applicationContext;

		/**
		 * Create a new {@link RunThread} instance.
		 * @param sources the sources to launch
		 */
		public RunThread(Object... sources) {
			this.sources = sources;
			if (sources.length != 0 && sources[0] instanceof Class) {
				setContextClassLoader(((Class<?>) sources[0]).getClassLoader());
			}
			setDaemon(true);
		}

		@Override
		public void run() {
			try {
				// User reflection to load and call Spring
				Class<?> application = getContextClassLoader().loadClass(
						"org.springframework.bootstrap.SpringApplication");
				Method method = application.getMethod("run", Object[].class,
						String[].class);
				this.applicationContext = method.invoke(null, this.sources,
						BootstrapRunner.this.args);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		/**
		 * Shutdown the thread, closing any previously opened application context.
		 */
		public synchronized void shutdown() {
			if (this.applicationContext != null) {
				try {
					Method method = this.applicationContext.getClass().getMethod("close");
					method.invoke(this.applicationContext);
				} catch (NoSuchMethodException ex) {
					// Not an application context that we can close
				} catch (Exception ex) {
					ex.printStackTrace();
				} finally {
					this.applicationContext = null;
				}
			}
		}
	}

	/**
	 * Thread to watch for file changes and trigger recompile/reload.
	 */
	private class FileWatchThread extends Thread {

		private long previous;

		public FileWatchThread() {
			this.previous = 0;
			for (File file : BootstrapRunner.this.files) {
				long current = file.lastModified();
				if (current > this.previous) {
					this.previous = current;
				}
			}
			setDaemon(false);
		}

		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(TimeUnit.SECONDS.toMillis(1));
					for (File file : BootstrapRunner.this.files) {
						long current = file.lastModified();
						if (this.previous < current) {
							this.previous = current;
							compileAndRun();
						}
					}
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				} catch (Exception ex) {
					// Swallow, will be reported by compileAndRun
				}
			}
		}

	}

	public void stop() {
		if (this.runThread != null) {
			this.runThread.shutdown();
			this.runThread = null;
		}
	}

}
