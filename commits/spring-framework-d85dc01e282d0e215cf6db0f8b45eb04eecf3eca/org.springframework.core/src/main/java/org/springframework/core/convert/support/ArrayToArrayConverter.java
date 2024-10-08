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

package org.springframework.core.convert.support;

import static org.springframework.core.convert.support.ConversionUtils.asList;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;

/**
 * Converts from a source array to a target array type.
 *
 * @author Keith Donald
 * @since 3.0
 */
final class ArrayToArrayConverter implements GenericConverter {

	private final GenericConverter helperConverter;

	public ArrayToArrayConverter(GenericConversionService conversionService) {
		this.helperConverter = new CollectionToArrayConverter(conversionService);
	}
	
	public Class<?>[][] getConvertibleTypes() {
		return new Class<?>[][] { { Object[].class, Object[].class } };
	}

	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {		
		return this.helperConverter.convert(asList(source), sourceType, targetType);
	}

}
