/*
 * Copyright (C) 2007 Júlio Vilmar Gesser.
 * 
 * This file is part of Java 1.5 parser and Abstract Syntax Tree.
 *
 * Java 1.5 parser and Abstract Syntax Tree is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Java 1.5 parser and Abstract Syntax Tree is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java 1.5 parser and Abstract Syntax Tree.  If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * Created on 05/10/2006
 */
package com.github.javaparser.ast;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EmptyTypeDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;

import java.util.List;

/**
 * <p>
 * This class represents the entire compilation unit. Each java file denotes a
 * compilation unit.
 * </p>
 * The CompilationUnit is constructed following the syntax:<br>
 * <code>
 * <table>
 * <tr valign=baseline>
 *   <td align=right>CompilationUnit</td>
 *   <td align=center>::=</td>
 *   <td align=left>
 *   ( {@link PackageDeclaration} )?<br> 
 *   ( {@link ImportDeclaration} )*<br>
 *   ( {@link TypeDeclaration} )*<br>
 *   </td>
 * </tr>
 * </table> 
 * </code>
 * 
 * @author Julio Vilmar Gesser
 */
public final class CompilationUnit extends Node {

    private PackageDeclaration pakage;

    private List<ImportDeclaration> imports;

    private List<TypeDeclaration> types;

    public CompilationUnit() {
    }

    public CompilationUnit(PackageDeclaration pakage, List<ImportDeclaration> imports, List<TypeDeclaration> types) {
        setPackage(pakage);
        setImports(imports);
        setTypes(types);
    }

    public CompilationUnit(int beginLine, int beginColumn, int endLine, int endColumn, PackageDeclaration pakage, List<ImportDeclaration> imports, List<TypeDeclaration> types) {
        super(beginLine, beginColumn, endLine, endColumn);
        setPackage(pakage);
        setImports(imports);
        setTypes(types);
    }

    @Override
    public <R, A> R accept(GenericVisitor<R, A> v, A arg) {
        return v.visit(this, arg);
    }

    @Override
    public <A> void accept(VoidVisitor<A> v, A arg) {
        v.visit(this, arg);
    }

    /**
     * Return a list containing all comments declared in this compilation unit.
     * Including javadocs, line comments and block comments of all types,
     * inner-classes and other members.<br>
     * If there is no comment, <code>null</code> is returned.
     * 
     * @return list with all comments of this compilation unit or
     *         <code>null</code>
     * @see JavadocComment
     * @see com.github.javaparser.ast.comments.LineComment
     * @see com.github.javaparser.ast.comments.BlockComment
     */
    public List<Comment> getComments() {
        return this.getAllContainedComments();
    }

    /**
     * Retrieves the list of imports declared in this compilation unit or
     * <code>null</code> if there is no import.
     * 
     * @return the list of imports or <code>null</code> if there is no import
     */
    public List<ImportDeclaration> getImports() {
        return imports;
    }

    /**
     * Retrieves the package declaration of this compilation unit.<br>
     * If this compilation unit has no package declaration (default package),
     * <code>null</code> is returned.
     * 
     * @return the package declaration or <code>null</code>
     */
    public PackageDeclaration getPackage() {
        return pakage;
    }

    /**
     * Return the list of types declared in this compilation unit.<br>
     * If there is no types declared, <code>null</code> is returned.
     * 
     * @return the list of types or <code>null</code> null if there is no type
     * @see AnnotationDeclaration
     * @see ClassOrInterfaceDeclaration
     * @see EmptyTypeDeclaration
     * @see EnumDeclaration
     */
    public List<TypeDeclaration> getTypes() {
        return types;
    }

    /**
     * Sets the list of comments of this compilation unit.
     * 
     * @param comments
     *            the list of comments
     */
    public void setComments(List<Comment> comments) {
        throw new RuntimeException("Not implemented!");
    }

    /**
     * Sets the list of imports of this compilation unit. The list is initially
     * <code>null</code>.
     * 
     * @param imports
     *            the list of imports
     */
    public void setImports(List<ImportDeclaration> imports) {
        this.imports = imports;
		setAsParentNodeOf(this.imports);
    }

    /**
     * Sets or clear the package declarations of this compilation unit.
     * 
     * @param pakage
     *            the pakage declaration to set or <code>null</code> to default
     *            package
     */
    public void setPackage(PackageDeclaration pakage) {
        this.pakage = pakage;
		setAsParentNodeOf(this.pakage);
    }

    /**
     * Sets the list of types declared in this compilation unit.
     * 
     * @param types
     *            the lis of types
     */
    public void setTypes(List<TypeDeclaration> types) {
        this.types = types;
		setAsParentNodeOf(this.types);
    }
}
