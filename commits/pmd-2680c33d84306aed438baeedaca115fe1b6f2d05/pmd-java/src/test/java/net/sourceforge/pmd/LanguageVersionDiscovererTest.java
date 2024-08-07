
package net.sourceforge.pmd;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.LanguageVersionDiscoverer;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;

import junit.framework.JUnit4TestAdapter;

public class LanguageVersionDiscovererTest {

    /**
     * Test on Java file with default options.
     */
    @Test
    public void testJavaFileUsingDefaults() {
        LanguageVersionDiscoverer discoverer = new LanguageVersionDiscoverer();
        File javaFile = new File("/path/to/MyClass.java");

        LanguageVersion languageVersion = discoverer.getDefaultLanguageVersionForFile(javaFile);
        assertEquals("LanguageVersion must be Java 1.8 !",
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.8"), languageVersion);
    }

    /**
     * Test on Java file with Java version set to 1.4.
     */
    @Test
    public void testJavaFileUsing14() {
        LanguageVersionDiscoverer discoverer = new LanguageVersionDiscoverer();
        discoverer.setDefaultLanguageVersion(LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.4"));
        File javaFile = new File("/path/to/MyClass.java");

        LanguageVersion languageVersion = discoverer.getDefaultLanguageVersionForFile(javaFile);
        assertEquals("LanguageVersion must be Java 1.4!",
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.4"), languageVersion);
    }

    @Test
    public void testLanguageVersionDiscoverer() {
        PMDConfiguration configuration = new PMDConfiguration();
        LanguageVersionDiscoverer languageVersionDiscoverer = configuration.getLanguageVersionDiscoverer();
        assertEquals("Default Java version", LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.8"),
                languageVersionDiscoverer
                        .getDefaultLanguageVersion(LanguageRegistry.getLanguage(JavaLanguageModule.NAME)));
        configuration
                .setDefaultLanguageVersion(LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"));
        assertEquals("Modified Java version", LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("1.5"),
                languageVersionDiscoverer
                        .getDefaultLanguageVersion(LanguageRegistry.getLanguage(JavaLanguageModule.NAME)));
    }

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(LanguageVersionDiscovererTest.class);
    }
}
