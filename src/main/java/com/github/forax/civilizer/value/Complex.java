package com.github.forax.civilizer.value;

import com.github.forax.civilizer.vrt.Value;
import com.github.forax.civilizer.vrt.ZeroDefault;

public @ZeroDefault @Value record Complex(double re, double im) {
  public static Complex of(double re, double im) {
    return new Complex(re, im);
  }
}
