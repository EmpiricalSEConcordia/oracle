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
package com.github.javaparser.ast.body;

import com.github.javaparser.Range;
import com.github.javaparser.ast.AllFieldsConstructor;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithAbstractModifier;
import com.github.javaparser.ast.visitor.CloneVisitor;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.metamodel.AnnotationDeclarationMetaModel;
import com.github.javaparser.metamodel.JavaParserMetaModel;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Generated;

/**
 * An annotation type declaration.<br/><code>@interface X { ... }</code>
 *
 * @author Julio Vilmar Gesser
 */
public final class AnnotationDeclaration extends TypeDeclaration<AnnotationDeclaration> implements NodeWithAbstractModifier<AnnotationDeclaration> {

    public AnnotationDeclaration() {
        this(null, EnumSet.noneOf(Modifier.class), new NodeList<>(), new SimpleName(), new NodeList<>());
    }

    public AnnotationDeclaration(EnumSet<Modifier> modifiers, String name) {
        this(null, modifiers, new NodeList<>(), new SimpleName(name), new NodeList<>());
    }

    @AllFieldsConstructor
    public AnnotationDeclaration(EnumSet<Modifier> modifiers, NodeList<AnnotationExpr> annotations, SimpleName name, NodeList<BodyDeclaration<?>> members) {
        this(null, modifiers, annotations, name, members);
    }

    /**This constructor is used by the parser and is considered private.*/
    @Generated("com.github.javaparser.generator.core.node.MainConstructorGenerator")
    public AnnotationDeclaration(Range range, EnumSet<Modifier> modifiers, NodeList<AnnotationExpr> annotations, SimpleName name, NodeList<BodyDeclaration<?>> members) {
        super(range, modifiers, annotations, name, members);
        customInitialization();
    }

    @Override
    public <R, A> R accept(GenericVisitor<R, A> v, A arg) {
        return v.visit(this, arg);
    }

    @Override
    public <A> void accept(VoidVisitor<A> v, A arg) {
        v.visit(this, arg);
    }

    @Override
    @Generated("com.github.javaparser.generator.core.node.GetNodeListsGenerator")
    public List<NodeList<?>> getNodeLists() {
        return Arrays.asList(getMembers(), getAnnotations());
    }

    @Override
    public boolean remove(Node node) {
        if (node == null)
            return false;
        return super.remove(node);
    }

    @Override
    @Generated("com.github.javaparser.generator.core.node.CloneGenerator")
    public AnnotationDeclaration clone() {
        return (AnnotationDeclaration) accept(new CloneVisitor(), null);
    }

    @Override
    @Generated("com.github.javaparser.generator.core.node.GetMetaModelGenerator")
    public AnnotationDeclarationMetaModel getMetaModel() {
        return JavaParserMetaModel.annotationDeclarationMetaModel;
    }
}
