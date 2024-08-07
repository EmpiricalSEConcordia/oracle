/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2007-2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
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
package org.hibernate.service.jdbc.connections.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Properties;
import javax.sql.DataSource;
import org.hibernate.HibernateException;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.service.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.spi.UnknownUnwrapTypeException;
import org.hibernate.util.ReflectHelper;
import org.jboss.logging.Logger;
import com.mchange.v2.c3p0.DataSources;

/**
 * A connection provider that uses a C3P0 connection pool. Hibernate will use this by
 * default if the <tt>hibernate.c3p0.*</tt> properties are set.
 *
 * @author various people
 * @see ConnectionProvider
 */
public class C3P0ConnectionProvider implements ConnectionProvider {

    private static final C3P0Logger LOG = Logger.getMessageLogger(C3P0Logger.class, C3P0ConnectionProvider.class.getName());

	//swaldman 2006-08-28: define c3p0-style configuration parameters for properties with
	//                     hibernate-specific overrides to detect and warn about conflicting
	//                     declarations
	private final static String C3P0_STYLE_MIN_POOL_SIZE = "c3p0.minPoolSize";
	private final static String C3P0_STYLE_MAX_POOL_SIZE = "c3p0.maxPoolSize";
	private final static String C3P0_STYLE_MAX_IDLE_TIME = "c3p0.maxIdleTime";
	private final static String C3P0_STYLE_MAX_STATEMENTS = "c3p0.maxStatements";
	private final static String C3P0_STYLE_ACQUIRE_INCREMENT = "c3p0.acquireIncrement";
	private final static String C3P0_STYLE_IDLE_CONNECTION_TEST_PERIOD = "c3p0.idleConnectionTestPeriod";
    // private final static String C3P0_STYLE_TEST_CONNECTION_ON_CHECKOUT = "c3p0.testConnectionOnCheckout";

	//swaldman 2006-08-28: define c3p0-style configuration parameters for initialPoolSize, which
	//                     hibernate sensibly lets default to minPoolSize, but we'll let users
	//                     override it with the c3p0-style property if they want.
	private final static String C3P0_STYLE_INITIAL_POOL_SIZE = "c3p0.initialPoolSize";

	private DataSource ds;
	private Integer isolation;
	private boolean autocommit;

	/**
	 * {@inheritDoc}
	 */
	public Connection getConnection() throws SQLException {
		final Connection c = ds.getConnection();
		if ( isolation != null ) {
			c.setTransactionIsolation( isolation.intValue() );
		}
		if ( c.getAutoCommit() != autocommit ) {
			c.setAutoCommit( autocommit );
		}
		return c;
	}

	/**
	 * {@inheritDoc}
	 */
	public void closeConnection(Connection conn) throws SQLException {
		conn.close();
	}

	@Override
	public boolean isUnwrappableAs(Class unwrapType) {
		return ConnectionProvider.class.equals( unwrapType ) ||
				C3P0ConnectionProvider.class.isAssignableFrom( unwrapType ) ||
				DataSource.class.isAssignableFrom( unwrapType );
	}

	@Override
	@SuppressWarnings( {"unchecked"})
	public <T> T unwrap(Class<T> unwrapType) {
		if ( ConnectionProvider.class.equals( unwrapType ) ||
				C3P0ConnectionProvider.class.isAssignableFrom( unwrapType ) ) {
			return (T) this;
		}
		else if ( DataSource.class.isAssignableFrom( unwrapType ) ) {
			return (T) ds;
		}
		else {
			throw new UnknownUnwrapTypeException( unwrapType );
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void configure(Properties props) throws HibernateException {
		String jdbcDriverClass = props.getProperty( Environment.DRIVER );
		String jdbcUrl = props.getProperty( Environment.URL );
		Properties connectionProps = ConnectionProviderInitiator.getConnectionProperties( props );

        LOG.c3p0UsingDriver(jdbcDriverClass, jdbcUrl);
        LOG.connectionProperties(ConfigurationHelper.maskOut(connectionProps, "password"));

		autocommit = ConfigurationHelper.getBoolean( Environment.AUTOCOMMIT, props );
        LOG.autoCommitMode(autocommit);

        if (jdbcDriverClass == null) LOG.jdbcDriverNotSpecified(Environment.DRIVER);
		else {
			try {
				Class.forName( jdbcDriverClass );
			}
			catch ( ClassNotFoundException cnfe ) {
				try {
					ReflectHelper.classForName( jdbcDriverClass );
				}
				catch ( ClassNotFoundException e ) {
                    String msg = LOG.jdbcDriverNotFound(jdbcDriverClass);
                    LOG.error(msg, e);
					throw new HibernateException( msg, e );
				}
			}
		}

		try {

			//swaldman 2004-02-07: modify to allow null values to signify fall through to c3p0 PoolConfig defaults
			Integer minPoolSize = ConfigurationHelper.getInteger( Environment.C3P0_MIN_SIZE, props );
			Integer maxPoolSize = ConfigurationHelper.getInteger( Environment.C3P0_MAX_SIZE, props );
			Integer maxIdleTime = ConfigurationHelper.getInteger( Environment.C3P0_TIMEOUT, props );
			Integer maxStatements = ConfigurationHelper.getInteger( Environment.C3P0_MAX_STATEMENTS, props );
			Integer acquireIncrement = ConfigurationHelper.getInteger( Environment.C3P0_ACQUIRE_INCREMENT, props );
			Integer idleTestPeriod = ConfigurationHelper.getInteger( Environment.C3P0_IDLE_TEST_PERIOD, props );

			Properties c3props = new Properties();

			// turn hibernate.c3p0.* into c3p0.*, so c3p0
			// gets a chance to see all hibernate.c3p0.*
			for ( Iterator ii = props.keySet().iterator(); ii.hasNext(); ) {
				String key = ( String ) ii.next();
				if ( key.startsWith( "hibernate.c3p0." ) ) {
					String newKey = key.substring( 10 );
					if ( props.containsKey( newKey ) ) {
						warnPropertyConflict( key, newKey );
					}
					c3props.put( newKey, props.get( key ) );
				}
			}

			setOverwriteProperty( Environment.C3P0_MIN_SIZE, C3P0_STYLE_MIN_POOL_SIZE, props, c3props, minPoolSize );
			setOverwriteProperty( Environment.C3P0_MAX_SIZE, C3P0_STYLE_MAX_POOL_SIZE, props, c3props, maxPoolSize );
			setOverwriteProperty( Environment.C3P0_TIMEOUT, C3P0_STYLE_MAX_IDLE_TIME, props, c3props, maxIdleTime );
			setOverwriteProperty(
					Environment.C3P0_MAX_STATEMENTS, C3P0_STYLE_MAX_STATEMENTS, props, c3props, maxStatements
			);
			setOverwriteProperty(
					Environment.C3P0_ACQUIRE_INCREMENT, C3P0_STYLE_ACQUIRE_INCREMENT, props, c3props, acquireIncrement
			);
			setOverwriteProperty(
					Environment.C3P0_IDLE_TEST_PERIOD, C3P0_STYLE_IDLE_CONNECTION_TEST_PERIOD, props, c3props, idleTestPeriod
			);

			// revert to traditional hibernate behavior of setting initialPoolSize to minPoolSize
			// unless otherwise specified with a c3p0.*-style parameter.
			Integer initialPoolSize = ConfigurationHelper.getInteger( C3P0_STYLE_INITIAL_POOL_SIZE, props );
			if ( initialPoolSize == null && minPoolSize != null ) {
				c3props.put( C3P0_STYLE_INITIAL_POOL_SIZE, String.valueOf( minPoolSize ).trim() );
			}

			/*DataSource unpooled = DataSources.unpooledDataSource(
				jdbcUrl, props.getProperty(Environment.USER), props.getProperty(Environment.PASS)
			);*/
			DataSource unpooled = DataSources.unpooledDataSource( jdbcUrl, connectionProps );

			Properties allProps = ( Properties ) props.clone();
			allProps.putAll( c3props );

			ds = DataSources.pooledDataSource( unpooled, allProps );
		}
		catch ( Exception e ) {
            LOG.error(LOG.unableToInstantiateC3p0ConnectionPool(), e);
            throw new HibernateException(LOG.unableToInstantiateC3p0ConnectionPool(), e);
		}

		String i = props.getProperty( Environment.ISOLATION );
        if (i == null) isolation = null;
		else {
			isolation = new Integer( i );
            LOG.jdbcIsolationLevel(Environment.isolationLevelToString(isolation.intValue()));
		}

	}

    /**
	 *
	 */
	public void close() {
		try {
			DataSources.destroy( ds );
		}
		catch ( SQLException sqle ) {
            LOG.unableToDestroyC3p0ConnectionPool(sqle);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean supportsAggressiveRelease() {
		return false;
	}

	private void setOverwriteProperty(String hibernateStyleKey, String c3p0StyleKey, Properties hibp, Properties c3p, Integer value) {
		if ( value != null ) {
			c3p.put( c3p0StyleKey, String.valueOf( value ).trim() );
			if ( hibp.getProperty( c3p0StyleKey ) != null ) {
				warnPropertyConflict( hibernateStyleKey, c3p0StyleKey );
			}
			String longC3p0StyleKey = "hibernate." + c3p0StyleKey;
			if ( hibp.getProperty( longC3p0StyleKey ) != null ) {
				warnPropertyConflict( hibernateStyleKey, longC3p0StyleKey );
			}
		}
	}

	private void warnPropertyConflict(String hibernateStyle, String c3p0Style) {
        LOG.bothHibernateAndC3p0StylesSet(hibernateStyle, c3p0Style, hibernateStyle, c3p0Style);
	}
}
