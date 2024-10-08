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
 */
package org.hibernate.envers.test.integration.naming;

import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;

import org.hibernate.envers.Versioned;
import org.hibernate.envers.test.entities.StrTestEntity;

/**
 * @author Adam Warski (adam at warski dot org)
 */
@Entity
public class DetachedNamingTestEntity {
    @Id
    private Integer id;

    @Versioned
    private String data;

    @Versioned
    @OneToMany
    @JoinTable(name = "UNI_NAMING_TEST",
        joinColumns = @JoinColumn(name = "ID_1"),
        inverseJoinColumns = @JoinColumn(name = "ID_2"))
    private Set<StrTestEntity> collection;

    public DetachedNamingTestEntity() {
    }

    public DetachedNamingTestEntity(Integer id, String data) {
        this.id = id;
        this.data = data;
    }

    public DetachedNamingTestEntity(String data) {
        this.data = data;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Set<StrTestEntity> getCollection() {
        return collection;
    }

    public void setCollection(Set<StrTestEntity> collection) {
        this.collection = collection;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DetachedNamingTestEntity)) return false;

        DetachedNamingTestEntity that = (DetachedNamingTestEntity) o;

        if (data != null ? !data.equals(that.data) : that.data != null) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (id != null ? id.hashCode() : 0);
        result = 31 * result + (data != null ? data.hashCode() : 0);
        return result;
    }

    public String toString() {
        return "DetachedNamingTestEntity(id = " + id + ", data = " + data + ")";
    }
}
