package me.tomassetti.symbolsolver.javaparsermodel.declarations;

import com.github.javaparser.ast.body.Parameter;
import me.tomassetti.symbolsolver.model.declarations.ParameterDeclaration;
import me.tomassetti.symbolsolver.model.resolution.TypeSolver;
import me.tomassetti.symbolsolver.model.typesystem.ArrayTypeUsage;
import me.tomassetti.symbolsolver.model.typesystem.TypeUsage;
import me.tomassetti.symbolsolver.javaparsermodel.JavaParserFacade;

public class JavaParserParameterDeclaration implements ParameterDeclaration {

    private Parameter wrappedNode;
    private TypeSolver typeSolver;

    public JavaParserParameterDeclaration(Parameter wrappedNode, TypeSolver typeSolver) {
        this.wrappedNode = wrappedNode;
        this.typeSolver = typeSolver;
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isField() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isParameter() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isVariadic() {
        return wrappedNode.isVarArgs();
    }

    @Override
    public boolean isType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeUsage getType() {
        TypeUsage res = JavaParserFacade.get(typeSolver).convert(wrappedNode.getType(), wrappedNode);
        for (int i=0;i<wrappedNode.getId().getArrayCount();i++) {
            res = new ArrayTypeUsage(res);
        }
        return res;
    }

	/**
	 * Returns the JavaParser node associated with this JavaParserParameterDeclaration.
	 *
	 * @return A visitable JavaParser node wrapped by this object.
	 */
	public Parameter getWrappedNode()
	{
		return wrappedNode;
	}
}
