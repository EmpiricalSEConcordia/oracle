/*
 * Copyright 2002-2008 the original author or authors.
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

package org.springframework.validation;

import java.beans.PropertyEditor;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.util.StringUtils;

/**
 * Abstract implementation of the {@link BindingResult} interface and
 * its super-interface {@link Errors}. Encapsulates common management of
 * {@link ObjectError ObjectErrors} and {@link FieldError FieldErrors}.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 2.0
 * @see Errors
 */
public abstract class AbstractBindingResult extends AbstractErrors implements BindingResult, Serializable {

	private final String objectName;

	private MessageCodesResolver messageCodesResolver = new DefaultMessageCodesResolver();

	private final List errors = new LinkedList();

	private final Set suppressedFields = new HashSet();


	/**
	 * Create a new AbstractBindingResult instance.
	 * @param objectName the name of the target object
	 * @see DefaultMessageCodesResolver
	 */
	protected AbstractBindingResult(String objectName) {
		this.objectName = objectName;
	}

	/**
	 * Set the strategy to use for resolving errors into message codes.
	 * Default is DefaultMessageCodesResolver.
	 * @see DefaultMessageCodesResolver
	 */
	public void setMessageCodesResolver(MessageCodesResolver messageCodesResolver) {
		this.messageCodesResolver = messageCodesResolver;
	}

	/**
	 * Return the strategy to use for resolving errors into message codes.
	 */
	public MessageCodesResolver getMessageCodesResolver() {
		return this.messageCodesResolver;
	}


	//---------------------------------------------------------------------
	// Implementation of the Errors interface
	//---------------------------------------------------------------------

	public String getObjectName() {
		return this.objectName;
	}


	public void reject(String errorCode, Object[] errorArgs, String defaultMessage) {
		addError(new ObjectError(getObjectName(), resolveMessageCodes(errorCode), errorArgs, defaultMessage));
	}

	public void rejectValue(String field, String errorCode, Object[] errorArgs, String defaultMessage) {
		if ("".equals(getNestedPath()) && !StringUtils.hasLength(field)) {
			// We're at the top of the nested object hierarchy,
			// so the present level is not a field but rather the top object.
			// The best we can do is register a global error here...
			reject(errorCode, errorArgs, defaultMessage);
			return;
		}
		String fixedField = fixedField(field);
		Object newVal = getActualFieldValue(fixedField);
		FieldError fe = new FieldError(
				getObjectName(), fixedField, newVal, false,
				resolveMessageCodes(errorCode, field), errorArgs, defaultMessage);
		addError(fe);
	}

	public void addError(ObjectError error) {
		this.errors.add(error);
	}

	public void addAllErrors(Errors errors) {
		if (!errors.getObjectName().equals(getObjectName())) {
			throw new IllegalArgumentException("Errors object needs to have same object name");
		}
		this.errors.addAll(errors.getAllErrors());
	}

	/**
	 * Resolve the given error code into message codes.
	 * Calls the MessageCodesResolver with appropriate parameters.
	 * @param errorCode the error code to resolve into message codes
	 * @return the resolved message codes
	 * @see #setMessageCodesResolver
	 */
	public String[] resolveMessageCodes(String errorCode) {
		return getMessageCodesResolver().resolveMessageCodes(errorCode, getObjectName());
	}

	public String[] resolveMessageCodes(String errorCode, String field) {
		String fixedField = fixedField(field);
		Class fieldType = getFieldType(fixedField);
		return getMessageCodesResolver().resolveMessageCodes(errorCode, getObjectName(), fixedField, fieldType);
	}


	@Override
	public boolean hasErrors() {
		return !this.errors.isEmpty();
	}

	@Override
	public int getErrorCount() {
		return this.errors.size();
	}

	@Override
	public List getAllErrors() {
		return Collections.unmodifiableList(this.errors);
	}

	public List getGlobalErrors() {
		List result = new LinkedList();
		for (Iterator it = this.errors.iterator(); it.hasNext();) {
			Object error = it.next();
			if (!(error instanceof FieldError)) {
				result.add(error);
			}
		}
		return Collections.unmodifiableList(result);
	}

	@Override
	public ObjectError getGlobalError() {
		for (Iterator it = this.errors.iterator(); it.hasNext();) {
			ObjectError objectError = (ObjectError) it.next();
			if (!(objectError instanceof FieldError)) {
				return objectError;
			}
		}
		return null;
	}

	public List getFieldErrors() {
		List result = new LinkedList();
		for (Iterator it = this.errors.iterator(); it.hasNext();) {
			Object error = it.next();
			if (error instanceof FieldError) {
				result.add(error);
			}
		}
		return Collections.unmodifiableList(result);
	}

	@Override
	public FieldError getFieldError() {
		for (Iterator it = this.errors.iterator(); it.hasNext();) {
			Object error = it.next();
			if (error instanceof FieldError) {
				return (FieldError) error;
			}
		}
		return null;
	}

	@Override
	public List getFieldErrors(String field) {
		List result = new LinkedList();
		String fixedField = fixedField(field);
		for (Iterator it = this.errors.iterator(); it.hasNext();) {
			Object error = it.next();
			if (error instanceof FieldError && isMatchingFieldError(fixedField, (FieldError) error)) {
				result.add(error);
			}
		}
		return Collections.unmodifiableList(result);
	}

	@Override
	public FieldError getFieldError(String field) {
		String fixedField = fixedField(field);
		for (Iterator it = this.errors.iterator(); it.hasNext();) {
			Object error = it.next();
			if (error instanceof FieldError) {
				FieldError fe = (FieldError) error;
				if (isMatchingFieldError(fixedField, fe)) {
					return fe;
				}
			}
		}
		return null;
	}

	public Object getFieldValue(String field) {
		FieldError fe = getFieldError(field);
		// Use rejected value in case of error, current bean property value else.
		Object value = null;
		if (fe != null) {
			value = fe.getRejectedValue();
		}
		else {
			value = getActualFieldValue(fixedField(field));
		}
		// Apply formatting, but not on binding failures like type mismatches.
		if (fe == null || !fe.isBindingFailure()) {
			value = formatFieldValue(field, value);
		}
		return value;
	}

	/**
	 * This default implementation determines the type based on the actual
	 * field value, if any. Subclasses should override this to determine
	 * the type from a descriptor, even for <code>null</code> values.
	 * @see #getActualFieldValue
	 */
	@Override
	public Class getFieldType(String field) {
		Object value = getActualFieldValue(fixedField(field));
		if (value != null) {
			return value.getClass();
		}
		return null;
	}


	//---------------------------------------------------------------------
	// Implementation of BindingResult interface
	//---------------------------------------------------------------------

	/**
	 * Return a model Map for the obtained state, exposing an Errors
	 * instance as '{@link #MODEL_KEY_PREFIX MODEL_KEY_PREFIX} + objectName'
	 * and the object itself.
	 * <p>Note that the Map is constructed every time you're calling this method.
	 * Adding things to the map and then re-calling this method will not work.
	 * <p>The attributes in the model Map returned by this method are usually
	 * included in the ModelAndView for a form view that uses Spring's bind tag,
	 * which needs access to the Errors instance. Spring's SimpleFormController
	 * will do this for you when rendering its form or success view. When
	 * building the ModelAndView yourself, you need to include the attributes
	 * from the model Map returned by this method yourself.
	 * @see #getObjectName
	 * @see #MODEL_KEY_PREFIX
	 * @see org.springframework.web.servlet.ModelAndView
	 * @see org.springframework.web.servlet.tags.BindTag
	 * @see org.springframework.web.servlet.mvc.SimpleFormController
	 */
	public Map getModel() {
		Map model = new HashMap(2);
		// Errors instance, even if no errors.
		model.put(MODEL_KEY_PREFIX + getObjectName(), this);
		// Mapping from name to target object.
		model.put(getObjectName(), getTarget());
		return model;
	}

	public Object getRawFieldValue(String field) {
		return getActualFieldValue(fixedField(field));
	}

	/**
	 * This implementation delegates to the
	 * {@link #getPropertyEditorRegistry() PropertyEditorRegistry}'s
	 * editor lookup facility, if available.
	 */
	public PropertyEditor findEditor(String field, Class valueType) {
		PropertyEditorRegistry editorRegistry = getPropertyEditorRegistry();
		if (editorRegistry != null) {
			Class valueTypeToUse = valueType;
			if (valueTypeToUse == null) {
				valueTypeToUse = getFieldType(field);
			}
			return editorRegistry.findCustomEditor(valueTypeToUse, fixedField(field));
		}
		else {
			return null;
		}
	}

	/**
	 * This implementation returns <code>null</code>.
	 */
	public PropertyEditorRegistry getPropertyEditorRegistry() {
		return null;
	}

	/**
	 * Mark the specified disallowed field as suppressed.
	 * <p>The data binder invokes this for each field value that was
	 * detected to target a disallowed field.
	 * @see DataBinder#setAllowedFields
	 */
	public void recordSuppressedField(String field) {
		this.suppressedFields.add(field);
	}

	/**
	 * Return the list of fields that were suppressed during the bind process.
	 * <p>Can be used to determine whether any field values were targetting
	 * disallowed fields.
	 * @see DataBinder#setAllowedFields
	 */
	public String[] getSuppressedFields() {
		return StringUtils.toStringArray(this.suppressedFields);
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BindingResult)) {
			return false;
		}
		BindingResult otherResult = (BindingResult) other;
		return (getObjectName().equals(otherResult.getObjectName()) &&
				getTarget().equals(otherResult.getTarget()) &&
				getAllErrors().equals(otherResult.getAllErrors()));
	}

	@Override
	public int hashCode() {
		return getObjectName().hashCode() * 29 + getTarget().hashCode();
	}


	//---------------------------------------------------------------------
	// Template methods to be implemented/overridden by subclasses
	//---------------------------------------------------------------------

	/**
	 * Return the wrapped target object.
	 */
	public abstract Object getTarget();

	/**
	 * Extract the actual field value for the given field.
	 * @param field the field to check
	 * @return the current value of the field
	 */
	protected abstract Object getActualFieldValue(String field);

	/**
	 * Format the given value for the specified field.
	 * <p>The default implementation simply returns the field value as-is.
	 * @param field the field to check
	 * @param value the value of the field (either a rejected value
	 * other than from a binding error, or an actual field value)
	 * @return the formatted value
	 */
	protected Object formatFieldValue(String field, Object value) {
		return value;
	}

}
