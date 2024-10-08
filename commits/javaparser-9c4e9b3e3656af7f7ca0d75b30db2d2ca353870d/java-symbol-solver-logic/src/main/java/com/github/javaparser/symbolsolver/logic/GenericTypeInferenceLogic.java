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

package com.github.javaparser.symbolsolver.logic;

import com.github.javaparser.symbolsolver.model.usages.typesystem.ReferenceType;
import com.github.javaparser.symbolsolver.model.usages.typesystem.Type;
import javaslang.Tuple2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenericTypeInferenceLogic {

    public static Map<String, Type> inferGenericTypes(List<Tuple2<Type, Type>> formalActualTypePairs) {
        Map<String, Type> map = new HashMap<>();

        for (Tuple2<Type, Type> formalActualTypePair : formalActualTypePairs) {
            Type formalType = formalActualTypePair._1;
            Type actualType = formalActualTypePair._2;
            consider(map, formalType, actualType);
            // we can infer also in the other direction
            consider(map, actualType, formalType);
        }

        return map;
    }

    private static void consider(Map<String, Type> map, Type formalType, Type actualType) {
        if (formalType == null) {
            throw new IllegalArgumentException();
        }
        if (actualType == null) {
            throw new IllegalArgumentException();
        }
        if (formalType.isTypeVariable()) {
            if (map.containsKey(formalType.asTypeParameter().getName())) {
                if (!actualType.equals(map.get(formalType.asTypeParameter().getName()))) {
                    throw new UnsupportedOperationException("Map already contains " + formalType);
                }
            }
            map.put(formalType.asTypeParameter().getName(), actualType);
        } else if (formalType.isReferenceType()) {
            if (actualType.isReferenceType()) {
                ReferenceType formalTypeAsReference = formalType.asReferenceType();
                ReferenceType actualTypeAsReference = actualType.asReferenceType();
                if (formalTypeAsReference.getQualifiedName().equals(actualTypeAsReference.getQualifiedName())) {
                    if (!formalTypeAsReference.typeParametersValues().isEmpty()) {
                        if (actualTypeAsReference.isRawType()) {
                            // nothing to do
                        } else {
                            int i = 0;
                            for (Type formalTypeParameter : formalTypeAsReference.typeParametersValues()) {
                                consider(map, formalTypeParameter, actualTypeAsReference.typeParametersValues().get(i));
                                i++;
                            }
                        }
                    }
                }
                // TODO: consider cases where the actual type extends or implements the formal type. Here the number and order of type typeParametersValues can be different.
            } else if (actualType.isTypeVariable()) {
                // nothing to do
            } else if (actualType.isWildcard()) {
                // nothing to do
            } else if (actualType.isPrimitive()) {
                // nothing to do
            } else {
                throw new UnsupportedOperationException(actualType.getClass().getCanonicalName());
            }
        } else if (formalType.isWildcard()) {
            if (actualType.isWildcard()) {
                if (actualType.asWildcard().isExtends() && formalType.asWildcard().isExtends()) {
                    consider(map, formalType.asWildcard().getBoundedType(), actualType.asWildcard().getBoundedType());
                } else if (actualType.asWildcard().isSuper() && formalType.asWildcard().isSuper()) {
                    consider(map, formalType.asWildcard().getBoundedType(), actualType.asWildcard().getBoundedType());
                }
            }
        } else if (formalType.isPrimitive()) {
            // nothing to do
        } else if (formalType.isArray()) {
            if (actualType.isArray()) {
                consider(map, formalType.asArrayType().getComponentType(), actualType.asArrayType().getComponentType());
            }
        } else {
            throw new UnsupportedOperationException(formalType.describe());
        }
    }

}
