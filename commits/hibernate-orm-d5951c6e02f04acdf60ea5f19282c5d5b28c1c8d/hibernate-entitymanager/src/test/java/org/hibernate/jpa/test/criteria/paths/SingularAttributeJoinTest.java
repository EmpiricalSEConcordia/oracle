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
package org.hibernate.jpa.test.criteria.paths;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Bindable;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.Type;

import org.hibernate.jpa.criteria.CriteriaBuilderImpl;
import org.hibernate.jpa.criteria.PathSource;
import org.hibernate.jpa.criteria.path.SingularAttributeJoin;
import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * @author Brad Koehn
 */
public class SingularAttributeJoinTest extends BaseEntityManagerFunctionalTestCase {
    @Override
    protected String[] getMappings() {
        return new String[] {
                getClass().getPackage().getName().replace( '.', '/' ) + "/PolicyAndDistribution.hbm.xml"
        };
    }

    /**
     * When building a join from a non-class based entity (EntityMode.MAP), make sure you get the Bindable from
     * the SingularAttribute as the join model. If you don't, you'll get the first non-classed based entity
     * you added to your configuration. Regression for HHH-9142.
     */
    @Test
    public void testEntityModeMapJoins() throws Exception {
        CriteriaBuilderImpl criteriaBuilder = mock( CriteriaBuilderImpl.class);
        PathSource pathSource = mock( PathSource.class);
        SingularAttribute joinAttribute = mock( SingularAttribute.class);
        when(joinAttribute.getPersistentAttributeType()).thenReturn(Attribute.PersistentAttributeType.MANY_TO_ONE);
        Type joinType = mock( Type.class, withSettings().extraInterfaces( Bindable.class));
        when(joinAttribute.getType()).thenReturn(joinType);
        SingularAttributeJoin join = new SingularAttributeJoin(criteriaBuilder, null, pathSource, joinAttribute, JoinType.LEFT);

        assertEquals( joinType, join.getModel());
    }

    @Test
    public void testEntityModeMapJoinCriteriaQuery() throws Exception {
        final EntityManager entityManager = entityManagerFactory().createEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery criteriaQuery = criteriaBuilder.createQuery();
        javax.persistence.metamodel.EntityType distributionEntity = getEntityType("Distribution");
        From distributionFrom = criteriaQuery.from(distributionEntity);
        From policyJoin = distributionFrom.join("policy");
        Path policyId = policyJoin.get("policyId");
        criteriaQuery.select(policyId);
        TypedQuery typedQuery = entityManager.createQuery(criteriaQuery);
//        typedQuery.getResultList();
    }

    private javax.persistence.metamodel.EntityType getEntityType(String entityName) {
        for(javax.persistence.metamodel.EntityType entityType : entityManagerFactory().getMetamodel().getEntities()) {
            if (entityType.getName().equals("Distribution")) {
                return entityType;
            }
        }

        throw new IllegalStateException("Unable to find entity " + entityName);
    }
}
