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
package org.hibernate.type.descriptor.sql;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.java.WrapperOptions;

import static org.hibernate.type.descriptor.sql.SqlTypeDescriptor.Extractor;

/**
 * Convenience base implementation of {@link Extractor}
 *
 * @author Steve Ebersole
 */
public abstract class BasicExtractor<J> implements Extractor<J> {
	private static final Logger log = LoggerFactory.getLogger( BasicExtractor.class );

	private final JavaTypeDescriptor<J> javaDescriptor;
	private final SqlTypeDescriptor sqlDescriptor;

	public BasicExtractor(JavaTypeDescriptor<J> javaDescriptor, SqlTypeDescriptor sqlDescriptor) {
		this.javaDescriptor = javaDescriptor;
		this.sqlDescriptor = sqlDescriptor;
	}

	public JavaTypeDescriptor<J> getJavaDescriptor() {
		return javaDescriptor;
	}

	public SqlTypeDescriptor getSqlDescriptor() {
		return sqlDescriptor;
	}

	/**
	 * {@inheritDoc}
	 */
	public J extract(ResultSet rs, String name, WrapperOptions options) throws SQLException {
		final J value = doExtract( rs, name, options );
		if ( value == null || rs.wasNull() ) {
			log.trace( "found [null] as column [{}]", name );
			return null;
		}
		else {
			log.trace( "found [{}] as column [{}]", getJavaDescriptor().extractLoggableRepresentation( value ), name );
			return value;
		}
	}

	/**
	 * Perform the extraction.
	 * <p/>
	 * Called from {@link #extract}.  Null checking of the value (as well as consulting {@link ResultSet#wasNull}) is
	 * done there.
	 *
	 * @param rs The result set
	 * @param name The value name in the result set
	 * @param options The binding options
	 *
	 * @return The extracted value.
	 *
	 * @throws SQLException Indicates a problem access the result set
	 */
	protected abstract J doExtract(ResultSet rs, String name, WrapperOptions options) throws SQLException;
}
