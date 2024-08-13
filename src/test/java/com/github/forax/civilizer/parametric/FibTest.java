package com.github.forax.civilizer.parametric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.forax.civilizer.prt.Location;
import com.github.forax.civilizer.prt.Location.Key;import com.github.forax.civilizer.prt.Parametric;
import com.github.forax.civilizer.prt.RT;import com.github.forax.civilizer.prt.TypeRestriction;
import java.util.Arrays;
import java.util.Objects;import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"ReturnValueIgnored", "UnusedVariable"})
public class FibTest {
  private static final ConcurrentHashMap<Integer, Object> SPECIALIZATION_CACHE = new ConcurrentHashMap<>();

  public static Object bsm(Location location, Integer value) {
    if (value == null) {
      value = 0;
    }
    var specialization = SPECIALIZATION_CACHE.get(value);
    if (specialization != null) {
      return specialization;
    }
    System.out.println("bsm specialize " + value);
    specialization = location.specialize(value);
    var result = SPECIALIZATION_CACHE.putIfAbsent(value, specialization);
    if (result != null) {
      return result;
    }
    return specialization;
  }


  @Parametric("P0")
  static class Fib {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/parametric/FibTest; 'bsm (Lcom/github/forax/civilizer/prt/Location;Ljava/lang/Integer;)Ljava/lang/Object;";
    private static final String $P1 = "anchor P0;";
    private static final String $P2 = "mh Ljava/lang/Math; 'subtractExact (II)I 1";
    private static final String $P3 = "mh Ljava/lang/Math; 'subtractExact (II)I 2";
    private static final String $P4 = "eval P2; P1;";
    private static final String $P5 = "eval P3; P1;";
    private static final String $P6 = "linkage P4;";
    private static final String $P7 = "linkage P5;";

    public int fib() {
      "P1".intern();
      var n = (int) RT.ldc();
      if (n < 2) {
        return n;
      }
      "P6".intern();
      var fib1 = new Fib();  // Fib<n-1>
      "P7".intern();
      var fib2 = new Fib();  // Fib<n-2>
      return Math.addExact(fib1.fib(), fib2.fib());
    }
  }

  private static final String $P0 = "linkage 11";

  @Test
  public void test() {
    "P0".intern();
    var fib = new Fib();  // Fib<11>

    assertEquals(89, fib.fib());
  }
}