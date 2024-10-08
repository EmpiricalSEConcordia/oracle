package org.hibernate.envers.test.integration.reventity.trackmodifiedentities;

import org.hibernate.ejb.Ejb3Configuration;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.DefaultTrackingModifiedTypesRevisionEntity;
import org.hibernate.envers.test.entities.reventity.trackmodifiedentities.ExtendedRevisionEntity;
import org.hibernate.envers.test.entities.reventity.trackmodifiedentities.ExtendedRevisionListener;
import org.junit.Test;

/**
 * Tests proper behavior of revision entity that extends {@link DefaultTrackingModifiedTypesRevisionEntity}.
 * @author Lukasz Antoniak (lukasz dot antoniak at gmail dot com)
 */
public class ExtendedRevisionEntityTest extends DefaultTrackingEntitiesTest {
    @Override
    public void configure(Ejb3Configuration cfg) {
        super.configure(cfg);
        cfg.addAnnotatedClass(ExtendedRevisionEntity.class);
        cfg.setProperty("org.hibernate.envers.track_entities_changed_in_revision", "false");
    }

    @Test
    public void testCommentPropertyValue() {
        AuditReader vr = getAuditReader();
        ExtendedRevisionEntity ere = vr.findRevision(ExtendedRevisionEntity.class, 1);

        assert ExtendedRevisionListener.COMMENT_VALUE.equals(ere.getCommnent());
    }
}
