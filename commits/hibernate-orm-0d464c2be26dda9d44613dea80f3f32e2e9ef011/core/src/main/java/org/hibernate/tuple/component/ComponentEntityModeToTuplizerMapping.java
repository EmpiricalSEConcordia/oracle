/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat Middleware LLC or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Middleware LLC.
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
 *
 */
package org.hibernate.tuple.component;

import org.hibernate.tuple.EntityModeToTuplizerMapping;
import org.hibernate.tuple.Tuplizer;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.EntityMode;
import org.hibernate.HibernateException;
import org.hibernate.util.ReflectHelper;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.io.Serializable;

/**
 * Handles mapping {@link EntityMode}s to {@link ComponentTuplizer}s.
 * <p/>
 * Most of the handling is really in the super class; here we just create
 * the tuplizers and add them to the superclass
 *
 * @author Steve Ebersole
 */
class ComponentEntityModeToTuplizerMapping extends EntityModeToTuplizerMapping implements Serializable {

	private static final Class[] COMPONENT_TUP_CTOR_SIG = new Class[] { Component.class };

	public ComponentEntityModeToTuplizerMapping(Component component) {
		PersistentClass owner = component.getOwner();

		// create our own copy of the user-supplied tuplizer impl map
		Map userSuppliedTuplizerImpls = new HashMap();
		if ( component.getTuplizerMap() != null ) {
			userSuppliedTuplizerImpls.putAll( component.getTuplizerMap() );
		}

		// Build the dynamic-map tuplizer...
		Tuplizer dynamicMapTuplizer = null;
		String tuplizerImpl = ( String ) userSuppliedTuplizerImpls.remove( EntityMode.MAP );
		if ( tuplizerImpl == null ) {
			dynamicMapTuplizer = new DynamicMapComponentTuplizer( component );
		}
		else {
			dynamicMapTuplizer = buildComponentTuplizer( tuplizerImpl, component );
		}

		// then the pojo tuplizer, using the dynamic-map tuplizer if no pojo representation is available
		tuplizerImpl = ( String ) userSuppliedTuplizerImpls.remove( EntityMode.POJO );
		Tuplizer pojoTuplizer = null;
		if ( owner.hasPojoRepresentation() && component.hasPojoRepresentation() ) {
			if ( tuplizerImpl == null ) {
				pojoTuplizer = new PojoComponentTuplizer( component );
			}
			else {
				pojoTuplizer = buildComponentTuplizer( tuplizerImpl, component );
			}
		}
		else {
			pojoTuplizer = dynamicMapTuplizer;
		}

		// then dom4j tuplizer, if dom4j representation is available
		Tuplizer dom4jTuplizer = null;
		tuplizerImpl = ( String ) userSuppliedTuplizerImpls.remove( EntityMode.DOM4J );
		if ( owner.hasDom4jRepresentation() ) {
			if ( tuplizerImpl == null ) {
				dom4jTuplizer = new Dom4jComponentTuplizer( component );
			}
			else {
				dom4jTuplizer = buildComponentTuplizer( tuplizerImpl, component );
			}
		}
		else {
			dom4jTuplizer = null;
		}

		// put the "standard" tuplizers into the tuplizer map first
		if ( pojoTuplizer != null ) {
			addTuplizer( EntityMode.POJO, pojoTuplizer );
		}
		if ( dynamicMapTuplizer != null ) {
			addTuplizer( EntityMode.MAP, dynamicMapTuplizer );
		}
		if ( dom4jTuplizer != null ) {
			addTuplizer( EntityMode.DOM4J, dom4jTuplizer );
		}

		// then handle any user-defined entity modes...
		if ( !userSuppliedTuplizerImpls.isEmpty() ) {
			Iterator itr = userSuppliedTuplizerImpls.entrySet().iterator();
			while ( itr.hasNext() ) {
				Map.Entry entry = ( Map.Entry ) itr.next();
				EntityMode entityMode = ( EntityMode ) entry.getKey();
				ComponentTuplizer tuplizer = buildComponentTuplizer( ( String ) entry.getValue(), component );
				addTuplizer( entityMode, tuplizer );
			}
		}
	}

	private ComponentTuplizer buildComponentTuplizer(String tuplizerImpl, Component component) {
		try {
			Class implClass = ReflectHelper.classForName( tuplizerImpl );
			return ( ComponentTuplizer ) implClass.getConstructor( COMPONENT_TUP_CTOR_SIG ).newInstance( new Object[] { component } );
		}
		catch( Throwable t ) {
			throw new HibernateException( "Could not build tuplizer [" + tuplizerImpl + "]", t );
		}
	}
}
