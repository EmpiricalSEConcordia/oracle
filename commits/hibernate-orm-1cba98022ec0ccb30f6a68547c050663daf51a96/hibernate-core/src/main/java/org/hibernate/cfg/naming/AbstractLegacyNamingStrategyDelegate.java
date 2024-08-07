/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2014, Red Hat, Inc. and/or its affiliates or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat, Inc.
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
package org.hibernate.cfg.naming;

import java.io.Serializable;

import org.hibernate.cfg.NamingStrategy;

/**
 * @author Gail Badner
 */
public abstract class AbstractLegacyNamingStrategyDelegate implements NamingStrategyDelegate, Serializable {
	private final LegacyNamingStrategyDelegate.LegacyNamingStrategyDelegateContext context;

	public AbstractLegacyNamingStrategyDelegate(LegacyNamingStrategyDelegate.LegacyNamingStrategyDelegateContext context) {
		this.context = context;
	}

	protected NamingStrategy getNamingStrategy() {
		return context.getNamingStrategy();
	}

	@Override
	public String determinePrimaryTableLogicalName(String entityName, String jpaEntityName) {
		// jpaEntity name is being passed here in order to not cause a regression. See HHH-4312.
		return getNamingStrategy().classToTableName( jpaEntityName );
	}

	@Override
	public String toPhysicalTableName(String tableName) {
		return getNamingStrategy().tableName( tableName );
	}

	@Override
	public String toPhysicalColumnName(String columnName) {
		return getNamingStrategy().columnName( columnName );
	}

	@Override
	public String determineAttributeColumnName(String propertyName) {
		return getNamingStrategy().propertyToColumnName( propertyName );
	}

	@Override
	public String determineJoinKeyColumnName(String joinedColumn, String joinedTable) {
		return getNamingStrategy().joinKeyColumnName( joinedColumn, joinedTable );
	}

	@Override
	public String logicalColumnName(String columnName, String propertyName) {
		return getNamingStrategy().logicalColumnName( columnName, propertyName );
	}

	@Override
	public String logicalCollectionColumnName(String columnName, String propertyName, String referencedColumn) {
		return getNamingStrategy().logicalCollectionColumnName( columnName, propertyName, referencedColumn );
	}
}
