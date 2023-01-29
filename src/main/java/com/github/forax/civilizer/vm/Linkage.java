package com.github.forax.civilizer.vm;

import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.joining;

public record Linkage(Species owner, /*@Nullable*/Object parameters, Species returnType, List<Species> parameterTypes) {
  public Linkage {
    Objects.requireNonNull(owner);
    Objects.requireNonNull(returnType);
    parameterTypes = List.copyOf(parameterTypes);
  }

  public MethodType toMethodType() {
    return MethodType.methodType(returnType.raw(), parameterTypes.stream().<Class<?>>map(Species::raw).toList());
  }

  @Override
  public String toString() {
    return owner + "."
        + (parameters == null ? "" : "<" + parameters + ">")
        + parameterTypes.stream().map(Species::toString).collect(joining(",", "(", ")")) + returnType;
  }
}
