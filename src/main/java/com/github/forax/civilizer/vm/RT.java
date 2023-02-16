package com.github.forax.civilizer.vm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.RecordComponentVisitor;

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

import static java.lang.invoke.MethodHandles.constant;
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

public final class RT {
  private RT() {
    throw new AssertionError();
  }

  public static Object ldc() {
    throw new LinkageError("method calls to this method should be rewritten by the rewriter");
  }

  static Class<?> createKiddyPoolClass(Lookup lookup, Class<?> type, Anchor classData) {
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
      public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        return null;
      }

      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return null;
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if ((access & ACC_SYNTHETIC) != 0 && (name.startsWith("$P") || name.equals("$classData"))) {
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
      throw (IllegalAccessError) new IllegalAccessError().initCause(e);
    }
    return kiddyPoolLookup.lookupClass();
  }

  record Anchor(Object classParameters, Object methodParameters) {}

  private static Lookup privateSpeciesLookup(Lookup lookup, Class<?> raw) {
    try {
       return MethodHandles.privateLookupIn(raw, lookup);
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError) new IllegalAccessError("class " + lookup.lookupClass().getName() + " can not access to class " + raw.getName()).initCause(e);
    }
  }

  private static Class<?> callBSM(Lookup speciesLookup, Class<?> raw, String bsmPoolRef, Location location, Object parameters) {
    //System.out.println("callBSM " + bsmPoolRef + " " + location + " " + parameters);
    MethodHandle bsmPool;
    try {
      bsmPool = speciesLookup.findStatic(raw, "$" + bsmPoolRef, methodType(Object.class));
    } catch(NoSuchMethodException | IllegalAccessException e) {
      throw new LinkageError("constant pool constant $" + bsmPoolRef, e);
    }
    try {
      var bsm = (MethodHandle) (Object) bsmPool.invokeExact();
      return (Class<?>) bsm.invoke(location, parameters);
    } catch (Error e) {
      throw e;
    } catch(Throwable e) {
      throw new LinkageError("error while calling the bootstrap method of $" + bsmPoolRef, e);
    }
  }

  private static Class<?> classKiddyPoolClass(Lookup lookup, Class<?> raw, Object classParameters) {
    var parametric = raw.getAnnotation(Parametric.class);
    if (parametric == null) {
      throw new LinkageError(raw + " is not declared parametric");
    }
    var speciesLookup = privateSpeciesLookup(lookup, raw);
    var bsmPoolRef = parametric.value();
    var location = Location.classLocation(speciesLookup, raw);
    var kiddyPoolClass = callBSM(speciesLookup, raw, bsmPoolRef, location, classParameters);
    if (raw.isAnnotationPresent(SuperType.class)) {
      //System.err.println("Super type " + speciesRaw.getAnnotation(SuperType.class));
      SUPER_SPECIES_MAP.get(kiddyPoolClass);
    }
    return kiddyPoolClass;
  }

  private record SuperSpecies(HashMap<Class<?>, Class<?>> superMap) {}

  private static final ClassValue<SuperSpecies> SUPER_SPECIES_MAP = new ClassValue<>() {
    @Override
    protected SuperSpecies computeValue(Class<?> type) {
      var superType = type.getAnnotation(SuperType.class);
      if (superType == null) {
        // no specialized super types specified
        return new SuperSpecies(new HashMap<>());
      }
      var superRef = superType.value();
      var speciesLookup = privateSpeciesLookup(MethodHandles.lookup(), type);
      MethodHandle accessor;
      try {
        accessor = speciesLookup.findStatic(type, "$" + superRef, methodType(Object.class));
      } catch (NoSuchMethodException e) {
        throw (NoSuchMethodError) new NoSuchMethodError().initCause(e);
      } catch (IllegalAccessException e) {
        throw (IllegalAccessError) new IllegalAccessError().initCause(e);
      }
      Super superValue;
      try {
        superValue = (Super) (Object) accessor.invokeExact();
      } catch(Error e) {
        throw e;
      } catch (Throwable e) {
        throw new LinkageError("error while accessing super " + superRef, e);
      }
      var superMap = new HashMap<Class<?>, Class<?>>();
      for(var superSpecies: superValue.species()) {
        superMap.put(superSpecies.raw(), classKiddyPoolClass(speciesLookup, superSpecies.raw(), superSpecies.parameters()));
      }
      return new SuperSpecies(superMap);
    }
  };

  private static Class<?> superKiddyPool(Lookup lookup, Class<?> kiddyPoolClass, Class<?> superRaw) {
    var superSpecies = SUPER_SPECIES_MAP.get(kiddyPoolClass);
    var superKiddyPool = superSpecies.superMap.get(superRaw);
    if (superKiddyPool != null) {
      return superKiddyPool;
    }
    // no specified in SuperType, so find a default kiddyPool
    var parametric = superRaw.isAnnotationPresent(Parametric.class);
    if (parametric) {
      // instantiate a raw super
      var superRawLookup = privateSpeciesLookup(lookup, superRaw);
      superKiddyPool = classKiddyPoolClass(superRawLookup, superRaw, null);
    } else {
      // not parametric, superRaw is good enough
      superKiddyPool = superRaw;
    }
    // update cache
    superSpecies.superMap.put(superRaw, superKiddyPool);
    return superKiddyPool;
  }

  private static Class<?> methodKiddyPoolClass(Lookup lookup, Class<?> raw, Object classParameters, String methodName, String methodDescriptor, Object methodParameters, MethodHandle method) {
    var mhInfo = lookup.revealDirect(method);
    var reflected = mhInfo.reflectAs(Method.class, lookup);
    var parametric = reflected.getAnnotation(Parametric.class);
    if (parametric == null) {
      throw new LinkageError(reflected + " is not declared parametric");
    }
    var speciesLookup = privateSpeciesLookup(lookup, raw);
    var bsmPoolRef = parametric.value();
    var location = Location.methodLocation(speciesLookup, raw, classParameters, methodName, methodDescriptor);
    return callBSM(speciesLookup, raw, bsmPoolRef, location, methodParameters);
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

    @SuppressWarnings("ThisEscapedInObjectConstruction")
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

  private static final class KiddyPoolSuperInliningCache extends MutableCallSite {
    private static final MethodHandle SLOW_PATH, POINTER_CHECK;
    static {
      var lookup = MethodHandles.lookup();
      try {
        SLOW_PATH = lookup.findVirtual(KiddyPoolSuperInliningCache.class, "slowPath", methodType(MethodHandle.class, Object.class));
        POINTER_CHECK = lookup.findStatic(KiddyPoolSuperInliningCache.class, "pointerCheck", methodType(boolean.class, Object.class, Object.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private final Lookup lookup;
    private final Class<?> superRaw;

    @SuppressWarnings("ThisEscapedInObjectConstruction")
    private KiddyPoolSuperInliningCache(MethodType type, Lookup lookup, Class<?> superRaw) {
      super(type);
      this.lookup = lookup;
      this.superRaw = superRaw;
      var combiner = dropArguments(SLOW_PATH.bindTo(this), 0, type.parameterList().subList(0, type.parameterCount() - 1));
      setTarget(foldArguments(exactInvoker(type), combiner));
    }

    private static boolean pointerCheck(Object o, Object o2) {
      return o == o2;
    }

    private MethodHandle slowPath(Object kiddyPool) {
      var kiddyPoolClass = (Class<?>) kiddyPool;
      var superKiddyPool = superKiddyPool(lookup, kiddyPoolClass, superRaw);

      var target = dropArguments(constant(Object.class, superKiddyPool), 0, Object.class);

      var test = POINTER_CHECK.bindTo(kiddyPool);
      var guard = guardWithTest(test, target, new KiddyPoolSuperInliningCache(type(), lookup, superRaw).dynamicInvoker());
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

    @SuppressWarnings("ThisEscapedInObjectConstruction")
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

  @SuppressWarnings({"unused", "WeakerAccess"})  // used by reflection
  public static CallSite bsm_ldc(Lookup lookup, String name, MethodType type, Object constant) {
    //System.out.println("bsm_ldc " + constant + " (instance of " + constant.getClass() + ")");
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_ldc(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), value));
    }
    return new ConstantCallSite(constant(Object.class, constant));
  }

  @SuppressWarnings({"unused", "WeakerAccess"})  // used by reflection
  public static CallSite bsm_static(Lookup lookup, String name, MethodType type, Class<?> owner, Object constant) throws NoSuchMethodException, IllegalAccessException {
    //System.out.println("bsm_static " + name + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      var method = lookup.findStatic(owner, name, type.appendParameterTypes(Object.class));
      var kiddyPoolClass = methodKiddyPoolClass(lookup, owner, null, name, type.toMethodDescriptorString(), linkage.parameters(), method);
      var mh = insertArguments(method, type.parameterCount(), kiddyPoolClass);
      return new ConstantCallSite(mh);
    }
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_static(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), owner, value));
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + owner + " " + constant);
  }

  @SuppressWarnings({"unused", "WeakerAccess"})  // used by reflection
  public static CallSite bsm_virtual(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchMethodException, IllegalAccessException {
    //System.out.println("bsm_virtual " + name + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      // is the instance method parametrized ?
      if (linkage.parameters() != null) {
        // an inlining cache for the receiver
        return new VirtualCallInliningCache(type, lookup,
            receiverClass -> {
              var parametric = receiverClass.getAnnotation(Parametric.class);
              var speciesLookup = privateSpeciesLookup(lookup, receiverClass);
              if (parametric != null) {
                // an inlining cache for the kiddyPool $classData constant pool ref
                var inliningCache = new KiddyPoolRefInliningCache(type.appendParameterTypes(Object.class), speciesLookup, "classData",
                    anchor -> {
                      var classParameters = ((Anchor) anchor).classParameters;

                      // call the de-virtualized method with a kiddy pool created with the pair (species parameter + method parameter)
                      var method = speciesLookup.findVirtual(receiverClass, name, type.dropParameterTypes(0, 1).appendParameterTypes(Object.class));
                      var kiddyPoolClass = methodKiddyPoolClass(speciesLookup, receiverClass, classParameters, name, type.toMethodDescriptorString(), linkage.parameters(), method);
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
              var method = speciesLookup.findVirtual(receiverClass, name, type.dropParameterTypes(0, 1).appendParameterTypes(Object.class));
              var kiddyPoolClass = methodKiddyPoolClass(speciesLookup, receiverClass, null, name, type.toMethodDescriptorString(), linkage.parameters(), method);
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

  @SuppressWarnings({"unused", "WeakerAccess"})  // used by reflection
  public static CallSite bsm_new(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchMethodException, IllegalAccessException {
    //System.out.println("bsm_new " + type + " " + constant + "(instance of " +constant.getClass() + ")");

    if (constant instanceof Linkage linkage) {
      var owner = type.returnType();
      var init = lookup.findConstructor(owner, type.changeReturnType(void.class).appendParameterTypes(Object.class));
      var kiddyPoolClass = classKiddyPoolClass(lookup, owner, linkage.parameters());
      var method = insertArguments(init, type.parameterCount(), kiddyPoolClass);
      return new ConstantCallSite(method);
    }
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_new(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), value));
    }

    throw new LinkageError(lookup + " " + name + " " + type + " " + constant);
  }

  @SuppressWarnings({"unused", "WeakerAccess"})  // used by reflection
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

  @SuppressWarnings({"unused", "WeakerAccess"})  // used by reflection
  public static CallSite bsm_init_default(Lookup lookup, String name, MethodType type, Object constant) {
    //System.out.println("bsm_init_default " + type + " " + constant);

    if (constant instanceof Restriction restriction) {
      var restrictionTypes = restriction.types();
      if (restrictionTypes.size() != 1) {
        throw new LinkageError(restriction + " has too many types, only one is required");
      }
      var defaultValue = Array.get(Array.newInstance(restrictionTypes.get(0), 1), 0);
      return new ConstantCallSite(constant(type.returnType(), defaultValue));
    }
    if (constant instanceof String kiddyPoolRef) {
      return new KiddyPoolRefInliningCache(type, lookup, kiddyPoolRef,
          value -> bsm_init_default(lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()), value));
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  @SuppressWarnings({"unused", "WeakerAccess"})  // used by reflection
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

  @SuppressWarnings({"unused", "WeakerAccess"})  // used by reflection
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

  @SuppressWarnings("unused") // used by reflection
  public static Object bsm_type(Lookup lookup, String name, Class<?> type) {
    //System.out.println("bsm_type " + name);
    // primitiveClass() is not called directly to avoid to have java/lang/Class in the descriptors
    return ConstantBootstraps.primitiveClass(lookup, name, Class.class);
  }

  @SuppressWarnings("unused") // used by reflection
  public static Object bsm_qtype(Lookup lookup, String name, Class<?> type, Class<?> primaryType) {
    //System.out.println("bsm_qtype " + primaryType.getName());
    return com.github.forax.civilizer.runtime.RT.asSecondaryType(primaryType);
  }

  @SuppressWarnings("unused") // used by reflection
  public static Object bsm_condy(Lookup lookup, String name, Class<?> type, String action, Object... args) throws Throwable {
    // System.err.println("bsm_condy " + name + " " + action + " " + Arrays.toString(args));

    try {
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
          var target = lookup.findStatic((Class<?>) args[0], (String) args[1], (MethodType) args[2]);
          yield insertArguments(target, target.type().parameterCount() - (args.length - 3), Arrays.stream(args).skip(3).toArray());
        }
        case "restriction" -> new Restriction(Arrays.stream(args).<Class<?>>map(o -> (Class<?>) o).toList());
        case "super" -> new Super(Arrays.stream(args).map(o -> (Species) o).toList());
        default -> throw new LinkageError("unknown method " + action + " " + Arrays.toString(args));
      };
    } catch(RuntimeException e) {
      throw new LinkageError("runtime error while computing constant " + name + ": " + action + " " + Arrays.toString(args), e);
    }
  }

  @SuppressWarnings("unused") // used by reflection
  public static Object bsm_raw_kiddy_pool(Lookup lookup, String name, Class<?> type) {
    //System.out.println("bsm_raw_kiddy_pool");
    return classKiddyPoolClass(lookup, lookup.lookupClass(), null);
  }

  @SuppressWarnings("unused") // used by reflection
  public static Object bsm_raw_method_kiddy_pool(Lookup lookup, String name, Class<?> type, MethodHandle method) {
    //System.out.println("bsm_raw_method_kiddy_pool");
    var methodInfo = lookup.revealDirect(method);
    return methodKiddyPoolClass(lookup, lookup.lookupClass(), null, methodInfo.getName(), methodInfo.getMethodType().toMethodDescriptorString(), null, method);
  }

  @SuppressWarnings("unused") // used by reflection
  public static CallSite bsm_super_kiddy_pool(Lookup lookup, String name, MethodType type) {
    //System.out.println("bsm_super_kiddy_pool " + lookup + " " + name + " " + type);

    var superclass = lookup.lookupClass().getSuperclass();
    return new KiddyPoolSuperInliningCache(methodType(Object.class, Object.class), lookup, superclass);
  }

  @SuppressWarnings("unused") // used by reflection
  public static CallSite bsm_interface_kiddy_pool(Lookup lookup, String name, MethodType type) {
    //System.out.println("bsm_interface_kiddy_pool " + lookup + " " + name + " " + type);

    var rawSuper = lookup.lookupClass();
    return new VirtualCallInliningCache(type, lookup,
        receiverClass -> {
          var receiverLookup = privateSpeciesLookup(lookup, receiverClass);
          var isParametric = receiverClass.isAnnotationPresent(Parametric.class);
          if (isParametric) {
             var inliningCache = new KiddyPoolSuperInliningCache(methodType(Object.class, Object.class), receiverLookup, rawSuper);
             var kiddyPoolGetter = receiverLookup.findGetter(receiverClass, "$kiddyPool", Object.class)
                 .asType(methodType(Object.class, type.parameterType(0)));
             var target = filterArguments(inliningCache.dynamicInvoker(), 0, kiddyPoolGetter);
             return new ConstantCallSite(target);
          }

          var superKiddyPool = superKiddyPool(receiverLookup, receiverClass, rawSuper);
          var target = dropArguments(constant(Object.class, superKiddyPool), 0, type.parameterType(0));
          return new ConstantCallSite(target);
        });
  }
}
