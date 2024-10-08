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
package org.hibernate.engine;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.hibernate.AssertionFailure;
import org.hibernate.HibernateException;
import org.hibernate.HibernateLogger;
import org.hibernate.action.internal.BulkOperationCleanupAction;
import org.hibernate.action.internal.CollectionAction;
import org.hibernate.action.internal.CollectionRecreateAction;
import org.hibernate.action.internal.EntityAction;
import org.hibernate.action.internal.EntityInsertAction;
import org.hibernate.action.internal.EntityUpdateAction;
import org.hibernate.action.spi.AfterTransactionCompletionProcess;
import org.hibernate.action.spi.BeforeTransactionCompletionProcess;
import org.hibernate.action.internal.CollectionRemoveAction;
import org.hibernate.action.internal.CollectionUpdateAction;
import org.hibernate.action.internal.EntityDeleteAction;
import org.hibernate.action.internal.EntityIdentityInsertAction;
import org.hibernate.action.spi.Executable;
import org.hibernate.cache.CacheException;
import org.hibernate.type.Type;
import org.jboss.logging.Logger;

/**
 * Responsible for maintaining the queue of actions related to events.
 * </p>
 * The ActionQueue holds the DML operations queued as part of a session's
 * transactional-write-behind semantics.  DML operations are queued here
 * until a flush forces them to be executed against the database.
 *
 * @author Steve Ebersole
 */
public class ActionQueue {

    static final HibernateLogger LOG = Logger.getMessageLogger(HibernateLogger.class, ActionQueue.class.getName());
	private static final int INIT_QUEUE_LIST_SIZE = 5;

	private SessionImplementor session;

	// Object insertions, updates, and deletions have list semantics because
	// they must happen in the right order so as to respect referential
	// integrity
	private ArrayList insertions;
	private ArrayList deletions;
	private ArrayList updates;
	// Actually the semantics of the next three are really "Bag"
	// Note that, unlike objects, collection insertions, updates,
	// deletions are not really remembered between flushes. We
	// just re-use the same Lists for convenience.
	private ArrayList collectionCreations;
	private ArrayList collectionUpdates;
	private ArrayList collectionRemovals;

	private AfterTransactionCompletionProcessQueue afterTransactionProcesses;
	private BeforeTransactionCompletionProcessQueue beforeTransactionProcesses;

	/**
	 * Constructs an action queue bound to the given session.
	 *
	 * @param session The session "owning" this queue.
	 */
	public ActionQueue(SessionImplementor session) {
		this.session = session;
		init();
	}

	private void init() {
		insertions = new ArrayList( INIT_QUEUE_LIST_SIZE );
		deletions = new ArrayList( INIT_QUEUE_LIST_SIZE );
		updates = new ArrayList( INIT_QUEUE_LIST_SIZE );

		collectionCreations = new ArrayList( INIT_QUEUE_LIST_SIZE );
		collectionRemovals = new ArrayList( INIT_QUEUE_LIST_SIZE );
		collectionUpdates = new ArrayList( INIT_QUEUE_LIST_SIZE );

		afterTransactionProcesses = new AfterTransactionCompletionProcessQueue( session );
		beforeTransactionProcesses = new BeforeTransactionCompletionProcessQueue( session );
	}

	public void clear() {
		updates.clear();
		insertions.clear();
		deletions.clear();

		collectionCreations.clear();
		collectionRemovals.clear();
		collectionUpdates.clear();
	}

	@SuppressWarnings({ "unchecked" })
	public void addAction(EntityInsertAction action) {
		insertions.add( action );
	}

	@SuppressWarnings({ "unchecked" })
	public void addAction(EntityDeleteAction action) {
		deletions.add( action );
	}

	@SuppressWarnings({ "unchecked" })
	public void addAction(EntityUpdateAction action) {
		updates.add( action );
	}

	@SuppressWarnings({ "unchecked" })
	public void addAction(CollectionRecreateAction action) {
		collectionCreations.add( action );
	}

	@SuppressWarnings({ "unchecked" })
	public void addAction(CollectionRemoveAction action) {
		collectionRemovals.add( action );
	}

	@SuppressWarnings({ "unchecked" })
	public void addAction(CollectionUpdateAction action) {
		collectionUpdates.add( action );
	}

	@SuppressWarnings({ "unchecked" })
	public void addAction(EntityIdentityInsertAction insert) {
		insertions.add( insert );
	}

	public void addAction(BulkOperationCleanupAction cleanupAction) {
		registerCleanupActions( cleanupAction );
	}

	public void registerProcess(AfterTransactionCompletionProcess process) {
		afterTransactionProcesses.register( process );
	}

	public void registerProcess(BeforeTransactionCompletionProcess process) {
		beforeTransactionProcesses.register( process );
	}

	/**
	 * Perform all currently queued entity-insertion actions.
	 *
	 * @throws HibernateException error executing queued insertion actions.
	 */
	public void executeInserts() throws HibernateException {
		executeActions( insertions );
	}

	/**
	 * Perform all currently queued actions.
	 *
	 * @throws HibernateException error executing queued actions.
	 */
	public void executeActions() throws HibernateException {
		executeActions( insertions );
		executeActions( updates );
		executeActions( collectionRemovals );
		executeActions( collectionUpdates );
		executeActions( collectionCreations );
		executeActions( deletions );
	}

	/**
	 * Prepares the internal action queues for execution.
	 *
	 * @throws HibernateException error preparing actions.
	 */
	public void prepareActions() throws HibernateException {
		prepareActions( collectionRemovals );
		prepareActions( collectionUpdates );
		prepareActions( collectionCreations );
	}

	/**
	 * Performs cleanup of any held cache softlocks.
	 *
	 * @param success Was the transaction successful.
	 */
	public void afterTransactionCompletion(boolean success) {
		afterTransactionProcesses.afterTransactionCompletion( success );
	}

	/**
	 * Execute any registered {@link org.hibernate.action.spi.BeforeTransactionCompletionProcess}
	 */
	public void beforeTransactionCompletion() {
		beforeTransactionProcesses.beforeTransactionCompletion();
	}

	/**
	 * Check whether the given tables/query-spaces are to be executed against
	 * given the currently queued actions.
	 *
	 * @param tables The table/query-spaces to check.
	 *
	 * @return True if we contain pending actions against any of the given
	 *         tables; false otherwise.
	 */
	public boolean areTablesToBeUpdated(Set tables) {
		return areTablesToUpdated( updates, tables ) ||
				areTablesToUpdated( insertions, tables ) ||
				areTablesToUpdated( deletions, tables ) ||
				areTablesToUpdated( collectionUpdates, tables ) ||
				areTablesToUpdated( collectionCreations, tables ) ||
				areTablesToUpdated( collectionRemovals, tables );
	}

	/**
	 * Check whether any insertion or deletion actions are currently queued.
	 *
	 * @return True if insertions or deletions are currently queued; false otherwise.
	 */
	public boolean areInsertionsOrDeletionsQueued() {
		return ( insertions.size() > 0 || deletions.size() > 0 );
	}

	@SuppressWarnings({ "unchecked" })
	private static boolean areTablesToUpdated(List actions, Set tableSpaces) {
		for ( Executable action : (List<Executable>) actions ) {
			final Serializable[] spaces = action.getPropertySpaces();
			for ( Serializable space : spaces ) {
				if ( tableSpaces.contains( space ) ) {
                    LOG.debugf("Changes must be flushed to space: %s", space);
					return true;
				}
			}
		}
		return false;
	}

	private void executeActions(List list) throws HibernateException {
		int size = list.size();
		for ( int i = 0; i < size; i++ ) {
			execute( (Executable) list.get( i ) );
		}
		list.clear();
		session.getTransactionCoordinator().getJdbcCoordinator().executeBatch();
	}

	public void execute(Executable executable) {
		try {
			executable.execute();
		}
		finally {
			registerCleanupActions( executable );
		}
	}

	private void registerCleanupActions(Executable executable) {
		beforeTransactionProcesses.register( executable.getBeforeTransactionCompletionProcess() );
		if ( session.getFactory().getSettings().isQueryCacheEnabled() ) {
			final String[] spaces = (String[]) executable.getPropertySpaces();
			afterTransactionProcesses.addSpacesToInvalidate( spaces );
			session.getFactory().getUpdateTimestampsCache().preinvalidate( spaces );
		}
		afterTransactionProcesses.register( executable.getAfterTransactionCompletionProcess() );
	}

	@SuppressWarnings({ "unchecked" })
	private void prepareActions(List queue) throws HibernateException {
		for ( Executable executable : (List<Executable>) queue ) {
			executable.beforeExecutions();
		}
	}

	/**
	 * Returns a string representation of the object.
	 *
	 * @return a string representation of the object.
	 */
	@Override
    public String toString() {
		return new StringBuffer()
				.append( "ActionQueue[insertions=" ).append( insertions )
				.append( " updates=" ).append( updates )
				.append( " deletions=" ).append( deletions )
				.append( " collectionCreations=" ).append( collectionCreations )
				.append( " collectionRemovals=" ).append( collectionRemovals )
				.append( " collectionUpdates=" ).append( collectionUpdates )
				.append( "]" )
				.toString();
	}

	public int numberOfCollectionRemovals() {
		return collectionRemovals.size();
	}

	public int numberOfCollectionUpdates() {
		return collectionUpdates.size();
	}

	public int numberOfCollectionCreations() {
		return collectionCreations.size();
	}

	public int numberOfDeletions() {
		return deletions.size();
	}

	public int numberOfUpdates() {
		return updates.size();
	}

	public int numberOfInsertions() {
		return insertions.size();
	}

	@SuppressWarnings({ "unchecked" })
	public void sortCollectionActions() {
		if ( session.getFactory().getSettings().isOrderUpdatesEnabled() ) {
			//sort the updates by fk
			java.util.Collections.sort( collectionCreations );
			java.util.Collections.sort( collectionUpdates );
			java.util.Collections.sort( collectionRemovals );
		}
	}

	@SuppressWarnings({ "unchecked" })
	public void sortActions() {
		if ( session.getFactory().getSettings().isOrderUpdatesEnabled() ) {
			//sort the updates by pk
			java.util.Collections.sort( updates );
		}
		if ( session.getFactory().getSettings().isOrderInsertsEnabled() ) {
			sortInsertActions();
		}
	}

	/**
	 * Order the {@link #insertions} queue such that we group inserts
	 * against the same entity together (without violating constraints).  The
	 * original order is generated by cascade order, which in turn is based on
	 * the directionality of foreign-keys.  So even though we will be changing
	 * the ordering here, we need to make absolutely certain that we do not
	 * circumvent this FK ordering to the extent of causing constraint
	 * violations
	 */
	private void sortInsertActions() {
		new InsertActionSorter().sort();
	}

	@SuppressWarnings({ "UnusedDeclaration" })
	public ArrayList cloneDeletions() {
		return ( ArrayList ) deletions.clone();
	}

	public void clearFromFlushNeededCheck(int previousCollectionRemovalSize) {
		collectionCreations.clear();
		collectionUpdates.clear();
		updates.clear();
		// collection deletions are a special case since update() can add
		// deletions of collections not loaded by the session.
		for ( int i = collectionRemovals.size() - 1; i >= previousCollectionRemovalSize; i-- ) {
			collectionRemovals.remove( i );
		}
	}

	@SuppressWarnings({ "UnusedDeclaration" })
	public boolean hasAfterTransactionActions() {
		return afterTransactionProcesses.processes.size() > 0;
	}

	public boolean hasBeforeTransactionActions() {
		return beforeTransactionProcesses.processes.size() > 0;
	}

	public boolean hasAnyQueuedActions() {
		return updates.size() > 0 ||
				insertions.size() > 0 ||
				deletions.size() > 0 ||
				collectionUpdates.size() > 0 ||
				collectionRemovals.size() > 0 ||
				collectionCreations.size() > 0;
	}

	/**
	 * Used by the owning session to explicitly control serialization of the
	 * action queue
	 *
	 * @param oos The stream to which the action queue should get written
	 *
	 * @throws IOException Indicates an error writing to the stream
	 */
	public void serialize(ObjectOutputStream oos) throws IOException {
        LOG.trace("Serializing action-queue");

		int queueSize = insertions.size();
        LOG.trace("Starting serialization of [" + queueSize + "] insertions entries");
		oos.writeInt( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			oos.writeObject( insertions.get( i ) );
		}

		queueSize = deletions.size();
        LOG.trace("Starting serialization of [" + queueSize + "] deletions entries");
		oos.writeInt( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			oos.writeObject( deletions.get( i ) );
		}

		queueSize = updates.size();
        LOG.trace("Starting serialization of [" + queueSize + "] updates entries");
		oos.writeInt( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			oos.writeObject( updates.get( i ) );
		}

		queueSize = collectionUpdates.size();
        LOG.trace("Starting serialization of [" + queueSize + "] collectionUpdates entries");
		oos.writeInt( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			oos.writeObject( collectionUpdates.get( i ) );
		}

		queueSize = collectionRemovals.size();
        LOG.trace("Starting serialization of [" + queueSize + "] collectionRemovals entries");
		oos.writeInt( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			oos.writeObject( collectionRemovals.get( i ) );
		}

		queueSize = collectionCreations.size();
        LOG.trace("Starting serialization of [" + queueSize + "] collectionCreations entries");
		oos.writeInt( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			oos.writeObject( collectionCreations.get( i ) );
		}
	}

	/**
	 * Used by the owning session to explicitly control deserialization of the
	 * action queue
	 *
	 * @param ois The stream from which to read the action queue
	 * @param session The session to which the action queue belongs
	 *
	 * @return The deserialized action queue
	 *
	 * @throws IOException indicates a problem reading from the stream
	 * @throws ClassNotFoundException Generally means we were unable to locate user classes.
	 */
	@SuppressWarnings({ "unchecked" })
	public static ActionQueue deserialize(
			ObjectInputStream ois,
			SessionImplementor session) throws IOException, ClassNotFoundException {
        LOG.trace("Dedeserializing action-queue");
		ActionQueue rtn = new ActionQueue( session );

		int queueSize = ois.readInt();
        LOG.trace("Starting deserialization of [" + queueSize + "] insertions entries");
		rtn.insertions = new ArrayList<Executable>( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			EntityAction action = ( EntityAction ) ois.readObject();
			action.afterDeserialize( session );
			rtn.insertions.add( action );
		}

		queueSize = ois.readInt();
        LOG.trace("Starting deserialization of [" + queueSize + "] deletions entries");
		rtn.deletions = new ArrayList<Executable>( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			EntityAction action = ( EntityAction ) ois.readObject();
			action.afterDeserialize( session );
			rtn.deletions.add( action );
		}

		queueSize = ois.readInt();
        LOG.trace("Starting deserialization of [" + queueSize + "] updates entries");
		rtn.updates = new ArrayList<Executable>( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			EntityAction action = ( EntityAction ) ois.readObject();
			action.afterDeserialize( session );
			rtn.updates.add( action );
		}

		queueSize = ois.readInt();
        LOG.trace("Starting deserialization of [" + queueSize + "] collectionUpdates entries");
		rtn.collectionUpdates = new ArrayList<Executable>( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			CollectionAction action = (CollectionAction) ois.readObject();
			action.afterDeserialize( session );
			rtn.collectionUpdates.add( action );
		}

		queueSize = ois.readInt();
        LOG.trace("Starting deserialization of [" + queueSize + "] collectionRemovals entries");
		rtn.collectionRemovals = new ArrayList<Executable>( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			CollectionAction action = ( CollectionAction ) ois.readObject();
			action.afterDeserialize( session );
			rtn.collectionRemovals.add( action );
		}

		queueSize = ois.readInt();
        LOG.trace("Starting deserialization of [" + queueSize + "] collectionCreations entries");
		rtn.collectionCreations = new ArrayList<Executable>( queueSize );
		for ( int i = 0; i < queueSize; i++ ) {
			CollectionAction action = ( CollectionAction ) ois.readObject();
			action.afterDeserialize( session );
			rtn.collectionCreations.add( action );
		}
		return rtn;
	}

	private static class BeforeTransactionCompletionProcessQueue {
		private SessionImplementor session;
		private List<BeforeTransactionCompletionProcess> processes = new ArrayList<BeforeTransactionCompletionProcess>();

		private BeforeTransactionCompletionProcessQueue(SessionImplementor session) {
			this.session = session;
		}

		public void register(BeforeTransactionCompletionProcess process) {
			if ( process == null ) {
				return;
			}
			processes.add( process );
		}

		public void beforeTransactionCompletion() {
			final int size = processes.size();
			for ( int i = 0; i < size; i++ ) {
				try {
					BeforeTransactionCompletionProcess process = processes.get( i );
					process.doBeforeTransactionCompletion( session );
				}
				catch ( HibernateException he ) {
					throw he;
				}
				catch ( Exception e ) {
					throw new AssertionFailure( "Unable to perform beforeTransactionCompletion callback", e );
				}
			}
			processes.clear();
		}
	}

	private static class AfterTransactionCompletionProcessQueue {
		private SessionImplementor session;
		private Set<String> querySpacesToInvalidate = new HashSet<String>();
		private List<AfterTransactionCompletionProcess> processes
				= new ArrayList<AfterTransactionCompletionProcess>( INIT_QUEUE_LIST_SIZE * 3 );

		private AfterTransactionCompletionProcessQueue(SessionImplementor session) {
			this.session = session;
		}

		public void addSpacesToInvalidate(String[] spaces) {
			if ( spaces == null ) {
				return;
			}
			for ( int i = 0, max = spaces.length; i < max; i++ ) {
				addSpaceToInvalidate( spaces[i] );
			}
		}

		public void addSpaceToInvalidate(String space) {
			querySpacesToInvalidate.add( space );
		}

		public void register(AfterTransactionCompletionProcess process) {
			if ( process == null ) {
				return;
			}
			processes.add( process );
		}

		public void afterTransactionCompletion(boolean success) {
			final int size = processes.size();
			for ( int i = 0; i < size; i++ ) {
				try {
					AfterTransactionCompletionProcess process = processes.get( i );
					process.doAfterTransactionCompletion( success, session );
				}
				catch ( CacheException ce ) {
                    LOG.unableToReleaseCacheLock(ce);
					// continue loop
				}
				catch ( Exception e ) {
					throw new AssertionFailure( "Exception releasing cache locks", e );
				}
			}
			processes.clear();

			if ( session.getFactory().getSettings().isQueryCacheEnabled() ) {
				session.getFactory().getUpdateTimestampsCache().invalidate(
						querySpacesToInvalidate.toArray( new String[ querySpacesToInvalidate.size()] )
				);
			}
			querySpacesToInvalidate.clear();
		}
	}

	/**
	 * Sorts the insert actions using more hashes.
	 *
	 * @author Jay Erb
	 */
	private class InsertActionSorter {
		// the mapping of entity names to their latest batch numbers.
		private HashMap latestBatches = new HashMap();
		private HashMap entityBatchNumber;

		// the map of batch numbers to EntityInsertAction lists
		private HashMap actionBatches = new HashMap();

		public InsertActionSorter() {
			//optimize the hash size to eliminate a rehash.
			entityBatchNumber = new HashMap( insertions.size() + 1, 1.0f );
		}

		/**
		 * Sort the insert actions.
		 */
		@SuppressWarnings({ "unchecked", "UnnecessaryBoxing" })
		public void sort() {
			// the list of entity names that indicate the batch number
			for ( EntityInsertAction action : (List<EntityInsertAction>) insertions ) {
				// remove the current element from insertions. It will be added back later.
				String entityName = action.getEntityName();

				// the entity associated with the current action.
				Object currentEntity = action.getInstance();

				Integer batchNumber;
				if ( latestBatches.containsKey( entityName ) ) {
					// There is already an existing batch for this type of entity.
					// Check to see if the latest batch is acceptable.
					batchNumber = findBatchNumber( action, entityName );
				}
				else {
					// add an entry for this type of entity.
					// we can be assured that all referenced entities have already
					// been processed,
					// so specify that this entity is with the latest batch.
					// doing the batch number before adding the name to the list is
					// a faster way to get an accurate number.

					batchNumber = Integer.valueOf( actionBatches.size() );
					latestBatches.put( entityName, batchNumber );
				}
				entityBatchNumber.put( currentEntity, batchNumber );
				addToBatch( batchNumber, action );
			}
			insertions.clear();

			// now rebuild the insertions list. There is a batch for each entry in the name list.
			for ( int i = 0; i < actionBatches.size(); i++ ) {
				List batch = ( List ) actionBatches.get( new Integer( i ) );
				for ( Object aBatch : batch ) {
					EntityInsertAction action = (EntityInsertAction) aBatch;
					insertions.add( action );
				}
			}
		}

		/**
		 * Finds an acceptable batch for this entity to be a member as part of the {@link InsertActionSorter}
		 *
		 * @param action The action being sorted
		 * @param entityName The name of the entity affected by the action
		 *
		 * @return An appropriate batch number; todo document this process better
		 */
		@SuppressWarnings({ "UnnecessaryBoxing", "unchecked" })
		private Integer findBatchNumber(
				EntityInsertAction action,
				String entityName) {
			// loop through all the associated entities and make sure they have been
			// processed before the latest
			// batch associated with this entity type.

			// the current batch number is the latest batch for this entity type.
			Integer latestBatchNumberForType = ( Integer ) latestBatches.get( entityName );

			// loop through all the associations of the current entity and make sure that they are processed
			// before the current batch number
			Object[] propertyValues = action.getState();
			Type[] propertyTypes = action.getPersister().getClassMetadata()
					.getPropertyTypes();

			for ( int i = 0; i < propertyValues.length; i++ ) {
				Object value = propertyValues[i];
				Type type = propertyTypes[i];
				if ( type.isEntityType() && value != null ) {
					// find the batch number associated with the current association, if any.
					Integer associationBatchNumber = ( Integer ) entityBatchNumber.get( value );
					if ( associationBatchNumber != null && associationBatchNumber.compareTo( latestBatchNumberForType ) > 0 ) {
						// create a new batch for this type. The batch number is the number of current batches.
						latestBatchNumberForType = Integer.valueOf( actionBatches.size() );
						latestBatches.put( entityName, latestBatchNumberForType );
						// since this entity will now be processed in the latest possible batch,
						// we can be assured that it will come after all other associations,
						// there's not need to continue checking.
						break;
					}
				}
			}
			return latestBatchNumberForType;
		}

		@SuppressWarnings({ "unchecked" })
		private void addToBatch(Integer batchNumber, EntityInsertAction action) {
			List actions = ( List ) actionBatches.get( batchNumber );

			if ( actions == null ) {
				actions = new LinkedList();
				actionBatches.put( batchNumber, actions );
			}
			actions.add( action );
		}

	}
}
