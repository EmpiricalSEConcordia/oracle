/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index;

import org.apache.lucene.util.SetOnce;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.cache.query.QueryCache;
import org.elasticsearch.index.cache.query.index.IndexQueryCache;
import org.elasticsearch.index.cache.query.none.NoneQueryCache;
import org.elasticsearch.index.engine.EngineFactory;
import org.elasticsearch.index.shard.IndexEventListener;
import org.elasticsearch.index.shard.IndexSearcherWrapper;
import org.elasticsearch.index.shard.IndexingOperationListener;
import org.elasticsearch.index.similarity.BM25SimilarityProvider;
import org.elasticsearch.index.similarity.SimilarityProvider;
import org.elasticsearch.index.similarity.SimilarityService;
import org.elasticsearch.index.store.IndexStore;
import org.elasticsearch.index.store.IndexStoreConfig;
import org.elasticsearch.indices.IndicesQueryCache;
import org.elasticsearch.indices.fielddata.cache.IndicesFieldDataCache;
import org.elasticsearch.indices.mapper.MapperRegistry;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * IndexModule represents the central extension point for index level custom implementations like:
 * <ul>
 *     <li>{@link SimilarityProvider} - New {@link SimilarityProvider} implementations can be registered through {@link #addSimilarity(String, BiFunction)}
 *         while existing Providers can be referenced through Settings under the {@link IndexModule#SIMILARITY_SETTINGS_PREFIX} prefix
 *         along with the "type" value.  For example, to reference the {@link BM25SimilarityProvider}, the configuration
 *         <tt>"index.similarity.my_similarity.type : "BM25"</tt> can be used.</li>
 *      <li>{@link IndexStore} - Custom {@link IndexStore} instances can be registered via {@link #addIndexStore(String, BiFunction)}</li>
 *      <li>{@link IndexEventListener} - Custom {@link IndexEventListener} instances can be registered via {@link #addIndexEventListener(IndexEventListener)}</li>
 *      <li>Settings update listener - Custom settings update listener can be registered via {@link #addSettingsUpdateConsumer(Setting, Consumer)}</li>
 * </ul>
 */
public final class IndexModule {

    public static final Setting<String> INDEX_STORE_TYPE_SETTING =
        new Setting<>("index.store.type", "", Function.identity(), Property.IndexScope);
    public static final String SIMILARITY_SETTINGS_PREFIX = "index.similarity";
    public static final String INDEX_QUERY_CACHE = "index";
    public static final String NONE_QUERY_CACHE = "none";
    public static final Setting<String> INDEX_QUERY_CACHE_TYPE_SETTING =
        new Setting<>("index.queries.cache.type", INDEX_QUERY_CACHE, Function.identity(), Property.IndexScope);
    // for test purposes only
    public static final Setting<Boolean> INDEX_QUERY_CACHE_EVERYTHING_SETTING =
        Setting.boolSetting("index.queries.cache.everything", false, Property.IndexScope);
    private final IndexSettings indexSettings;
    private final IndexStoreConfig indexStoreConfig;
    private final AnalysisRegistry analysisRegistry;
    // pkg private so tests can mock
    final SetOnce<EngineFactory> engineFactory = new SetOnce<>();
    private SetOnce<IndexSearcherWrapperFactory> indexSearcherWrapper = new SetOnce<>();
    private final Set<IndexEventListener> indexEventListeners = new HashSet<>();
    private IndexEventListener listener;
    private final Map<String, BiFunction<String, Settings, SimilarityProvider>> similarities = new HashMap<>();
    private final Map<String, BiFunction<IndexSettings, IndexStoreConfig, IndexStore>> storeTypes = new HashMap<>();
    private final Map<String, BiFunction<IndexSettings, IndicesQueryCache, QueryCache>> queryCaches = new HashMap<>();


    public IndexModule(IndexSettings indexSettings, IndexStoreConfig indexStoreConfig, AnalysisRegistry analysisRegistry) {
        this.indexStoreConfig = indexStoreConfig;
        this.indexSettings = indexSettings;
        this.analysisRegistry = analysisRegistry;
        registerQueryCache(INDEX_QUERY_CACHE, IndexQueryCache::new);
        registerQueryCache(NONE_QUERY_CACHE, (a, b) -> new NoneQueryCache(a));
    }

    /**
     * Adds a Setting and it's consumer for this index.
     */
    public <T> void addSettingsUpdateConsumer(Setting<T> setting, Consumer<T> consumer) {
        if (setting == null) {
            throw new IllegalArgumentException("setting must not be null");
        }
        indexSettings.getScopedSettings().addSettingsUpdateConsumer(setting, consumer);
    }

    /**
     * Returns the index {@link Settings} for this index
     */
    public Settings getSettings() {
        return indexSettings.getSettings();
    }

    /**
     * Returns the index this module is associated with
     */
    public Index getIndex() {
        return indexSettings.getIndex();
    }

    /**
     * Adds an {@link IndexEventListener} for this index. All listeners added here
     * are maintained for the entire index lifecycle on this node. Once an index is closed or deleted these
     * listeners go out of scope.
     * <p>
     * Note: an index might be created on a node multiple times. For instance if the last shard from an index is
     * relocated to another node the internal representation will be destroyed which includes the registered listeners.
     * Once the node holds at least one shard of an index all modules are reloaded and listeners are registered again.
     * Listeners can't be unregistered they will stay alive for the entire time the index is allocated on a node.
     * </p>
     */
    public void addIndexEventListener(IndexEventListener listener) {
        if (this.listener != null) {
            throw new IllegalStateException("can't add listener after listeners are frozen");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (indexEventListeners.contains(listener)) {
            throw new IllegalArgumentException("listener already added");
        }

        this.indexEventListeners.add(listener);
    }

    /**
     * Adds an {@link IndexStore} type to this index module. Typically stores are registered with a reference to
     * it's constructor:
     * <pre>
     *     indexModule.addIndexStore("my_store_type", MyStore::new);
     * </pre>
     *
     * @param type the type to register
     * @param provider the instance provider / factory method
     */
    public void addIndexStore(String type, BiFunction<IndexSettings, IndexStoreConfig, IndexStore> provider) {
        if (storeTypes.containsKey(type)) {
            throw new IllegalArgumentException("key [" + type +"] already registered");
        }
        storeTypes.put(type, provider);
    }


    /**
     * Registers the given {@link SimilarityProvider} with the given name
     *
     * @param name Name of the SimilarityProvider
     * @param similarity SimilarityProvider to register
     */
    public void addSimilarity(String name, BiFunction<String, Settings, SimilarityProvider> similarity) {
        if (similarities.containsKey(name) || SimilarityService.BUILT_IN.containsKey(name)) {
            throw new IllegalArgumentException("similarity for name: [" + name + " is already registered");
        }
        similarities.put(name, similarity);
    }

    /**
     * Registers a {@link QueryCache} provider for a given name
     * @param name the providers / caches name
     * @param provider the provider instance
     */
    public void registerQueryCache(String name, BiFunction<IndexSettings, IndicesQueryCache, QueryCache> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (queryCaches.containsKey(name)) {
            throw new IllegalArgumentException("Can't register the same [query_cache] more than once for [" + name + "]");
        }
        queryCaches.put(name, provider);
    }

    /**
     * Sets a {@link org.elasticsearch.index.IndexModule.IndexSearcherWrapperFactory} that is called once the IndexService is fully constructed.
     * Note: this method can only be called once per index. Multiple wrappers are not supported.
     */
    public void setSearcherWrapper(IndexSearcherWrapperFactory indexSearcherWrapperFactory) {
        this.indexSearcherWrapper.set(indexSearcherWrapperFactory);
    }

    public IndexEventListener freeze() {
        // TODO somehow we need to make this pkg private...
        if (listener == null) {
            listener = new CompositeIndexEventListener(indexSettings, indexEventListeners);
        }
        return listener;
    }

    private static boolean isBuiltinType(String storeType) {
        for (Type type : Type.values()) {
            if (type.match(storeType)) {
                return true;
            }
        }
        return false;
    }

    public enum Type {
        NIOFS,
        MMAPFS,
        SIMPLEFS,
        FS,
        DEFAULT;

        public String getSettingsKey() {
            return this.name().toLowerCase(Locale.ROOT);
        }
        /**
         * Returns true iff this settings matches the type.
         */
        public boolean match(String setting) {
            return getSettingsKey().equals(setting);
        }
    }

    /**
     * Factory for creating new {@link IndexSearcherWrapper} instances
     */
    public interface IndexSearcherWrapperFactory {
        /**
         * Returns a new IndexSearcherWrapper. This method is called once per index per node
         */
        IndexSearcherWrapper newWrapper(final IndexService indexService);
    }

    public IndexService newIndexService(NodeEnvironment environment, IndexService.ShardStoreDeleter shardStoreDeleter, NodeServicesProvider servicesProvider, IndicesQueryCache indicesQueryCache, MapperRegistry mapperRegistry, IndicesFieldDataCache indicesFieldDataCache,
                                        IndexingOperationListener... listeners) throws IOException {
        IndexSearcherWrapperFactory searcherWrapperFactory = indexSearcherWrapper.get() == null ? (shard) -> null : indexSearcherWrapper.get();
        IndexEventListener eventListener = freeze();
        final String storeType = indexSettings.getValue(INDEX_STORE_TYPE_SETTING);
        final IndexStore store;
        if (Strings.isEmpty(storeType) || isBuiltinType(storeType)) {
            store = new IndexStore(indexSettings, indexStoreConfig);
        } else {
            BiFunction<IndexSettings, IndexStoreConfig, IndexStore> factory = storeTypes.get(storeType);
            if (factory == null) {
                throw new IllegalArgumentException("Unknown store type [" + storeType + "]");
            }
            store = factory.apply(indexSettings, indexStoreConfig);
            if (store == null) {
                throw new IllegalStateException("store must not be null");
            }
        }
        indexSettings.getScopedSettings().addSettingsUpdateConsumer(IndexStore.INDEX_STORE_THROTTLE_TYPE_SETTING, store::setType);
        indexSettings.getScopedSettings().addSettingsUpdateConsumer(IndexStore.INDEX_STORE_THROTTLE_MAX_BYTES_PER_SEC_SETTING, store::setMaxRate);
        final String queryCacheType = indexSettings.getValue(INDEX_QUERY_CACHE_TYPE_SETTING);
        final BiFunction<IndexSettings, IndicesQueryCache, QueryCache> queryCacheProvider = queryCaches.get(queryCacheType);
        final QueryCache queryCache = queryCacheProvider.apply(indexSettings, indicesQueryCache);
        return new IndexService(indexSettings, environment, new SimilarityService(indexSettings, similarities), shardStoreDeleter, analysisRegistry, engineFactory.get(),
                servicesProvider, queryCache, store, eventListener, searcherWrapperFactory, mapperRegistry, indicesFieldDataCache, listeners);
    }

}
