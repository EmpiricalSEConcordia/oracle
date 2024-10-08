/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.analyzer.spi;

import java.util.Collection;
import java.util.Map;

import org.hibernate.search.annotations.AnalyzerDef;

/**
 * A strategy for applying analyzers.
 *
 * @author Gunnar Morling
 * @author Yoann Rodiere
 * @hsearch.experimental This type is under active development as part of the Elasticsearch integration. You
 * should be prepared for incompatible changes in future releases.
 */
public interface AnalyzerStrategy {

	/**
	 * @return a reference to the default analyzer, the one to be used when no specific configuration is set
	 * on a given field.
	 */
	AnalyzerReference createDefaultAnalyzerReference();

	/**
	 * @return a reference to an analyzer that applies no operation whatsoever to the flux.
	 * This is useful for queries operating on non-tokenized fields.
	 */
	AnalyzerReference createPassThroughAnalyzerReference();

	/**
	 * @param name The name of the analyzer to be referenced.
	 * @return a reference that will be {@link #initializeAnalyzerReferences(Collection, Map) initialized later}.
	 */
	AnalyzerReference createNamedAnalyzerReference(String name);

	/**
	 * @param analyzerClass The analyzer class the reference should reference.
	 * @return a reference to an instance of the given analyzer class that will be
	 * {@link #initializeAnalyzerReferences(Collection, Map) initialized later}.
	 */
	AnalyzerReference createLuceneClassAnalyzerReference(Class<?> analyzerClass);

	/**
	 * Initializes references created by this strategy, i.e. make them point to the actual analyzer definition.
	 * @param references The references to initialize, gathered through calls to methods of this strategy.
	 * @param mappingAnalyzerDefinitions The analyzer definitions gathered through the Hibernate Search mappings.
	 */
	void initializeAnalyzerReferences(Collection<AnalyzerReference> references, Map<String, AnalyzerDef> mappingAnalyzerDefinitions);

	/**
	 * Creates a {@link ScopedAnalyzerReference} builder.
	 * @param initialGlobalAnalyzerReference The global analyzer to set initially on the builder.
	 * @return A {@link ScopedAnalyzerReference} builder. The returned reference will be
	 * {@link #initializeAnalyzerReferences(Collection, Map) initialized later}.
	 */
	ScopedAnalyzerReference.Builder buildScopedAnalyzerReference(AnalyzerReference initialGlobalAnalyzerReference);

}
