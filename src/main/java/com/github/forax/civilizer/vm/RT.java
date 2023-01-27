package com.github.forax.civilizer.vm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodHandles.Lookup.ClassOption;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static java.lang.invoke.MethodType.methodType;
import static org.objectweb.asm.Opcodes.*;

public class RT {
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

  private static final HashMap<Species, Class<?>> KIDDY_POOL_CACHE = new HashMap<>();

  private static Class<?> kiddyPoolClass(Lookup lookup, Species species) throws IllegalAccessException {
    return KIDDY_POOL_CACHE.computeIfAbsent(species, sp -> {
      Lookup speciesLookup;
      try {
        speciesLookup = MethodHandles.privateLookupIn(sp.raw(), lookup);
      } catch (IllegalAccessException e) {
        throw (IllegalAccessError) new IllegalAccessError().initCause(e);
      }
      return createKiddyPoolClass(speciesLookup, sp.raw(), sp.parameters());
    });
  }

  private static class InliningCache extends MutableCallSite {
    private static final MethodHandle SLOW_PATH;
    static {
      var lookup = MethodHandles.lookup();
      try {
        SLOW_PATH = lookup.findVirtual(InliningCache.class, "slowPath", methodType(MethodHandle.class, Object.class));
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

    private MethodHandle slowPath(Object kiddyPool) throws Throwable {
      var accessor = lookup.findStatic((Class<?>) kiddyPool, "$" + kiddyPoolRef, methodType(Object.class));
      var value = accessor.invokeExact();

      var target = ((CallSite) bsm.invoke(value)).dynamicInvoker();
      target = MethodHandles.dropArguments(target, type().parameterCount() - 1, Object.class);
      return target;
    }
  }

  private static final MethodHandle BSM_NEW;
  static {
    var lookup = MethodHandles.lookup();
    try {
      BSM_NEW = lookup.findStatic(RT.class, "bsm_new", methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  public static CallSite bsm_static(Lookup lookup, String name, MethodType type, Class<?> owner, Object constant) {
    throw new LinkageError(lookup + " " + name + " " + type+ " " + owner + " " + constant);
  }

  public static CallSite bsm_virtual(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchMethodException, IllegalAccessException {
    System.out.println("bsm_virtual " + name + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      var method = lookup.findVirtual(type.parameterType(0), name, type.dropParameterTypes(0, 1));
      // specialize descriptor
      var specializedType = linkage.toMethodType().insertParameterTypes(0, linkage.owner().raw());
      var target = method.asType(specializedType).asType(type);
      return new ConstantCallSite(target);
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_new(Lookup lookup, String name, MethodType type, Object constant) throws NoSuchMethodException, IllegalAccessException {
    System.out.println("bsm_new " + type + " " + constant);

    if (constant instanceof Linkage linkage) {
      var init = lookup.findConstructor(type.returnType(), type.changeReturnType(void.class).appendParameterTypes(Object.class));
      var kiddyPoolClass = kiddyPoolClass(lookup, linkage.owner());
      var target = MethodHandles.insertArguments(init, type.parameterCount(), kiddyPoolClass);
      return new ConstantCallSite(target);
    }
    if (constant instanceof String kiddyPoolRef) {
      var bsmNew = MethodHandles.insertArguments(BSM_NEW, 0, lookup, name, type.dropParameterTypes(type.parameterCount() - 1, type.parameterCount()));
      return new InliningCache(type, lookup, bsmNew, kiddyPoolRef);
    }

    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
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
    System.out.println("bsm_condy " + action + " " + Arrays.toString(args));

    return switch (action) {
      case "classData" -> MethodHandles.classData(lookup, "_", Object.class);
      case "list.of" -> List.of(args);
      case "list.get" -> ((List<?>) args[0]).get((int) args[1]);
      case "species" -> new Species((Class<?>) args[0], args.length == 1 ? null: args[1]);
      case "linkage" -> new Linkage(asSpecies(args[0]), asSpecies(args[1]), Arrays.stream(args).skip(2).map(RT::asSpecies).toList());
      default -> throw new LinkageError("unknown method " + Arrays.toString(args));
    };
  }
}
