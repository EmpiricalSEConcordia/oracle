/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.configurationprocessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.springframework.boot.configurationprocessor.metadata.ConfigurationMetadata;
import org.springframework.boot.configurationprocessor.metadata.JsonMarshaller;

/**
 * A {@code MetadataStore} is responsible for the storage of metadata on the filesystem
 *
 * @author Andy Wilkinson
 * @since 1.2.2
 */
public class MetadataStore {

	static final String METADATA_PATH = "META-INF/spring-configuration-metadata.json";

	private static final String ADDITIONAL_METADATA_PATH = "META-INF/additional-spring-configuration-metadata.json";

	private static final String RESOURCES_FOLDER = "resources";

	private static final String CLASSES_FOLDER = "classes";

	private final ProcessingEnvironment environment;

	public MetadataStore(ProcessingEnvironment environment) {
		this.environment = environment;
	}

	public ConfigurationMetadata readMetadata() {
		try {
			return readMetadata(getMetadataResource().openInputStream());
		}
		catch (IOException ex) {
			return null;
		}
	}

	public void writeMetadata(ConfigurationMetadata metadata) throws IOException {
		if (!metadata.getItems().isEmpty()) {
			OutputStream outputStream = createMetadataResource().openOutputStream();
			try {
				new JsonMarshaller().write(metadata, outputStream);
			}
			finally {
				outputStream.close();
			}
		}
	}

	public ConfigurationMetadata readAdditionalMetadata() throws IOException {
		return readMetadata(getAdditionalMetadataStream());
	}

	private ConfigurationMetadata readMetadata(InputStream in) throws IOException {
		try {
			return new JsonMarshaller().read(in);
		}
		catch (IOException ex) {
			return null;
		}
		finally {
			in.close();
		}
	}

	private FileObject getMetadataResource() throws IOException {
		FileObject resource = this.environment.getFiler()
				.getResource(StandardLocation.CLASS_OUTPUT, "", METADATA_PATH);
		return resource;
	}

	private FileObject createMetadataResource() throws IOException {
		FileObject resource = this.environment.getFiler()
				.createResource(StandardLocation.CLASS_OUTPUT, "", METADATA_PATH);
		return resource;
	}

	private InputStream getAdditionalMetadataStream() throws IOException {
		// Most build systems will have copied the file to the class output location
		FileObject fileObject = this.environment.getFiler()
				.getResource(StandardLocation.CLASS_OUTPUT, "", ADDITIONAL_METADATA_PATH);
		File file = new File(fileObject.toUri());
		if (!file.exists()) {
			// Gradle keeps things separate
			String path = file.getPath();
			int index = path.lastIndexOf(CLASSES_FOLDER);
			if (index >= 0) {
				path = path.substring(0, index) + RESOURCES_FOLDER
						+ path.substring(index + CLASSES_FOLDER.length());
				file = new File(path);
			}
		}
		return (file.exists() ? new FileInputStream(file)
				: fileObject.toUri().toURL().openStream());
	}

}
