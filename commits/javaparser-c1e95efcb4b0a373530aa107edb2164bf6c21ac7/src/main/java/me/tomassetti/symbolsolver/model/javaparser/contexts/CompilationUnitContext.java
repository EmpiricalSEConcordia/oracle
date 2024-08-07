package me.tomassetti.symbolsolver.model.javaparser.contexts;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.QualifiedNameExpr;
import me.tomassetti.symbolsolver.model.*;
import me.tomassetti.symbolsolver.model.declarations.MethodDeclaration;
import me.tomassetti.symbolsolver.model.declarations.ValueDeclaration;
import me.tomassetti.symbolsolver.model.javaparser.declarations.JavaParserClassDeclaration;
import me.tomassetti.symbolsolver.model.usages.TypeUsage;

import java.util.List;
import java.util.Optional;

/**
 * Created by federico on 30/07/15.
 */
public class CompilationUnitContext extends AbstractJavaParserContext<CompilationUnit> {

    public CompilationUnitContext(CompilationUnit wrappedNode) {
        super(wrappedNode);
    }

    @Override
    public Optional<Value> solveSymbolAsValue(String name, TypeSolver typeSolver) {
        return Optional.empty();
    }

    @Override
    public SymbolReference<? extends ValueDeclaration> solveSymbol(String name, TypeSolver typeSolver) {

        // solve absolute references
        TODO implement

        // Look among statically imported values
        if (wrappedNode.getImports() != null) {
            for (ImportDeclaration importDecl : wrappedNode.getImports()) {
                if (importDecl.isStatic()) {
                    if (importDecl.isAsterisk()) {
                        if (importDecl.getName() instanceof QualifiedNameExpr) {
                            throw new UnsupportedOperationException("A");
                        } else {
                            throw new UnsupportedOperationException("B");
                        }
                    } else {
                        if (importDecl.getName() instanceof QualifiedNameExpr) {
                            String qName = importDecl.getName().toString();
                            // split in field/method name and type name
                            int index = qName.lastIndexOf('.');
                            if (index == -1) {
                                throw new UnsupportedOperationException();
                            }
                            String typeName = qName.substring(0, index);
                            String memberName = qName.substring(index + 1);

                            me.tomassetti.symbolsolver.model.declarations.TypeDeclaration importedType = typeSolver.solveType(typeName);
                            return importedType.solveSymbol(memberName, typeSolver);
                        } else {
                            throw new UnsupportedOperationException("C");
                        }
                    }
                }
            }
        }

        return SymbolReference.unsolved(ValueDeclaration.class);
    }

    @Override
    public SymbolReference<me.tomassetti.symbolsolver.model.declarations.TypeDeclaration> solveType(String name, TypeSolver typeSolver) {
        for (TypeDeclaration type : wrappedNode.getTypes()) {
            if (type.getName().equals(name)){
                if (type instanceof ClassOrInterfaceDeclaration) {
                    return SymbolReference.solved(new JavaParserClassDeclaration((ClassOrInterfaceDeclaration)type));
                } else {
                    throw new UnsupportedOperationException();
                }
            }
        }

        if (wrappedNode.getImports() != null) {
            for (ImportDeclaration importDecl : wrappedNode.getImports()) {
                if (!importDecl.isStatic()) {
                    if (!importDecl.isAsterisk()) {
                        if (importDecl.getName() instanceof QualifiedNameExpr) {
                            String qName = importDecl.getName().toString();
                            if (qName.equals(name) || qName.endsWith("." + name)) {
                                SymbolReference<me.tomassetti.symbolsolver.model.declarations.TypeDeclaration> ref = typeSolver.tryToSolveType(qName);
                                if (ref.isSolved()) {
                                    return ref;
                                }
                            }
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    }
                }
            }
        }

        // Look in current package
        if (this.wrappedNode.getPackage() != null) {
            String qName = this.wrappedNode.getPackage().getName().toString() + "." + name;
            SymbolReference<me.tomassetti.symbolsolver.model.declarations.TypeDeclaration> ref = typeSolver.tryToSolveType(qName);
            if (ref.isSolved()) {
                return ref;
            }
        }

        // Look in the java.lang package
        SymbolReference<me.tomassetti.symbolsolver.model.declarations.TypeDeclaration> ref = typeSolver.tryToSolveType("java.lang."+name);
        if (ref.isSolved()) {
            return ref;
        }

        // DO NOT look for absolute name if this name is not qualified: you cannot import classes from the default package
        if (isQualifiedName(name)) {
            return typeSolver.tryToSolveType(name);
        } else {
            return SymbolReference.unsolved(me.tomassetti.symbolsolver.model.declarations.TypeDeclaration.class);
        }
    }

    private static boolean isQualifiedName(String name) {
        return name.contains(".");
    }

    @Override
    public SymbolReference<MethodDeclaration> solveMethod(String name, List<TypeUsage> parameterTypes, TypeSolver typeSolver) {
        throw new UnsupportedOperationException();
    }
}
