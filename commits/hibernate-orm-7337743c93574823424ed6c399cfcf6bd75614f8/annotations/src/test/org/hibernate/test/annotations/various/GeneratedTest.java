//$Id$
package org.hibernate.test.annotations.various;

import org.hibernate.test.annotations.TestCase;
import org.hibernate.Session;
import org.hibernate.Transaction;

/**
 * @author Emmanuel Bernard
 */
public class GeneratedTest extends TestCase {

	public void testGenerated() throws Exception {
		Session s = openSession();
		Transaction tx = s.beginTransaction();
		Antenna antenna = new Antenna();
		antenna.id = new Integer(1);
		s.persist( antenna );
		assertNull( antenna.latitude );
		assertNull( antenna.longitude );
		tx.rollback();
		s.close();
	}

	protected Class[] getMappings() {
		return new Class[] {
				Antenna.class
		};
	}
}
