package org.hibernate.test.abstractembeddedcomponents.cid;
import junit.framework.Test;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.testing.junit.functional.FunctionalTestCase;
import org.hibernate.testing.junit.functional.FunctionalTestClassTestSuite;

/**
 * @author Steve Ebersole
 */
public class AbstractCompositeIdTest extends FunctionalTestCase {
	public AbstractCompositeIdTest(String x) {
		super( x );
	}

	public static Test suite() {
		return new FunctionalTestClassTestSuite( AbstractCompositeIdTest.class );
	}

	public String[] getMappings() {
		return new String[] { "abstractembeddedcomponents/cid/Mappings.hbm.xml" };
	}

	public void testEmbeddedCompositeIdentifierOnAbstractClass() {
		MyInterfaceImpl myInterface = new MyInterfaceImpl();
		myInterface.setKey1( "key1" );
		myInterface.setKey2( "key2" );
		myInterface.setName( "test" );

		Session s = openSession();
		Transaction t = s.beginTransaction();
		s.save( myInterface );
		s.flush();

		s.createQuery( "from MyInterface" ).list();

		s.delete( myInterface );
		t.commit();
		s.close();

	}
}
