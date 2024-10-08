/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2013, Red Hat Inc. or third-party contributors as
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
package org.hibernate.envers.internal.tools;

import java.util.Iterator;
import java.util.Locale;

/**
 * @author Adam Warski (adam at warski dot org)
 * @author Lukasz Zuchowski (author at zuchos dot com)
 */
public abstract class StringTools {
	public static boolean isEmpty(String s) {
		return s == null || "".equals( s );
	}

	public static boolean isEmpty(Object o) {
		return o == null || "".equals( o );
	}

	/**
	 * @param s String, from which to get the last component.
	 *
	 * @return The last component of the dot-separated string <code>s</code>. For example, for a string
	 * "a.b.c", the result is "c".
	 */
	public static String getLastComponent(String s) {
		if ( s == null ) {
			return null;
		}
		final int lastDot = s.lastIndexOf( "." );
		if ( lastDot == -1 ) {
			return s;
		}
		else {
			return s.substring( lastDot + 1 );
		}
	}

	/**
	 * To the given string builder, appends all strings in the given iterator, separating them with the given
	 * separator. For example, for an interator "a" "b" "c" and separator ":" the output is "a:b:c".
	 *
	 * @param sb String builder, to which to append.
	 * @param contents Strings to be appended.
	 * @param separator Separator between subsequent content.
	 */
	public static void append(StringBuilder sb, Iterator<String> contents, String separator) {
		boolean isFirst = true;
		while ( contents.hasNext() ) {
			if ( !isFirst ) {
				sb.append( separator );
			}
			sb.append( contents.next() );
			isFirst = false;
		}
	}

	/**
	 * Capitalizes first letter of the string
	 *
	 * @param fieldName
	 *
	 * @return capitalized string
	 */
	public static String capitalizeFirst(String fieldName) {
		return fieldName.substring( 0, 1 ).toUpperCase( Locale.ROOT ) + fieldName.substring( 1 );
	}
}
