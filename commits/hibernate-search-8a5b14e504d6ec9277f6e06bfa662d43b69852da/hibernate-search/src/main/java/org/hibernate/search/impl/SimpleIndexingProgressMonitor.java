/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 *  Copyright (c) 2010, Red Hat, Inc. and/or its affiliates or third-party contributors as
 *  indicated by the @author tags or express copyright attribution
 *  statements applied by the authors.  All third-party contributions are
 *  distributed under license by Red Hat, Inc.
 *
 *  This copyrighted material is made available to anyone wishing to use, modify,
 *  copy, or redistribute it subject to the terms and conditions of the GNU
 *  Lesser General Public License, as published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this distribution; if not, write to:
 *  Free Software Foundation, Inc.
 *  51 Franklin Street, Fifth Floor
 *  Boston, MA  02110-1301  USA
 */
package org.hibernate.search.impl;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;

import org.hibernate.search.batchindexing.MassIndexerProgressMonitor;
import org.hibernate.search.util.LoggerFactory;

/**
 * A very simple implementation of {@code MassIndexerProgressMonitor}.
 *
 * @author Sanne Grinovero
 */
public class SimpleIndexingProgressMonitor implements MassIndexerProgressMonitor {

	private static final Logger log = LoggerFactory.make();
	private final AtomicLong documentsDoneCounter = new AtomicLong();
	private final AtomicLong totalCounter = new AtomicLong();
	private volatile long startTimeMs;

	public void entitiesLoaded(int size) {
		//not used
	}

	public void documentsAdded(long increment) {
		long current = documentsDoneCounter.addAndGet( increment );
		if ( current == increment ) {
			startTimeMs = System.currentTimeMillis();
		}
		if ( current % getStatusMessagePeriod() == 0 ) {
			printStatusMessage( startTimeMs, totalCounter.get(), current );
		}
	}

	public void documentsBuilt(int number) {
		//not used
	}

	public void addToTotalCount(long count) {
		totalCounter.addAndGet( count );
		log.info( "Going to reindex {} entities", count );
	}

	public void indexingCompleted() {
		log.info( "Reindexed {} entities", totalCounter.get() );
	}

	protected int getStatusMessagePeriod() {
		return 50;
	}

	protected void printStatusMessage(long starttimems, long totalTodoCount, long doneCount) {
		long elapsedMs = System.currentTimeMillis() - starttimems;
		log.info( "{} documents indexed in {} ms", doneCount, elapsedMs );
		float estimateSpeed = doneCount * 1000f / elapsedMs;
		float estimatePercentileComplete = doneCount * 100f / totalTodoCount;
		log.info( "Indexing speed: {} documents/second; progress: {}%", estimateSpeed, estimatePercentileComplete );
	}
}
