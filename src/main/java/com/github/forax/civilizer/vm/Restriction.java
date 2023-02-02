package com.github.forax.civilizer.vm;

import java.util.List;
import java.util.stream.Collectors;

public record Restriction(List<Class<?>> types) {
  public Restriction {
    types = List.copyOf(types);
  }

  @Override
  public String toString() {
    return types.stream()
        .map(Class::getSimpleName)
        .collect(Collectors.joining(" ", "Restriction ", ""));
  }
}
