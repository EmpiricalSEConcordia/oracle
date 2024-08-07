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

package com.github.javaparser.symbolsolver.reflectionmodel;

import com.github.javaparser.symbolsolver.core.resolution.Context;
import com.github.javaparser.symbolsolver.model.declarations.MethodDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.TypeParameterDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.TypeParametrizable;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.usages.MethodUsage;
import com.github.javaparser.symbolsolver.model.usages.typesystem.ReferenceTypeImpl;
import com.github.javaparser.symbolsolver.model.usages.typesystem.Type;
import com.github.javaparser.symbolsolver.model.usages.typesystem.TypeVariable;
import com.github.javaparser.symbolsolver.resolution.MethodResolutionLogic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class ReflectionMethodResolutionLogic {

    static Optional<MethodUsage> solveMethodAsUsage(String name, List<Type> argumentsTypes, TypeSolver typeSolver,
                                                    Context invokationContext, List<Type> typeParameterValues,
                                                    TypeParametrizable typeParametrizable, Class clazz) {
        if (typeParameterValues.size() != typeParametrizable.getTypeParameters().size()) {
            // if it is zero we are going to ignore them
            if (!typeParametrizable.getTypeParameters().isEmpty()) {
                // Parameters not specified, so default to Object
                typeParameterValues = new ArrayList<>();
                for (int i = 0; i < typeParametrizable.getTypeParameters().size(); i++) {
                    typeParameterValues.add(new ReferenceTypeImpl(new ReflectionClassDeclaration(Object.class, typeSolver), typeSolver));
                }
            }
        }
        List<MethodUsage> methods = new ArrayList<>();
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name) && !method.isBridge() && !method.isSynthetic()) {
                MethodDeclaration methodDeclaration = new ReflectionMethodDeclaration(method, typeSolver);
                MethodUsage methodUsage = new MethodUsage(methodDeclaration);
                int i = 0;
                for (TypeParameterDeclaration tp : typeParametrizable.getTypeParameters()) {
                    methodUsage = methodUsage.replaceTypeParameterByName(tp, typeParameterValues.get(i));
                    i++;
                }
                for (TypeParameterDeclaration methodTypeParameter : methodDeclaration.getTypeParameters()) {
                    methodUsage = methodUsage.replaceTypeParameterByName(methodTypeParameter, new TypeVariable(methodTypeParameter));
                }
                methods.add(methodUsage);
            }

        }
        final List<Type> finalTypeParameterValues = typeParameterValues;
        argumentsTypes = argumentsTypes.stream().map((pt) -> {
            int i = 0;
            for (TypeParameterDeclaration tp : typeParametrizable.getTypeParameters()) {
                pt = pt.replaceParam(tp.getName(), finalTypeParameterValues.get(i));
                i++;
            }
            return pt;
        }).collect(Collectors.toList());
        return MethodResolutionLogic.findMostApplicableUsage(methods, name, argumentsTypes, typeSolver);
    }
}
