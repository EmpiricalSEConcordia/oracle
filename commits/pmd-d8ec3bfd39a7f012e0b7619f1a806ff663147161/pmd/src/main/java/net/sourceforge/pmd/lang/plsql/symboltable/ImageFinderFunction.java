/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.lang.plsql.symboltable;

import net.sourceforge.pmd.util.UnaryFunction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImageFinderFunction implements UnaryFunction<NameDeclaration> {

    private Set<String> images = new HashSet<String>();
    private NameDeclaration decl;

    public ImageFinderFunction(String img) {
        images.add(img);
    }

    public ImageFinderFunction(List<String> imageList) {
        images.addAll(imageList);
    }

    public void applyTo(NameDeclaration nameDeclaration) {
        if (images.contains(nameDeclaration.getImage())) {
            decl = nameDeclaration;
        }
    }

    public NameDeclaration getDecl() {
        return this.decl;
    }
}
