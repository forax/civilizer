package com.github.forax.civilizer.prt;

import java.util.Objects;

public record Species(Class<?> raw, /*@Nullable*/ Object parameters) {
  public Species {
    //Objects.requireIdentity(raw);
  }

  @Override
  public String toString() {
    return "Species " + raw.getSimpleName() + (parameters == null? "": parameters);
  }
}
