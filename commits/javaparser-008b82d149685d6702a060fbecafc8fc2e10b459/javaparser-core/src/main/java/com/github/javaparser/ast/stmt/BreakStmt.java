/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2016 The JavaParser Team.
 *
 * This file is part of JavaParser.
 * 
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License 
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package com.github.javaparser.ast.stmt;

import static com.github.javaparser.utils.Utils.assertNotNull;

import java.util.Optional;

import com.github.javaparser.Range;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;

/**
 * @author Julio Vilmar Gesser
 */
public final class BreakStmt extends Statement {

    private String id;

    public BreakStmt() {
        this(Range.UNKNOWN, "empty");
    }

    public BreakStmt(final String id) {
        this(Range.UNKNOWN, id);
    }

    public BreakStmt(final Range range, final String id) {
        super(range);
        this.id = id;
    }

    @Override
    public <R, A> R accept(final GenericVisitor<R, A> v, final A arg) {
        return v.visit(this, arg);
    }

    @Override
    public <A> void accept(final VoidVisitor<A> v, final A arg) {
        v.visit(this, arg);
    }

    public Optional<String> getId() {
        return Optional.ofNullable(id);
    }

    /**
     * Sets the id
     * 
     * @param id the id, can be null
     * @return this, the BreakStmt
     */
    public BreakStmt setId(final String id) {
        this.id = assertNotNull(id);
        return this;
    }
}
