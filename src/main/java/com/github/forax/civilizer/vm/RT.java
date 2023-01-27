package com.github.forax.civilizer.vm;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;

public class RT {
  public static CallSite bsm_static(Lookup lookup, String name, MethodType type, Class<?> owner, Object constant) {
    throw new LinkageError(lookup + " " + name + " " + type+ " " + owner + " " + constant);
  }

  public static CallSite bsm_virtual(Lookup lookup, String name, MethodType type, Object constant) {
    throw new LinkageError(lookup + " " + name + " " + type+ " " + constant);
  }

  public static CallSite bsm_new(Lookup lookup, String name, MethodType type, Object classConstant, Object methodConstant) {
    throw new LinkageError(lookup + " " + name + " " + type+ " " + classConstant + " " + methodConstant);
  }

  public static Object bsm_condy(Lookup lookup, String name, Class<?> type, String action, Object... args) throws IllegalAccessException {
    System.out.println("bsm_condy " + action + " " + Arrays.toString(args));
    return switch (action) {
      case "classData" -> MethodHandles.classData(lookup, "_", Object.class);
      case "list.of" -> List.of(args);
      case "list.get" -> ((List<?>) args[0]).get((int) args[1]);
      case "species" -> new Species((Class<?>) args[0], args.length == 1 ? null: args[1]);
      case "methodSpecies" -> new MethodSpecies((Species) args[0], Arrays.stream(args).skip(1).map(Species.class::cast).toList());
      default -> throw new LinkageError("unknown method " + Arrays.toString(args));
    };
  }
}
