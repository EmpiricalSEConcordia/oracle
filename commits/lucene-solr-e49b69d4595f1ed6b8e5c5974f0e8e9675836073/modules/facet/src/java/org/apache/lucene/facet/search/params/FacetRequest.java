package org.apache.lucene.facet.search.params;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;

import org.apache.lucene.facet.index.params.CategoryListParams;
import org.apache.lucene.facet.search.CategoryListIterator;
import org.apache.lucene.facet.search.FacetArrays;
import org.apache.lucene.facet.search.FacetResultsHandler;
import org.apache.lucene.facet.search.TopKFacetResultsHandler;
import org.apache.lucene.facet.search.TopKInEachNodeHandler;
import org.apache.lucene.facet.search.aggregator.Aggregator;
import org.apache.lucene.facet.search.cache.CategoryListData;
import org.apache.lucene.facet.search.cache.CategoryListCache;
import org.apache.lucene.facet.taxonomy.CategoryPath;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Request to accumulate facet information for a specified facet and possibly 
 * also some of its descendants, upto a specified depth.
 * <p>
 * The facet request additionally defines what information should 
 * be computed within the facet results, if and how should results
 * be ordered, etc.
 * <P>
 * An example facet request is to look at all sub-categories of "Author", and
 * return the 10 with the highest counts (sorted by decreasing count). 
 * 
 * @lucene.experimental
 */
public abstract class FacetRequest implements Cloneable {

  /**
   * Default depth for facets accumulation.
   * @see #getDepth()
   */
  public static final int DEFAULT_DEPTH = 1;

  /**
   * Default sort mode.
   * @see #getSortBy()
   */
  public static final SortBy DEFAULT_SORT_BY = SortBy.VALUE;

  /**
   * Default result mode
   * @see #getResultMode()
   */
  public static final ResultMode DEFAULT_RESULT_MODE = ResultMode.GLOBAL_FLAT;

  private final CategoryPath categoryPath;
  private final int numResults;
  private int numLabel;
  private int depth;
  private SortOrder sortOrder;
  private SortBy sortBy;

  /**
   * Computed at construction, this hashCode is based on two final members
   * {@link CategoryPath} and <code>numResults</code>
   */
  private final int hashCode;
  
  private ResultMode resultMode = DEFAULT_RESULT_MODE;

  /**
   * Initialize the request with a given path, and a requested number of facets
   * results. By default, all returned results would be labeled - to alter this
   * default see {@link #setNumLabel(int)}.
   * <p>
   * <b>NOTE:</b> if <code>numResults</code> is given as
   * <code>Integer.MAX_VALUE</code> than all the facet results would be
   * returned, without any limit.
   * <p>
   * <b>NOTE:</b> it is assumed that the given {@link CategoryPath} is not
   * modified after construction of this object. Otherwise, some things may not
   * function properly, e.g. {@link #hashCode()}.
   * 
   * @throws IllegalArgumentException if numResults is &le; 0
   */
  public FacetRequest(CategoryPath path, int numResults) {
    if (numResults <= 0) {
      throw new IllegalArgumentException("num results must be a positive (>0) number: " + numResults);
    }
    if (path == null) {
      throw new IllegalArgumentException("category path cannot be null!");
    }
    categoryPath = path;
    this.numResults = numResults;
    numLabel = numResults;
    depth = DEFAULT_DEPTH;
    sortBy = DEFAULT_SORT_BY;
    sortOrder = SortOrder.DESCENDING;
    
    hashCode = categoryPath.hashCode() ^ this.numResults;
  }

  @Override
  public FacetRequest clone() throws CloneNotSupportedException {
    // Overridden to make it public
    return (FacetRequest)super.clone();
  }
  
  public void setNumLabel(int numLabel) {
    this.numLabel = numLabel;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }

  public void setSortOrder(SortOrder sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void setSortBy(SortBy sortBy) {
    this.sortBy = sortBy;
  }

  /**
   * The root category of this facet request. The categories that are returned
   * as a result of this request will all be descendants of this root.
   * <p>
   * <b>NOTE:</b> you should not modify the returned {@link CategoryPath}, or
   * otherwise some methonds may not work properly, e.g. {@link #hashCode()}.
   */
  public final CategoryPath getCategoryPath() {
    return categoryPath;
  }

  /**
   * How deeply to look under the given category. If the depth is 0,
   * only the category itself is counted. If the depth is 1, its immediate
   * children are also counted, and so on. If the depth is Integer.MAX_VALUE,
   * all the category's descendants are counted.<br>
   * TODO (Facet): add AUTO_EXPAND option  
   */
  public final int getDepth() {
    return depth;
  }

  /**
   * If getNumLabel()<getNumResults(), only the first getNumLabel() results
   * will have their category paths calculated, and the rest will only be
   * available as ordinals (category numbers) and will have null paths.
   * <P>
   * If Integer.MAX_VALUE is specified, all 
   * results are labled.
   * <P>
   * The purpose of this parameter is to avoid having to run the whole
   * faceted search again when the user asks for more values for the facet;
   * The application can ask (getNumResults()) for more values than it needs
   * to show, but keep getNumLabel() only the number it wants to immediately
   * show. The slow-down caused by finding more values is negligible, because
   * the slowest part - finding the categories' paths, is avoided.
   * <p>
   * Depending on the {@link #getResultMode() LimitsMode},
   * this limit is applied globally or per results node.
   * In the global mode, if this limit is 3, 
   * only 3 top results would be labeled.
   * In the per-node mode, if this limit is 3,
   * 3 top children of {@link #getCategoryPath() the target category} would be labeled,
   * as well as 3 top children of each of them, and so forth, until the depth defined 
   * by {@link #getDepth()}.
   * @see #getResultMode()
   */
  public final int getNumLabel() {
    return numLabel;
  }

  /**
   * The number of sub-categories to return (at most).
   * If the sub-categories are returned.
   * <p>
   * If Integer.MAX_VALUE is specified, all 
   * sub-categories are returned.
   * <p>
   * Depending on the {@link #getResultMode() LimitsMode},
   * this limit is applied globally or per results node.
   * In the global mode, if this limit is 3, 
   * only 3 top results would be computed.
   * In the per-node mode, if this limit is 3,
   * 3 top children of {@link #getCategoryPath() the target category} would be returned,
   * as well as 3 top children of each of them, and so forth, until the depth defined 
   * by {@link #getDepth()}.
   * @see #getResultMode()
   */
  public final int getNumResults() {
    return numResults;
  }

  /**
   * Sort options for facet results.
   */
  public enum SortBy { 
    /** sort by category ordinal with the taxonomy */
    ORDINAL, 

    /** sort by computed category value */ 
    VALUE 
  }

  /** Specify how should results be sorted. */
  public final SortBy getSortBy() {
    return sortBy;
  }

  /** Requested sort order for the results. */
  public enum SortOrder { ASCENDING, DESCENDING }

  /** Return the requested order of results. */
  public final SortOrder getSortOrder() {
    return sortOrder;
  }

  @Override
  public String toString() {
    return categoryPath.toString()+" nRes="+numResults+" nLbl="+numLabel;
  }

  /**
   * Creates a new {@link FacetResultsHandler} that matches the request logic
   * and current settings, such as {@link #getDepth() depth},
   * {@link #getResultMode() limits-mode}, etc, as well as the passed in
   * {@link TaxonomyReader}.
   * 
   * @param taxonomyReader taxonomy reader is needed e.g. for knowing the
   *        taxonomy size.
   */
  public FacetResultsHandler createFacetResultsHandler(TaxonomyReader taxonomyReader) {
    try {
      if (resultMode == ResultMode.PER_NODE_IN_TREE) {
        return new TopKInEachNodeHandler(taxonomyReader, (FacetRequest) clone());
      } 
      return new TopKFacetResultsHandler(taxonomyReader, (FacetRequest) clone());
    } catch (CloneNotSupportedException e) {
      // Shouldn't happen since we implement Cloneable. If it does happen, it is
      // probably because the class was changed to not implement Cloneable
      // anymore.
      throw new RuntimeException(e);
    }
  }

  /**
   * Result structure manner of applying request's limits such as 
   * {@link #getNumLabel()} and
   * {@link #getNumResults()}.
   */
  public enum ResultMode { 
    /** Limits are applied per node, and the result has a full tree structure. */
    PER_NODE_IN_TREE, 

    /** Limits are applied globally, on total number of results, and the result has a flat structure. */
    GLOBAL_FLAT
  }

  /** Return the requested result mode. */
  public final ResultMode getResultMode() {
    return resultMode;
  }

  /**
   * @param resultMode the resultMode to set
   * @see #getResultMode()
   */
  public void setResultMode(ResultMode resultMode) {
    this.resultMode = resultMode;
  }

  @Override
  public int hashCode() {
    return hashCode; 
  }
  
  @Override
  public boolean equals(Object o) {
    if (o instanceof FacetRequest) {
      FacetRequest that = (FacetRequest)o;
      return that.hashCode == this.hashCode &&
          that.categoryPath.equals(this.categoryPath) &&
          that.numResults == this.numResults &&
          that.depth == this.depth &&
          that.resultMode == this.resultMode &&
          that.numLabel == this.numLabel;
    }
    return false;
  }

  /**
   * Create an aggregator for this facet request. Aggregator action depends on
   * request definition. For a count request, it will usually increment the
   * count for that facet.
   * 
   * @param useComplements
   *          whether the complements optimization is being used for current
   *          computation.
   * @param arrays
   *          provider for facet arrays in use for current computation.
   * @param indexReader
   *          index reader in effect.
   * @param taxonomy
   *          reader of taxonomy in effect.
   * @throws IOException
   */
  public abstract Aggregator createAggregator(boolean useComplements,
      FacetArrays arrays, IndexReader indexReader,
      TaxonomyReader taxonomy) throws IOException;

  /**
   * Create the category list iterator for the specified partition.
   * If a non null cache is provided which contains the required data, 
   * use it for the iteration.
   */
  public CategoryListIterator createCategoryListIterator(IndexReader reader,
      TaxonomyReader taxo, FacetSearchParams sParams, int partition)
      throws IOException {
    CategoryListCache clCache = sParams.getClCache();
    CategoryListParams clParams = sParams.getFacetIndexingParams().getCategoryListParams(categoryPath);
    if (clCache!=null) {
      CategoryListData clData = clCache.get(clParams);
      if (clData!=null) {
        return clData.iterator(partition);
      }
    }
    return clParams.createCategoryListIterator(reader, partition);
  }

  /**
   * Return the value of a category used for facets computations for this
   * request. For a count request this would be the count for that facet, i.e.
   * an integer number. but for other requests this can be the result of a more
   * complex operation, and the result can be any double precision number.
   * Having this method with a general name <b>value</b> which is double
   * precision allows to have more compact API and code for handling counts and
   * perhaps other requests (such as for associations) very similarly, and by
   * the same code and API, avoiding code duplication.
   * 
   * @param arrays
   *          provider for facet arrays in use for current computation.
   * @param idx
   *          an index into the count arrays now in effect in
   *          <code>arrays</code>. E.g., for ordinal number <i>n</i>, with
   *          partition, of size <i>partitionSize</i>, now covering <i>n</i>,
   *          <code>getValueOf</code> would be invoked with <code>idx</code>
   *          being <i>n</i> % <i>partitionSize</i>.
   */
  public abstract double getValueOf(FacetArrays arrays, int idx);
  
  /**
   * Indicates whether this facet request is eligible for applying the complements optimization.
   */
  public boolean supportsComplements() {
    return false; // by default: no
  }

  /** Indicates whether the results of this request depends on each result document's score */
  public abstract boolean requireDocumentScore();

}
