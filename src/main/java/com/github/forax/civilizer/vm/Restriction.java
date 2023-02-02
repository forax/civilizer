package com.github.forax.civilizer.vm;

import java.util.List;
import java.util.stream.Collectors;

public record Restriction(List<Class<?>> restrictions) {
  public Restriction {
    restrictions = List.copyOf(restrictions);
  }

  @Override
  public String toString() {
    return restrictions.stream()
        .map(Class::getSimpleName)
        .collect(Collectors.joining(" ", "Restriction ", ""));
  }
}
