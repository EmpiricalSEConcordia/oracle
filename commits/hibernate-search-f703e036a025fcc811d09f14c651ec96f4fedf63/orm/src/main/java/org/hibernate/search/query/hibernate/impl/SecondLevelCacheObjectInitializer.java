/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.query.hibernate.impl;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.search.engine.spi.SearchFactoryImplementor;
import org.hibernate.search.util.logging.impl.Log;

import org.hibernate.Criteria;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.Session;
import org.hibernate.search.query.engine.spi.EntityInfo;
import org.hibernate.search.query.engine.spi.TimeoutManager;
import org.hibernate.search.util.logging.impl.LoggerFactory;

/**
 * Check if the entity is available in the second level cache and load it if there
 * before falling back to the delegate method.
 *
 * @author Emmanuel Bernard <emmanuel@hibernate.org>
 */
public class SecondLevelCacheObjectInitializer implements ObjectInitializer {
	private static final Log log = LoggerFactory.make();
	private final ObjectInitializer delegate;

	public SecondLevelCacheObjectInitializer(ObjectInitializer delegate) {
		this.delegate = delegate;
	}

	@Override
	public void initializeObjects(EntityInfo[] entityInfos,
										Criteria criteria,
										Class<?> entityType,
										SearchFactoryImplementor searchFactoryImplementor,
										TimeoutManager timeoutManager,
										Session session) {
		boolean traceEnabled = log.isTraceEnabled();
		//Do not call isTimeOut here as the caller might be the last biggie on the list.
		final int maxResults = entityInfos.length;
		if ( maxResults == 0 ) {
			if ( traceEnabled ) {
				log.tracef( "No object to initialize", maxResults );
			}
			return;
		}

		//check the second-level cache
		List<EntityInfo> remainingEntityInfos = new ArrayList<>( entityInfos.length );
		for ( EntityInfo entityInfo : entityInfos ) {
			if ( ObjectLoaderHelper.areDocIdAndEntityIdIdentical( entityInfo, session ) ) {
				final boolean isIn2LCache = session.getSessionFactory().getCache().containsEntity( entityInfo.getClazz(), entityInfo.getId() );
				if ( isIn2LCache ) {
					try {
						//load the object from the second level cache
						session.get( entityInfo.getClazz(), entityInfo.getId() );
					}
					catch (ObjectNotFoundException onfe) {
						// Unlikely but needed: an index might be out of sync, and the cache might be as well
						remainingEntityInfos.add( entityInfo );
					}
				}
				else {
					remainingEntityInfos.add( entityInfo );
				}
			}
			else {
				//if document id !=  entity id we can't use 2LC
				remainingEntityInfos.add( entityInfo );
			}

		}
		//update entityInfos to only contains the remaining ones
		final int remainingSize = remainingEntityInfos.size();

		if ( traceEnabled ) {
			log.tracef( "Initialized %d objects out of %d in the second level cache", maxResults - remainingSize, maxResults );
		}
		if ( remainingSize > 0 ) {
			delegate.initializeObjects(
					remainingEntityInfos.toArray( new EntityInfo[remainingSize] ),
					criteria,
					entityType,
					searchFactoryImplementor,
					timeoutManager,
					session
			);
		}
	}
}
