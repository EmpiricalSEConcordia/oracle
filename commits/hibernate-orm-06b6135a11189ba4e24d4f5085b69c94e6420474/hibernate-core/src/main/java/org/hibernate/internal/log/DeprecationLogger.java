/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2014, Red Hat Inc. or third-party contributors as
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
package org.hibernate.internal.log;

import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

/**
 * Class to consolidate logging about usage of deprecated features.
 *
 * @author Steve Ebersole
 */
@MessageLogger( projectCode = "HHH" )
@ValidIdRange( min = 90000001, max = 90001000 )
public interface DeprecationLogger {
	public static final DeprecationLogger DEPRECATION_LOGGER = Logger.getMessageLogger(
			DeprecationLogger.class,
			"org.hibernate.orm.deprecation"
	);

	/**
	 * Log about usage of deprecated Scanner setting
	 */
	@LogMessage( level = INFO )
	@Message(
			value = "Found usage of deprecated setting for specifying Scanner [hibernate.ejb.resource_scanner]; " +
					"use [hibernate.archive.scanner] instead",
			id = 90000001
	)
	public void logDeprecatedScannerSetting();

	/**
	 * Log message indicating the use of multiple EntityModes for a single entity.
	 */
	@LogMessage( level = WARN )
	@Message(
			value = "Support for an entity defining multiple entity-modes is deprecated",
			id = 90000002
	)
	public void logDeprecationOfMultipleEntityModeSupport();

	/**
	 * Log message indicating the use of DOM4J EntityMode.
	 */
	@LogMessage( level = WARN )
	@Message(
			value = "Use of DOM4J entity-mode is considered deprecated",
			id = 90000003
	)
	public void logDeprecationOfDomEntityModeSupport();

	@LogMessage(level = WARN)
	@Message(
			value = "embed-xml attributes were intended to be used for DOM4J entity mode. Since that entity mode has been " +
					"removed, embed-xml attributes are no longer supported and should be removed from mappings.",
			id = 90000004
	)
	public void logDeprecationOfEmbedXmlSupport();

	@LogMessage(level = WARN)
	@Message(
			value = "Defining an entity [%s] with no physical id attribute is no longer supported; please map the " +
					"identifier to a physical entity attribute",
			id = 90000005
	)
	public void logDeprecationOfNonNamedIdAttribute(String entityName);

	/**
	 * Log a warning about an attempt to specify no-longer-supported NamingStrategy
	 *
	 * @param setting - The old setting that indicates the NamingStrategy to use
	 * @param implicitInstead - The new setting that indicates the ImplicitNamingStrategy to use
	 * @param physicalInstead - The new setting that indicates the PhysicalNamingStrategy to use
	 */
	@LogMessage(level = WARN)
	@Message(
			value = "Attempted to specify unsupported NamingStrategy via setting [%s]; NamingStrategy " +
					"has been removed in favor of the split ImplicitNamingStrategy and " +
					"PhysicalNamingStrategy; use [%s] or [%s], respectively, instead.",
			id = 90000006
	)
	void logDeprecatedNamingStrategySetting(String setting, String implicitInstead, String physicalInstead);

	/**
	 * Log a warning about an attempt to specify unsupported NamingStrategy
	 */
	@LogMessage(level = WARN)
	@Message(
			value = "Attempted to specify unsupported NamingStrategy via command-line argument [--naming]. " +
					"NamingStrategy has been removed in favor of the split ImplicitNamingStrategy and " +
					"PhysicalNamingStrategy; use [--implicit-naming] or [--physical-naming], respectively, instead.",
			id = 90000007
	)
	void logDeprecatedNamingStrategyArgument();

	/**
	 * Log a warning about an attempt to specify unsupported NamingStrategy
	 */
	@LogMessage(level = WARN)
	@Message(
			value = "Attempted to specify unsupported NamingStrategy via Ant task argument. " +
					"NamingStrategy has been removed in favor of the split ImplicitNamingStrategy and " +
					"PhysicalNamingStrategy.",
			id = 90000008
	)
	void logDeprecatedNamingStrategyAntArgument();

	@LogMessage(level = WARN)
	@Message(
			value = "The outer-join attribute on <many-to-many> has been deprecated. " +
					"Instead of outer-join=\"false\", use lazy=\"extra\" with <map>, <set>, " +
					"<bag>, <idbag>, or <list>, which will only initialize entities (not as " +
					"a proxy) as needed.",
			id = 90000009
	)
	void deprecatedManyToManyOuterJoin();

	@LogMessage(level = WARN)
	@Message(
			value = "The fetch attribute on <many-to-many> has been deprecated. " +
					"Instead of fetch=\"select\", use lazy=\"extra\" with <map>, <set>, " +
					"<bag>, <idbag>, or <list>, which will only initialize entities (not as " +
					"a proxy) as needed.",
			id = 90000010
	)
	void deprecatedManyToManyFetch();


	@LogMessage(level = WARN)
	@Message(
			value = "org.hibernate.hql.spi.TemporaryTableBulkIdStrategy (temporary) has been deprecated in favor of the" +
					" more specific org.hibernate.hql.spi.LocalTemporaryTableBulkIdStrategy (local_temporary).",
			id = 90000010
	)
	void logDeprecationOfTemporaryTableBulkIdStrategy();
}
