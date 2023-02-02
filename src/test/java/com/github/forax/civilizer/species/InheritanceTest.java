package com.github.forax.civilizer.species;

import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.RT;
import com.github.forax.civilizer.vm.Species;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InheritanceTest {
  @Parametric("P0")
  static class Holder<T> {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/vm/RT; \"identity\" (Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String $P1 = "mh Lcom/github/forax/civilizer/vm/RT; \"identity\" (Ljava/lang/Object;)Ljava/lang/Object;";

    private static final String $KP0 = "anchor P0;";
    private static final String $KP1 = "list.get KP0; 0";
    private static final String $KP2 = "anchor P1;";
    private static final String $KP3 = "linkage KP2;";

    final T element;

    public Holder(T element) {
      this.element = element;
    }

    private Object debug() {
      "KP1".intern();
      return RT.ldc();
    }

    @Parametric("P1")
    <U> Holder<U> map(Function<? super T, ? extends U> fun) {
      "KP3".intern();
      return new Holder<>(fun.apply(element));
    }
  }

  @Test
  public void test() {
    class StringTest {
      private static final String $P0 = "species Ljava/lang/String;";
      private static final String $P1 = "list.of P0;";
      private static final String $P2 = "linkage P1;";

      private static final String $P3 = "species Ljava/lang/Integer;";
      private static final String $P4 = "list.of P3;";
      private static final String $P5 = "linkage P4;";

      public static void test() {
        "P2".intern();
         var holder = new Holder<>("42");

        "P5".intern();
        var holder2 = holder.map(Integer::parseInt);

         assertAll(
             () -> assertEquals("42", holder.element),
             () -> assertEquals(new Species(String.class, null), holder.debug()),
             () -> assertEquals(42, holder2.element),
             () -> assertEquals(new Species(Integer.class, null), holder2.debug())
         );
      }
    }

    StringTest.test();
  }
}
