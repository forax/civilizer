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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.exactInvoker;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.permuteArguments;
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

  @SuppressWarnings("unused")  // used by reflection
  public static Object erase(/*List<?>*/Object parameters, /*List<?>*/Object defaults) {
    var parameterList = (List<?>) parameters;
    var defaultList = (List<?>) defaults;
    if (parameterList == null) {
      return defaults;
    }
    if (parameterList.size() != defaultList.size()) {
      throw new LinkageError("instantiation arguments " + parameters + " and default arguments " + defaultList + " have no the same size");
    }
    return IntStream.range(0, parameterList.size())
        .mapToObj(i -> {
          var parameter = parameterList.get(i);
          return parameter instanceof Class<?> clazz && com.github.forax.civilizer.runtime.RT.isSecondaryType(clazz) ? clazz : defaultList.get(i);
        }).toList();
  }

  @SuppressWarnings("unused")  // used by reflection
  public static Object identity(Object parameters) {
    return parameters;
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
        if ((access & ACC_SYNTHETIC) != 0 && (name.startsWith("$KP") || name.startsWith("$P"))) {
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

  private record Anchor(Object classParameters, Object methodParameters) {}

  private record MethodSpecies(Species species, String name, String descriptor, Object methodParameters) {}

  // caches should be concurrent and maintain a weak ref on the class
  private static final HashMap<Species, Class<?>> KIDDY_POOL_CACHE = new HashMap<>();
  private static final HashMap<MethodSpecies, Class<?>> KIDDY_POOL_METH_CACHE = new HashMap<>();

  private static Lookup privateSpeciesLookup(Lookup lookup, Class<?> raw) {
    try {
       return MethodHandles.privateLookupIn(raw, lookup);
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError) new IllegalAccessError().initCause(e);
    }
  }

  private static Object callBSM(Lookup speciesLookup, Species species, String bsmPoolRef, Object parameters) {
    try {
      var bsmPool = speciesLookup.findStatic(species.raw(), "$" + bsmPoolRef, methodType(Object.class));
      var bsm = (MethodHandle) (Object) bsmPool.invokeExact();
      return bsm.invoke(parameters);
    } catch (Error e) {
      throw e;
    } catch(Throwable e) {
      throw (LinkageError) new LinkageError().initCause(e);
    }
  }

  private static Class<?> kiddyPoolClass(Lookup lookup, Species species) {
    var parametric = species.raw().getAnnotation(Parametric.class);
    if (parametric == null) {
      throw new LinkageError(species.raw() + " is not declared parametric");
    }
    var speciesLookup = privateSpeciesLookup(lookup, species.raw());
    var bsmPoolRef = parametric.value();
    var parameters = callBSM(speciesLookup, species, bsmPoolRef, species.parameters());
    var keySpecies = new Species(species.raw(), parameters);
    return KIDDY_POOL_CACHE.computeIfAbsent(keySpecies, sp -> {
      return createKiddyPoolClass(speciesLookup, sp.raw(), new Anchor(sp.parameters(), null));
    });
  }

  private static Class<?> kiddyPoolClass(Lookup lookup, MethodSpecies methodSpecies, MethodHandle method) {
    var mhInfo = lookup.revealDirect(method);
    var reflected = mhInfo.reflectAs(Method.class, lookup);
    var parametric = reflected.getAnnotation(Parametric.class);
    if (parametric == null) {
      throw new LinkageError(reflected + " is not declared parametric");
    }
    var speciesLookup = privateSpeciesLookup(lookup, methodSpecies.species.raw());
    var bsmPoolRef = parametric.value();
    var methodParameters = callBSM(speciesLookup, methodSpecies.species, bsmPoolRef, methodSpecies.methodParameters);
    var keyMethodSpecies = new MethodSpecies(methodSpecies.species, methodSpecies.name, methodSpecies.descriptor, methodParameters);
    return KIDDY_POOL_METH_CACHE.computeIfAbsent(keyMethodSpecies, msp -> {
      return createKiddyPoolClass(speciesLookup, msp.species.raw(), new Anchor(msp.species.parameters(), msp.methodParameters));
    });
  }

  private static final class KiddyPoolRefInliningCache extends MutableCallSite {
    @FunctionalInterface
    private interface BSM {
      CallSite apply(Object value) throws Throwable;
    }

    private static final MethodHandle SLOW_PATH, POINTER_CHECK;
    static {
      var lookup = MethodHandles.lookup();
      try {
        SLOW_PATH = lookup.findVirtual(KiddyPoolRefInliningCache.class, "slowPath", methodType(MethodHandle.class, Object.class));
        POINTER_CHECK = lookup.findStatic(KiddyPoolRefInliningCache.class, "pointerCheck", methodType(boolean.class, Object.class, Object.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private final Lookup lookup;
    private final BSM bsm;
    private final String kiddyPoolRef;

    private KiddyPoolRefInliningCache(MethodType type, Lookup lookup, String kiddyPoolRef, BSM bsm) {
      super(type);
      this.lookup = lookup;
      this.kiddyPoolRef = kiddyPoolRef;
      this.bsm = bsm;
      var combiner = dropArguments(SLOW_PATH.bindTo(this), 0, type.parameterList().subList(0, type.parameterCount() - 1));
      setTarget(foldArguments(exactInvoker(type), combiner));
    }

    private static boolean pointerCheck(Object o, Object o2) {
      return o == o2;
    }

    private MethodHandle slowPath(Object kiddyPool) throws Throwable {
      var accessor = lookup.findStatic((Class<?>) kiddyPool, "$" + kiddyPoolRef, methodType(Object.class));
      var value = accessor.invokeExact();

      var target = bsm.apply(value).dynamicInvoker();
      target = dropArguments(target, type().parameterCount() - 1, Object.class);

      var test = dropArguments(POINTER_CHECK.bindTo(kiddyPool),0, type().parameterList().subList(0, type().parameterCount() - 1));
      var guard = guardWithTest(test, target, new KiddyPoolRefInliningCache(type(), lookup, kiddyPoolRef, bsm).dynamicInvoker());
      setTarget(guard);

      return target;
    }
  }

  private static final class VirtualCallInliningCache extends MutableCallSite {
    @FunctionalInterface
    private interface BSM {
      CallSite apply(Class<?> receiverClass) throws Throwable;
    }

    private static final MethodHandle SLOW_PATH, CLASS_CHECK;
    static {
      var lookup = MethodHandles.lookup();
      try {
        SLOW_PATH = lookup.findVirtual(VirtualCallInliningCache.class, "slowPath", methodType(MethodHandle.class, Object.class));
        CLASS_CHECK = lookup.findStatic(VirtualCallInliningCache.class, "classCheck", methodType(boolean.class, Class.class, Object.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private final Lookup lookup;
    private final BSM bsm;

    private VirtualCallInliningCache(MethodType type, Lookup lookup, BSM bsm) {
      super(type);
      this.lookup = lookup;
      this.bsm = bsm;
      var combiner = SLOW_PATH.bindTo(this);
      setTarget(foldArguments(exactInvoker(type), combiner.asType(methodType(MethodHandle.class, type.parameterType(0)))));
    }

    private static boolean classCheck(Class<?> clazz, Object o) {
      return o.getClass() == clazz;
    }

    private MethodHandle slowPath(Object receiver) throws Throwable {
      var receiverClass = receiver.getClass();
      var target = bsm.apply(receiverClass).dynamicInvoker();

      var test = CLASS_CHECK.bindTo(receiverClass).asType(methodType(boolean.class, type().parameterType(0)));
      var guard = guardWithTest(test, target, new VirtualCallInliningCache(type(), lookup, bsm).dynamicInvoker());
      setTarget(guard);

      return target;
    }
  }

  public static CallSite bsm_ldc(Lookup lookup, String name, MethodType type, Object constant) {
    //System.out.println("bsm_ldc " + constant + " (instance of " + constant.getClass() + ")");
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_ldc(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), value));
    }
    return new ConstantCallSite(MethodHandles.constant(Object.class, constant));
  }

  public static CallSite bsm_static(Lookup lookup, String name, MethodType type, Class<?> owner, Object constant) throws NoSuchMethodException, IllegalAccessException {
    //System.out.println("bsm_static " + name + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      var method = lookup.findStatic(owner, name, type.appendParameterTypes(Object.class));
      var methodSpecies = new MethodSpecies(new Species(owner, null), name, type.toMethodDescriptorString(), linkage.parameters());
      var kiddyPoolClass = kiddyPoolClass(lookup, methodSpecies, method);
      var mh = insertArguments(method, type.parameterCount(), kiddyPoolClass);
      return new ConstantCallSite(mh);
    }
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_static(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), owner, value));
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + owner + " " + constant);
  }

  public static CallSite bsm_virtual(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchMethodException, IllegalAccessException {
    //System.out.println("bsm_virtual " + name + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      // the instance method is parametrized ?
      if (linkage.parameters() != null) {
        // an inlining cache for the receiver
        return new VirtualCallInliningCache(type, lookup,
            receiverClass -> {
              var parametric = receiverClass.getAnnotation(Parametric.class);
              var speciesLookup = privateSpeciesLookup(lookup, receiverClass);
              if (parametric != null) {
                // an inlining cache for the kiddyPool anchor constant pool ref
                var kiddyPoolRef = parametric.value();
                var inliningCache = new KiddyPoolRefInliningCache(type.appendParameterTypes(Object.class), speciesLookup, kiddyPoolRef,
                    classParameters -> {


                      // call the de-virtualized method with a kiddy pool created with the pair (species parameter + method parameter)
                      var species = new Species(receiverClass, classParameters);
                      var method = speciesLookup.findVirtual(receiverClass, name, type.dropParameterTypes(0, 1).appendParameterTypes(Object.class));
                      var methodSpecies = new MethodSpecies(species, name, type.toMethodDescriptorString(), linkage.parameters());
                      var kiddyPoolClass = kiddyPoolClass(speciesLookup, methodSpecies, method);
                      var target = insertArguments(method, type.parameterCount(), kiddyPoolClass);
                      return new ConstantCallSite(target);
                    });

                // access to the kiddy pool of the receiver
                var kiddyPoolGetter = speciesLookup.findGetter(receiverClass, "$kiddyPool", Object.class);
                var mh = filterArguments(inliningCache.dynamicInvoker(), type.parameterCount(), kiddyPoolGetter);
                var reorder = IntStream.concat(IntStream.range(0, type.parameterCount()), IntStream.of(0)).toArray();
                var target = permuteArguments(mh, type, reorder);
                return new ConstantCallSite(target);
              }

              // call the de-virtualized method with a kiddy pool created with no species parameters (only a method parameters)
              var species = new Species(receiverClass, null);
              var method = speciesLookup.findVirtual(receiverClass, name, type.dropParameterTypes(0, 1).appendParameterTypes(Object.class));
              var methodSpecies = new MethodSpecies(species, name, type.toMethodDescriptorString(), linkage.parameters());
              var kiddyPoolClass = kiddyPoolClass(speciesLookup, methodSpecies, method);
              var target = insertArguments(method, type.parameterCount(), kiddyPoolClass);
              return new ConstantCallSite(target);
            });
      }

      var method = lookup.findVirtual(type.parameterType(0), name, type.dropParameterTypes(0, 1));
      return new ConstantCallSite(method);
    }
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_virtual(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), value));
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_new(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchMethodException, IllegalAccessException {
    //System.out.println("bsm_new " + type + " " + constant + "(instance of " +constant.getClass() + ")");

    if (constant instanceof Linkage linkage) {
      var owner = type.returnType();
      var init = lookup.findConstructor(owner, type.changeReturnType(void.class).appendParameterTypes(Object.class));
      var kiddyPoolClass = kiddyPoolClass(lookup, new Species(owner, linkage.parameters()));
      var method = insertArguments(init, type.parameterCount(), kiddyPoolClass);
      return new ConstantCallSite(method);
    }
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_new(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), value));
    }

    throw new LinkageError(lookup + " " + name + " " + type + " " + constant);
  }

  public static CallSite bsm_new_array(Lookup lookup, String name, MethodType type, Object constant) {
    //System.out.println("bsm_new_array " + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      var specializedClass = ((Class<?>) linkage.parameters()).arrayType();
      var newArray = MethodHandles.arrayConstructor(specializedClass);
      var target = newArray.asType(type);
      return new ConstantCallSite(target);
    }
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_new_array(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), value));
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_init_default(Lookup lookup, String name, MethodType type, Object constant) {
    //System.out.println("bsm_init_default " + type + " " + constant);

    if (constant instanceof Restriction restriction) {
      var restrictionTypes = restriction.types();
      if (restrictionTypes.size() != 1) {
        throw new LinkageError(restriction + " has too many types, only one is required");
      }
      var defaultValue = Array.get(Array.newInstance(restrictionTypes.get(0), 1), 0);
      return new ConstantCallSite(MethodHandles.constant(type.returnType(), defaultValue));
    }
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_init_default(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), value));
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_put_value(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchFieldException, IllegalAccessException {
    //System.out.println("bsm_put_value_check " + type + " " + constant);

    if (constant instanceof Restriction restriction) {
      var restrictionTypes = restriction.types();
      if (restrictionTypes.size() != 1) {
        throw new LinkageError(restriction + " has too many types, only one is required");
      }
      var setter = lookup.findSetter(type.parameterType(0), name, type.parameterType(1));
      var target = setter.asType(methodType(type.returnType(), type.parameterType(0), restrictionTypes.get(0))).asType(type);
      return new ConstantCallSite(target);
    }
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_put_value(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), value));
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_method_restriction(Lookup lookup, String name, MethodType type, Object constant) {
    //System.out.println("bsm_method_restriction " + type + " " + constant);

    if (constant instanceof Restriction restriction) {
      var restrictionTypes = restriction.types();
      if (restrictionTypes.size() != type.parameterCount()) {
        throw new LinkageError(restriction + " types count != parameter count");
      }
      var empty = MethodHandles.empty(type);
      var target = empty.asType(methodType(type.returnType(), restriction.types())).asType(type);
      return new ConstantCallSite(target);
    }
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_method_restriction(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), value));
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

  public static Object bsm_condy(Lookup lookup, String name, Class<?> type, String action, Object... args) throws Throwable {
    //System.out.println("bsm_condy " + action + " " + Arrays.toString(args));

    return switch (action) {
      case "anchor" -> {
        var anchor = (Anchor) MethodHandles.classData(lookup, "_", Object.class);
        yield switch (args[0].toString()) {
          case "class" -> anchor.classParameters;
          case "method" -> anchor.methodParameters;
          default -> throw new AssertionError("unknown kind " + args[0]);
        };
      }
      case "list" -> List.of(args);
      case "list.get" -> ((List<?>) args[0]).get((int) args[1]);
      case "species" -> new Species((Class<?>) args[0], args.length == 1 ? null: args[1]);
      case "species.raw" -> ((Species) args[0]).raw();
      case "species.parameters" -> ((Species) args[0]).parameters();
      case "linkage" -> new Linkage(args[0]);
      case "mh" -> {
        yield insertArguments(
            lookup.findStatic((Class<?>) args[0], (String) args[1], (MethodType)args[2]),
            1, Arrays.stream(args).skip(3).toArray());
      }
      case "restriction" -> new Restriction(Arrays.stream(args).<Class<?>>map(o -> (Class<?>) o).toList());
      default -> throw new LinkageError("unknown method " + action + " " + Arrays.toString(args));
    };
  }

  public static Object bsm_raw_kiddy_pool(Lookup lookup, String name, Class<?> type) throws IllegalAccessException {
    //System.out.println("bsm_raw_kiddy_pool");
    var species = new Species(lookup.lookupClass(), null);
    return kiddyPoolClass(lookup, species);
  }

  public static Object bsm_raw_method_kiddy_pool(Lookup lookup, String name, Class<?> type, MethodHandle method) throws IllegalAccessException {
    //System.out.println("bsm_raw_method_kiddy_pool");
    var methodInfo = lookup.revealDirect(method);
    var species = new Species(lookup.lookupClass(), null);
    var methodSpecies = new MethodSpecies(species, methodInfo.getName(), methodInfo.getMethodType().toMethodDescriptorString(), null);
    return kiddyPoolClass(lookup, methodSpecies, method);
  }
}
