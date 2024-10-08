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

import com.github.javaparser.symbolsolver.model.declarations.MethodDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.TypeParameterDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.SignatureAttribute;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Federico Tomassetti
 */
public class JavassistTypeDeclarationAdapter {

    private CtClass ctClass;
    private TypeSolver typeSolver;

    public JavassistTypeDeclarationAdapter(CtClass ctClass, TypeSolver typeSolver) {
        this.ctClass = ctClass;
        this.typeSolver = typeSolver;
    }

    public Set<MethodDeclaration> getDeclaredMethods() {
        return Arrays.stream(ctClass.getDeclaredMethods())
                .map(m -> new JavassistMethodDeclaration(m, typeSolver))
                .collect(Collectors.toSet());
    }

    public List<TypeParameterDeclaration> getTypeParameters() {
        if (null == ctClass.getGenericSignature()) {
            return Collections.emptyList();
        } else {
            try {
                SignatureAttribute.ClassSignature classSignature = SignatureAttribute.toClassSignature(ctClass.getGenericSignature());
                String qualifier = ctClass.getName();
                return Arrays.<SignatureAttribute.TypeParameter>stream(classSignature.getParameters()).map((tp) -> new JavassistTypeParameter(tp, true, qualifier, typeSolver)).collect(Collectors.toList());
            } catch (BadBytecode badBytecode) {
                throw new RuntimeException(badBytecode);
            }
        }
    }

    public Optional<TypeDeclaration> containerType() {
        try {
            if (ctClass.getDeclaringClass() == null) {
                return Optional.empty();
            } else {
                throw new UnsupportedOperationException();
            }
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
