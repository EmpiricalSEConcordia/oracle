package me.tomassetti.symbolsolver;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;
import me.tomassetti.symbolsolver.model.TypeSolver;
import me.tomassetti.symbolsolver.model.declarations.TypeDeclaration;
import me.tomassetti.symbolsolver.model.javaparser.UnsolvedSymbolException;
import me.tomassetti.symbolsolver.model.typesolvers.CombinedTypeSolver;
import me.tomassetti.symbolsolver.model.typesolvers.JavaParserTypeSolver;
import me.tomassetti.symbolsolver.model.typesolvers.JreTypeSolver;
import me.tomassetti.symbolsolver.model.usages.TypeUsage;

import java.io.File;
import java.io.IOException;

/**
 * Created by federico on 21/08/15.
 */
public class ProjectResolver {

    private static TypeSolver typeSolver;

    private static int ok = 0;
    private static int ko = 0;
    private static int unsupported = 0;

    private static void solveField(Node node) {
        if (node instanceof Expression) {
            Expression expression = (Expression)node;
            //System.out.println("  Looking into " + node);
            TypeUsage ref =  JavaParserFacade.get(typeSolver).getType(expression);
            //System.out.println("  * solving " + node + " : " + ref);
            //System.out.println("  " + node.getParentNode().getClass().getCanonicalName());
        } else {
            for (Node child : node.getChildrenNodes()){
                solveField(child);
            }
        }
    }

    private static void solveTypeDecl(ClassOrInterfaceDeclaration node) {
        TypeDeclaration typeDeclaration = JavaParserFacade.get(typeSolver).getTypeDeclaration(node);
        if (typeDeclaration.isClass()) {
            System.out.println("\n[ Class "+ typeDeclaration.getQualifiedName() + " ]");
            for (TypeDeclaration sc : typeDeclaration.asClass().getAllSuperClasses(typeSolver)) {
                System.out.println("  superclass: " + sc.getQualifiedName());
            }
            for (TypeDeclaration sc : typeDeclaration.asClass().getAllInterfaces(typeSolver)) {
                System.out.println("  interface: " + sc.getQualifiedName());
            }
        }
    }

    private static void solve(Node node) {
        if (node instanceof ClassOrInterfaceDeclaration) {
            solveTypeDecl((ClassOrInterfaceDeclaration)node);
        }  else if (node instanceof FieldDeclaration) {
            solveField(node);
            return;
        } else if (node instanceof Expression) {
            if ((node.getParentNode() instanceof ImportDeclaration) || (node.getParentNode() instanceof Expression)
                    || (node.getParentNode() instanceof MethodDeclaration)
                    || (node.getParentNode() instanceof PackageDeclaration)) {
                // skip
            } else if ((node.getParentNode() instanceof Statement) || (node.getParentNode() instanceof VariableDeclarator)){
                //System.out.println(node + " GOOD from " + node.getParentNode().getClass().getCanonicalName());
                try {
                    TypeUsage ref = JavaParserFacade.get(typeSolver).getType(node);
                    System.out.println("  Line " + node.getBeginLine() + ") " + node + " ==> " + ref.prettyPrint());
                    ok++;
                    System.out.println("OK "+ok+" KO "+ko+" unsupported "+unsupported);
                } catch (UnsupportedOperationException upe){
                    unsupported++;
                } catch (RuntimeException re){
                    ko++;
                }
            } else {
                //System.out.println(node + " ? from " + node.getParentNode().getClass().getCanonicalName());
            }

        }
        for (Node child : node.getChildrenNodes()){
            solve(child);
        }
    }

    private static void solve(File file) throws IOException, ParseException {
        if (file.isDirectory()) {
            for (File f : file.listFiles()){
                solve(f);
            }
        } else {
            if (file.getName().endsWith(".java")) {
                System.out.println("- parsing " + file.getAbsolutePath());
                CompilationUnit cu = JavaParser.parse(file);
                solve(cu);
            }
        }
    }

    public static void main(String[] args) throws IOException, ParseException {
        File src = new File("/home/federico/repos/javaparser/javaparser-core/src/main/java");
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new JreTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(src));
        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/home/federico/repos/javaparser/javaparser-core/target/generated-sources/javacc")));
        typeSolver = combinedTypeSolver;
        solve(src);
        System.out.println("OK "+ ok);
        System.out.println("KO "+ ko);
        System.out.println("UNSUPPORTED "+ unsupported);
    }

}
