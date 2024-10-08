package com.github.javaparser;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.javaparser.utils.Utils.assertNotNull;

/**
 * Factory for providers of source code for JavaParser. Providers that have no parameter for encoding but need it will
 * use UTF-8.
 */
public final class Providers {
    public static final Charset UTF8 = Charset.forName("utf-8");

    private Providers() {
    }

    public static Provider provider(Reader reader) {
        return new StreamProvider(assertNotNull(reader));
    }

    public static Provider provider(InputStream input, Charset encoding) {
        assertNotNull(input);
        assertNotNull(encoding);
        try {
            return new StreamProvider(input, encoding.name());
        } catch (IOException e) {
            // The only one that is thrown is UnsupportedCharacterEncodingException,
            // and that's a fundamental problem, so runtime exception.
            throw new RuntimeException(e);
        }
    }

    public static Provider provider(InputStream input) {
        return provider(input, UTF8);
    }

    public static Provider provider(File file, Charset encoding) throws FileNotFoundException {
        return provider(new FileInputStream(assertNotNull(file)), assertNotNull(encoding));
    }

    public static Provider provider(File file) throws FileNotFoundException {
        return provider(assertNotNull(file), UTF8);
    }

    public static Provider provider(Path path, Charset encoding) throws IOException {
        return provider(Files.newInputStream(assertNotNull(path)), assertNotNull(encoding));
    }

    public static Provider provider(Path path) throws IOException {
        return provider(assertNotNull(path), UTF8);
    }

    public static Provider provider(String source) {
        return new StringProvider(assertNotNull(source));
    }


    /**
     * Provide a Provider from the resource found in class loader with the provided encoding.<br/> As resource is
     * accessed through a class loader, a leading "/" is not allowed in pathToResource
     */
    public static Provider resourceProvider(ClassLoader classLoader, String pathToResource, Charset encoding) throws IOException {
        InputStream resourceAsStream = classLoader.getResourceAsStream(pathToResource);
        if (resourceAsStream == null) {
            throw new IOException("Cannot find " + pathToResource);
        }
        return provider(resourceAsStream, encoding);
    }

    /**
     * Provide a Provider from the resource found in the current class loader with the provided encoding.<br/> As
     * resource is accessed through a class loader, a leading "/" is not allowed in pathToResource
     */
    public static Provider resourceProvider(String pathToResource, Charset encoding) throws IOException {
        ClassLoader classLoader = Provider.class.getClassLoader();
        return resourceProvider(classLoader, pathToResource, encoding);
    }

    /**
     * Provide a Provider from the resource found in the current class loader with UTF-8 encoding.<br/> As resource is
     * accessed through a class loader, a leading "/" is not allowed in pathToResource
     */
    public static Provider resourceProvider(String pathToResource) throws IOException {
        return resourceProvider(pathToResource, UTF8);
    }

}
