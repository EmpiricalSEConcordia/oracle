package me.tomassetti.symbolsolver.resolution;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.NameExpr;
import me.tomassetti.symbolsolver.javaparser.Navigator;
import me.tomassetti.symbolsolver.javaparsermodel.JavaParserFacade;
import me.tomassetti.symbolsolver.model.resolution.TypeParameter;
import me.tomassetti.symbolsolver.model.resolution.TypeSolver;
import me.tomassetti.symbolsolver.model.typesystem.TypeUsage;
import me.tomassetti.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import me.tomassetti.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import me.tomassetti.symbolsolver.resolution.typesolvers.JreTypeSolver;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * We analize a more recent version of JavaParser, after the project moved to Java 8.
 */
public class AnalyseNewJavaParserHelpersTest extends AbstractResolutionTest {

    private static final File src = adaptPath(new File("src/test/resources/javaparser_new_src/javaparser-core"));

    private static TypeSolver TYPESOLVER = typeSolver();

    private static TypeSolver typeSolver() {
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new JreTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(src));
        combinedTypeSolver.add(new JavaParserTypeSolver(adaptPath(new File("src/test/resources/javaparser_new_src/javaparser-generated-sources"))));
        return combinedTypeSolver;
    }

    private CompilationUnit parse(String fileName) throws IOException, ParseException {
        File sourceFile = new File(src.getAbsolutePath() + "/" + fileName + ".java");
        CompilationUnit cu = JavaParser.parse(sourceFile);
        return cu;
    }

//    @Test
//    public void o1TypeIsCorrect() throws IOException, ParseException {
//        CompilationUnit cu = parse("com/github/javaparser/utils/PositionUtils");
//        NameExpr o1 = Navigator.findAllNodesOfGivenClass(cu, NameExpr.class).stream().filter(it -> it.getName()!=null && it.getName().equals("o1")).findFirst().get();
//        System.out.println(JavaParserFacade.get(TYPESOLVER).solve(o1).getCorrespondingDeclaration().getType());
//    }
//
//    @Test
//    public void o2TypeIsCorrect() throws IOException, ParseException {
//        CompilationUnit cu = parse("com/github/javaparser/utils/PositionUtils");
//        NameExpr o2 = Navigator.findAllNodesOfGivenClass(cu, NameExpr.class).stream().filter(it -> it.getName()!=null && it.getName().equals("o2")).findFirst().get();
//        System.out.println(JavaParserFacade.get(TYPESOLVER).solve(o2).getCorrespondingDeclaration().getType());
//    }
//
//    // To calculate the type of o1 and o2 I need to first calculate the type of the lambda
//    @Test
//    public void lambdaTypeIsCorrect() throws IOException, ParseException {
//        CompilationUnit cu = parse("com/github/javaparser/utils/PositionUtils");
//        LambdaExpr lambda = Navigator.findAllNodesOfGivenClass(cu, LambdaExpr.class).stream().filter(it -> it.getRange().begin.line == 50).findFirst().get();
//        System.out.println(JavaParserFacade.get(TYPESOLVER).getType(lambda));
//    }

    @Test
    public void nodesTypeIsCorrect() throws IOException, ParseException {
        CompilationUnit cu = parse("com/github/javaparser/utils/PositionUtils");
        NameExpr nodes = Navigator.findAllNodesOfGivenClass(cu, NameExpr.class).stream().filter(it -> it.getName()!=null && it.getName().equals("nodes")).findFirst().get();
        TypeUsage typeUsage = JavaParserFacade.get(TYPESOLVER).solve(nodes).getCorrespondingDeclaration().getType();
        assertEquals("java.util.List<T>", typeUsage.describe());
        assertEquals(1, typeUsage.asReferenceTypeUsage().parameters().size());
        assertEquals(true, typeUsage.asReferenceTypeUsage().parameters().get(0).isTypeVariable());
        assertEquals("T", typeUsage.asReferenceTypeUsage().parameters().get(0).asTypeParameter().getName());
        assertEquals("com.github.javaparser.utils.PositionUtils.sortByBeginPosition(java.util.List<T>).T", typeUsage.asReferenceTypeUsage().parameters().get(0).asTypeParameter().qualifiedName());
        assertEquals(1, typeUsage.asReferenceTypeUsage().parameters().get(0).asTypeParameter().getBounds(TYPESOLVER).size());
        TypeParameter.Bound bound = typeUsage.asReferenceTypeUsage().parameters().get(0).asTypeParameter().getBounds(TYPESOLVER).get(0);
        assertEquals(true, bound.isExtends());
        assertEquals("com.github.javaparser.ast.Node", bound.getType().describe());
    }

}
