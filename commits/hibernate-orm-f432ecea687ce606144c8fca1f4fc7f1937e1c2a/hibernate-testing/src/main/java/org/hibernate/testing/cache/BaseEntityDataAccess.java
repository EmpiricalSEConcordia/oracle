/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.testing.cache;

import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;

/**
 * @author Steve Ebersole
 */
public abstract class BaseEntityDataAccess
		extends AbstractCachedDomainDataAccess
		implements EntityDataAccess {

	public BaseEntityDataAccess(DomainDataRegionImpl region) {
		super( region );
	}

	@Override
	public Object generateCacheKey(
			Object id,
			EntityPersister rootEntityDescriptor,
			SessionFactoryImplementor factory,
			String tenantIdentifier) {
		return getRegion().getEffectiveKeysFactory().createEntityKey(
				id,
				rootEntityDescriptor,
				factory,
				tenantIdentifier
		);
	}

	@Override
	public Object getCacheKeyId(Object cacheKey) {
		return getRegion().getEffectiveKeysFactory().getEntityId( cacheKey );
	}

	@Override
	public SoftLock lockRegion() {
		return null;
	}

	@Override
	public void unlockRegion(SoftLock lock) {
		clearCache();
	}

	public SoftLock lockItem(
			SharedSessionContractImplementor session,
			Object key,
			Object version) {
		return null;
	}

	@Override
	public void unlockItem(
			SharedSessionContractImplementor session,
			Object key,
			SoftLock lock) {
	}
}
