/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.hibernate.search.query.hibernate.impl;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.search.engine.EntityInfo;
import org.hibernate.search.engine.SearchFactoryImplementor;
import org.hibernate.search.query.engine.spi.TimeoutManager;

/**
 * Initializes a set of objects from EntityInfos
 *
 * @author Emmanuel Bernard <emmanuel@hibernate.org>
 */
public interface ObjectsInitializer {
	void initializeObjects(EntityInfo[] entityInfos,
										 Criteria criteria, Class<?> entityType,
										 SearchFactoryImplementor searchFactoryImplementor,
										 TimeoutManager timeoutManager,
										 Session session);
}
