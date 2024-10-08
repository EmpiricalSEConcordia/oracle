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

package org.springframework.beans.factory.config;

import java.beans.PropertyEditor;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

/**
 * {@link BeanFactoryPostProcessor} implementation that allows for convenient
 * registration of custom {@link PropertyEditor property editors}.
 *
 * <p>As of Spring 2.0, the recommended usage is to use custom
 * {@link PropertyEditorRegistrar} implementations that in turn register
 * any desired editors on a given
 * {@link org.springframework.beans.PropertyEditorRegistry registry}.
 * Each PropertyEditorRegistrar can register any number of custom editors.
 *
 * <pre class="code">
 * &lt;bean id="customEditorConfigurer" class="org.springframework.beans.factory.config.CustomEditorConfigurer"&gt;
 *   &lt;property name="propertyEditorRegistrars"&gt;
 *     &lt;list&gt;
 *       &lt;bean class="mypackage.MyCustomDateEditorRegistrar"/&gt;
 *       &lt;bean class="mypackage.MyObjectEditorRegistrar"/&gt;
 *     &lt;/list&gt;
 *   &lt;/property&gt;
 * &lt;/bean&gt;</pre>
 *
 * <p>Alternative configuration example with custom editor classes:
 *
 * <pre class="code">
 * &lt;bean id="customEditorConfigurer" class="org.springframework.beans.factory.config.CustomEditorConfigurer"&gt;
 *   &lt;property name="customEditors"&gt;
 *     &lt;map&gt;
 *       &lt;entry key="java.util.Date" value="mypackage.MyCustomDateEditor"/&gt;
 *       &lt;entry key="mypackage.MyObject" value="mypackage.MyObjectEditor"/&gt;
 *     &lt;/map&gt;
 *   &lt;/property&gt;
 * &lt;/bean&gt;</pre>
 *
 * <p>Also supports "java.lang.String[]"-style array class names and primitive
 * class names (e.g. "boolean"). Delegates to {@link ClassUtils} for actual
 * class name resolution.
 *
 * <p><b>NOTE:</b> Custom property editors registered with this configurer do
 * <i>not</i> apply to data binding. Custom editors for data binding need to
 * be registered on the {@link org.springframework.validation.DataBinder}:
 * Use a common base class or delegate to common PropertyEditorRegistrar
 * implementations to reuse editor registration there.
 *
 * @author Juergen Hoeller
 * @since 27.02.2004
 * @see java.beans.PropertyEditor
 * @see org.springframework.beans.PropertyEditorRegistrar
 * @see ConfigurableBeanFactory#addPropertyEditorRegistrar
 * @see ConfigurableBeanFactory#registerCustomEditor
 * @see org.springframework.validation.DataBinder#registerCustomEditor
 * @see org.springframework.web.servlet.mvc.BaseCommandController#setPropertyEditorRegistrars
 * @see org.springframework.web.servlet.mvc.BaseCommandController#initBinder
 */
public class CustomEditorConfigurer implements BeanFactoryPostProcessor, BeanClassLoaderAware, Ordered {

	protected final Log logger = LogFactory.getLog(getClass());

	private int order = Ordered.LOWEST_PRECEDENCE;  // default: same as non-Ordered

	private PropertyEditorRegistrar[] propertyEditorRegistrars;

	private Map<String, String> customEditors;

	private boolean ignoreUnresolvableEditors = false;

	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();


	public void setOrder(int order) {
	  this.order = order;
	}

	public int getOrder() {
	  return this.order;
	}

	/**
	 * Specify the {@link PropertyEditorRegistrar PropertyEditorRegistrars}
	 * to apply to beans defined within the current application context.
	 * <p>This allows for sharing <code>PropertyEditorRegistrars</code> with
	 * {@link org.springframework.validation.DataBinder DataBinders}, etc.
	 * Furthermore, it avoids the need for synchronization on custom editors:
	 * A <code>PropertyEditorRegistrar</code> will always create fresh editor
	 * instances for each bean creation attempt.
	 * @see ConfigurableListableBeanFactory#addPropertyEditorRegistrar
	 */
	public void setPropertyEditorRegistrars(PropertyEditorRegistrar[] propertyEditorRegistrars) {
		this.propertyEditorRegistrars = propertyEditorRegistrars;
	}

	/**
	 * Specify the custom editors to register via a {@link Map}, using the
	 * class name of the required type as the key and the class name of the
	 * associated {@link PropertyEditor} as value.
	 * @param customEditors said <code>Map</code> of editors (can be <code>null</code>)
	 * @see ConfigurableListableBeanFactory#registerCustomEditor
	 */
	public void setCustomEditors(Map<String, String> customEditors) {
		this.customEditors = customEditors;
	}

	/**
	 * Set whether unresolvable editors should simply be skipped.
	 * Default is to raise an exception in such a case.
	 * <p>This typically applies to either the editor class or the required type
	 * class not being found in the classpath. If you expect this to happen in
	 * some deployments and prefer to simply ignore the affected editors,
	 * then switch this flag to "true".
	 */
	public void setIgnoreUnresolvableEditors(boolean ignoreUnresolvableEditors) {
		this.ignoreUnresolvableEditors = ignoreUnresolvableEditors;
	}

	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}


	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		if (this.propertyEditorRegistrars != null) {
			for (PropertyEditorRegistrar propertyEditorRegistrar : this.propertyEditorRegistrars) {
				beanFactory.addPropertyEditorRegistrar(propertyEditorRegistrar);
			}
		}

		if (this.customEditors != null) {
			for (Map.Entry<String, String> entry : this.customEditors.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				Class requiredType = null;

				try {
					requiredType = ClassUtils.forName(key, this.beanClassLoader);
					Class editorClass = ClassUtils.forName(value, this.beanClassLoader);
					beanFactory.registerCustomEditor(requiredType, editorClass);
				}
				catch (ClassNotFoundException ex) {
					if (this.ignoreUnresolvableEditors) {
						logger.info("Skipping editor [" + value + "] for required type [" + key + "]: " +
								(requiredType != null ? "editor" : "required type") + " class not found.");
					}
					else {
						throw new FatalBeanException(
								(requiredType != null ? "Editor" : "Required type") + " class not found", ex);
					}
				}
			}
		}
	}

}
