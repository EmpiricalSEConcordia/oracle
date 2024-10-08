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
package org.hibernate.engine.loading;

import static org.jboss.logging.Logger.Level.WARN;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.LogMessage;
import org.jboss.logging.Message;
import org.jboss.logging.MessageLogger;

/**
 * {@inheritDoc}
 *
 * @author Steve Ebersole
 */
public class EntityLoadContext {

    private static final Logger LOG = org.jboss.logging.Logger.getMessageLogger(Logger.class,
                                                                                EntityLoadContext.class.getPackage().getName());

	private final LoadContexts loadContexts;
	private final ResultSet resultSet;
	private final List hydratingEntities = new ArrayList( 20 ); // todo : need map? the prob is a proper key, right?

	public EntityLoadContext(LoadContexts loadContexts, ResultSet resultSet) {
		this.loadContexts = loadContexts;
		this.resultSet = resultSet;
	}

	void cleanup() {
        if (!hydratingEntities.isEmpty()) LOG.hydratingEntitiesCount(hydratingEntities.size());
		hydratingEntities.clear();
	}


	@Override
    public String toString() {
		return super.toString() + "<rs=" + resultSet + ">";
	}

    /**
     * Interface defining messages that may be logged by the outer class
     */
    @MessageLogger
    interface Logger extends BasicLogger {

        @LogMessage( level = WARN )
        @Message( value = "On EntityLoadContext#clear, hydratingEntities contained [%d] entries" )
        void hydratingEntitiesCount( int size );
    }
}
