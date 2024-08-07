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
package org.hibernate.stat.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import org.hibernate.cache.spi.Region;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.service.Service;
import org.hibernate.stat.CollectionStatistics;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.spi.StatisticsImplementor;

/**
 * Implementation of {@link org.hibernate.stat.Statistics} based on the {@link java.util.concurrent} package.
 *
 * @author Alex Snaps
 */
@SuppressWarnings({ "unchecked" })
public class ConcurrentStatisticsImpl implements StatisticsImplementor, Service {

    private static final CoreMessageLogger LOG = Logger.getMessageLogger(CoreMessageLogger.class, ConcurrentStatisticsImpl.class.getName());

	private SessionFactoryImplementor sessionFactory;

	private volatile boolean isStatisticsEnabled;
	private volatile long startTime;
	private AtomicLong sessionOpenCount = new AtomicLong();
	private AtomicLong sessionCloseCount = new AtomicLong();
	private AtomicLong flushCount = new AtomicLong();
	private AtomicLong connectCount = new AtomicLong();

	private AtomicLong prepareStatementCount = new AtomicLong();
	private AtomicLong closeStatementCount = new AtomicLong();

	private AtomicLong entityLoadCount = new AtomicLong();
	private AtomicLong entityUpdateCount = new AtomicLong();
	private AtomicLong entityInsertCount = new AtomicLong();
	private AtomicLong entityDeleteCount = new AtomicLong();
	private AtomicLong entityFetchCount = new AtomicLong();
	private AtomicLong collectionLoadCount = new AtomicLong();
	private AtomicLong collectionUpdateCount = new AtomicLong();
	private AtomicLong collectionRemoveCount = new AtomicLong();
	private AtomicLong collectionRecreateCount = new AtomicLong();
	private AtomicLong collectionFetchCount = new AtomicLong();

	private AtomicLong secondLevelCacheHitCount = new AtomicLong();
	private AtomicLong secondLevelCacheMissCount = new AtomicLong();
	private AtomicLong secondLevelCachePutCount = new AtomicLong();

	private AtomicLong queryExecutionCount = new AtomicLong();
	private AtomicLong queryExecutionMaxTime = new AtomicLong();
	private volatile String queryExecutionMaxTimeQueryString;
	private AtomicLong queryCacheHitCount = new AtomicLong();
	private AtomicLong queryCacheMissCount = new AtomicLong();
	private AtomicLong queryCachePutCount = new AtomicLong();

	private AtomicLong committedTransactionCount = new AtomicLong();
	private AtomicLong transactionCount = new AtomicLong();

	private AtomicLong optimisticFailureCount = new AtomicLong();

	/**
	 * second level cache statistics per region
	 */
	private final ConcurrentMap secondLevelCacheStatistics = new ConcurrentHashMap();
	/**
	 * entity statistics per name
	 */
	private final ConcurrentMap entityStatistics = new ConcurrentHashMap();
	/**
	 * collection statistics per name
	 */
	private final ConcurrentMap collectionStatistics = new ConcurrentHashMap();
	/**
	 * entity statistics per query string (HQL or SQL)
	 */
	private final ConcurrentMap queryStatistics = new ConcurrentHashMap();

	@SuppressWarnings({ "UnusedDeclaration" })
	public ConcurrentStatisticsImpl() {
		clear();
	}

	public ConcurrentStatisticsImpl(SessionFactoryImplementor sessionFactory) {
		clear();
		this.sessionFactory = sessionFactory;
	}

	/**
	 * reset all statistics
	 */
	public void clear() {
		secondLevelCacheHitCount.set( 0 );
		secondLevelCacheMissCount.set( 0 );
		secondLevelCachePutCount.set( 0 );

		sessionCloseCount.set( 0 );
		sessionOpenCount.set( 0 );
		flushCount.set( 0 );
		connectCount.set( 0 );

		prepareStatementCount.set( 0 );
		closeStatementCount.set( 0 );

		entityDeleteCount.set( 0 );
		entityInsertCount.set( 0 );
		entityUpdateCount.set( 0 );
		entityLoadCount.set( 0 );
		entityFetchCount.set( 0 );

		collectionRemoveCount.set( 0 );
		collectionUpdateCount.set( 0 );
		collectionRecreateCount.set( 0 );
		collectionLoadCount.set( 0 );
		collectionFetchCount.set( 0 );

		queryExecutionCount.set( 0 );
		queryCacheHitCount.set( 0 );
		queryExecutionMaxTime.set( 0 );
		queryExecutionMaxTimeQueryString = null;
		queryCacheMissCount.set( 0 );
		queryCachePutCount.set( 0 );

		transactionCount.set( 0 );
		committedTransactionCount.set( 0 );

		optimisticFailureCount.set( 0 );

		secondLevelCacheStatistics.clear();
		entityStatistics.clear();
		collectionStatistics.clear();
		queryStatistics.clear();

		startTime = System.currentTimeMillis();
	}

	public void openSession() {
		sessionOpenCount.getAndIncrement();
	}

	public void closeSession() {
		sessionCloseCount.getAndIncrement();
	}

	public void flush() {
		flushCount.getAndIncrement();
	}

	public void connect() {
		connectCount.getAndIncrement();
	}

	public void loadEntity(String entityName) {
		entityLoadCount.getAndIncrement();
		( (ConcurrentEntityStatisticsImpl) getEntityStatistics( entityName ) ).incrementLoadCount();
	}

	public void fetchEntity(String entityName) {
		entityFetchCount.getAndIncrement();
		( (ConcurrentEntityStatisticsImpl) getEntityStatistics( entityName ) ).incrementFetchCount();
	}

	/**
	 * find entity statistics per name
	 *
	 * @param entityName entity name
	 *
	 * @return EntityStatistics object
	 */
	public EntityStatistics getEntityStatistics(String entityName) {
		ConcurrentEntityStatisticsImpl es = (ConcurrentEntityStatisticsImpl) entityStatistics.get( entityName );
		if ( es == null ) {
			es = new ConcurrentEntityStatisticsImpl( entityName );
			ConcurrentEntityStatisticsImpl previous;
			if ( ( previous = (ConcurrentEntityStatisticsImpl) entityStatistics.putIfAbsent(
					entityName, es
			) ) != null ) {
				es = previous;
			}
		}
		return es;
	}

	public void updateEntity(String entityName) {
		entityUpdateCount.getAndIncrement();
		ConcurrentEntityStatisticsImpl es = (ConcurrentEntityStatisticsImpl) getEntityStatistics( entityName );
		es.incrementUpdateCount();
	}

	public void insertEntity(String entityName) {
		entityInsertCount.getAndIncrement();
		ConcurrentEntityStatisticsImpl es = (ConcurrentEntityStatisticsImpl) getEntityStatistics( entityName );
		es.incrementInsertCount();
	}

	public void deleteEntity(String entityName) {
		entityDeleteCount.getAndIncrement();
		ConcurrentEntityStatisticsImpl es = (ConcurrentEntityStatisticsImpl) getEntityStatistics( entityName );
		es.incrementDeleteCount();
	}

	/**
	 * Get collection statistics per role
	 *
	 * @param role collection role
	 *
	 * @return CollectionStatistics
	 */
	public CollectionStatistics getCollectionStatistics(String role) {
		ConcurrentCollectionStatisticsImpl cs = (ConcurrentCollectionStatisticsImpl) collectionStatistics.get( role );
		if ( cs == null ) {
			cs = new ConcurrentCollectionStatisticsImpl( role );
			ConcurrentCollectionStatisticsImpl previous;
			if ( ( previous = (ConcurrentCollectionStatisticsImpl) collectionStatistics.putIfAbsent(
					role, cs
			) ) != null ) {
				cs = previous;
			}
		}
		return cs;
	}

	public void loadCollection(String role) {
		collectionLoadCount.getAndIncrement();
		( (ConcurrentCollectionStatisticsImpl) getCollectionStatistics( role ) ).incrementLoadCount();
	}

	public void fetchCollection(String role) {
		collectionFetchCount.getAndIncrement();
		( (ConcurrentCollectionStatisticsImpl) getCollectionStatistics( role ) ).incrementFetchCount();
	}

	public void updateCollection(String role) {
		collectionUpdateCount.getAndIncrement();
		( (ConcurrentCollectionStatisticsImpl) getCollectionStatistics( role ) ).incrementUpdateCount();
	}

	public void recreateCollection(String role) {
		collectionRecreateCount.getAndIncrement();
		( (ConcurrentCollectionStatisticsImpl) getCollectionStatistics( role ) ).incrementRecreateCount();
	}

	public void removeCollection(String role) {
		collectionRemoveCount.getAndIncrement();
		( (ConcurrentCollectionStatisticsImpl) getCollectionStatistics( role ) ).incrementRemoveCount();
	}

	/**
	 * Second level cache statistics per region
	 *
	 * @param regionName region name
	 *
	 * @return SecondLevelCacheStatistics
	 */
	public SecondLevelCacheStatistics getSecondLevelCacheStatistics(String regionName) {
		ConcurrentSecondLevelCacheStatisticsImpl slcs
				= (ConcurrentSecondLevelCacheStatisticsImpl) secondLevelCacheStatistics.get( regionName );
		if ( slcs == null ) {
			if ( sessionFactory == null ) {
				return null;
			}
			Region region = sessionFactory.getSecondLevelCacheRegion( regionName );
			if ( region == null ) {
				return null;
			}
			slcs = new ConcurrentSecondLevelCacheStatisticsImpl( region );
			ConcurrentSecondLevelCacheStatisticsImpl previous;
			if ( ( previous = (ConcurrentSecondLevelCacheStatisticsImpl) secondLevelCacheStatistics.putIfAbsent(
					regionName, slcs
			) ) != null ) {
				slcs = previous;
			}
		}
		return slcs;
	}

	public void secondLevelCachePut(String regionName) {
		secondLevelCachePutCount.getAndIncrement();
		( (ConcurrentSecondLevelCacheStatisticsImpl) getSecondLevelCacheStatistics( regionName ) ).incrementPutCount();
	}

	public void secondLevelCacheHit(String regionName) {
		secondLevelCacheHitCount.getAndIncrement();
		( (ConcurrentSecondLevelCacheStatisticsImpl) getSecondLevelCacheStatistics( regionName ) ).incrementHitCount();
	}

	public void secondLevelCacheMiss(String regionName) {
		secondLevelCacheMissCount.getAndIncrement();
		( (ConcurrentSecondLevelCacheStatisticsImpl) getSecondLevelCacheStatistics( regionName ) ).incrementMissCount();
	}

	@SuppressWarnings({ "UnnecessaryBoxing" })
	public void queryExecuted(String hql, int rows, long time) {
        LOG.hql(hql, Long.valueOf(time), Long.valueOf(rows));
		queryExecutionCount.getAndIncrement();
		boolean isLongestQuery = false;
		for ( long old = queryExecutionMaxTime.get();
			  ( isLongestQuery = time > old ) && ( !queryExecutionMaxTime.compareAndSet( old, time ) );
			  old = queryExecutionMaxTime.get() ) {
			// nothing to do here given the odd loop structure...
		}
		if ( isLongestQuery ) {
			queryExecutionMaxTimeQueryString = hql;
		}
		if ( hql != null ) {
			ConcurrentQueryStatisticsImpl qs = (ConcurrentQueryStatisticsImpl) getQueryStatistics( hql );
			qs.executed( rows, time );
		}
	}

	public void queryCacheHit(String hql, String regionName) {
		queryCacheHitCount.getAndIncrement();
		if ( hql != null ) {
			ConcurrentQueryStatisticsImpl qs = (ConcurrentQueryStatisticsImpl) getQueryStatistics( hql );
			qs.incrementCacheHitCount();
		}
		ConcurrentSecondLevelCacheStatisticsImpl slcs = (ConcurrentSecondLevelCacheStatisticsImpl) getSecondLevelCacheStatistics(
				regionName
		);
		slcs.incrementHitCount();
	}

	public void queryCacheMiss(String hql, String regionName) {
		queryCacheMissCount.getAndIncrement();
		if ( hql != null ) {
			ConcurrentQueryStatisticsImpl qs = (ConcurrentQueryStatisticsImpl) getQueryStatistics( hql );
			qs.incrementCacheMissCount();
		}
		ConcurrentSecondLevelCacheStatisticsImpl slcs = (ConcurrentSecondLevelCacheStatisticsImpl) getSecondLevelCacheStatistics(
				regionName
		);
		slcs.incrementMissCount();
	}

	public void queryCachePut(String hql, String regionName) {
		queryCachePutCount.getAndIncrement();
		if ( hql != null ) {
			ConcurrentQueryStatisticsImpl qs = (ConcurrentQueryStatisticsImpl) getQueryStatistics( hql );
			qs.incrementCachePutCount();
		}
		ConcurrentSecondLevelCacheStatisticsImpl slcs = (ConcurrentSecondLevelCacheStatisticsImpl) getSecondLevelCacheStatistics(
				regionName
		);
		slcs.incrementPutCount();
	}

	/**
	 * Query statistics from query string (HQL or SQL)
	 *
	 * @param queryString query string
	 *
	 * @return QueryStatistics
	 */
	public QueryStatistics getQueryStatistics(String queryString) {
		ConcurrentQueryStatisticsImpl qs = (ConcurrentQueryStatisticsImpl) queryStatistics.get( queryString );
		if ( qs == null ) {
			qs = new ConcurrentQueryStatisticsImpl( queryString );
			ConcurrentQueryStatisticsImpl previous;
			if ( ( previous = (ConcurrentQueryStatisticsImpl) queryStatistics.putIfAbsent(
					queryString, qs
			) ) != null ) {
				qs = previous;
			}
		}
		return qs;
	}

	/**
	 * @return entity deletion count
	 */
	public long getEntityDeleteCount() {
		return entityDeleteCount.get();
	}

	/**
	 * @return entity insertion count
	 */
	public long getEntityInsertCount() {
		return entityInsertCount.get();
	}

	/**
	 * @return entity load (from DB)
	 */
	public long getEntityLoadCount() {
		return entityLoadCount.get();
	}

	/**
	 * @return entity fetch (from DB)
	 */
	public long getEntityFetchCount() {
		return entityFetchCount.get();
	}

	/**
	 * @return entity update
	 */
	public long getEntityUpdateCount() {
		return entityUpdateCount.get();
	}

	public long getQueryExecutionCount() {
		return queryExecutionCount.get();
	}

	public long getQueryCacheHitCount() {
		return queryCacheHitCount.get();
	}

	public long getQueryCacheMissCount() {
		return queryCacheMissCount.get();
	}

	public long getQueryCachePutCount() {
		return queryCachePutCount.get();
	}

	/**
	 * @return flush
	 */
	public long getFlushCount() {
		return flushCount.get();
	}

	/**
	 * @return session connect
	 */
	public long getConnectCount() {
		return connectCount.get();
	}

	/**
	 * @return second level cache hit
	 */
	public long getSecondLevelCacheHitCount() {
		return secondLevelCacheHitCount.get();
	}

	/**
	 * @return second level cache miss
	 */
	public long getSecondLevelCacheMissCount() {
		return secondLevelCacheMissCount.get();
	}

	/**
	 * @return second level cache put
	 */
	public long getSecondLevelCachePutCount() {
		return secondLevelCachePutCount.get();
	}

	/**
	 * @return session closing
	 */
	public long getSessionCloseCount() {
		return sessionCloseCount.get();
	}

	/**
	 * @return session opening
	 */
	public long getSessionOpenCount() {
		return sessionOpenCount.get();
	}

	/**
	 * @return collection loading (from DB)
	 */
	public long getCollectionLoadCount() {
		return collectionLoadCount.get();
	}

	/**
	 * @return collection fetching (from DB)
	 */
	public long getCollectionFetchCount() {
		return collectionFetchCount.get();
	}

	/**
	 * @return collection update
	 */
	public long getCollectionUpdateCount() {
		return collectionUpdateCount.get();
	}

	/**
	 * @return collection removal
	 *         FIXME: even if isInverse="true"?
	 */
	public long getCollectionRemoveCount() {
		return collectionRemoveCount.get();
	}

	/**
	 * @return collection recreation
	 */
	public long getCollectionRecreateCount() {
		return collectionRecreateCount.get();
	}

	/**
	 * @return start time in ms (JVM standards {@link System#currentTimeMillis()})
	 */
	public long getStartTime() {
		return startTime;
	}

	/**
	 * log in info level the main statistics
	 */
	public void logSummary() {
        LOG.loggingStatistics();
        LOG.startTime(startTime);
        LOG.sessionsOpened(sessionOpenCount.get());
        LOG.sessionsClosed(sessionCloseCount.get());
        LOG.transactions(transactionCount.get());
        LOG.successfulTransactions(committedTransactionCount.get());
        LOG.optimisticLockFailures(optimisticFailureCount.get());
        LOG.flushes(flushCount.get());
        LOG.connectionsObtained(connectCount.get());
        LOG.statementsPrepared(prepareStatementCount.get());
        LOG.statementsClosed(closeStatementCount.get());
        LOG.secondLevelCachePuts(secondLevelCachePutCount.get());
        LOG.secondLevelCacheHits(secondLevelCacheHitCount.get());
        LOG.secondLevelCacheMisses(secondLevelCacheMissCount.get());
        LOG.entitiesLoaded(entityLoadCount.get());
        LOG.entitiesUpdated(entityUpdateCount.get());
        LOG.entitiesInserted(entityInsertCount.get());
        LOG.entitiesDeleted(entityDeleteCount.get());
        LOG.entitiesFetched(entityFetchCount.get());
        LOG.collectionsLoaded(collectionLoadCount.get());
        LOG.collectionsUpdated(collectionUpdateCount.get());
        LOG.collectionsRemoved(collectionRemoveCount.get());
        LOG.collectionsRecreated(collectionRecreateCount.get());
        LOG.collectionsFetched(collectionFetchCount.get());
        LOG.queriesExecuted(queryExecutionCount.get());
        LOG.queryCachePuts(queryCachePutCount.get());
        LOG.queryCacheHits(queryCacheHitCount.get());
        LOG.queryCacheMisses(queryCacheMissCount.get());
        LOG.maxQueryTime(queryExecutionMaxTime.get());
	}

	/**
	 * Are statistics logged
	 */
	public boolean isStatisticsEnabled() {
		return isStatisticsEnabled;
	}

	/**
	 * Enable statistics logs (this is a dynamic parameter)
	 */
	public void setStatisticsEnabled(boolean b) {
		isStatisticsEnabled = b;
	}

	/**
	 * @return Returns the max query execution time,
	 *         for all queries
	 */
	public long getQueryExecutionMaxTime() {
		return queryExecutionMaxTime.get();
	}

	/**
	 * Get all executed query strings
	 */
	public String[] getQueries() {
		return ArrayHelper.toStringArray( queryStatistics.keySet() );
	}

	/**
	 * Get the names of all entities
	 */
	public String[] getEntityNames() {
		if ( sessionFactory == null ) {
			return ArrayHelper.toStringArray( entityStatistics.keySet() );
		}
		else {
			return ArrayHelper.toStringArray( sessionFactory.getAllClassMetadata().keySet() );
		}
	}

	/**
	 * Get the names of all collection roles
	 */
	public String[] getCollectionRoleNames() {
		if ( sessionFactory == null ) {
			return ArrayHelper.toStringArray( collectionStatistics.keySet() );
		}
		else {
			return ArrayHelper.toStringArray( sessionFactory.getAllCollectionMetadata().keySet() );
		}
	}

	/**
	 * Get all second-level cache region names
	 */
	public String[] getSecondLevelCacheRegionNames() {
		if ( sessionFactory == null ) {
			return ArrayHelper.toStringArray( secondLevelCacheStatistics.keySet() );
		}
		else {
			return ArrayHelper.toStringArray( sessionFactory.getAllSecondLevelCacheRegions().keySet() );
		}
	}

	public void endTransaction(boolean success) {
		transactionCount.getAndIncrement();
		if ( success ) {
			committedTransactionCount.getAndIncrement();
		}
	}

	public long getSuccessfulTransactionCount() {
		return committedTransactionCount.get();
	}

	public long getTransactionCount() {
		return transactionCount.get();
	}

	public void closeStatement() {
		closeStatementCount.getAndIncrement();
	}

	public void prepareStatement() {
		prepareStatementCount.getAndIncrement();
	}

	public long getCloseStatementCount() {
		return closeStatementCount.get();
	}

	public long getPrepareStatementCount() {
		return prepareStatementCount.get();
	}

	public void optimisticFailure(String entityName) {
		optimisticFailureCount.getAndIncrement();
		( (ConcurrentEntityStatisticsImpl) getEntityStatistics( entityName ) ).incrementOptimisticFailureCount();
	}

	public long getOptimisticFailureCount() {
		return optimisticFailureCount.get();
	}

	@Override
    public String toString() {
		return new StringBuilder()
				.append( "Statistics[" )
				.append( "start time=" ).append( startTime )
				.append( ",sessions opened=" ).append( sessionOpenCount )
				.append( ",sessions closed=" ).append( sessionCloseCount )
				.append( ",transactions=" ).append( transactionCount )
				.append( ",successful transactions=" ).append( committedTransactionCount )
				.append( ",optimistic lock failures=" ).append( optimisticFailureCount )
				.append( ",flushes=" ).append( flushCount )
				.append( ",connections obtained=" ).append( connectCount )
				.append( ",statements prepared=" ).append( prepareStatementCount )
				.append( ",statements closed=" ).append( closeStatementCount )
				.append( ",second level cache puts=" ).append( secondLevelCachePutCount )
				.append( ",second level cache hits=" ).append( secondLevelCacheHitCount )
				.append( ",second level cache misses=" ).append( secondLevelCacheMissCount )
				.append( ",entities loaded=" ).append( entityLoadCount )
				.append( ",entities updated=" ).append( entityUpdateCount )
				.append( ",entities inserted=" ).append( entityInsertCount )
				.append( ",entities deleted=" ).append( entityDeleteCount )
				.append( ",entities fetched=" ).append( entityFetchCount )
				.append( ",collections loaded=" ).append( collectionLoadCount )
				.append( ",collections updated=" ).append( collectionUpdateCount )
				.append( ",collections removed=" ).append( collectionRemoveCount )
				.append( ",collections recreated=" ).append( collectionRecreateCount )
				.append( ",collections fetched=" ).append( collectionFetchCount )
				.append( ",queries executed to database=" ).append( queryExecutionCount )
				.append( ",query cache puts=" ).append( queryCachePutCount )
				.append( ",query cache hits=" ).append( queryCacheHitCount )
				.append( ",query cache misses=" ).append( queryCacheMissCount )
				.append( ",max query time=" ).append( queryExecutionMaxTime )
				.append( ']' )
				.toString();
	}

	public String getQueryExecutionMaxTimeQueryString() {
		return queryExecutionMaxTimeQueryString;
	}
}
