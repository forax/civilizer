package com.github.forax.civilizer.vm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodHandles.Lookup.ClassOption;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static java.lang.invoke.MethodType.methodType;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ASM9;

public class RT {
  private RT() {
    throw new AssertionError();
  }

  public static Object ldc() {
    throw new LinkageError("this method should be rewritten by the rewriter, so never reach that point");
  }

  private static Class<?> createKiddyPoolClass(Lookup lookup, Class<?> type, Object classData) {
    var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class");
    if (input == null) {
      throw new LinkageError("no bytecode available for " + type);
    }
    byte[] bytecode;
    try(input) {
      bytecode = input.readAllBytes();
    } catch (IOException e) {
      throw (LinkageError) new LinkageError().initCause(e);
    }
    var reader = new ClassReader(bytecode);
    var writer = new ClassWriter(reader, 0);
    reader.accept(new ClassVisitor(ASM9, writer) {
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, name + "$KiddyPool", null, "java/lang/Object", null);
      }

      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return null;
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if ((access & ACC_SYNTHETIC) != 0 && name.startsWith("$KP")) {
          return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        return null;
      }
    }, 0);
    var data = writer.toByteArray();

    Lookup kiddyPoolLookup;
    try {
      kiddyPoolLookup = lookup.defineHiddenClassWithClassData(data, classData, true, ClassOption.NESTMATE, ClassOption.STRONG);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return kiddyPoolLookup.lookupClass();
  }

  private record ClassDataPair(Object speciesParameters, Object methodParameters) {}
  private record MethodSpecies(Species species, Object parameters, String name, String descriptor) {}

  // caches should be concurrent and maintain a weak ref on the class
  private static final HashMap<Species, Class<?>> KIDDY_POOL_CACHE = new HashMap<>();
  private static final HashMap<MethodSpecies, Class<?>> KIDDY_POOL_METH_CACHE = new HashMap<>();

  private static Class<?> kiddyPoolClass(Lookup lookup, Species species) throws IllegalAccessException {
    return KIDDY_POOL_CACHE.computeIfAbsent(species, sp -> {
      Lookup speciesLookup;
      try {
        speciesLookup = MethodHandles.privateLookupIn(sp.raw(), lookup);
      } catch (IllegalAccessException e) {
        throw (IllegalAccessError) new IllegalAccessError().initCause(e);
      }
      return createKiddyPoolClass(speciesLookup, sp.raw(), new ClassDataPair(sp.parameters(), null));
    });
  }

  private static Class<?> kiddyPoolClass(Lookup lookup, MethodSpecies methodSpecies) throws IllegalAccessException {
    return KIDDY_POOL_METH_CACHE.computeIfAbsent(methodSpecies, msp -> {
      Lookup speciesLookup;
      try {
        speciesLookup = MethodHandles.privateLookupIn(msp.species.raw(), lookup);
      } catch (IllegalAccessException e) {
        throw (IllegalAccessError) new IllegalAccessError().initCause(e);
      }
      return createKiddyPoolClass(speciesLookup, msp.species.raw(), new ClassDataPair(msp.species.parameters(), msp.parameters));
    });
  }

  private static class InliningCache extends MutableCallSite {
    private static final MethodHandle SLOW_PATH, POINTER_CHECK;
    static {
      var lookup = MethodHandles.lookup();
      try {
        SLOW_PATH = lookup.findVirtual(InliningCache.class, "slowPath", methodType(MethodHandle.class, Object.class));
        POINTER_CHECK = lookup.findStatic(InliningCache.class, "pointerCheck", methodType(boolean.class, Object.class, Object.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private final Lookup lookup;
    private final MethodHandle bsm;
    private final String kiddyPoolRef;

    private InliningCache(MethodType type, Lookup lookup, MethodHandle bsm, String kiddyPoolRef) {
      super(type);
      this.lookup = lookup;
      this.bsm = bsm;
      this.kiddyPoolRef = kiddyPoolRef;
      var combiner = MethodHandles.dropArguments(SLOW_PATH.bindTo(this), 0, type.parameterList().subList(0, type.parameterCount() - 1));
      setTarget(MethodHandles.foldArguments(MethodHandles.exactInvoker(type), combiner));
    }

    private static boolean pointerCheck(Object o, Object o2) {
      return o == o2;
    }

    private MethodHandle slowPath(Object kiddyPool) throws Throwable {
      var accessor = lookup.findStatic((Class<?>) kiddyPool, "$" + kiddyPoolRef, methodType(Object.class));
      var value = accessor.invokeExact();

      var target = ((CallSite) bsm.invoke(value)).dynamicInvoker();
      target = MethodHandles.dropArguments(target, type().parameterCount() - 1, Object.class);

      var test = MethodHandles.dropArguments(POINTER_CHECK.bindTo(kiddyPool),0, type().parameterList().subList(0, type().parameterCount() - 1));
      var guard = MethodHandles.guardWithTest(test, target, new InliningCache(type(), lookup, bsm, kiddyPoolRef).dynamicInvoker());
      setTarget(guard);

      return target;
    }
  }

  private static final MethodHandle BSM_LDC, BSM_STATIC, BSM_NEW, BSM_NEW_ARRAY, BSM_INIT_DEFAULT, BSM_PUT_VALUE_CHECK;
  static {
    var lookup = MethodHandles.lookup();
    try {
      BSM_LDC = lookup.findStatic(RT.class, "bsm_ldc", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
      BSM_STATIC = lookup.findStatic(RT.class, "bsm_static", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Class.class, Object.class));
      BSM_NEW = lookup.findStatic(RT.class, "bsm_new", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
      BSM_NEW_ARRAY = lookup.findStatic(RT.class, "bsm_new_array", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
      BSM_INIT_DEFAULT = lookup.findStatic(RT.class, "bsm_init_default", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
      BSM_PUT_VALUE_CHECK = lookup.findStatic(RT.class, "bsm_put_value_check", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private static MethodHandle specializeInstanceMethod(Linkage linkage, MethodHandle method, MethodType type) {
    var specializedType = linkage.toMethodType().insertParameterTypes(0, linkage.owner().raw());
    return method.asType(specializedType).asType(type);
  }

  private static MethodHandle specializeStaticMethod(Linkage linkage, MethodHandle method, MethodType type) {
    var specializedType = linkage.toMethodType();
    return method.asType(specializedType).asType(type);
  }

  public static CallSite bsm_ldc(Lookup lookup, String name, MethodType type, Object constant) {
    //System.out.println("bsm_ldc " + constant + " (instance of " + constant.getClass() + ")");
    if (constant instanceof String kiddyPoolRef) {
      var bsmNew = MethodHandles.insertArguments(BSM_LDC, 0, lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()));
      return new InliningCache(type, lookup, bsmNew, kiddyPoolRef);
    }
    return new ConstantCallSite(MethodHandles.constant(Object.class, constant));
  }

  public static CallSite bsm_static(Lookup lookup, String name, MethodType type, Class<?> owner, Object constant) throws NoSuchMethodException, IllegalAccessException {
    System.out.println("bsm_static " + name + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      var method = lookup.findStatic(owner, name, type.appendParameterTypes(Object.class));
      var methodSpecies = new MethodSpecies(linkage.owner(), linkage.parameters(), name, type.toMethodDescriptorString());
      var kiddyPoolClass = kiddyPoolClass(lookup, methodSpecies);
      var mh = MethodHandles.insertArguments(method, type.parameterCount(), kiddyPoolClass);
      var target = specializeStaticMethod(linkage, mh, type);
      return new ConstantCallSite(target);
    }
    if (constant instanceof String kiddyPoolRef) {
      var bsmNew = MethodHandles.insertArguments(BSM_STATIC, 0, lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()));
      return new InliningCache(type, lookup, bsmNew, kiddyPoolRef);
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + owner + " " + constant);
  }

  public static CallSite bsm_virtual(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchMethodException, IllegalAccessException {
    //System.out.println("bsm_virtual " + name + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      var method = lookup.findVirtual(type.parameterType(0), name, type.dropParameterTypes(0, 1));
      var target = specializeInstanceMethod(linkage, method, type);
      return new ConstantCallSite(target);
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_new(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchMethodException, IllegalAccessException {
    //System.out.println("bsm_new " + type + " " + constant + "(instance of " +constant.getClass() + ")");

    if (constant instanceof Linkage linkage) {
      var init = lookup.findConstructor(type.returnType(), type.changeReturnType(void.class).appendParameterTypes(Object.class));
      var kiddyPoolClass = kiddyPoolClass(lookup, linkage.owner());
      var method = MethodHandles.insertArguments(init, type.parameterCount(), kiddyPoolClass);
      var target = specializeStaticMethod(linkage, method, type);
      return new ConstantCallSite(target);
    }
    if (constant instanceof String kiddyPoolRef) {
      var bsmNew = MethodHandles.insertArguments(BSM_NEW, 0, lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()));
      return new InliningCache(type, lookup, bsmNew, kiddyPoolRef);
    }

    throw new LinkageError(lookup + " " + name + " " + type + " " + constant);
  }

  public static CallSite bsm_new_array(Lookup lookup, String name, MethodType type, Object constant) {
    //System.out.println("bsm_new_array " + type + " " + constant);

    if (constant instanceof Species species) {
      var newArray = MethodHandles.arrayConstructor(species.raw().arrayType());
      var target = newArray.asType(type);
      return new ConstantCallSite(target);
    }
    if (constant instanceof String kiddyPoolRef) {
      var bsmNewArray = MethodHandles.insertArguments(BSM_NEW_ARRAY, 0, lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()));
      return new InliningCache(type, lookup, bsmNewArray, kiddyPoolRef);
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_init_default(Lookup lookup, String name, MethodType type, Object constant) {
    System.out.println("bsm_init_default " + type + " " + constant);

    if (constant instanceof Species species) {
      var defaultValue = Array.get(Array.newInstance(species.raw(), 1), 0);
      return new ConstantCallSite(MethodHandles.constant(type.returnType(), defaultValue));
    }
    if (constant instanceof String kiddyPoolRef) {
      var bsmInitDefault = MethodHandles.insertArguments(BSM_INIT_DEFAULT, 0, lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()));
      return new InliningCache(type, lookup, bsmInitDefault, kiddyPoolRef);
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_put_value_check(Lookup lookup, String name, MethodType type, Object constant) {
    System.out.println("bsm_put_value_check " + type + " " + constant);

    if (constant instanceof Species species) {
      var identity = MethodHandles.identity(type.parameterType(0));
      var target = identity.asType(methodType(type.returnType(), species.raw())).asType(type);
      return new ConstantCallSite(target);
    }
    if (constant instanceof String kiddyPoolRef) {
      var bsmInitDefault = MethodHandles.insertArguments(BSM_PUT_VALUE_CHECK, 0, lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()));
      return new InliningCache(type, lookup, bsmInitDefault, kiddyPoolRef);
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static Object bsm_type(Lookup lookup, String name, Class<?> type) throws IllegalAccessException, ClassNotFoundException {
    //System.out.println("bsm_type " + name);
    // primitiveClass() is not called directly to avoid to have java/lang/Class in the descriptors
    return ConstantBootstraps.primitiveClass(lookup, name, Class.class);
  }

  public static Object bsm_qtype(Lookup lookup, String name, Class<?> type, Class<?> primaryType) throws IllegalAccessException, ClassNotFoundException {
    //System.out.println("bsm_qtype " + primaryType.getName());
    return com.github.forax.civilizer.runtime.RT.asSecondaryType(primaryType);
  }

  private static Species asSpecies(Object o) {
    if (o instanceof Species species) {
      return species;
    }
    if (o instanceof Class<?> clazz) {
      return new Species(clazz, null);
    }
    throw new IllegalStateException("object " + o + " is not a species or a class");
  }

  public static Object bsm_condy(Lookup lookup, String name, Class<?> type, String action, Object... args) throws IllegalAccessException {
    //System.out.println("bsm_condy " + action + " " + Arrays.toString(args));

    return switch (action) {
      case "classData" -> {
        var classDataPair = (ClassDataPair) MethodHandles.classData(lookup, "_", Object.class);
        yield classDataPair.speciesParameters == null ? args[0] : classDataPair.speciesParameters;
      }
      case "methodData" -> {
        var classDataPair = (ClassDataPair) MethodHandles.classData(lookup, "_", Object.class);
        System.out.println("method data classDataPair " + classDataPair);
        yield classDataPair.methodParameters == null ? args[0] : classDataPair.methodParameters;
      }
      case "list.of" -> List.of(args);
      case "list.get" -> ((List<?>) args[0]).get((int) args[1]);
      case "species" -> new Species((Class<?>) args[0], args.length == 1 ? null: args[1]);
      case "linkage" -> new Linkage(asSpecies(args[0]), null, asSpecies(args[1]), Arrays.stream(args).skip(2).map(RT::asSpecies).toList());
      case "linkaze" -> new Linkage(asSpecies(args[0]), args[1], asSpecies(args[2]), Arrays.stream(args).skip(3).map(RT::asSpecies).toList());
      default -> throw new LinkageError("unknown method " + action + " " + Arrays.toString(args));
    };
  }

  public static Object bsm_raw_kiddy_pool(Lookup lookup, String name, Class<?> type) throws IllegalAccessException {
    //System.out.println("bsm_raw_kiddy_pool");
    return kiddyPoolClass(lookup, new Species(lookup.lookupClass(), null));
  }
}
