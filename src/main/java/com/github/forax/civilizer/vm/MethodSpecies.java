package com.github.forax.civilizer.vm;

import java.lang.invoke.MethodType;
import java.util.List;

public record MethodSpecies(Species returnType, List<Species> parameters) {
  public MethodSpecies {
    parameters = List.copyOf(parameters);
  }

  public MethodType toMethodType() {
    return MethodType.methodType(returnType.raw(), parameters.stream().<Class<?>>map(Species::raw).toList());
  }
}
