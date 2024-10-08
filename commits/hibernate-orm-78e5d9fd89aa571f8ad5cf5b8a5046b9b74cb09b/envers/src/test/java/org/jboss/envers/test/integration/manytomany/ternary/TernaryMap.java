package org.jboss.envers.test.integration.manytomany.ternary;

import org.jboss.envers.test.AbstractEntityTest;
import org.jboss.envers.test.entities.StrTestEntity;
import org.jboss.envers.test.entities.IntTestEntity;
import org.jboss.envers.test.tools.TestTools;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.hibernate.ejb.Ejb3Configuration;

import javax.persistence.EntityManager;
import java.util.Arrays;

/**
 * @author Adam Warski (adam at warski dot org)
 */
public class TernaryMap extends AbstractEntityTest {
    private Integer str1_id;
    private Integer str2_id;

    private Integer int1_id;
    private Integer int2_id;

    private Integer map1_id;
    private Integer map2_id;

    public void configure(Ejb3Configuration cfg) {
        cfg.addAnnotatedClass(TernaryMapEntity.class);
        cfg.addAnnotatedClass(StrTestEntity.class);
        cfg.addAnnotatedClass(IntTestEntity.class);
    }

    @BeforeClass(dependsOnMethods = "init")
    public void initData() {
        EntityManager em = getEntityManager();

        StrTestEntity str1 = new StrTestEntity("a");
        StrTestEntity str2 = new StrTestEntity("b");

        IntTestEntity int1 = new IntTestEntity(1);
        IntTestEntity int2 = new IntTestEntity(2);

        TernaryMapEntity map1 = new TernaryMapEntity();
        TernaryMapEntity map2 = new TernaryMapEntity();

        // Revision 1 (map1: initialy one mapping int1 -> str1, map2: empty)
        em.getTransaction().begin();

        em.persist(str1);
        em.persist(str2);
        em.persist(int1);
        em.persist(int2);

        map1.getMap().put(int1, str1);

        em.persist(map1);
        em.persist(map2);

        em.getTransaction().commit();

        // Revision 2 (map1: replacing the mapping, map2: adding two mappings)

        em.getTransaction().begin();

        map1 = em.find(TernaryMapEntity.class, map1.getId());
        map2 = em.find(TernaryMapEntity.class, map2.getId());

        str1 = em.find(StrTestEntity.class, str1.getId());
        str2 = em.find(StrTestEntity.class, str2.getId());

        int1 = em.find(IntTestEntity.class, int1.getId());
        int2 = em.find(IntTestEntity.class, int2.getId());

        map1.getMap().put(int1, str2);

        map2.getMap().put(int1, str1);
        map2.getMap().put(int2, str1);

        em.getTransaction().commit();

        // Revision 3 (map1: removing a non-existing mapping, adding an existing mapping - no changes, map2: removing a mapping)
        em.getTransaction().begin();

        map1 = em.find(TernaryMapEntity.class, map1.getId());
        map2 = em.find(TernaryMapEntity.class, map2.getId());

        str2 = em.find(StrTestEntity.class, str2.getId());

        int1 = em.find(IntTestEntity.class, int1.getId());
        int2 = em.find(IntTestEntity.class, int2.getId());

        map1.getMap().remove(int2);
        map1.getMap().put(int1, str2);

        map2.getMap().remove(int1);

        em.getTransaction().commit();

        // Revision 4 (map1: adding a mapping, map2: adding a mapping)
        em.getTransaction().begin();

        map1 = em.find(TernaryMapEntity.class, map1.getId());
        map2 = em.find(TernaryMapEntity.class, map2.getId());

        str2 = em.find(StrTestEntity.class, str2.getId());

        int1 = em.find(IntTestEntity.class, int1.getId());
        int2 = em.find(IntTestEntity.class, int2.getId());

        map1.getMap().put(int2, str2);

        map2.getMap().put(int1, str2);

        em.getTransaction().commit();
        //

        map1_id = map1.getId();
        map2_id = map2.getId();

        str1_id = str1.getId();
        str2_id = str2.getId();

        int1_id = int1.getId();
        int2_id = int2.getId();
    }

    @Test
    public void testRevisionsCounts() {
        assert Arrays.asList(1, 2, 4).equals(getVersionsReader().getRevisions(TernaryMapEntity.class, map1_id));
        assert Arrays.asList(1, 2, 3, 4).equals(getVersionsReader().getRevisions(TernaryMapEntity.class, map2_id));

        assert Arrays.asList(1).equals(getVersionsReader().getRevisions(StrTestEntity.class, str1_id));
        assert Arrays.asList(1).equals(getVersionsReader().getRevisions(StrTestEntity.class, str2_id));

        assert Arrays.asList(1).equals(getVersionsReader().getRevisions(IntTestEntity.class, int1_id));
        assert Arrays.asList(1).equals(getVersionsReader().getRevisions(IntTestEntity.class, int2_id));
    }

    @Test
    public void testHistoryOfMap1() {
        StrTestEntity str1 = getEntityManager().find(StrTestEntity.class, str1_id);
        StrTestEntity str2 = getEntityManager().find(StrTestEntity.class, str2_id);

        IntTestEntity int1 = getEntityManager().find(IntTestEntity.class, int1_id);
        IntTestEntity int2 = getEntityManager().find(IntTestEntity.class, int2_id);

        TernaryMapEntity rev1 = getVersionsReader().find(TernaryMapEntity.class, map1_id, 1);
        TernaryMapEntity rev2 = getVersionsReader().find(TernaryMapEntity.class, map1_id, 2);
        TernaryMapEntity rev3 = getVersionsReader().find(TernaryMapEntity.class, map1_id, 3);
        TernaryMapEntity rev4 = getVersionsReader().find(TernaryMapEntity.class, map1_id, 4);

        assert rev1.getMap().equals(TestTools.makeMap(int1, str1));
        assert rev2.getMap().equals(TestTools.makeMap(int1, str2));
        assert rev3.getMap().equals(TestTools.makeMap(int1, str2));
        assert rev4.getMap().equals(TestTools.makeMap(int1, str2, int2, str2));
    }

    @Test
    public void testHistoryOfMap2() {
        StrTestEntity str1 = getEntityManager().find(StrTestEntity.class, str1_id);
        StrTestEntity str2 = getEntityManager().find(StrTestEntity.class, str2_id);

        IntTestEntity int1 = getEntityManager().find(IntTestEntity.class, int1_id);
        IntTestEntity int2 = getEntityManager().find(IntTestEntity.class, int2_id);

        TernaryMapEntity rev1 = getVersionsReader().find(TernaryMapEntity.class, map2_id, 1);
        TernaryMapEntity rev2 = getVersionsReader().find(TernaryMapEntity.class, map2_id, 2);
        TernaryMapEntity rev3 = getVersionsReader().find(TernaryMapEntity.class, map2_id, 3);
        TernaryMapEntity rev4 = getVersionsReader().find(TernaryMapEntity.class, map2_id, 4);

        assert rev1.getMap().equals(TestTools.makeMap());
        assert rev2.getMap().equals(TestTools.makeMap(int1, str1, int2, str1));
        assert rev3.getMap().equals(TestTools.makeMap(int2, str1));
        assert rev4.getMap().equals(TestTools.makeMap(int1, str2, int2, str1));
    }
}