package org.apache.lucene.facet.index.params;

import java.util.Collections;
import java.util.List;

import org.apache.lucene.facet.index.categorypolicy.OrdinalPolicy;
import org.apache.lucene.facet.index.categorypolicy.PathPolicy;
import org.apache.lucene.facet.search.FacetArrays;
import org.apache.lucene.facet.taxonomy.CategoryPath;

/*
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
 * Defines parameters that are needed for facets indexing. Note that this class
 * does not have any setters. That's because overriding the default parameters
 * is considered expert. If you wish to override them, simply extend this class
 * and override the relevant getter.
 * 
 * <p>
 * <b>NOTE:</b> This class is also used during faceted search in order to e.g.
 * know which field holds the drill-down terms or the fulltree posting.
 * Therefore this class should be initialized once and you should refrain from
 * changing it. Also note that if you make any changes to it (e.g. suddenly
 * deciding that drill-down terms should be read from a different field) and use
 * it on an existing index, things may not work as expected.
 * 
 * @lucene.experimental
 */
public class FacetIndexingParams {
  
  // the default CLP, can be a singleton
  protected static final CategoryListParams DEFAULT_CATEGORY_LIST_PARAMS = new CategoryListParams();

  /**
   * A {@link FacetIndexingParams} which fixes {@link OrdinalPolicy} to
   * {@link OrdinalPolicy#NO_PARENTS}. This is a singleton equivalent to new
   * {@link #FacetIndexingParams()}.
   */
  public static final FacetIndexingParams ALL_PARENTS = new FacetIndexingParams();
  
  /**
   * The default delimiter with which {@link CategoryPath#components} are
   * concatenated when written to the index, e.g. as drill-down terms. If you
   * choose to override it by overiding {@link #getFacetDelimChar()}, you should
   * make sure that you return a character that's not found in any path
   * component.
   */
  public static final char DEFAULT_FACET_DELIM_CHAR = '\uF749';
  
  private final OrdinalPolicy ordinalPolicy = OrdinalPolicy.ALL_PARENTS;
  private final PathPolicy pathPolicy = PathPolicy.ALL_CATEGORIES;
  private final int partitionSize = Integer.MAX_VALUE;

  protected final CategoryListParams clParams;

  /**
   * Initializes new default params. You should use this constructor only if you
   * intend to override any of the getters, otherwise you can use
   * {@link #ALL_PARENTS} to save unnecessary object allocations.
   */
  public FacetIndexingParams() {
    this(DEFAULT_CATEGORY_LIST_PARAMS);
  }

  /** Initializes new params with the given {@link CategoryListParams}. */
  public FacetIndexingParams(CategoryListParams categoryListParams) {
    clParams = categoryListParams;
  }

  /**
   * The name of the category list to put this category in, or {@code null} if
   * this category should not be aggregatable.
   * <p>
   * By default, all categories are written to the same category list, but
   * applications which know in advance that in some situations only parts of
   * the category hierarchy needs to be counted can divide the categories into
   * two or more different category lists.
   * <p>
   * If {@code null} is returned for a category, it means that this category
   * should not appear in any category list, and thus weights for it cannot be
   * aggregated. This category can still be used for drill-down, even though the
   * its weight is unknown.
   * 
   * @see PerDimensionIndexingParams
   */
  public CategoryListParams getCategoryListParams(CategoryPath category) {
    return clParams;
  }

  /**
   * Copies the text required to execute a drill-down query on the given
   * category to the given {@code char[]}, and returns the number of characters
   * that were written.
   * <p>
   * <b>NOTE:</b> You should make sure that the {@code char[]} is large enough,
   * by e.g. calling {@link CategoryPath#fullPathLength()}.
   */
  public int drillDownTermText(CategoryPath path, char[] buffer) {
    return path.copyFullPath(buffer, 0, getFacetDelimChar());
  }
  
  /**
   * Returns the size of a partition. <i>Partitions</i> allow you to divide
   * (hence, partition) the categories space into small sets to e.g. improve RAM
   * consumption during faceted search. For instance, {@code partitionSize=100K}
   * would mean that if your taxonomy index contains 420K categories, they will
   * be divided into 5 groups and at search time a {@link FacetArrays} will be
   * allocated at the size of the partition.
   * 
   * <p>
   * This is real advanced setting and should be changed with care. By default,
   * all categories are put in one partition. You should modify this setting if
   * you have really large taxonomies (e.g. 1M+ nodes).
   */
  public int getPartitionSize() {
    return partitionSize;
  }
  
  /**
   * Returns a list of all {@link CategoryListParams categoryListParams} that
   * are used for facets indexing.
   */
  public List<CategoryListParams> getAllCategoryListParams() {
    return Collections.singletonList(clParams);
  }

  /**
   * Returns the {@link OrdinalPolicy} that is used during indexing. By default
   * returns {@link OrdinalPolicy#ALL_PARENTS} which means that the full
   * hierarchy will be stored for every document.
   */
  public OrdinalPolicy getOrdinalPolicy() {
    return ordinalPolicy;
  }

  /**
   * Returns the {@link PathPolicy} that is used during indexing. By default
   * returns {@link PathPolicy#ALL_CATEGORIES} which means that the full
   * hierarchy is added as drill-down terms for every document.
   */
  public PathPolicy getPathPolicy() {
    return pathPolicy;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((clParams == null) ? 0 : clParams.hashCode());
    result = prime * result + ((ordinalPolicy == null) ? 0 : ordinalPolicy.hashCode());
    result = prime * result + partitionSize;
    result = prime * result + ((pathPolicy == null) ? 0 : pathPolicy.hashCode());
    
    for (CategoryListParams clp : getAllCategoryListParams()) {
      result ^= clp.hashCode();
    }
    
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof FacetIndexingParams)) {
      return false;
    }
    FacetIndexingParams other = (FacetIndexingParams) obj;
    if (clParams == null) {
      if (other.clParams != null) {
        return false;
      }
    } else if (!clParams.equals(other.clParams)) {
      return false;
    }
    if (ordinalPolicy == null) {
      if (other.ordinalPolicy != null) {
        return false;
      }
    } else if (!ordinalPolicy.equals(other.ordinalPolicy)) {
      return false;
    }
    if (partitionSize != other.partitionSize) {
      return false;
    }
    if (pathPolicy == null) {
      if (other.pathPolicy != null) {
        return false;
      }
    } else if (!pathPolicy.equals(other.pathPolicy)) {
      return false;
    }
    
    Iterable<CategoryListParams> cLs = getAllCategoryListParams();
    Iterable<CategoryListParams> otherCLs = other.getAllCategoryListParams();
    
    return cLs.equals(otherCLs);
  }

  /**
   * Returns the delimiter character used internally for concatenating category
   * path components, e.g. for drill-down terms.
   */
  public char getFacetDelimChar() {
    return DEFAULT_FACET_DELIM_CHAR;
  }

}
