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
package org.hibernate.id;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import junit.framework.TestCase;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.TestingDatabaseInfo;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.cfg.NamingStrategy;
import org.hibernate.cfg.ObjectNameNormalizer;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.impl.SessionImpl;
import org.hibernate.jdbc.Work;
import org.hibernate.mapping.SimpleAuxiliaryDatabaseObject;
import org.hibernate.test.common.ServiceRegistryHolder;

/**
 * I went back to 3.3 source and grabbed the code/logic as it existed back then and crafted this
 * unit test so that we can make sure the value keep being generated in the expected manner
 *
 * @author Steve Ebersole
 */
@SuppressWarnings({ "deprecation" })
public class SequenceHiLoGeneratorNoIncrementTest extends TestCase {
	private static final String TEST_SEQUENCE = "test_sequence";

	private Configuration cfg;
	ServiceRegistryHolder serviceRegistryHolder;
	private SessionFactoryImplementor sessionFactory;
	private SequenceHiLoGenerator generator;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		Properties properties = new Properties();
		properties.setProperty( SequenceGenerator.SEQUENCE, TEST_SEQUENCE );
		properties.setProperty( SequenceHiLoGenerator.MAX_LO, "0" ); // JPA allocationSize of 1
		properties.put(
				PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER,
				new ObjectNameNormalizer() {
					@Override
					protected boolean isUseQuotedIdentifiersGlobally() {
						return false;
					}

					@Override
					protected NamingStrategy getNamingStrategy() {
						return cfg.getNamingStrategy();
					}
				}
		);

		Dialect dialect = new H2Dialect();

		generator = new SequenceHiLoGenerator();
		generator.configure( Hibernate.LONG, properties, dialect );

		cfg = TestingDatabaseInfo.buildBaseConfiguration()
				.setProperty( Environment.HBM2DDL_AUTO, "create-drop" );
		cfg.addAuxiliaryDatabaseObject(
				new SimpleAuxiliaryDatabaseObject(
						generator.sqlCreateStrings( dialect )[0],
						generator.sqlDropStrings( dialect )[0]
				)
		);

		serviceRegistryHolder = new ServiceRegistryHolder( cfg.getProperties() );
		sessionFactory = (SessionFactoryImplementor) cfg.buildSessionFactory( serviceRegistryHolder.getServiceRegistry() );
	}

	@Override
	protected void tearDown() throws Exception {
		if ( sessionFactory != null ) {
			sessionFactory.close();
		}
		if ( serviceRegistryHolder != null ) {
			serviceRegistryHolder.destroy();
		}
		super.tearDown();
	}

	public void testHiLoAlgorithm() {
		SessionImpl session = (SessionImpl) sessionFactory.openSession();
		session.beginTransaction();

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// initially sequence should be uninitialized
		assertEquals( 0L, extractSequenceValue( session ) );

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// historically the hilo generators skipped the initial block of values;
		// 		so the first generated id value is maxlo + 1, here be 4
		Long generatedValue = (Long) generator.generate( session, null );
		assertEquals( 1L, generatedValue.longValue() );
		// which should also perform the first read on the sequence which should set it to its "start with" value (1)
		assertEquals( 1L, extractSequenceValue( session ) );

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		generatedValue = (Long) generator.generate( session, null );
		assertEquals( 2L, generatedValue.longValue() );
		assertEquals( 2L, extractSequenceValue( session ) );

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		generatedValue = (Long) generator.generate( session, null );
		assertEquals( 3L, generatedValue.longValue() );
		assertEquals( 3L, extractSequenceValue( session ) );

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		generatedValue = (Long) generator.generate( session, null );
		assertEquals( 4L, generatedValue.longValue() );
		assertEquals( 4L, extractSequenceValue( session ) );

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		generatedValue = (Long) generator.generate( session, null );
		assertEquals( 5L, generatedValue.longValue() );
		assertEquals( 5L, extractSequenceValue( session ) );

		session.getTransaction().commit();
		session.close();
	}

	private long extractSequenceValue(Session session) {
		class WorkImpl implements Work {
			private long value;
			public void execute(Connection connection) throws SQLException {
				PreparedStatement query = connection.prepareStatement( "select currval('" + TEST_SEQUENCE + "');" );
				ResultSet resultSet = query.executeQuery();
				resultSet.next();
				value = resultSet.getLong( 1 );
			}
		}
		WorkImpl work = new WorkImpl();
		session.doWork( work );
		return work.value;
	}
}