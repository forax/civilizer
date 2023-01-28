package com.github.forax.civilizer.species;

import com.github.forax.civilizer.demo.Complex;
import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.TypeRestriction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TypeRestrictionTest {
  @Parametric
  static class Holder<T> {
    private static final String $P0 = "species Ljava/lang/Object;";
    private static final String $P1 = "list.of P0;";
    private static final String $KP0 = "classData P1;";
    private static final String $KP1 = "list.get KP0; 0";

    @TypeRestriction("KP1")
    private T t;

    Holder() { }

    T get() { return t; }
  }

  @Test
  public void initDefaultIdentity() {
    class Test {
      private static final String $P0 = "species Ljava/lang/String;";
      private static final String $P1 = "list.of P0;";
      private static final String $P2 = "species Lcom/github/forax/civilizer/species/TypeRestrictionTest$Holder; P1;";
      private static final String $P3 = "linkage P2; P2;";
      private static final String $P4 = "linkage P2; P0;";

      public static void initDefault() {
        "P3".intern();
        var holder = new Holder<String>();
        "P4".intern();
        var element = holder.get();
        assertNull(element);
      }
    }
    Test.initDefault();
  }

  @Test
  public void initDefaultZeroDefaultValue() {
    class Test {
      private static final String $P0 = "species Qcom/github/forax/civilizer/demo/Complex;";
      private static final String $P1 = "list.of P0;";
      private static final String $P2 = "species Lcom/github/forax/civilizer/species/TypeRestrictionTest$Holder; P1;";
      private static final String $P3 = "linkage P2; P2;";
      private static final String $P4 = "linkage P2; P0;";

      public static void initDefault() {
        "P3".intern();
        var holder = new Holder<Complex>();
        "P4".intern();
        var element = holder.get();
        assertEquals(Complex.of(0.0, 0.0), element);
      }
    }
    Test.initDefault();
  }
}
