/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2009 by Red Hat Inc and/or its affiliates or by
 * third-party contributors as indicated by either @author tags or express
 * copyright attribution statements applied by the authors.  All
 * third-party contributions are distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.engine.jdbc;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Clob;
import java.sql.SQLException;
import org.hibernate.type.descriptor.java.DataHelper;

/**
 * Manages aspects of proxying {@link Clob Clobs} for non-contextual creation, including proxy creation and
 * handling proxy invocations.
 *
 * @author Gavin King
 * @author Steve Ebersole
 * @author Gail Badner
 */
public class ClobProxy implements InvocationHandler {
	private static final Class[] PROXY_INTERFACES = new Class[] { Clob.class, ClobImplementer.class };

	private String string;
	private Reader reader;
	private long length;
	private boolean needsReset = false;


	/**
	 * Constructor used to build {@link Clob} from string data.
	 *
	 * @param string The byte array
	 * @see #generateProxy(String)
	 */
	protected ClobProxy(String string) {
		this.string = string;
		reader = new StringReader(string);
		length = string.length();
	}

	/**
	 * Constructor used to build {@link Clob} from a reader.
	 *
	 * @param reader The character reader.
	 * @param length The length of the reader stream.
	 * @see #generateProxy(java.io.Reader, long)
	 */
	protected ClobProxy(Reader reader, long length) {
		this.reader = reader;
		this.length = length;
	}

	protected long getLength() {
		return length;
	}

	protected InputStream getAsciiStream() throws SQLException {
		resetIfNeeded();
		return new ReaderInputStream( reader );
	}

	protected Reader getCharacterStream() throws SQLException {
		resetIfNeeded();
		return reader;
	}

	protected String getSubString(long start, int length) {
		if ( string == null ) {
			throw new UnsupportedOperationException( "Clob was not created from string; cannot substring" );
		}
		// semi-naive implementation
		int endIndex = Math.min( ((int)start)+length, string.length() );
		return string.substring( (int)start, endIndex );
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws UnsupportedOperationException if any methods other than {@link Clob#length()},
	 * {@link Clob#getAsciiStream()}, or {@link Clob#getCharacterStream()} are invoked.
	 */
	@SuppressWarnings({ "UnnecessaryBoxing" })
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		final String methodName = method.getName();
		final int argCount = method.getParameterTypes().length;

		if ( "length".equals( methodName ) && argCount == 0 ) {
			return Long.valueOf( getLength() );
		}
		if ( "getAsciiStream".equals( methodName ) && argCount == 0 ) {
			return getAsciiStream();
		}
		if ( "getCharacterStream".equals( methodName ) ) {
			if ( argCount == 0 ) {
				return getCharacterStream();
			}
			else if ( argCount == 2 ) {
				long start = (Long) args[0];
				if ( start < 1 ) {
					throw new SQLException( "Start position 1-based; must be 1 or more." );
				}
				if ( start > getLength() ) {
					throw new SQLException( "Start position [" + start + "] cannot exceed overall CLOB length [" + getLength() + "]" );
				}
				int length = (Integer) args[1];
				if ( length < 0 ) {
					// java docs specifically say for getCharacterStream(long,int) that the start+length must not exceed the
					// total length, however that is at odds with the getSubString(long,int) behavior.
					throw new SQLException( "Length must be great-than-or-equal to zero." );
				}
				return DataHelper.subStream( getCharacterStream(), start-1, length );
			}
		}
		if ( "getSubString".equals( methodName ) && argCount == 2 ) {
			long start = (Long) args[0];
			if ( start < 1 ) {
				throw new SQLException( "Start position 1-based; must be 1 or more." );
			}
			if ( start > getLength() ) {
				throw new SQLException( "Start position [" + start + "] cannot exceed overall CLOB length [" + getLength() + "]" );
			}
			int length = (Integer) args[1];
			if ( length < 0 ) {
				throw new SQLException( "Length must be great-than-or-equal to zero." );
			}
			return getSubString( start-1, length );
		}
		if ( "free".equals( methodName ) && argCount == 0 ) {
			reader.close();
			return null;
		}
		if ( "toString".equals( methodName ) && argCount == 0 ) {
			return this.toString();
		}
		if ( "equals".equals( methodName ) && argCount == 1 ) {
			return Boolean.valueOf( proxy == args[0] );
		}
		if ( "hashCode".equals( methodName ) && argCount == 0 ) {
			return new Integer( this.hashCode() );
		}

		throw new UnsupportedOperationException( "Clob may not be manipulated from creating session" );
	}

	protected void resetIfNeeded() throws SQLException {
		try {
			if ( needsReset ) {
				reader.reset();
			}
		}
		catch ( IOException ioe ) {
			throw new SQLException( "could not reset reader" );
		}
		needsReset = true;
	}

	/**
	 * Generates a {@link Clob} proxy using the string data.
	 *
	 * @param string The data to be wrapped as a {@link Clob}.
	 *
	 * @return The generated proxy.
	 */
	public static Clob generateProxy(String string) {
		return ( Clob ) Proxy.newProxyInstance(
				getProxyClassLoader(),
				PROXY_INTERFACES,
				new ClobProxy( string )
		);
	}

	/**
	 * Generates a {@link Clob} proxy using a character reader of given length.
	 *
	 * @param reader The character reader
	 * @param length The length of the character reader
	 *
	 * @return The generated proxy.
	 */
	public static Clob generateProxy(Reader reader, long length) {
		return ( Clob ) Proxy.newProxyInstance(
				getProxyClassLoader(),
				PROXY_INTERFACES,
				new ClobProxy( reader, length )
		);
	}

	/**
	 * Determines the appropriate class loader to which the generated proxy
	 * should be scoped.
	 *
	 * @return The class loader appropriate for proxy construction.
	 */
	protected static ClassLoader getProxyClassLoader() {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if ( cl == null ) {
			cl = ClobImplementer.class.getClassLoader();
		}
		return cl;
	}
}
