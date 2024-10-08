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

package com.github.javaparser.symbolsolver.javassistmodel;

import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.core.resolution.Context;
import com.github.javaparser.symbolsolver.javaparsermodel.LambdaArgumentTypePlaceholder;
import com.github.javaparser.symbolsolver.logic.AbstractClassDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.symbolsolver.resolution.MethodResolutionLogic;
import com.github.javaparser.symbolsolver.resolution.SymbolSolver;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.SyntheticAttribute;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Federico Tomassetti
 */
public class JavassistClassDeclaration extends AbstractClassDeclaration {

    private CtClass ctClass;
    private TypeSolver typeSolver;
    private JavassistTypeDeclarationAdapter javassistTypeDeclarationAdapter;

    public JavassistClassDeclaration(CtClass ctClass, TypeSolver typeSolver) {
        if (ctClass == null) {
            throw new IllegalArgumentException();
        }
        if (ctClass.isInterface() || ctClass.isAnnotation() || ctClass.isPrimitive() || ctClass.isEnum()) {
            throw new IllegalArgumentException("Trying to instantiate a JavassistClassDeclaration with something which is not a class: " + ctClass.toString());
        }
        this.ctClass = ctClass;
        this.typeSolver = typeSolver;
        this.javassistTypeDeclarationAdapter = new JavassistTypeDeclarationAdapter(ctClass, typeSolver);
    }

    @Override
    protected ResolvedReferenceType object() {
        return new ReferenceTypeImpl(typeSolver.solveType(Object.class.getCanonicalName()), typeSolver);
    }

    @Override
    public boolean hasDirectlyAnnotation(String canonicalName) {
        return ctClass.hasAnnotation(canonicalName);
    }

    @Override
    public Set<ResolvedMethodDeclaration> getDeclaredMethods() {
        return javassistTypeDeclarationAdapter.getDeclaredMethods();
    }

    @Override
    public boolean isAssignableBy(ResolvedReferenceTypeDeclaration other) {
        return isAssignableBy(new ReferenceTypeImpl(other, typeSolver));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JavassistClassDeclaration that = (JavassistClassDeclaration) o;

        return ctClass.equals(that.ctClass);
    }

    @Override
    public int hashCode() {
        return ctClass.hashCode();
    }

    @Override
    public String getPackageName() {
        return ctClass.getPackageName();
    }

    @Override
    public String getClassName() {
        String className = ctClass.getName().replace('$', '.');
        if (getPackageName() != null) {
            return className.substring(getPackageName().length() + 1, className.length());
        }
        return className;
    }

    @Override
    public String getQualifiedName() {
        return ctClass.getName().replace('$', '.');
    }

    public Optional<MethodUsage> solveMethodAsUsage(String name, List<ResolvedType> argumentsTypes, TypeSolver typeSolver,
                                                    Context invokationContext, List<ResolvedType> typeParameterValues) {
        return JavassistUtils.getMethodUsage(ctClass, name, argumentsTypes, typeSolver, invokationContext);
    }

    @Deprecated
    public SymbolReference<? extends ResolvedValueDeclaration> solveSymbol(String name, TypeSolver typeSolver) {
        for (CtField field : ctClass.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return SymbolReference.solved(new JavassistFieldDeclaration(field, typeSolver));
            }
        }

        final String superclassFQN = getSuperclassFQN();
        SymbolReference<? extends ResolvedValueDeclaration> ref = solveSymbolForFQN(name, typeSolver, superclassFQN);
        if (ref.isSolved()) {
            return ref;
        }

        String[] interfaceFQNs = getInterfaceFQNs();
        for (String interfaceFQN : interfaceFQNs) {
            SymbolReference<? extends ResolvedValueDeclaration> interfaceRef = solveSymbolForFQN(name, typeSolver, interfaceFQN);
            if (interfaceRef.isSolved()) {
                return interfaceRef;
            }
        }

        return SymbolReference.unsolved(ResolvedValueDeclaration.class);
    }

    private SymbolReference<? extends ResolvedValueDeclaration> solveSymbolForFQN(String symbolName, TypeSolver typeSolver, String fqn) {
        if (fqn == null) {
            return SymbolReference.unsolved(ResolvedValueDeclaration.class);
        }

        ResolvedReferenceTypeDeclaration fqnTypeDeclaration = typeSolver.solveType(fqn);
        return new SymbolSolver(typeSolver).solveSymbolInType(fqnTypeDeclaration, symbolName);
    }

    private String[] getInterfaceFQNs() {
        return ctClass.getClassFile().getInterfaces();
    }

    private String getSuperclassFQN() {
        return ctClass.getClassFile().getSuperclass();
    }

    @Override
    public List<ResolvedReferenceType> getAncestors() {
        List<ResolvedReferenceType> ancestors = new ArrayList<>();
        if (getSuperClass() != null) {
            ancestors.add(getSuperClass());
        }
        ancestors.addAll(getInterfaces());
        return ancestors;
    }

    @Deprecated
    public SymbolReference<ResolvedMethodDeclaration> solveMethod(String name, List<ResolvedType> argumentsTypes, boolean staticOnly) {
        List<ResolvedMethodDeclaration> candidates = new ArrayList<>();
        Predicate<CtMethod> staticOnlyCheck = m -> !staticOnly || (staticOnly && Modifier.isStatic(m.getModifiers()));
        for (CtMethod method : ctClass.getDeclaredMethods()) {
            boolean isSynthetic = method.getMethodInfo().getAttribute(SyntheticAttribute.tag) != null;
            boolean isNotBridge = (method.getMethodInfo().getAccessFlags() & AccessFlag.BRIDGE) == 0;
            if (method.getName().equals(name) && !isSynthetic && isNotBridge && staticOnlyCheck.test(method)) {
                candidates.add(new JavassistMethodDeclaration(method, typeSolver));
            }
        }

        try {
            CtClass superClass = ctClass.getSuperclass();
            if (superClass != null) {
                SymbolReference<ResolvedMethodDeclaration> ref = new JavassistClassDeclaration(superClass, typeSolver).solveMethod(name, argumentsTypes, staticOnly);
                if (ref.isSolved()) {
                    candidates.add(ref.getCorrespondingDeclaration());
                }
            }
        } catch (NotFoundException e) {
            candidates.addAll(solveSuperClass().getAllMethods());
        }

        try {
            for (CtClass interfaze : ctClass.getInterfaces()) {
                SymbolReference<ResolvedMethodDeclaration> ref = new JavassistInterfaceDeclaration(interfaze, typeSolver).solveMethod(name, argumentsTypes, staticOnly);
                if (ref.isSolved()) {
                    candidates.add(ref.getCorrespondingDeclaration());
                }
            }
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }

        return MethodResolutionLogic.findMostApplicable(candidates, name, argumentsTypes, typeSolver);
    }

    public ResolvedType getUsage(Node node) {
        return new ReferenceTypeImpl(this, typeSolver);
    }

    @Override
    public boolean isAssignableBy(ResolvedType type) {
        if (type.isNull()) {
            return true;
        }

        if (type instanceof LambdaArgumentTypePlaceholder) {
            return isFunctionalInterface();
        }

        // TODO look into generics
        if (type.describe().equals(this.getQualifiedName())) {
            return true;
        }
        try {
            if (this.ctClass.getSuperclass() != null
                    && new JavassistClassDeclaration(this.ctClass.getSuperclass(), typeSolver).isAssignableBy(type)) {
                return true;
            }
            for (CtClass interfaze : ctClass.getInterfaces()) {
                if (new JavassistInterfaceDeclaration(interfaze, typeSolver).isAssignableBy(type)) {
                    return true;
                }
            }
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    @Override
    public boolean isTypeParameter() {
        return false;
    }

    @Override
    public List<ResolvedFieldDeclaration> getAllFields() {
        return javassistTypeDeclarationAdapter.getDeclaredFields();
    }

    @Override
    public String getName() {
        String[] nameElements = ctClass.getSimpleName().replace('$', '.').split("\\.");
        return nameElements[nameElements.length - 1];
    }

    @Override
    public boolean isField() {
        return false;
    }

    @Override
    public boolean isParameter() {
        return false;
    }

    @Override
    public boolean isType() {
        return true;
    }

    @Override
    public boolean isClass() {
        return !ctClass.isInterface();
    }

    @Override
    public ResolvedReferenceType getSuperClass() {
        try {
            if (ctClass.getSuperclass() == null) {
                return new ReferenceTypeImpl(typeSolver.solveType(Object.class.getCanonicalName()), typeSolver);
            }
            if (ctClass.getGenericSignature() == null) {
                return new ReferenceTypeImpl(new JavassistClassDeclaration(ctClass.getSuperclass(), typeSolver), typeSolver);
            }

            SignatureAttribute.ClassSignature classSignature = SignatureAttribute.toClassSignature(ctClass.getGenericSignature());
            return JavassistUtils.signatureTypeToType(classSignature.getSuperClass(), typeSolver, this).asReferenceType();
        } catch (NotFoundException e) {
            return solveSuperClass();
        } catch (BadBytecode e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Solve the fqn of the super class with the {@link #typeSolver}.
     */
    private ReferenceTypeImpl solveSuperClass() {
        String superclass = ctClass.getClassFile().getSuperclass();
        SymbolReference<ResolvedReferenceTypeDeclaration> reference = typeSolver.tryToSolveType(superclass);
        if (reference.isSolved()) {
            return new ReferenceTypeImpl(reference.getCorrespondingDeclaration(), typeSolver);
        }
        throw new RuntimeException("Unable to find " + superclass);
    }

    @Override
    public List<ResolvedReferenceType> getInterfaces() {
        try {
            if (ctClass.getGenericSignature() == null) {
                return Arrays.stream(ctClass.getInterfaces())
                        .map(i -> new JavassistInterfaceDeclaration(i, typeSolver))
                        .map(i -> new ReferenceTypeImpl(i, typeSolver))
                        .collect(Collectors.toList());
            } else {
                SignatureAttribute.ClassSignature classSignature = SignatureAttribute.toClassSignature(ctClass.getGenericSignature());
                return Arrays.stream(classSignature.getInterfaces())
                        .map(i -> JavassistUtils.signatureTypeToType(i, typeSolver, this).asReferenceType())
                        .collect(Collectors.toList());
            }
        } catch (NotFoundException | BadBytecode e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isInterface() {
        return ctClass.isInterface();
    }

    @Override
    public String toString() {
        return "JavassistClassDeclaration {" + ctClass.getName() + '}';
    }

    @Override
    public List<ResolvedTypeParameterDeclaration> getTypeParameters() {
        return javassistTypeDeclarationAdapter.getTypeParameters();
    }

    @Override
    public AccessSpecifier accessSpecifier() {
        return JavassistFactory.modifiersToAccessLevel(ctClass.getModifiers());
    }

    @Override
    public List<ResolvedConstructorDeclaration> getConstructors() {
        return javassistTypeDeclarationAdapter.getConstructors();
    }

    @Override
    public Optional<ResolvedReferenceTypeDeclaration> containerType() {
        return javassistTypeDeclarationAdapter.containerType();
    }

    @Override
    public Set<ResolvedReferenceTypeDeclaration> internalTypes() {
        try {
            /*
            Get all internal types of the current class and get their corresponding ReferenceTypeDeclaration.
            Finally, return them in a Set.
             */
            return Arrays.stream(ctClass.getDeclaredClasses()).map(itype -> JavassistFactory.toTypeDeclaration(itype, typeSolver)).collect(Collectors.toSet());
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResolvedReferenceTypeDeclaration getInternalType(String name) {
        /*
        The name of the ReferenceTypeDeclaration could be composed of the internal class and the outer class, e.g. A$B. That's why we search the internal type in the ending part.
        In case the name is composed of the internal type only, i.e. f.getName() returns B, it will also works.
         */
        Optional<ResolvedReferenceTypeDeclaration> type =
                this.internalTypes().stream().filter(f -> f.getName().endsWith(name)).findFirst();
        return type.orElseThrow(() ->
                new UnsolvedSymbolException("Internal type not found: " + name));
    }

    @Override
    public boolean hasInternalType(String name) {
        /*
        The name of the ReferenceTypeDeclaration could be composed of the internal class and the outer class, e.g. A$B. That's why we search the internal type in the ending part.
        In case the name is composed of the internal type only, i.e. f.getName() returns B, it will also works.
         */
        return this.internalTypes().stream().anyMatch(f -> f.getName().endsWith(name));
    }
}
