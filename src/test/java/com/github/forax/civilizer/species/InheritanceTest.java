package com.github.forax.civilizer.species;

import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.RT;
import com.github.forax.civilizer.vm.Species;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InheritanceTest {
  @Parametric("P2")
  static class Holder<T> {
    private static final String $P0 = "species Ljava/lang/Object;";
    private static final String $P1 = "list.of P0;";
    private static final String $P2 = "mh Lcom/github/forax/civilizer/vm/RT; \"identity\" (Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String $P3 = "mh Lcom/github/forax/civilizer/vm/RT; \"identity\" (Ljava/lang/Object;)Ljava/lang/Object;";

    private static final String $KP0 = "anchor P2;";
    private static final String $KP1 = "list.get KP0; 0";
    private static final String $KP2 = "anchor P3;";
    private static final String $KP3 = "list.get KP2; 0";
    private static final String $KP4 = "species Lcom/github/forax/civilizer/species/InheritanceTest$Holder; KP2;";
    private static final String $KP5 = "linkaze KP4; KP3; KP4; KP3;";

    final T element;

    public Holder(T element) {
      this.element = element;
    }

    private Object debug() {
      "KP1".intern();
      return RT.ldc();
    }

    @Parametric("P3")
    <U> Holder<U> map(Function<? super T, ? extends U> fun) {
      "KP5".intern();
      return new Holder<>(fun.apply(element));
    }
  }

  @Test
  public void test() {
    class StringTest {
      private static final String $P0 = "species Ljava/lang/String;";
      private static final String $P1 = "list.of P0;";
      private static final String $P2 = "species Lcom/github/forax/civilizer/species/InheritanceTest$Holder; P1;";
      private static final String $P3 = "linkage P2; P2; P0;";

      private static final String $P4 = "species Ljava/lang/Integer;";
      private static final String $P5 = "list.of P4;";
      private static final String $P6 = "linkaze Lcom/github/forax/civilizer/species/InheritanceTest$Holder; P5; Lcom/github/forax/civilizer/species/InheritanceTest$Holder; Ljava/util/function/Function;";

      public static void test() {
        "P3".intern();
         var holder = new Holder<>("42");

        "P6".intern();
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
