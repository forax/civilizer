package com.github.forax.civilizer.vrt;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.util.Objects;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

public final class RT {
  private RT() {
    throw new AssertionError();
  }

  public static boolean isValue(Class<?> type) {
    //return (type.getModifiers() & ACC_IDENTITY) == 0;
    return type.isValue();
  }

  private static final MethodHandle IS_IMPLICITLY_CONSTRUCTIBLE, ZERO_INSTANCE,
      NEW_NULL_RESTRICTED_ARRAY, IS_NULL_RESTRICTED_ARRAY, REQUIRE_IDENTTY;

  static {
    Class<?> valueClass;
    try {
      valueClass = Class.forName("jdk.internal.value.ValueClass");
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);
    }
    var lookup = lookup();
    try {
      IS_IMPLICITLY_CONSTRUCTIBLE = lookup.findStatic(valueClass, "isImplicitlyConstructible",
          methodType(boolean.class, Class.class));
      ZERO_INSTANCE = lookup.findStatic(valueClass, "zeroInstance",
          methodType(Object.class, Class.class));
      NEW_NULL_RESTRICTED_ARRAY = lookup.findStatic(valueClass,"newNullRestrictedArray",
          methodType(Object[].class, Class.class, int.class));
      IS_NULL_RESTRICTED_ARRAY = lookup.findStatic(valueClass, "isNullRestrictedArray",
          methodType(boolean.class, Object.class));
      REQUIRE_IDENTTY = lookup.findStatic(Objects.class, "requireIdentity",
          methodType(Object.class, Object.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  public static boolean isImplicitlyConstructible(Class<?> type) {
    if (!isValue(type)) {
      return false;
    }
    try {
      return (boolean) IS_IMPLICITLY_CONSTRUCTIBLE.invokeExact(type);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new AssertionError(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T zeroInstance(Class<T> type) {
    try {
      return (T) ZERO_INSTANCE.invokeExact(type);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new AssertionError(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T defaultValue(Class<T> type) {
    if (isImplicitlyConstructible(type)) {
      return zeroInstance(type);
    }
    return (T) Array.get(Array.newInstance(type, 1), 0);
  }

  @SuppressWarnings("unchecked")
  public static <T> T[] newNullRestrictedArray(Class<T> component, int length) {
    try {
      return (T[]) NEW_NULL_RESTRICTED_ARRAY.invokeExact(component, length);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new AssertionError(e);
    }
  }

  public static boolean isNullRestrictedArray(Object array) {
    try {
      return (boolean) IS_NULL_RESTRICTED_ARRAY.invokeExact(array);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new AssertionError(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T requireIdentity(Object object, String message) {
    try {
      return (T) REQUIRE_IDENTTY.invokeExact(object, message);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new AssertionError(e);
    }
  }
}
