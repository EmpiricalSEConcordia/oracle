/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.indexes.serialization.avro.logging.impl;

import org.hibernate.search.exception.SearchException;
import org.jboss.logging.Message;
import org.jboss.logging.MessageLogger;

/**
 * Hibernate Search Avro Serialization log abstraction.
 *
 * @author Hardy Ferentschik
 */
@MessageLogger(projectCode = "HSEARCH")
public interface Log extends org.hibernate.search.util.logging.impl.Log {

	@Message(id = AVRO_SERIALIZATION_MESSAGES_START_ID + 1, value = "Unable to find Avro schema '%s'")
	SearchException unableToLoadAvroSchema(String avroSchemaFile);
}
