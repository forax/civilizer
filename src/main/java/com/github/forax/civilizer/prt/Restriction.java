package com.github.forax.civilizer.prt;

import java.util.List;

import static java.util.stream.Collectors.joining;

public record Restriction(List<Class<?>> types) {
  public Restriction {
    types = List.copyOf(types);
  }

  @Override
  public String toString() {
    return types.stream()
        .map(Class::getSimpleName)
        .collect(joining(" ", "Restriction ", ""));
  }
}
