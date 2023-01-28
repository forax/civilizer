package com.github.forax.civilizer.vm;

import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.joining;

public record Linkage(Species owner, Species returnType, List<Species> parameters) {
  public Linkage {
    Objects.requireNonNull(owner);
    Objects.requireNonNull(returnType);
    parameters = List.copyOf(parameters);
  }

  public MethodType toMethodType() {
    return MethodType.methodType(returnType.raw(), parameters.stream().<Class<?>>map(Species::raw).toList());
  }

  @Override
  public String toString() {
    return owner + "." + parameters.stream().map(Species::toString).collect(joining(",", "(", ")")) + returnType;
  }
}
