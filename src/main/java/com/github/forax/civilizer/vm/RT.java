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

  public static Object erase(/*List<Species>*/Object parameters, /*List<Species>*/Object defaultSpecies) {
    var parameterList = parameters == null ? null: ((List<?>) parameters).stream().map(o -> (Species) o).toList();
    var defaultSpeciesList = ((List<?>) defaultSpecies).stream().map(o -> (Species) o).toList();
    if (parameters == null) {
      return defaultSpecies;
    }
    if (parameterList.size() != defaultSpeciesList.size()) {
      throw new LinkageError("instantiation arguments " + parameters + " and default arguments " + defaultSpeciesList + " have no the same size");
    }
    return IntStream.range(0, parameterList.size())
        .mapToObj(i -> {
          var species = parameterList.get(i);
          return com.github.forax.civilizer.runtime.RT.isSecondaryType(species.raw()) ? species : defaultSpeciesList.get(i);
        }).toList();
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

  private record Anchor(Object parameter) {}

  private record MethodSpecies(Species species, Object parameters, String name, String descriptor) {}

  // caches should be concurrent and maintain a weak ref on the class
  private static final HashMap<Species, Class<?>> KIDDY_POOL_CACHE = new HashMap<>();
  private static final HashMap<MethodSpecies, Class<?>> KIDDY_POOL_METH_CACHE = new HashMap<>();

  private static Lookup privateSpeciesLookup(Lookup lookup, Species species) {
    try {
       return MethodHandles.privateLookupIn(species.raw(), lookup);
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError) new IllegalAccessError().initCause(e);
    }
  }

  private static Object callBSM(Lookup speciesLookup, Species species, String bsmPoolRef, Object parameters) {
    if (bsmPoolRef.isEmpty()) {  // useful for debugging
      return parameters;
    }
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
    var speciesLookup = privateSpeciesLookup(lookup, species);
    var bsmPoolRef = parametric.value();
    var parameters = callBSM(speciesLookup, species, bsmPoolRef, species.parameters());
    var keySpecies = new Species(species.raw(), parameters);
    return KIDDY_POOL_CACHE.computeIfAbsent(keySpecies, sp -> {
      return createKiddyPoolClass(speciesLookup, sp.raw(), new Anchor(sp.parameters()));
    });
  }

  private static Class<?> kiddyPoolClass(Lookup lookup, MethodSpecies methodSpecies, MethodHandle method) throws IllegalAccessException {
    var mhInfo = lookup.revealDirect(method);
    var reflected = mhInfo.reflectAs(Method.class, lookup);
    var parametric = reflected.getAnnotation(Parametric.class);
    if (parametric == null) {
      throw new LinkageError(reflected + " is not declared parametric");
    }
    var speciesLookup = privateSpeciesLookup(lookup, methodSpecies.species);
    var bsmPoolRef = parametric.value();
    var parameters = callBSM(speciesLookup, methodSpecies.species, bsmPoolRef, methodSpecies.parameters);
    var keyMethodSpecies = new MethodSpecies(methodSpecies.species, parameters, methodSpecies.name, methodSpecies.descriptor);
    return KIDDY_POOL_METH_CACHE.computeIfAbsent(keyMethodSpecies, msp -> {
      return createKiddyPoolClass(speciesLookup, msp.species.raw(), new Anchor(msp.parameters));
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

  private static final MethodHandle BSM_LDC, BSM_STATIC, BSM_NEW, BSM_NEW_ARRAY, BSM_INIT_DEFAULT, BSM_PUT_VALUE, BSM_METHOD_RESTRICTION;
  static {
    var lookup = MethodHandles.lookup();
    try {
      BSM_LDC = lookup.findStatic(RT.class, "bsm_ldc", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
      BSM_STATIC = lookup.findStatic(RT.class, "bsm_static", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Class.class, Object.class));
      BSM_NEW = lookup.findStatic(RT.class, "bsm_new", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
      BSM_NEW_ARRAY = lookup.findStatic(RT.class, "bsm_new_array", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
      BSM_INIT_DEFAULT = lookup.findStatic(RT.class, "bsm_init_default", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
      BSM_PUT_VALUE = lookup.findStatic(RT.class, "bsm_put_value", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
      BSM_METHOD_RESTRICTION = lookup.findStatic(RT.class, "bsm_method_restriction", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
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
    //System.out.println("bsm_static " + name + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      var method = lookup.findStatic(owner, name, type.appendParameterTypes(Object.class));
      var methodSpecies = new MethodSpecies(new Species(linkage.owner().raw(), null), linkage.parameters(), name, type.toMethodDescriptorString());
      var kiddyPoolClass = kiddyPoolClass(lookup, methodSpecies, method);
      var mh = MethodHandles.insertArguments(method, type.parameterCount(), kiddyPoolClass);
      return new ConstantCallSite(mh);
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
      return new ConstantCallSite(method);
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_new(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchMethodException, IllegalAccessException {
    //System.out.println("bsm_new " + type + " " + constant + "(instance of " +constant.getClass() + ")");

    if (constant instanceof Linkage linkage) {
      var owner = type.returnType();
      var init = lookup.findConstructor(owner, type.changeReturnType(void.class).appendParameterTypes(Object.class));
      var kiddyPoolClass = kiddyPoolClass(lookup, linkage.owner());
      var method = MethodHandles.insertArguments(init, type.parameterCount(), kiddyPoolClass);
      return new ConstantCallSite(method);
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
    //System.out.println("bsm_init_default " + type + " " + constant);

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

  public static CallSite bsm_put_value(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchFieldException, IllegalAccessException {
    //System.out.println("bsm_put_value_check " + type + " " + constant);

    if (constant instanceof Species species) {
      var setter = lookup.findSetter(type.parameterType(0), name, type.parameterType(1));
      var target = setter.asType(methodType(type.returnType(), type.parameterType(0), species.raw())).asType(type);
      return new ConstantCallSite(target);
    }
    if (constant instanceof String kiddyPoolRef) {
      var bsmInitDefault = MethodHandles.insertArguments(BSM_PUT_VALUE, 0, lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()));
      return new InliningCache(type, lookup, bsmInitDefault, kiddyPoolRef);
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_method_restriction(Lookup lookup, String name, MethodType type, Object constant) {
    System.out.println("bsm_method_restriction " + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      var empty = MethodHandles.empty(type);
      var target = empty.asType(linkage.toMethodType()).asType(type);
      return new ConstantCallSite(target);
    }
    if (constant instanceof String kiddyPoolRef) {
      var bsmInitDefault = MethodHandles.insertArguments(BSM_METHOD_RESTRICTION, 0, lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()));
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

  public static Object bsm_condy(Lookup lookup, String name, Class<?> type, String action, Object... args) throws Throwable {
    //System.out.println("bsm_condy " + action + " " + Arrays.toString(args));

    return switch (action) {
      case "anchor" -> {
        var anchor = (Anchor) MethodHandles.classData(lookup, "_", Object.class);
        yield anchor.parameter;
      }
      case "list.of" -> List.of(args);
      case "list.get" -> ((List<?>) args[0]).get((int) args[1]);
      case "species" -> new Species((Class<?>) args[0], args.length == 1 ? null: args[1]);
      case "linkage" -> new Linkage(asSpecies(args[0]), null, asSpecies(args[1]), Arrays.stream(args).skip(2).map(RT::asSpecies).toList());
      case "linkaze" -> new Linkage(asSpecies(args[0]), args[1], asSpecies(args[2]), Arrays.stream(args).skip(3).map(RT::asSpecies).toList());
      case "mh" -> {
        yield MethodHandles.insertArguments(
            lookup.findStatic((Class<?>) args[0], (String) args[1], (MethodType)args[2]),
            1, Arrays.stream(args).skip(3).toArray());
      }
      default -> throw new LinkageError("unknown method " + action + " " + Arrays.toString(args));
    };
  }

  public static Object bsm_raw_kiddy_pool(Lookup lookup, String name, Class<?> type) throws IllegalAccessException {
    //System.out.println("bsm_raw_kiddy_pool");
    return kiddyPoolClass(lookup, new Species(lookup.lookupClass(), null));
  }
}
