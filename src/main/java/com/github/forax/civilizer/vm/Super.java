package com.github.forax.civilizer.vm;

import java.util.List;

import static java.util.stream.Collectors.joining;

public record Super(List<Species> species) {
  public Super {
    species = List.copyOf(species);
  }

  @Override
  public String toString() {
    return species.stream()
        .map(Species::toString)
        .collect(joining(" ", "Super ", ""));
  }
}
