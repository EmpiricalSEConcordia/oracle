package net.sourceforge.pmd.lang.java.symboltable;

import java.io.StringReader;

import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.LanguageVersionHandler;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;

public abstract class STBBaseTst {

    protected ASTCompilationUnit acu;
    protected SymbolFacade stb;

    protected void parseCode(String code) {
        parseCode(code, LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getDefaultVersion());
    }

    protected void parseCode15(String code) {
        parseCode(code, LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
    }

    protected void parseCode(String code, LanguageVersion languageVersion) {
        LanguageVersionHandler languageVersionHandler = languageVersion.getLanguageVersionHandler();
        acu = (ASTCompilationUnit) languageVersionHandler.getParser(languageVersionHandler.getDefaultParserOptions())
                .parse(null, new StringReader(code));
        stb = new SymbolFacade();
        stb.initializeWith(acu);
    }
}
