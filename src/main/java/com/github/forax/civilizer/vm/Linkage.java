package com.github.forax.civilizer.vm;

import java.util.Objects;

import static java.util.stream.Collectors.joining;

public record Linkage(Object parameters) {
  public Linkage {
    Objects.requireNonNull(parameters, "parameters is null");
  }

  @Override
  public String toString() {
    return "Linkage " + parameters;
  }
}
