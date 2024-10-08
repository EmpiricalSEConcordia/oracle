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

package me.tomassetti.symbolsolver.model.usages.typesystem;

import me.tomassetti.symbolsolver.model.declarations.TypeParameterDeclaration;
import me.tomassetti.symbolsolver.reflectionmodel.ReflectionClassDeclaration;
import me.tomassetti.symbolsolver.model.resolution.TypeSolver;
import me.tomassetti.symbolsolver.resolution.typesolvers.JreTypeSolver;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class WildcardUsageTest {

    class Foo { }

    class Bar extends Foo { }

    private TypeSolver typeSolver;
    private ReferenceTypeImpl foo;
    private ReferenceTypeImpl bar;
    private ReferenceTypeImpl object;
    private ReferenceTypeImpl string;
    private Wildcard unbounded = Wildcard.UNBOUNDED;
    private Wildcard superFoo;
    private Wildcard superBar;
    private Wildcard extendsFoo;
    private Wildcard extendsBar;
    private Wildcard superA;
    private Wildcard extendsA;
    private Wildcard superString;
    private Wildcard extendsString;
    private TypeVariable a;

    @Before
    public void setup() {
        typeSolver = new JreTypeSolver();
        foo = new ReferenceTypeImpl(new ReflectionClassDeclaration(Foo.class, typeSolver), typeSolver);
        bar = new ReferenceTypeImpl(new ReflectionClassDeclaration(Bar.class, typeSolver), typeSolver);
        object = new ReferenceTypeImpl(new ReflectionClassDeclaration(Object.class, typeSolver), typeSolver);
        string = new ReferenceTypeImpl(new ReflectionClassDeclaration(String.class, typeSolver), typeSolver);
        superFoo = Wildcard.superBound(foo);
        superBar = Wildcard.superBound(bar);
        extendsFoo = Wildcard.extendsBound(foo);
        extendsBar = Wildcard.extendsBound(bar);
        a = new TypeVariable(TypeParameterDeclaration.onClass("A", "foo.Bar", Collections.emptyList()));
        superA = Wildcard.superBound(a);
        extendsA = Wildcard.extendsBound(a);
        superString = Wildcard.superBound(string);
        extendsString = Wildcard.extendsBound(string);
    }

    @Test
    public void testIsArray() {
        assertEquals(false, unbounded.isArray());
        assertEquals(false, superFoo.isArray());
        assertEquals(false, superBar.isArray());
        assertEquals(false, extendsFoo.isArray());
        assertEquals(false, extendsBar.isArray());
    }

    @Test
    public void testIsPrimitive() {
        assertEquals(false, unbounded.isPrimitive());
        assertEquals(false, superFoo.isPrimitive());
        assertEquals(false, superBar.isPrimitive());
        assertEquals(false, extendsFoo.isPrimitive());
        assertEquals(false, extendsBar.isPrimitive());
    }

    @Test
    public void testIsNull() {
        assertEquals(false, unbounded.isNull());
        assertEquals(false, superFoo.isNull());
        assertEquals(false, superBar.isNull());
        assertEquals(false, extendsFoo.isNull());
        assertEquals(false, extendsBar.isNull());
    }

    @Test
    public void testIsReference() {
        assertEquals(true, unbounded.isReference());
        assertEquals(true, superFoo.isReference());
        assertEquals(true, superBar.isReference());
        assertEquals(true, extendsFoo.isReference());
        assertEquals(true, extendsBar.isReference());
    }

    @Test
    public void testIsReferenceType() {
        assertEquals(false, unbounded.isReferenceType());
        assertEquals(false, superFoo.isReferenceType());
        assertEquals(false, superBar.isReferenceType());
        assertEquals(false, extendsFoo.isReferenceType());
        assertEquals(false, extendsBar.isReferenceType());    }

    @Test
    public void testIsVoid() {
        assertEquals(false, unbounded.isVoid());
        assertEquals(false, superFoo.isVoid());
        assertEquals(false, superBar.isVoid());
        assertEquals(false, extendsFoo.isVoid());
        assertEquals(false, extendsBar.isVoid());
    }

    @Test
    public void testIsTypeVariable() {
        assertEquals(false, unbounded.isTypeVariable());
        assertEquals(false, superFoo.isTypeVariable());
        assertEquals(false, superBar.isTypeVariable());
        assertEquals(false, extendsFoo.isTypeVariable());
        assertEquals(false, extendsBar.isTypeVariable());
    }

    @Test
    public void testIsWildcard() {
        assertEquals(true, unbounded.isWildcard());
        assertEquals(true, superFoo.isWildcard());
        assertEquals(true, superBar.isWildcard());
        assertEquals(true, extendsFoo.isWildcard());
        assertEquals(true, extendsBar.isWildcard());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAsArrayTypeUsage() {
        unbounded.asArrayType();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAsReferenceTypeUsage() {
        unbounded.asReferenceType();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAsTypeParameter() {
        unbounded.asTypeParameter();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAsPrimitive() {
        unbounded.asPrimitive();
    }

    @Test
    public void testAsWildcard() {
        assertTrue(unbounded == unbounded.asWildcard());
        assertTrue(superFoo == superFoo.asWildcard());
        assertTrue(superBar == superBar.asWildcard());
        assertTrue(extendsFoo == extendsFoo.asWildcard());
        assertTrue(extendsBar == extendsBar.asWildcard());
    }

    @Test
    public void testAsDescribe() {
        assertEquals("?", unbounded.describe());
        assertEquals("? super me.tomassetti.symbolsolver.model.usages.typesystem.WildcardUsageTest.Foo", superFoo.describe());
        assertEquals("? super me.tomassetti.symbolsolver.model.usages.typesystem.WildcardUsageTest.Bar", superBar.describe());
        assertEquals("? extends me.tomassetti.symbolsolver.model.usages.typesystem.WildcardUsageTest.Foo", extendsFoo.describe());
        assertEquals("? extends me.tomassetti.symbolsolver.model.usages.typesystem.WildcardUsageTest.Bar", extendsBar.describe());
    }

    @Test
    public void testReplaceParam() {
        assertTrue(unbounded == unbounded.replaceParam("A", string));
        assertTrue(superFoo == superFoo.replaceParam("A", string));
        assertTrue(extendsFoo == extendsFoo.replaceParam("A", string));
        assertEquals(superString, superA.replaceParam("A", string));
        assertEquals(extendsString, extendsA.replaceParam("A", string));
        assertTrue(superA == superA.replaceParam("B", string));
        assertTrue(extendsA == extendsA.replaceParam("B", string));
    }

    @Test
    public void testIsAssignableBySimple() {
        assertEquals(false, unbounded.isAssignableBy(object));
        assertEquals(true, object.isAssignableBy(unbounded));
        assertEquals(false, string.isAssignableBy(unbounded));
        assertEquals(true, superFoo.isAssignableBy(foo));
        assertEquals(false, foo.isAssignableBy(superFoo));
        assertEquals(false, extendsFoo.isAssignableBy(foo));
        assertEquals(true, foo.isAssignableBy(extendsFoo));
    }

    /*@Test
    public void testIsAssignableByGenerics() {
        assertEquals(false, listOfStrings.isAssignableBy(listOfWildcardExtendsString));
        assertEquals(false, listOfStrings.isAssignableBy(listOfWildcardExtendsString));
        assertEquals(true,  listOfWildcardExtendsString.isAssignableBy(listOfStrings));
        assertEquals(false, listOfWildcardExtendsString.isAssignableBy(listOfWildcardSuperString));
        assertEquals(true,  listOfWildcardSuperString.isAssignableBy(listOfStrings));
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

    @Test
    public void testGetAllAncestorsConsideringGenericsCases() {
        ReferenceTypeUsage foo = new ReferenceTypeUsage(new ReflectionClassDeclaration(Foo.class, typeSolver), typeSolver);
        ReferenceTypeUsage bar = new ReferenceTypeUsage(new ReflectionClassDeclaration(Bar.class, typeSolver), typeSolver);
        ReferenceTypeUsage left, right;

        //YES MoreBazzing<Foo, Bar> e1 = new MoreBazzing<Foo, Bar>();
        assertEquals(true,
                new ReferenceTypeUsage(
                    new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                    ImmutableList.of(foo, bar), typeSolver)
                .isAssignableBy(new ReferenceTypeUsage(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(foo, bar), typeSolver))
        );

        //YES MoreBazzing<? extends Foo, Bar> e2 = new MoreBazzing<Foo, Bar>();
        assertEquals(true,
                new ReferenceTypeUsage(
                        new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                        ImmutableList.of(WildcardUsage.extendsBound(foo), bar), typeSolver)
                        .isAssignableBy(new ReferenceTypeUsage(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(foo, bar), typeSolver))
        );

        //YES MoreBazzing<Foo, ? extends Bar> e3 = new MoreBazzing<Foo, Bar>();
        assertEquals(true,
                new ReferenceTypeUsage(
                        new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                        ImmutableList.of(foo, WildcardUsage.extendsBound(bar)), typeSolver)
                        .isAssignableBy(new ReferenceTypeUsage(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(foo, bar), typeSolver))
        );

        //YES MoreBazzing<? extends Foo, ? extends Foo> e4 = new MoreBazzing<Foo, Bar>();
        assertEquals(true,
                new ReferenceTypeUsage(
                        new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                        ImmutableList.of(WildcardUsage.extendsBound(foo), WildcardUsage.extendsBound(foo)), typeSolver)
                        .isAssignableBy(new ReferenceTypeUsage(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(foo, bar), typeSolver))
        );

        //YES MoreBazzing<? extends Foo, ? extends Foo> e5 = new MoreBazzing<Bar, Bar>();
        left = new ReferenceTypeUsage(
                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                ImmutableList.of(WildcardUsage.extendsBound(foo), WildcardUsage.extendsBound(foo)), typeSolver);
        right = new ReferenceTypeUsage(
                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                ImmutableList.of(bar, bar), typeSolver);
        assertEquals(true, left.isAssignableBy(right));

        //YES Bazzer<Object, String, String> e6 = new MoreBazzing<String, Object>();
        left = new ReferenceTypeUsage(
                new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                ImmutableList.of(object, string, string), typeSolver);
        right = new ReferenceTypeUsage(
                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                ImmutableList.of(string, object), typeSolver);
        assertEquals(true, left.isAssignableBy(right));

        //YES Bazzer<String,String,String> e7 = new MoreBazzing<String, String>();
        assertEquals(true,
                new ReferenceTypeUsage(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(string, string, string), typeSolver)
                        .isAssignableBy(new ReferenceTypeUsage(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(string, string), typeSolver))
        );

        //YES Bazzer<Bar,String,Foo> e8 = new MoreBazzing<Foo, Bar>();
        assertEquals(true,
                new ReferenceTypeUsage(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(bar, string, foo), typeSolver)
                        .isAssignableBy(new ReferenceTypeUsage(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(foo, bar), typeSolver))
        );

        //YES Bazzer<Foo,String,Bar> e9 = new MoreBazzing<Bar, Foo>();
        assertEquals(true,
                new ReferenceTypeUsage(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(foo, string, bar), typeSolver)
                        .isAssignableBy(new ReferenceTypeUsage(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(bar, foo), typeSolver))
        );

        //NO Bazzer<Bar,String,Foo> n1 = new MoreBazzing<Bar, Foo>();
        assertEquals(false,
                new ReferenceTypeUsage(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(bar,string,foo), typeSolver)
                        .isAssignableBy(new ReferenceTypeUsage(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(bar, foo), typeSolver))
        );

        //NO Bazzer<Bar,String,Bar> n2 = new MoreBazzing<Bar, Foo>();
        assertEquals(false,
                new ReferenceTypeUsage(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(bar, string, foo), typeSolver)
                        .isAssignableBy(new ReferenceTypeUsage(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(bar, foo), typeSolver))
        );

        //NO Bazzer<Foo,Object,Bar> n3 = new MoreBazzing<Bar, Foo>();
        assertEquals(false,
                new ReferenceTypeUsage(
                        new ReflectionClassDeclaration(Bazzer.class, typeSolver),
                        ImmutableList.of(foo, object, bar), typeSolver)
                        .isAssignableBy(new ReferenceTypeUsage(
                                new ReflectionClassDeclaration(MoreBazzing.class, typeSolver),
                                ImmutableList.of(bar, foo), typeSolver))
        );
    }

    @Test
    public void charSequenceIsAssignableToObject() {
        TypeSolver typeSolver = new JreTypeSolver();
        ReferenceTypeUsage charSequence = new ReferenceTypeUsage(new ReflectionInterfaceDeclaration(CharSequence.class, typeSolver), typeSolver);
        ReferenceTypeUsage object = new ReferenceTypeUsage(new ReflectionClassDeclaration(Object.class, typeSolver), typeSolver);
        assertEquals(false, charSequence.isAssignableBy(object));
        assertEquals(true, object.isAssignableBy(charSequence));
    }

    @Test
    public void testGetFieldTypeExisting() {
        class Foo<A> {
            List<A> elements;
        }

        TypeSolver typeSolver = new JreTypeSolver();
        ReferenceTypeUsage ref = new ReferenceTypeUsage(new ReflectionClassDeclaration(Foo.class, typeSolver), typeSolver);

        assertEquals(true, ref.getFieldType("elements").isPresent());
        assertEquals(true, ref.getFieldType("elements").get().isReferenceType());
        assertEquals(List.class.getCanonicalName(), ref.getFieldType("elements").get().asReferenceType().getQualifiedName());
        assertEquals(1, ref.getFieldType("elements").get().asReferenceType().typeParametersValues().size());
        assertEquals(true, ref.getFieldType("elements").get().asReferenceType().typeParametersValues().get(0).isTypeVariable());
        assertEquals("A", ref.getFieldType("elements").get().asReferenceType().typeParametersValues().get(0).asTypeParameter().getName());

        ref = new ReferenceTypeUsage(new ReflectionClassDeclaration(Foo.class, typeSolver),
                ImmutableList.of(new ReferenceTypeUsage(new ReflectionClassDeclaration(String.class, typeSolver), typeSolver)),
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
        ReferenceTypeUsage ref = new ReferenceTypeUsage(new ReflectionClassDeclaration(Foo.class, typeSolver), typeSolver);

        assertEquals(false, ref.getFieldType("bar").isPresent());

        ref = new ReferenceTypeUsage(new ReflectionClassDeclaration(Foo.class, typeSolver),
                ImmutableList.of(new ReferenceTypeUsage(new ReflectionClassDeclaration(String.class, typeSolver), typeSolver)),
                typeSolver);

        assertEquals(false, ref.getFieldType("bar").isPresent());
    }*/

}
