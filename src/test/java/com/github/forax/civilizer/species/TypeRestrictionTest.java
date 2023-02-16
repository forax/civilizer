package com.github.forax.civilizer.species;

import com.github.forax.civilizer.demo.Complex;
import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.TypeRestriction;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TypeRestrictionTest {
  @Parametric("P1")
  static class Holder<T> {
    private static final String $P0 = "list Ljava/lang/Object;";
    private static final String $P1 = "mh Lcom/github/forax/civilizer/vm/JDK; 'erase (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; P0;";
    private static final String $PA0 = "anchor P1;";
    private static final String $PA1 = "list.get PA0; 0";
    private static final String $PA2 = "restriction PA1;";

    @TypeRestriction("PA2")
    private T t;

    Holder() { }

    T get() { return t; }

    void set(Object value) {
      this.t = (T) value;  // should check type restriction
    }
  }

  @Test
  public void initIdentityDefault() {
    class Test {
      private static final String $P0 = "list Ljava/lang/String;";
      private static final String $P1 = "linkage P0;";

      public static void initDefault() {
        "P1".intern();
        var holder = new Holder<String>();

        var element = holder.get();
        assertNull(element);
      }
    }
    Test.initDefault();
  }

  @Test
  public void initZeroDefaultValue() {
    class Test {
      private static final String $P0 = "list Qcom/github/forax/civilizer/demo/Complex;";
      private static final String $P1 = "linkage P0;";

      public static void initDefault() {
        "P1".intern();
        var holder = new Holder<Complex>();

        var element = holder.get();
        assertEquals(Complex.of(0.0, 0.0), element);
      }
    }
    Test.initDefault();
  }

  @Test
  public void putFieldZeroDefaultValue() {
    class Test {
      private static final String $P0 = "list Qcom/github/forax/civilizer/demo/Complex;";
      private static final String $P1 = "linkage P0;";

      public static void initDefault() {
        "P1".intern();
        var holder = new Holder<Complex>();

        assertThrows(NullPointerException.class, () -> holder.set(null));
      }
    }
    Test.initDefault();
  }
}
