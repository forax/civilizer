package com.github.forax.civilizer.parametric;

import com.github.forax.civilizer.prt.Parametric;
import com.github.forax.civilizer.prt.RT;
import com.github.forax.civilizer.value.Complex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

@SuppressWarnings({"ReturnValueIgnored", "UnusedVariable"})
public class NullRestrictedArrayTest {
  @Parametric("P1")
  static class ArrayCreation<E> {
    private static final String $P0 = "list Ljava/lang/Object;";
    private static final String $P1 = "mh Lcom/github/forax/civilizer/prt/JDK; 'erase (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; P0;";
    private static final String $P2 = "anchor P1;";
    private static final String $P3 = "list.get P2; 0";
    private static final String $P4 = "list P3;";
    private static final String $P5 = "linkage P4;";

    public Object[] create(int length) {
      "P5".intern();
      return RT.newFlattableArray(length);
    }
  }

  private static final String $P_string_0 = "list Ljava/lang/String;";
  private static final String $P_string_1 = "linkage P_string_0;";

  @Test
  public void objectArray() {
    "P_string_1".intern();
    var arrayCreation = new ArrayCreation<String>();

    var array = arrayCreation.create(16);
    array[0] = null;
    assertSame(Object[].class, array.getClass());
  }

  private static final String $P_complex_0 = "list Lcom/github/forax/civilizer/value/Complex;";
  private static final String $P_complex_1 = "linkage P_complex_0;";

  @Test
  public void nullRestrictedArray() {
    "P_complex_1".intern();
    var arrayCreation = new ArrayCreation<Complex>();

    var array = arrayCreation.create(16);
    Assertions.assertThrows(NullPointerException.class, () -> array[0] = null);
  }
}
