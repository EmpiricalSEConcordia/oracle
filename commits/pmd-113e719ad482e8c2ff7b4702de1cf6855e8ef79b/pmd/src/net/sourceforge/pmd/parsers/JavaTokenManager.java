/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.parsers;

import java.io.Reader;

import net.sourceforge.pmd.ast.JavaCharStream;
import net.sourceforge.pmd.ast.JavaParserTokenManager;

/**
 * Java Token Manager implementation.
 */
public class JavaTokenManager implements TokenManager {
    private final JavaParserTokenManager tokenManager;

    public JavaTokenManager(Reader source) {
	tokenManager = new JavaParserTokenManager(new JavaCharStream(source));
    }

    public Object getNextToken() {
	return tokenManager.getNextToken();
    }
}
