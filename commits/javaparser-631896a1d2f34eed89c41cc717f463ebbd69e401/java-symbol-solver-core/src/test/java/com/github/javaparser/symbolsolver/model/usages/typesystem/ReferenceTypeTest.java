/*
 * Copyright 2016 Federico Tomassetti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.javaparser.symbolsolver.model.usages.typesystem;

import com.github.javaparser.symbolsolver.model.declarations.ClassDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.InterfaceDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.TypeParameterDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionClassDeclaration;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionInterfaceDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JreTypeSolver;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.util.*;

import static org.junit.Assert.*;

public class ReferenceTypeTest {

    private ReferenceTypeImpl listOfA;
    private ReferenceTypeImpl listOfStrings;
    private ReferenceTypeImpl linkedListOfString;
    private ReferenceTypeImpl collectionOfString;
    private ReferenceTypeImpl listOfWildcardExtendsString;
    private ReferenceTypeImpl listOfWildcardSuperString;
    private ReferenceTypeImpl object;
    private ReferenceTypeImpl string;
    private TypeSolver typeSolver;

    @Before
    public void setup() {
        typeSolver = new JreTypeSolver();
        object = new ReferenceTypeImpl(new ReflectionClassDeclaration(Object.class, typeSolver), typeSolver);
        string = new ReferenceTypeImpl(new ReflectionClassDeclaration(String.class, typeSolver), typeSolver);
        listOfA = new ReferenceTypeImpl(
                new ReflectionInterfaceDeclaration(List.class, typeSolver),
                ImmutableList.of(new TypeVariable(TypeParameterDeclaration.onType("A", "foo.Bar", Collections.emptyList()))), typeSolver);
        listOfStrings = new ReferenceTypeImpl(
                new ReflectionInterfaceDeclaration(List.class, typeSolver),
                ImmutableList.of(new ReferenceTypeImpl(new ReflectionClassDeclaration(String.class, typeSolver), typeSolver)), typeSolver);
        linkedListOfString = new ReferenceTypeImpl(
                new ReflectionClassDeclaration(LinkedList.class, typeSolver),
                ImmutableList.of(new ReferenceTypeImpl(new ReflectionClassDeclaration(String.class, typeSolver), typeSolver)), typeSolver);
        collectionOfString = new ReferenceTypeImpl(
                new ReflectionInterfaceDeclaration(Collection.class, typeSolver),
                ImmutableList.of(new ReferenceTypeImpl(new ReflectionClassDeclaration(String.class, typeSolver), typeSolver)), typeSolver);
        listOfWildcardExtendsString = new ReferenceTypeImpl(
                new ReflectionInterfaceDeclaration(List.class, typeSolver),
                ImmutableList.of(Wildcard.extendsBound(string)), typeSolver);
        listOfWildcardSuperString = new ReferenceTypeImpl(
                new ReflectionInterfaceDeclaration(List.class, typeSolver),
                ImmutableList.of(Wildcard.superBound(string)), typeSolver);
    }

    @Test
    public void testDerivationOfTypeParameters() {
        JreTypeSolver typeSolver = new JreTypeSolver();
        ReferenceTypeImpl ref1 = new ReferenceTypeImpl(typeSolver.solveType(LinkedList.class.getCanonicalName()), typeSolver);
        assertEquals(1, ref1.typeParametersValues().size());
        assertEquals(true, ref1.typeParametersValues().get(0).isTypeVariable());
        assertEquals("E", ref1.typeParametersValues().get(0).asTypeParameter().getName());
    }

    @Test
    public void testIsArray() {
        assertEquals(false, object.isArray());
        assertEquals(false, string.isArray());
        assertEquals(false, listOfA.isArray());
        assertEquals(false, listOfStrings.isArray());
    }

    @Test
    public void testIsPrimitive() {
        assertEquals(false, object.isPrimitive());
        assertEquals(false, string.isPrimitive());
        assertEquals(false, listOfA.isPrimitive());
        assertEquals(false, listOfStrings.isPrimitive());
    }

    @Test
    public void testIsNull() {
        assertEquals(false, object.isNull());
        assertEquals(false, string.isNull());
        assertEquals(false, listOfA.isNull());
        assertEquals(false, listOfStrings.isNull());
    }

    @Test
    public void testIsReference() {
        assertEquals(true, object.isReference());
        assertEquals(true, string.isReference());
        assertEquals(true, listOfA.isReference());
        assertEquals(true, listOfStrings.isReference());
    }

    @Test
    public void testIsReferenceType() {
        assertEquals(true, object.isReferenceType());
        assertEquals(true, string.isReferenceType());
        assertEquals(true, listOfA.isReferenceType());
        assertEquals(true, listOfStrings.isReferenceType());
    }

    @Test
    public void testIsVoid() {
        assertEquals(false, object.isVoid());
        assertEquals(false, string.isVoid());
        assertEquals(false, listOfA.isVoid());
        assertEquals(false, listOfStrings.isVoid());
    }

    @Test
    public void testIsTypeVariable() {
        assertEquals(false, object.isTypeVariable());
        assertEquals(false, string.isTypeVariable());
        assertEquals(false, listOfA.isTypeVariable());
        assertEquals(false, listOfStrings.isTypeVariable());
    }

    @Test
    public void testAsReferenceTypeUsage() {
        assertTrue(object == object.asReferenceType());
        assertTrue(string == string.asReferenceType());
        assertTrue(listOfA == listOfA.asReferenceType());
        assertTrue(listOfStrings == listOfStrings.asReferenceType());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAsTypeParameter() {
        object.asTypeParameter();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAsArrayTypeUsage() {
        object.asArrayType();
    }

    @Test
    public void testAsDescribe() {
        assertEquals("java.lang.Object", object.describe());
        assertEquals("java.lang.String", string.describe());
        assertEquals("java.util.List<A>", listOfA.describe());
        assertEquals("java.util.List<java.lang.String>", listOfStrings.describe());
    }

    @Test
    public void testReplaceParam() {
        TypeParameterDeclaration tpA = TypeParameterDeclaration.onType("A", "foo.Bar", Collections.emptyList());
        assertTrue(object == object.replaceTypeVariables(tpA, object));
        assertTrue(string == string.replaceTypeVariables(tpA, object));
        assertTrue(listOfStrings == listOfStrings.replaceTypeVariables(tpA, object));
        assertEquals(listOfStrings, listOfA.replaceTypeVariables(tpA, string));
    }

    @Test
    public void testIsAssignableBySimple() {
        assertEquals(true, object.isAssignableBy(string));
        assertEquals(false, string.isAssignableBy(object));
        assertEquals(false, listOfStrings.isAssignableBy(listOfA));
        assertEquals(false, listOfA.isAssignableBy(listOfStrings));

        assertEquals(false, object.isAssignableBy(VoidType.INSTANCE));
        assertEquals(false, string.isAssignableBy(VoidType.INSTANCE));
        assertEquals(false, listOfStrings.isAssignableBy(VoidType.INSTANCE));
        assertEquals(false, listOfA.isAssignableBy(VoidType.INSTANCE));

        assertEquals(true, object.isAssignableBy(NullType.INSTANCE));
        assertEquals(true, string.isAssignableBy(NullType.INSTANCE));
        assertEquals(true, listOfStrings.isAssignableBy(NullType.INSTANCE));
        assertEquals(true, listOfA.isAssignableBy(NullType.INSTANCE));
    }

    @Test
    public void testIsAssignableByGenerics() {
        assertEquals(false, listOfStrings.isAssignableBy(listOfWildcardExtendsString));
        assertEquals(false, listOfStrings.isAssignableBy(listOfWildcardExtendsString));
        assertEquals(true, listOfWildcardExtendsString.isAssignableBy(listOfStrings));
        assertEquals(false, listOfWildcardExtendsString.isAssignableBy(listOfWildcardSuperString));
        assertEquals(true, listOfWildcardSuperString.isAssignableBy(listOfStrings));
        assertEquals(false, listOfWildcardSuperString.isAssignableBy(listOfWildcardExtendsString));
    }

    @Test
    public void testIsAssignableByGenericsInheritance() {
        assertEquals(true, collectionOfString.isAssignableBy(collectionOfString));
        assertEquals(true, collectionOfString.isAssignableBy(listOfStrings));
        assertEquals(true, collectionOfString.isAssignableBy(linkedListOfString));

        assertEquals(false, listOfStrings.isAssignableBy(collectionOfString));
        assertEquals(true, listOfStrings.isAssignableBy(listOfStrings));
        assertEquals(true, listOfStrings.isAssignableBy(linkedListOfString));

        assertEquals(false, linkedListOfString.isAssignableBy(collectionOfString));
        assertEquals(false, linkedListOfString.isAssignableBy(listOfStrings));
        assertEquals(true, linkedListOfString.isAssignableBy(linkedListOfString));
    }

    @Test
    public void testGetAllAncestorsConsideringTypeParameters() {
        assertTrue(linkedListOfString.getAllAncestors().contains(object));
        assertTrue(linkedListOfString.getAllAncestors().contains(listOfStrings));
        assertTrue(linkedListOfString.getAllAncestors().contains(collectionOfString));
        assertFalse(linkedListOfString.getAllAncestors().contains(listOfA));
    }

    class Foo {

    }

    class Bar extends Foo {

    }

    class Bazzer<A, B, C> {

    }

    class MoreBazzing<A, B> extends Bazzer<B, String, A> {

    }

    @Test
    public void testGetAllAncestorsConsideringGenericsCases() {
        ReferenceTypeImpl foo = new ReferenceTypeImpl(new ReflectionClassDeclaration(Foo.class, typeSolver), typeSolver);
        ReferenceTypeImpl bar = new ReferenceTypeImpl(new ReflectionClassDeclaration(Bar.class, typeSolver), typeSolver);
        ReferenceTypeImpl left, right;

        //YES MoreBazzing<Foo, Bar> e1 = new MoreBazzing<Foo, Bar>();
        assertEquals(true,
                new ReferenceTypeImpl(
                        new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                        ImmutableList.of(foo, bar), typeSolver)
                        .isAssignableBy(new ReferenceTypeImpl(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(foo, bar), typeSolver))
        );

        //YES MoreBazzing<? extends Foo, Bar> e2 = new MoreBazzing<Foo, Bar>();
        assertEquals(true,
                new ReferenceTypeImpl(
                        new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                        ImmutableList.of(Wildcard.extendsBound(foo), bar), typeSolver)
                        .isAssignableBy(new ReferenceTypeImpl(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(foo, bar), typeSolver))
        );

        //YES MoreBazzing<Foo, ? extends Bar> e3 = new MoreBazzing<Foo, Bar>();
        assertEquals(true,
                new ReferenceTypeImpl(
                        new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                        ImmutableList.of(foo, Wildcard.extendsBound(bar)), typeSolver)
                        .isAssignableBy(new ReferenceTypeImpl(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(foo, bar), typeSolver))
        );

        //YES MoreBazzing<? extends Foo, ? extends Foo> e4 = new MoreBazzing<Foo, Bar>();
        assertEquals(true,
                new ReferenceTypeImpl(
                        new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                        ImmutableList.of(Wildcard.extendsBound(foo), Wildcard.extendsBound(foo)), typeSolver)
                        .isAssignableBy(new ReferenceTypeImpl(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(foo, bar), typeSolver))
        );

        //YES MoreBazzing<? extends Foo, ? extends Foo> e5 = new MoreBazzing<Bar, Bar>();
        left = new ReferenceTypeImpl(
                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                ImmutableList.of(Wildcard.extendsBound(foo), Wildcard.extendsBound(foo)), typeSolver);
        right = new ReferenceTypeImpl(
                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                ImmutableList.of(bar, bar), typeSolver);
        assertEquals(true, left.isAssignableBy(right));

        //YES Bazzer<Object, String, String> e6 = new MoreBazzing<String, Object>();
        left = new ReferenceTypeImpl(
                new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                ImmutableList.of(object, string, string), typeSolver);
        right = new ReferenceTypeImpl(
                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                ImmutableList.of(string, object), typeSolver);
        assertEquals(true, left.isAssignableBy(right));

        //YES Bazzer<String,String,String> e7 = new MoreBazzing<String, String>();
        assertEquals(true,
                new ReferenceTypeImpl(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(string, string, string), typeSolver)
                        .isAssignableBy(new ReferenceTypeImpl(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(string, string), typeSolver))
        );

        //YES Bazzer<Bar,String,Foo> e8 = new MoreBazzing<Foo, Bar>();
        assertEquals(true,
                new ReferenceTypeImpl(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(bar, string, foo), typeSolver)
                        .isAssignableBy(new ReferenceTypeImpl(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(foo, bar), typeSolver))
        );

        //YES Bazzer<Foo,String,Bar> e9 = new MoreBazzing<Bar, Foo>();
        assertEquals(true,
                new ReferenceTypeImpl(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(foo, string, bar), typeSolver)
                        .isAssignableBy(new ReferenceTypeImpl(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(bar, foo), typeSolver))
        );

        //NO Bazzer<Bar,String,Foo> n1 = new MoreBazzing<Bar, Foo>();
        assertEquals(false,
                new ReferenceTypeImpl(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(bar, string, foo), typeSolver)
                        .isAssignableBy(new ReferenceTypeImpl(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(bar, foo), typeSolver))
        );

        //NO Bazzer<Bar,String,Bar> n2 = new MoreBazzing<Bar, Foo>();
        assertEquals(false,
                new ReferenceTypeImpl(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(bar, string, foo), typeSolver)
                        .isAssignableBy(new ReferenceTypeImpl(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(bar, foo), typeSolver))
        );

        //NO Bazzer<Foo,Object,Bar> n3 = new MoreBazzing<Bar, Foo>();
        assertEquals(false,
                new ReferenceTypeImpl(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(foo, object, bar), typeSolver)
                        .isAssignableBy(new ReferenceTypeImpl(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(bar, foo), typeSolver))
        );
    }

    @Test
    public void charSequenceIsAssignableToObject() {
        TypeSolver typeSolver = new JreTypeSolver();
        ReferenceTypeImpl charSequence = new ReferenceTypeImpl(new ReflectionInterfaceDeclaration(CharSequence.class, typeSolver), typeSolver);
        ReferenceTypeImpl object = new ReferenceTypeImpl(new ReflectionClassDeclaration(Object.class, typeSolver), typeSolver);
        assertEquals(false, charSequence.isAssignableBy(object));
        assertEquals(true, object.isAssignableBy(charSequence));
    }

    @Test
    public void testGetFieldTypeExisting() {
        class Foo<A> {
            List<A> elements;
        }

        TypeSolver typeSolver = new JreTypeSolver();
        ReferenceTypeImpl ref = new ReferenceTypeImpl(new ReflectionClassDeclaration(Foo.class, typeSolver), typeSolver);

        assertEquals(true, ref.getFieldType("elements").isPresent());
        assertEquals(true, ref.getFieldType("elements").get().isReferenceType());
        assertEquals(List.class.getCanonicalName(), ref.getFieldType("elements").get().asReferenceType().getQualifiedName());
        assertEquals(1, ref.getFieldType("elements").get().asReferenceType().typeParametersValues().size());
        assertEquals(true, ref.getFieldType("elements").get().asReferenceType().typeParametersValues().get(0).isTypeVariable());
        assertEquals("A", ref.getFieldType("elements").get().asReferenceType().typeParametersValues().get(0).asTypeParameter().getName());

        ref = new ReferenceTypeImpl(new ReflectionClassDeclaration(Foo.class, typeSolver),
                ImmutableList.of(new ReferenceTypeImpl(new ReflectionClassDeclaration(String.class, typeSolver), typeSolver)),
                typeSolver);

        assertEquals(true, ref.getFieldType("elements").isPresent());
        assertEquals(true, ref.getFieldType("elements").get().isReferenceType());
        assertEquals(List.class.getCanonicalName(), ref.getFieldType("elements").get().asReferenceType().getQualifiedName());
        assertEquals(1, ref.getFieldType("elements").get().asReferenceType().typeParametersValues().size());
        assertEquals(true, ref.getFieldType("elements").get().asReferenceType().typeParametersValues().get(0).isReferenceType());
        assertEquals(String.class.getCanonicalName(), ref.getFieldType("elements").get().asReferenceType().typeParametersValues().get(0).asReferenceType().getQualifiedName());
    }

    @Test
    public void testGetFieldTypeUnexisting() {
        class Foo<A> {
            List<A> elements;
        }

        TypeSolver typeSolver = new JreTypeSolver();
        ReferenceTypeImpl ref = new ReferenceTypeImpl(new ReflectionClassDeclaration(Foo.class, typeSolver), typeSolver);

        assertEquals(false, ref.getFieldType("bar").isPresent());

        ref = new ReferenceTypeImpl(new ReflectionClassDeclaration(Foo.class, typeSolver),
                ImmutableList.of(new ReferenceTypeImpl(new ReflectionClassDeclaration(String.class, typeSolver), typeSolver)),
                typeSolver);

        assertEquals(false, ref.getFieldType("bar").isPresent());
    }

    @Test
    public void testTypeParamValue() {
        TypeSolver typeResolver = new JreTypeSolver();
        ClassDeclaration arraylist = new ReflectionClassDeclaration(ArrayList.class, typeResolver);
        ClassDeclaration abstractList = new ReflectionClassDeclaration(AbstractList.class, typeResolver);
        ClassDeclaration abstractCollection = new ReflectionClassDeclaration(AbstractCollection.class, typeResolver);
        InterfaceDeclaration list = new ReflectionInterfaceDeclaration(List.class, typeResolver);
        InterfaceDeclaration collection = new ReflectionInterfaceDeclaration(Collection.class, typeResolver);
        InterfaceDeclaration iterable = new ReflectionInterfaceDeclaration(Iterable.class, typeResolver);
        Type string = new ReferenceTypeImpl(new ReflectionClassDeclaration(String.class, typeResolver), typeResolver);
        ReferenceType arrayListOfString = new ReferenceTypeImpl(arraylist, ImmutableList.of(string), typeResolver);
        assertEquals(Optional.of(string), arrayListOfString.typeParamValue(arraylist.getTypeParameters().get(0)));
        assertEquals(Optional.of(string), arrayListOfString.typeParamValue(abstractList.getTypeParameters().get(0)));
        assertEquals(Optional.of(string), arrayListOfString.typeParamValue(abstractCollection.getTypeParameters().get(0)));
        assertEquals(Optional.of(string), arrayListOfString.typeParamValue(list.getTypeParameters().get(0)));
        assertEquals(Optional.of(string), arrayListOfString.typeParamValue(collection.getTypeParameters().get(0)));
        assertEquals(Optional.of(string), arrayListOfString.typeParamValue(iterable.getTypeParameters().get(0)));
    }

    @Test
    public void testGetAllAncestors() {
        TypeSolver typeResolver = new JreTypeSolver();
        ClassDeclaration arraylist = new ReflectionClassDeclaration(ArrayList.class, typeResolver);
        ClassDeclaration abstractList = new ReflectionClassDeclaration(AbstractList.class, typeResolver);
        ClassDeclaration abstractCollection = new ReflectionClassDeclaration(AbstractCollection.class, typeResolver);
        InterfaceDeclaration list = new ReflectionInterfaceDeclaration(List.class, typeResolver);
        InterfaceDeclaration collection = new ReflectionInterfaceDeclaration(Collection.class, typeResolver);
        InterfaceDeclaration iterable = new ReflectionInterfaceDeclaration(Iterable.class, typeResolver);
        Type string = new ReferenceTypeImpl(new ReflectionClassDeclaration(String.class, typeResolver), typeResolver);
        ReferenceType arrayListOfString = new ReferenceTypeImpl(arraylist, ImmutableList.of(string), typeResolver);

        Map<String, ReferenceType> ancestors = new HashMap<>();
        arrayListOfString.getAllAncestors().forEach(a -> ancestors.put(a.getQualifiedName(), a));
        assertEquals(9, ancestors.size());

        assertEquals(new ReferenceTypeImpl(new ReflectionInterfaceDeclaration(RandomAccess.class, typeResolver), typeResolver), ancestors.get("java.util.RandomAccess"));
        assertEquals(new ReferenceTypeImpl(new ReflectionClassDeclaration(AbstractCollection.class, typeResolver), ImmutableList.of(string), typeResolver), ancestors.get("java.util.AbstractCollection"));
        assertEquals(new ReferenceTypeImpl(new ReflectionInterfaceDeclaration(List.class, typeResolver), ImmutableList.of(string), typeResolver), ancestors.get("java.util.List"));
        assertEquals(new ReferenceTypeImpl(new ReflectionInterfaceDeclaration(Cloneable.class, typeResolver), typeResolver), ancestors.get("java.lang.Cloneable"));
        assertEquals(new ReferenceTypeImpl(new ReflectionInterfaceDeclaration(Collection.class, typeResolver), ImmutableList.of(string), typeResolver), ancestors.get("java.util.Collection"));
        assertEquals(new ReferenceTypeImpl(new ReflectionClassDeclaration(AbstractList.class, typeResolver), ImmutableList.of(string), typeResolver), ancestors.get("java.util.AbstractList"));
        assertEquals(new ReferenceTypeImpl(new ReflectionClassDeclaration(Object.class, typeResolver), typeResolver), ancestors.get("java.lang.Object"));
        assertEquals(new ReferenceTypeImpl(new ReflectionInterfaceDeclaration(Iterable.class, typeResolver), ImmutableList.of(string), typeResolver), ancestors.get("java.lang.Iterable"));
        assertEquals(new ReferenceTypeImpl(new ReflectionInterfaceDeclaration(Serializable.class, typeResolver), typeResolver), ancestors.get("java.io.Serializable"));
    }

}
