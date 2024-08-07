/**
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsearch.util.inject.internal;

import org.elasticsearch.util.collect.ImmutableSet;
import org.elasticsearch.util.inject.Binder;
import org.elasticsearch.util.inject.Injector;
import org.elasticsearch.util.inject.Key;
import org.elasticsearch.util.inject.Provider;
import org.elasticsearch.util.inject.spi.*;
import org.elasticsearch.util.inject.util.Providers;

import java.util.Set;

public class InstanceBindingImpl<T> extends BindingImpl<T> implements InstanceBinding<T> {

  final T instance;
  final Provider<T> provider;
  final ImmutableSet<InjectionPoint> injectionPoints;

  public InstanceBindingImpl(Injector injector, Key<T> key, Object source,
      InternalFactory<? extends T> internalFactory, Set<InjectionPoint> injectionPoints,
      T instance) {
    super(injector, key, source, internalFactory, Scoping.UNSCOPED);
    this.injectionPoints = ImmutableSet.copyOf(injectionPoints);
    this.instance = instance;
    this.provider = Providers.of(instance);
  }

  public InstanceBindingImpl(Object source, Key<T> key, Scoping scoping,
      Set<InjectionPoint> injectionPoints, T instance) {
    super(source, key, scoping);
    this.injectionPoints = ImmutableSet.copyOf(injectionPoints);
    this.instance = instance;
    this.provider = Providers.of(instance);
  }

  @Override public Provider<T> getProvider() {
    return this.provider;
  }

  public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
    return visitor.visit(this);
  }

  public T getInstance() {
    return instance;
  }

  public Set<InjectionPoint> getInjectionPoints() {
    return injectionPoints;
  }

  public Set<Dependency<?>> getDependencies() {
    return instance instanceof HasDependencies
        ? ImmutableSet.copyOf(((HasDependencies) instance).getDependencies())
        : Dependency.forInjectionPoints(injectionPoints);
  }

  public BindingImpl<T> withScoping(Scoping scoping) {
    return new InstanceBindingImpl<T>(getSource(), getKey(), scoping, injectionPoints, instance);
  }

  public BindingImpl<T> withKey(Key<T> key) {
    return new InstanceBindingImpl<T>(getSource(), key, getScoping(), injectionPoints, instance);
  }

  public void applyTo(Binder binder) {
    // instance bindings aren't scoped
    binder.withSource(getSource()).bind(getKey()).toInstance(instance);
  }

  @Override public String toString() {
    return new ToStringBuilder(InstanceBinding.class)
        .add("key", getKey())
        .add("source", getSource())
        .add("instance", instance)
        .toString();
  }
}
