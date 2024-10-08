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

package me.tomassetti.symbolsolver.javaparsermodel.contexts;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import me.tomassetti.symbolsolver.model.declarations.ValueDeclaration;
import me.tomassetti.symbolsolver.core.resolution.Context;
import me.tomassetti.symbolsolver.model.resolution.SymbolReference;
import me.tomassetti.symbolsolver.model.resolution.TypeSolver;
import me.tomassetti.symbolsolver.model.resolution.Value;
import me.tomassetti.symbolsolver.model.usages.typesystem.Type;
import me.tomassetti.symbolsolver.resolution.*;
import me.tomassetti.symbolsolver.javaparsermodel.JavaParserFactory;

import java.util.Optional;

import static me.tomassetti.symbolsolver.javaparser.Navigator.getParentNode;

/**
 * @author Federico Tomassetti
 */
public abstract class AbstractJavaParserContext<N extends Node> implements Context {

    protected N wrappedNode;
    protected TypeSolver typeSolver;

    public AbstractJavaParserContext(N wrappedNode, TypeSolver typeSolver) {
        if (wrappedNode == null) {
            throw new NullPointerException();
        }
        this.wrappedNode = wrappedNode;
        this.typeSolver = typeSolver;
    }

    protected static final SymbolReference<ValueDeclaration> solveWith(SymbolDeclarator symbolDeclarator, String name) {
        for (ValueDeclaration decl : symbolDeclarator.getSymbolDeclarations()) {
            if (decl.getName().equals(name)) {
                return SymbolReference.solved(decl);
            }
        }
        return SymbolReference.unsolved(ValueDeclaration.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractJavaParserContext that = (AbstractJavaParserContext) o;

        if (wrappedNode != null ? !wrappedNode.equals(that.wrappedNode) : that.wrappedNode != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return wrappedNode != null ? wrappedNode.hashCode() : 0;
    }

    @Override
    public Optional<Type> solveGenericType(String name, TypeSolver typeSolver) {
        Context parent = getParent();
        if (parent == null) {
            return Optional.empty();
        } else {
            return parent.solveGenericType(name, typeSolver);
        }
    }

    protected Optional<Value> solveWithAsValue(SymbolDeclarator symbolDeclarator, String name, TypeSolver typeSolver) {
        for (ValueDeclaration decl : symbolDeclarator.getSymbolDeclarations()) {
            if (decl.getName().equals(name)) {
                return Optional.of(Value.from(decl, typeSolver));
            }
        }
        return Optional.empty();
    }

    @Override
    public final Context getParent() {
        if (getParentNode(wrappedNode) instanceof MethodCallExpr) {
            MethodCallExpr parentCall = (MethodCallExpr) getParentNode(wrappedNode);
            boolean found = false;
            if (parentCall.getArgs() != null) {
                for (Expression expression : parentCall.getArgs()) {
                    if (expression == wrappedNode) {
                        found = true;
                    }
                }
            }
            if (found) {
                Node notMethod = getParentNode(wrappedNode);
                while (notMethod instanceof MethodCallExpr) {
                    notMethod = getParentNode(notMethod);
                }
                return JavaParserFactory.getContext(notMethod, typeSolver);
            }
        }
        Node notMethod = getParentNode(wrappedNode);
        while (notMethod instanceof MethodCallExpr) {
            notMethod = getParentNode(notMethod);
        }
        return JavaParserFactory.getContext(notMethod, typeSolver);
    }

}
