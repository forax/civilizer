package com.github.forax.civilizer.vm;

import java.util.Objects;

public record Species(Class<?> raw, /*@Nullable*/ Object parameters) {
  public Species {
    Objects.requireIdentity(raw);
  }

  @Override
  public String toString() {
    return raw.getSimpleName() + (parameters == null? "": parameters);
  }
}
