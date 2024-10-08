package org.jboss.envers.test.integration.components;

import org.jboss.envers.test.AbstractEntityTest;
import org.jboss.envers.test.entities.components.Component1;
import org.jboss.envers.test.entities.components.Component2;
import org.jboss.envers.test.entities.components.ComponentTestEntity;
import org.hibernate.ejb.Ejb3Configuration;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.persistence.EntityManager;
import java.util.Arrays;

/**
 * @author Adam Warski (adam at warski dot org)
 */
public class Components extends AbstractEntityTest {
    private Integer id1;
    private Integer id2;
    private Integer id3;

    public void configure(Ejb3Configuration cfg) {
        cfg.addAnnotatedClass(ComponentTestEntity.class);
    }

    @BeforeClass(dependsOnMethods = "init")
    public void initData() {
        // Revision 1
        EntityManager em = getEntityManager();
        em.getTransaction().begin();

        ComponentTestEntity cte1 = new ComponentTestEntity(new Component1("a", "b"), new Component2("x", "y"));
        ComponentTestEntity cte2 = new ComponentTestEntity(new Component1("a2", "b2"), new Component2("x2", "y2"));
        ComponentTestEntity cte3 = new ComponentTestEntity(new Component1("a3", "b3"), new Component2("x3", "y3"));

        em.persist(cte1);
        em.persist(cte2);
        em.persist(cte3);

        em.getTransaction().commit();

        // Revision 2
        em = getEntityManager();
        em.getTransaction().begin();

        cte1 = em.find(ComponentTestEntity.class, cte1.getId());
        cte2 = em.find(ComponentTestEntity.class, cte2.getId());
        cte3 = em.find(ComponentTestEntity.class, cte3.getId());

        cte1.setComp1(new Component1("a'", "b'"));
        cte2.getComp1().setStr1("a2'");
        cte3.getComp2().setStr6("y3'");

        em.getTransaction().commit();

        // Revision 3
        em = getEntityManager();
        em.getTransaction().begin();

        cte1 = em.find(ComponentTestEntity.class, cte1.getId());
        cte2 = em.find(ComponentTestEntity.class, cte2.getId());
        cte3 = em.find(ComponentTestEntity.class, cte3.getId());

        cte1.setComp2(new Component2("x'", "y'"));
        cte3.getComp1().setStr2("b3'");

        em.getTransaction().commit();

        // Revision 4
        em = getEntityManager();
        em.getTransaction().begin();

        cte2 = em.find(ComponentTestEntity.class, cte2.getId());

        em.remove(cte2);

        em.getTransaction().commit();

        id1 = cte1.getId();
        id2 = cte2.getId();
        id3 = cte3.getId();
    }

    @Test
    public void testRevisionsCounts() {
        assert Arrays.asList(1, 2).equals(getVersionsReader().getRevisions(ComponentTestEntity.class, id1));

        assert Arrays.asList(1, 2, 4).equals(getVersionsReader().getRevisions(ComponentTestEntity.class, id2));

        assert Arrays.asList(1, 3).equals(getVersionsReader().getRevisions(ComponentTestEntity.class, id3));
    }

    @Test
    public void testHistoryOfId1() {
        ComponentTestEntity ver1 = new ComponentTestEntity(id1, new Component1("a", "b"), null);
        ComponentTestEntity ver2 = new ComponentTestEntity(id1, new Component1("a'", "b'"), null);

        assert getVersionsReader().find(ComponentTestEntity.class, id1, 1).equals(ver1);
        assert getVersionsReader().find(ComponentTestEntity.class, id1, 2).equals(ver2);
        assert getVersionsReader().find(ComponentTestEntity.class, id1, 3).equals(ver2);
        assert getVersionsReader().find(ComponentTestEntity.class, id1, 4).equals(ver2);
    }

    @Test
    public void testHistoryOfId2() {
        ComponentTestEntity ver1 = new ComponentTestEntity(id2, new Component1("a2", "b2"), null);
        ComponentTestEntity ver2 = new ComponentTestEntity(id2, new Component1("a2'", "b2"), null);

        assert getVersionsReader().find(ComponentTestEntity.class, id2, 1).equals(ver1);
        assert getVersionsReader().find(ComponentTestEntity.class, id2, 2).equals(ver2);
        assert getVersionsReader().find(ComponentTestEntity.class, id2, 3).equals(ver2);
        assert getVersionsReader().find(ComponentTestEntity.class, id2, 4) == null;
    }

    @Test
    public void testHistoryOfId3() {
        ComponentTestEntity ver1 = new ComponentTestEntity(id3, new Component1("a3", "b3"), null);
        ComponentTestEntity ver2 = new ComponentTestEntity(id3, new Component1("a3", "b3'"), null);

        assert getVersionsReader().find(ComponentTestEntity.class, id3, 1).equals(ver1);
        assert getVersionsReader().find(ComponentTestEntity.class, id3, 2).equals(ver1);
        assert getVersionsReader().find(ComponentTestEntity.class, id3, 3).equals(ver2);
        assert getVersionsReader().find(ComponentTestEntity.class, id3, 4).equals(ver2);
    }
}
