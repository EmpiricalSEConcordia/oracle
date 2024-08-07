/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010, Red Hat Inc. or third-party contributors as
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
package org.hibernate.ejb.test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jboss.logging.Logger;

import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.Dialect;
import org.hibernate.ejb.AvailableSettings;
import org.hibernate.ejb.Ejb3Configuration;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.service.ServiceRegistryBuilder;
import org.hibernate.service.internal.BasicServiceRegistryImpl;

import org.junit.After;
import org.junit.Before;

import org.hibernate.testing.junit4.BaseUnitTestCase;

/**
 * A base class for all ejb tests.
 *
 * @author Emmanuel Bernard
 * @author Hardy Ferentschik
 */
public abstract class BaseEntityManagerFunctionalTestCase extends BaseUnitTestCase {
	private static final Logger log = Logger.getLogger( BaseEntityManagerFunctionalTestCase.class );

	// IMPL NOTE : Here we use @Before and @After (instead of @BeforeClassOnce and @AfterClassOnce like we do in
	// BaseCoreFunctionalTestCase) because the old HEM test methodology was to create an EMF for each test method.

	private static final Dialect dialect = Dialect.getDialect();

	private Ejb3Configuration ejb3Configuration;
	private BasicServiceRegistryImpl serviceRegistry;
	private EntityManagerFactory entityManagerFactory;

	private EntityManager em;
	private ArrayList<EntityManager> isolatedEms = new ArrayList<EntityManager>();

	protected Dialect getDialect() {
		return dialect;
	}

	protected EntityManagerFactory entityManagerFactory() {
		return entityManagerFactory;
	}

	protected BasicServiceRegistryImpl serviceRegistry() {
		return serviceRegistry;
	}

	@Before
	@SuppressWarnings( {"UnusedDeclaration"})
	public void buildEntityManagerFactory() throws Exception {
		log.trace( "Building session factory" );
		ejb3Configuration = buildConfiguration();
		ejb3Configuration.configure( getConfig() );
		afterConfigurationBuilt( ejb3Configuration );
		serviceRegistry = buildServiceRegistry( ejb3Configuration.getHibernateConfiguration() );
		applyServices( serviceRegistry );
		entityManagerFactory = ejb3Configuration.buildEntityManagerFactory( serviceRegistry );
		afterEntityManagerFactoryBuilt();
	}

	protected Ejb3Configuration buildConfiguration() {
		Ejb3Configuration ejb3Cfg = constructConfiguration();
		addMappings( ejb3Cfg.getHibernateConfiguration() );
		return ejb3Cfg;
	}

	protected Ejb3Configuration constructConfiguration() {
		Ejb3Configuration ejb3Configuration = new Ejb3Configuration();
		if ( createSchema() ) {
			ejb3Configuration.getHibernateConfiguration().setProperty( Environment.HBM2DDL_AUTO, "create-drop" );
		}
		ejb3Configuration
				.getHibernateConfiguration()
				.setProperty( Configuration.USE_NEW_ID_GENERATOR_MAPPINGS, "true" );
		ejb3Configuration
				.getHibernateConfiguration()
				.setProperty( Environment.DIALECT, getDialect().getClass().getName() );
		return ejb3Configuration;
	}

	protected void addMappings(Configuration configuration) {
		String[] mappings = getMappings();
		if ( mappings != null ) {
			for ( String mapping : mappings ) {
				configuration.addResource( mapping, getClass().getClassLoader() );
			}
		}
	}

	protected static final String[] NO_MAPPINGS = new String[0];

	protected String[] getMappings() {
		return NO_MAPPINGS;
	}

	protected Map getConfig() {
		Map<Object, Object> config = loadProperties();
		ArrayList<Class> classes = new ArrayList<Class>();

		classes.addAll( Arrays.asList( getAnnotatedClasses() ) );
		config.put( AvailableSettings.LOADED_CLASSES, classes );
		for ( Map.Entry<Class, String> entry : getCachedClasses().entrySet() ) {
			config.put( AvailableSettings.CLASS_CACHE_PREFIX + "." + entry.getKey().getName(), entry.getValue() );
		}
		for ( Map.Entry<String, String> entry : getCachedCollections().entrySet() ) {
			config.put( AvailableSettings.COLLECTION_CACHE_PREFIX + "." + entry.getKey(), entry.getValue() );
		}
		if ( getEjb3DD().length > 0 ) {
			ArrayList<String> dds = new ArrayList<String>();
			dds.addAll( Arrays.asList( getEjb3DD() ) );
			config.put( AvailableSettings.XML_FILE_NAMES, dds );
		}

		addConfigOptions( config );
		return config;
	}

	protected void addConfigOptions(Map options) {
	}

	private Properties loadProperties() {
		Properties props = new Properties();
		InputStream stream = Persistence.class.getResourceAsStream( "/hibernate.properties" );
		if ( stream != null ) {
			try {
				props.load( stream );
			}
			catch ( Exception e ) {
				throw new RuntimeException( "could not load hibernate.properties" );
			}
			finally {
				try {
					stream.close();
				}
				catch ( IOException ignored ) {
				}
			}
		}
		return props;
	}

	protected static final Class<?>[] NO_CLASSES = new Class[0];

	protected Class<?>[] getAnnotatedClasses() {
		return NO_CLASSES;
	}

	public Map<Class, String> getCachedClasses() {
		return new HashMap<Class, String>();
	}

	public Map<String, String> getCachedCollections() {
		return new HashMap<String, String>();
	}

	public String[] getEjb3DD() {
		return new String[] { };
	}

	@SuppressWarnings( {"UnusedParameters"})
	protected void afterConfigurationBuilt(Ejb3Configuration ejb3Configuration) {
	}

	protected BasicServiceRegistryImpl buildServiceRegistry(Configuration configuration) {
		Properties properties = new Properties();
		properties.putAll( configuration.getProperties() );
		Environment.verifyProperties( properties );
		ConfigurationHelper.resolvePlaceHolders( properties );
		return (BasicServiceRegistryImpl) new ServiceRegistryBuilder( properties ).buildServiceRegistry();
	}

	@SuppressWarnings( {"UnusedParameters"})
	protected void applyServices(BasicServiceRegistryImpl serviceRegistry) {
	}

	protected void afterEntityManagerFactoryBuilt() {
	}

	protected boolean createSchema() {
		return true;
	}


	@After
	@SuppressWarnings( {"UnusedDeclaration"})
	public void releaseResources() {
		releaseUnclosedEntityManagers();

		if ( entityManagerFactory != null ) {
			entityManagerFactory.close();
		}

		if ( serviceRegistry != null ) {
			serviceRegistry.destroy();
		}
	}

	private void releaseUnclosedEntityManagers() {
		releaseUnclosedEntityManager( this.em );

		for ( EntityManager isolatedEm : isolatedEms ) {
			releaseUnclosedEntityManager( isolatedEm );
		}
	}

	private void releaseUnclosedEntityManager(EntityManager em) {
		if ( em == null ) {
			return;
		}
		if ( em.getTransaction().isActive() ) {
			em.getTransaction().rollback();
            log.warn("You left an open transaction! Fix your test case. For now, we are closing it for you.");
		}
		if ( em.isOpen() ) {
			// as we open an EM before the test runs, it will still be open if the test uses a custom EM.
			// or, the person may have forgotten to close. So, do not raise a "fail", but log the fact.
			em.close();
            log.warn("The EntityManager is not closed. Closing it.");
		}
	}

	protected EntityManager getOrCreateEntityManager() {
		if ( em == null || !em.isOpen() ) {
			em = entityManagerFactory.createEntityManager();
		}
		return em;
	}

	protected EntityManager createIsolatedEntityManager() {
		EntityManager isolatedEm = entityManagerFactory.createEntityManager();
		isolatedEms.add( isolatedEm );
		return isolatedEm;
	}

	protected EntityManager createIsolatedEntityManager(Map props) {
		EntityManager isolatedEm = entityManagerFactory.createEntityManager(props);
		isolatedEms.add( isolatedEm );
		return isolatedEm;
	}

	protected EntityManager createEntityManager(Map properties) {
		// always reopen a new EM and close the existing one
		if ( em != null && em.isOpen() ) {
			em.close();
		}
		em = entityManagerFactory.createEntityManager( properties );
		return em;
	}
}
