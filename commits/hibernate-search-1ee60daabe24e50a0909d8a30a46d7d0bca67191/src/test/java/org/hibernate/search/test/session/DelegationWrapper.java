//$Id$
package org.hibernate.search.test.session;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.io.Serializable;

import org.hibernate.Session;

/**
 * @author Emmanuel Bernard
 */
public class DelegationWrapper implements InvocationHandler, Serializable {
	Object realSession;

	public DelegationWrapper(Session session) {
		this.realSession = session;
	}

	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		try {
			return method.invoke( realSession, args );
		}
		catch (InvocationTargetException e) {
			if ( e.getTargetException() instanceof RuntimeException ) {
				throw (RuntimeException) e.getTargetException();
			}
			else {
				throw e;
			}
		}
	}
}
