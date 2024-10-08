package test.net.sourceforge.pmd.symboltable;

import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.symboltable.VariableNameDeclaration;
import net.sourceforge.pmd.symboltable.NameOccurrence;

import java.util.List;
import java.util.Map;

public class MethodScopeTest extends STBBaseTst {

    public void testMethodParameterOccurrenceRecorded() {
        parseCode(TEST1);
        Map m = ((ASTMethodDeclaration)(acu.findChildrenOfType(ASTMethodDeclaration.class)).get(0)).getScope().getVariableDeclarations();
        VariableNameDeclaration vnd = (VariableNameDeclaration)m.keySet().iterator().next();
        assertEquals("bar", vnd.getImage());
        List occs = (List)m.get(vnd);
        NameOccurrence occ = (NameOccurrence)occs.get(0);
        assertEquals(3, occ.getBeginLine());
    }

    public static final String TEST1 =
    "public class Foo {" + PMD.EOL +
    " void foo(int bar) {" + PMD.EOL +
    "  bar = 2;" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

}
