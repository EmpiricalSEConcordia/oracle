/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.parsers;

import java.io.Reader;

import net.sourceforge.pmd.jsp.ast.JspCharStream;
import net.sourceforge.pmd.jsp.ast.JspParserTokenManager;

/**
 * JSP Token Manager implementation.
 */
public class JspTokenManager implements TokenManager {
    private final JspParserTokenManager tokenManager;

    public JspTokenManager(Reader source) {
	tokenManager = new JspParserTokenManager(new JspCharStream(source));
    }

    public Object getNextToken() {
	return tokenManager.getNextToken();
    }
}
