// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ReflectionUtil;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Please use {@link SkipDefaultsSerializationFilter} if state class doesn't implement "equals" (in Kotlin use {@link com.intellij.openapi.components.BaseState})
 */
public class SkipDefaultValuesSerializationFilters extends SerializationFilterBase {
  private final Map<Class<?>, Object> myDefaultBeans = new THashMap<>();

  public SkipDefaultValuesSerializationFilters() { }

  public SkipDefaultValuesSerializationFilters(Object... defaultBeans) {
    for (Object defaultBean : defaultBeans) {
      myDefaultBeans.put(defaultBean.getClass(), defaultBean);
    }
  }

  @Override
  protected boolean accepts(@NotNull Accessor accessor, @NotNull Object bean, @Nullable Object beanValue) {
    Object defValue = accessor.read(getDefaultBean(bean));
    if (defValue instanceof Element && beanValue instanceof Element) {
      return !JDOMUtil.areElementsEqual((Element)beanValue, (Element)defValue);
    }
    else {
      return !Comparing.equal(beanValue, defValue);
    }
  }

  @NotNull
  Object getDefaultBean(@NotNull Object bean) {
    Class<?> c = bean.getClass();
    return getDefaultValue(c);
  }

  @NotNull
  public Object getDefaultValue(Class<?> c) {
    Object o = myDefaultBeans.get(c);

    if (o == null) {
      o = ReflectionUtil.newInstance(c);
      configure(o);
      myDefaultBeans.put(c, o);
    }

    return o;
  }

  /**
   * Override to put your own default object configuration
   */
  protected void configure(@NotNull Object o) {
  }
}
