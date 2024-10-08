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
package org.hibernate.envers.test.entities.customtype;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.envers.Versioned;

import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

/**
 * @author Adam Warski (adam at warski dot org)
 */
@Entity
@TypeDef(name = "param", typeClass = ParametrizedTestUserType.class,
        parameters = { @Parameter(name="param1", value = "x"), @Parameter(name="param2", value = "y") })
public class ParametrizedCustomTypeEntity {
    @Id
    @GeneratedValue
    private Integer id;

    @Versioned
    @Type(type = "param")
    private String str;

    public ParametrizedCustomTypeEntity() {
    }

    public ParametrizedCustomTypeEntity(Integer id, String str) {
        this.id = id;
        this.str = str;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getStr() {
        return str;
    }

    public void setStr(String str) {
        this.str = str;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParametrizedCustomTypeEntity)) return false;

        ParametrizedCustomTypeEntity that = (ParametrizedCustomTypeEntity) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (str != null ? !str.equals(that.str) : that.str != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (id != null ? id.hashCode() : 0);
        result = 31 * result + (str != null ? str.hashCode() : 0);
        return result;
    }

    public String toString() {
        return "PCTE(id = " + id + ", str = " + str + ")";
    }
}