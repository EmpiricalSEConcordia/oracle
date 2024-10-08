/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.core.type.classreading;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.Type;
import org.springframework.asm.commons.EmptyVisitor;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * ASM visitor which looks for the annotations defined on a class or method.
 *
 * @author Juergen Hoeller
 * @since 3.0
 */
final class AnnotationAttributesReadingVisitor implements AnnotationVisitor {

	private final String annotationType;

	private final Map<String, Map<String, Object>> annotationMap;

	private final Map<String, Set<String>> metaAnnotationMap;

	private final ClassLoader classLoader;

	private final Map<String, Object> attributes =  new LinkedHashMap<String, Object>();


	public AnnotationAttributesReadingVisitor(
			String annotationType, Map<String, Map<String, Object>> attributesMap,
			Map<String, Set<String>> metaAnnotationMap, ClassLoader classLoader) {

		this.annotationType = annotationType;
		this.annotationMap = attributesMap;
		this.metaAnnotationMap = metaAnnotationMap;
		this.classLoader = classLoader;
	}


	public void visit(String name, Object value) {
		Object valueToUse = value;
		if (valueToUse instanceof Type) {
			valueToUse = ((Type) value).getClassName();
		}
		this.attributes.put(name, valueToUse);
	}

	public void visitEnum(String name, String desc, String value) {
		Object valueToUse = value;
		try {
			Class<?> enumType = this.classLoader.loadClass(Type.getType(desc).getClassName());
			Field enumConstant = ReflectionUtils.findField(enumType, value);
			if (enumConstant != null) {
				valueToUse = enumConstant.get(null);
			}
		}
		catch (Exception ex) {
			// Class not found - can't resolve class reference in annotation attribute.
		}
		this.attributes.put(name, valueToUse);
	}

	public AnnotationVisitor visitAnnotation(String name, String desc) {
		return new EmptyVisitor();
	}

	public AnnotationVisitor visitArray(final String attrName) {
		return new AnnotationVisitor() {
			public void visit(String name, Object value) {
				Object newValue = value;
				if (newValue instanceof Type) {
					newValue = ((Type) value).getClassName();
				}
				Object existingValue = attributes.get(attrName);
				if (existingValue != null) {
					newValue = ObjectUtils.addObjectToArray((Object[]) existingValue, newValue);
				}
				else {
					Object[] newArray = (Object[]) Array.newInstance(newValue.getClass(), 1);
					newArray[0] = newValue;
					newValue = newArray;
				}
				attributes.put(attrName, newValue);
			}
			public void visitEnum(String name, String desc, String value) {
			}
			public AnnotationVisitor visitAnnotation(String name, String desc) {
				return new EmptyVisitor();
			}
			public AnnotationVisitor visitArray(String name) {
				return new EmptyVisitor();
			}
			public void visitEnd() {
			}
		};
	}

	public void visitEnd() {
		try {
			Class<?> annotationClass = this.classLoader.loadClass(this.annotationType);
			// Check declared default values of attributes in the annotation type.
			Method[] annotationAttributes = annotationClass.getMethods();
			for (Method annotationAttribute : annotationAttributes) {
				String attributeName = annotationAttribute.getName();
				Object defaultValue = annotationAttribute.getDefaultValue();
				if (defaultValue != null && !this.attributes.containsKey(attributeName)) {
					this.attributes.put(attributeName, defaultValue);
				}
			}
			// Register annotations that the annotation type is annotated with.
			if (this.metaAnnotationMap != null) {
				Annotation[] metaAnnotations = annotationClass.getAnnotations();
				Set<String> metaAnnotationTypeNames = new HashSet<String>();
				for (Annotation metaAnnotation : metaAnnotations) {
					metaAnnotationTypeNames.add(metaAnnotation.annotationType().getName());
				}
				this.metaAnnotationMap.put(this.annotationType, metaAnnotationTypeNames);
			}
		}
		catch (ClassNotFoundException ex) {
			// Class not found - can't determine meta-annotations.
		}
		this.annotationMap.put(this.annotationType, this.attributes);
	}

}
