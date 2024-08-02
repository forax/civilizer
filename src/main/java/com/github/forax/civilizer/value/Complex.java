package com.github.forax.civilizer.value;

import com.github.forax.civilizer.vrt.Value;
import com.github.forax.civilizer.vrt.ZeroDefault;

import java.util.Objects;

/*
public @ZeroDefault @Value record Complex(double re, double im) {
  public static Complex of(double re, double im) {
    return new Complex(re, im);
  }
}
*/

public @ZeroDefault @Value final class Complex {private final double re;
  private final double im;

  public Complex(double re, double im) {
    this.re=re;
    this.im=im;
  }

  public static Complex of(double re, double im) {
    return new Complex(re, im);
  }

  public double re(){ return re; }

  public double im(){ return im; }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Complex that &&
        Double.doubleToLongBits(this.re)==Double.doubleToLongBits(that.re) &&
        Double.doubleToLongBits(this.im)==Double.doubleToLongBits(that.im);
  }

  @Override
  public int hashCode() {
    return Objects.hash(re, im);
  }

  @Override
  public String toString() {
    return "Complex[re=" + re + ", " +  "im=" + im + ']';
  }
}
