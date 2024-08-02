package com.github.forax.civilizer.vrt;

import java.lang.annotation.Annotation;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.TypeDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.runtime.ObjectMethods;

public final class RT {
  private RT() {
    throw new AssertionError();
  }

  private static final int ACC_IDENTITY = 0x0020;

  public static boolean isValue(Class<?> type) {
    //return (type.getModifiers() & ACC_IDENTITY) == 0;
    return type.isValue();
  }

  private static final Method IS_IMPLICITLY_CONSTRUCTIBLE, ZERO_INSTANCE,
      NEW_NULL_RESTRICTED_ARRAY, IS_NULL_RESTRICTED_ARRAY;

  static {
    Class<?> valueClass;
    try {
      valueClass = Class.forName("jdk.internal.value.ValueClass");
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);
    }
    try {
      IS_IMPLICITLY_CONSTRUCTIBLE = valueClass.getMethod("isImplicitlyConstructible", Class.class);
      ZERO_INSTANCE = valueClass.getMethod("zeroInstance", Class.class);
      NEW_NULL_RESTRICTED_ARRAY = valueClass.getMethod("newNullRestrictedArray", Class.class, int.class);
      IS_NULL_RESTRICTED_ARRAY = valueClass.getMethod("isNullRestrictedArray", Object.class);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  public static boolean isZeroDefault(Class<?> type) {
    if (!isValue(type)) {
      return false;
    }
    try {
      return (boolean) IS_IMPLICITLY_CONSTRUCTIBLE.invoke(null, type);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new AssertionError(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T defaultValue(Class<T> type) {
    if (!isZeroDefault(type)) {
      return (T) Array.get(Array.newInstance(type, 1), 0);
    }
    try {
      return (T) ZERO_INSTANCE.invoke(null, type);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new AssertionError(e);
    }
  }



  @SuppressWarnings("unchecked")
  public static <T> T[] newNonNullArray(Class<T> component, int length) {
    try {
      return (T[]) NEW_NULL_RESTRICTED_ARRAY.invoke(null, component, length);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new AssertionError(e);
    }
  }

  public static boolean isNullRestrictedArray(Object array) {
    try {
      return (boolean) IS_NULL_RESTRICTED_ARRAY.invoke(null, array);
    }  catch (IllegalAccessException | InvocationTargetException e) {
      throw new AssertionError(e);
    }
  }

  /*
  public static boolean isZeroDefault(Class<?> type) {
    if (!isValue(type)) {
      return false;
    }
    return jdk.internal.value.ValueClass.isImplicitlyConstructible(type);
  }

  @SuppressWarnings("unchecked")
  public static <T> T defaultValue(Class<T> type) {
    if (!isZeroDefault(type)) {
      return (T) Array.get(Array.newInstance(type, 1), 0);
    }
    return jdk.internal.value.ValueClass.zeroInstance(type);
  }

  @SuppressWarnings("unchecked")
  public static <T> T[] newNonNullArray(Class<T> component, int length) {
    return (T[]) jdk.internal.value.ValueClass.newNullRestrictedArray(component, length);
  }

  public static boolean isNullRestrictedArray(Object array) {
    return jdk.internal.value.ValueClass.isNullRestrictedArray(array);
  }*/
}
