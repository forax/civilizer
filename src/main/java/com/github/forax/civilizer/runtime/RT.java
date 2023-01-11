package com.github.forax.civilizer.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.TypeDescriptor;
import java.lang.reflect.Array;
import java.lang.runtime.ObjectMethods;

public final class RT {
  private RT() {
    throw new AssertionError();
  }

  private static final int ACC_PRIMITIVE = 0x0800;  // see jdk.internal.value.PrimitiveClass

  public static boolean isZeroDefault(Class<?> clazz) {
    return (clazz.getModifiers() & ACC_PRIMITIVE) != 0;
  }

  /*public static Class<?> asPrimaryType(Class<?> type) {
    if (type.isPrimitive()) {
      throw new IllegalArgumentException("component is primitive");
    }
    return MethodType.fromMethodDescriptorString("()L" + type.getName().replace('.', '/') + ";", type.getClassLoader()).returnType();
  }*/

  public static Class<?> asSecondaryType(Class<?> type) {
    if (type.isPrimitive()) {
      throw new IllegalArgumentException("component is primitive");
    }
    return MethodType.fromMethodDescriptorString("()Q" + type.getName().replace('.', '/') + ";", type.getClassLoader()).returnType();
  }

  @SuppressWarnings("unchecked")
  public static <T> T defaultValue(Class<T> type) {
    return (T) Array.get(Array.newInstance(type,1), 0);
  }

  @SuppressWarnings("unchecked")
  public static <T> T[] newNonNullArray(Class<T> component, int length) {
    return (T[]) Array.newInstance(asSecondaryType(component), length);
  }

  // bootstrap methods

  public static CallSite bsm_getfield(Lookup lookup, String name, MethodType methodType) throws NoSuchFieldException, IllegalAccessException {
    var owner = methodType.parameterType(0);
    var desc = methodType.returnType();
    MethodHandle mh;
    try {
      mh = lookup.findGetter(owner, name, desc);
    } catch (NoSuchFieldException e) {
      mh = lookup.findGetter(owner, name, asSecondaryType(desc));
    }
    return new ConstantCallSite(mh.asType(methodType));
  }

  public static CallSite bsm_putfield(Lookup lookup, String name, MethodType methodType) throws NoSuchFieldException, IllegalAccessException {
    var owner = methodType.parameterType(0);
    var desc = methodType.parameterType(1);
    MethodHandle mh;
    try {
      mh = lookup.findSetter(owner, name, desc);
    } catch (NoSuchFieldException e) {
      mh = lookup.findSetter(owner, name, asSecondaryType(desc));
    }
    return new ConstantCallSite(mh.asType(methodType));
  }

  public static Object bsm_record(MethodHandles.Lookup lookup, String methodName, TypeDescriptor type, Class<?> recordClass, String names, MethodHandle... getters) throws Throwable {
     switch (methodName) {
       case "equals", "hashCode", "toString" -> {}
       default -> throw new LinkageError("unknown method" + methodName + " " + type);
     }
     if (!(type instanceof MethodType methodType)) {
       throw new LinkageError("unknown type" + methodName + " " + type);
     }
     if (isZeroDefault(recordClass)) {
       var newMethodType = methodType.changeParameterType(0, asSecondaryType(recordClass));
       var callsite = (CallSite) ObjectMethods.bootstrap(lookup, methodName, newMethodType, recordClass, names, getters);
       var mh = callsite.dynamicInvoker();
       return new ConstantCallSite(mh.asType(methodType));
     }
     return ObjectMethods.bootstrap(lookup, methodName, methodType, recordClass, names, getters);
  }
}
