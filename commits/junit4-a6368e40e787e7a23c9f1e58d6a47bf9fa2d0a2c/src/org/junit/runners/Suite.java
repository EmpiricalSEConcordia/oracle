package org.junit.runners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.internal.runners.ParentRunner;
import org.junit.internal.runners.model.InitializationError;
import org.junit.internal.runners.model.TestClass;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;

/**
 * Using <code>Suite</code> as a runner allows you to manually
 * build a suite containing tests from many classes. It is the JUnit 4 equivalent of the JUnit 3.8.x
 * static {@link junit.framework.Test} <code>suite()</code> method. To use it, annotate a class
 * with <code>@RunWith(Suite.class)</code> and <code>@SuiteClasses(TestClass1.class, ...)</code>.
 * When you run this class, it will run all the tests in all the suite classes.
 */
public class Suite extends ParentRunner<Runner> implements Filterable, Sortable {
	/**
	 * The <code>SuiteClasses</code> annotation specifies the classes to be run when a class
	 * annotated with <code>@RunWith(Suite.class)</code> is run.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface SuiteClasses {
		public Class<?>[] value();
	}

	/**
	 * Internal use only.
	 */
	public Suite(Class<?> klass) throws InitializationError {
		this(klass, getAnnotatedClasses(klass));
	}

	// This won't work correctly in the face of concurrency. For that we need to
	// add parameters to getRunner(), which would be much more complicated.
	private static Set<Class<?>> parents = new HashSet<Class<?>>();
	private List<Runner> fRunners = new ArrayList<Runner>();
	
	protected Suite(Class<?> klass, Class<?>[] annotatedClasses) throws InitializationError {
		super(klass);
		
		addParent(klass);
		for (Class<?> each : annotatedClasses) {
			Runner childRunner= Request.aClass(each).getRunner();
			if (childRunner != null)
				add(childRunner);
		}
		removeParent(klass);

		fTestClass= new TestClass(klass);
		List<Throwable> errors= new ArrayList<Throwable>();
		fTestClass.validateStaticMethods(errors);
		assertValid(errors);

	}

	private void add(Runner childRunner) {
		fRunners .add(childRunner);
	}

	private Class<?> addParent(Class<?> parent) throws InitializationError {
		if (!parents.add(parent))
			throw new InitializationError(String.format("class '%s' (possibly indirectly) contains itself as a SuiteClass", parent.getName()));
		return parent;
	}
	
	private void removeParent(Class<?> klass) {
		parents.remove(klass);
	}

	private static Class<?>[] getAnnotatedClasses(Class<?> klass) throws InitializationError {
		SuiteClasses annotation= klass.getAnnotation(SuiteClasses.class);
		if (annotation == null)
			throw new InitializationError(String.format("class '%s' must have a SuiteClasses annotation", klass.getName()));
		return annotation.value();
	}
	
	protected void validate(List<Throwable> errors) {
		fTestClass.validateStaticMethods(errors);
		fTestClass.validateInstanceMethods(errors);
	}

	@Override
	public List<Runner> getChildren() {
		return fRunners;
	}

	@Override
	protected void runChild(Runner each, final RunNotifier notifier) {
		each.run(notifier);
	}

	@Override
	protected Description describeChild(Runner runner) {
		return runner.getDescription();
	}

	public void filter(Filter filter) throws NoTestsRemainException {
		for (Iterator<Runner> iter= fRunners.iterator(); iter.hasNext();) {
			Runner runner= iter.next();
			
			// if filter(parent) == false, tree is pruned			
			if (filter.shouldRun(describeChild(runner)))
				filter.apply(runner);
			else
				iter.remove();
		}
	}

	public void sort(final Sorter sorter) {
		Collections.sort(fRunners, new Comparator<Runner>() {
			public int compare(Runner o1, Runner o2) {
				return sorter.compare(describeChild(o1), describeChild(o2));
			}
		});
		for (Runner each : fRunners)
			sorter.apply(each);
	}
}
