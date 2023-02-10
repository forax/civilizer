package com.github.forax.civilizer.species;

import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.RT;
import com.github.forax.civilizer.vm.SuperType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InheritanceTest {
  @Parametric("P0")
  static class Holder<T> {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/vm/RT; 'identity (Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String $P1 = "mh Lcom/github/forax/civilizer/vm/RT; 'identity (Ljava/lang/Object;)Ljava/lang/Object;";

    private static final String $PA_0 = "anchor P0;";
    private static final String $PA_1 = "list.get PA_0; 0";
    private static final String $PB_0 = "anchor P1;";
    private static final String $PB_1 = "linkage PB_0;";

    final T element;

    public Holder(T element) {
      this.element = element;
    }

    private Object debug() {
      "PA_1".intern();
      return RT.ldc();
    }

    @Parametric("P1")
    <U> Holder<U> map(Function<? super T, ? extends U> fun) {
      "PB_1".intern();
      return new Holder<>(fun.apply(element));
    }
  }

  @Test
  public void testHolder() {
    class StringTest {
      private static final String $P0 = "list Ljava/lang/String;";
      private static final String $P1 = "linkage P0;";

      private static final String $P2 = "list Ljava/lang/Integer;";
      private static final String $P3 = "linkage P2;";

      public static void test() {
        "P1".intern();
         var holder = new Holder<>("42");

        "P3".intern();
        var holder2 = holder.map(Integer::parseInt);

         assertAll(
             () -> assertEquals("42", holder.element),
             () -> assertEquals(String.class, holder.debug()),
             () -> assertEquals(42, holder2.element),
             () -> assertEquals(Integer.class, holder2.debug())
         );
      }
    }

    StringTest.test();
  }

  @Parametric("P0")
  static class Pair<T, U> {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/vm/RT; 'identity (Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String $P1 = "mh Lcom/github/forax/civilizer/vm/RT; 'identity (Ljava/lang/Object;)Ljava/lang/Object;";

    private static final String $PA_0 = "anchor P0;";
    private static final String $PA_1 = "list.get PA_0; 0";
    private static final String $PA_2 = "list.get PA_0; 1";
    private static final String $PB_0 = "anchor P1; PA_0;";
    private static final String $PB_1 = "list.get PB_0; 0";
    private static final String $PB_2 = "list PA_1; PB_1;";
    private static final String $PB_3 = "linkage PB_2;";

    private List<Object> debug() {
      "PA_1".intern();
      var first = RT.ldc();

      "PA_2".intern();
      var second = RT.ldc();

      return List.of(first, second);
    }

    @Parametric("P1")
    <V> Pair<T, V> replaceSecond() {
      "PB_3".intern();
      return new Pair<>();
    }
  }

  @Test
  public void testPair() {
    class PairTest {
      private static final String $P0 = "list Ljava/lang/String; Ljava/lang/Integer;";
      private static final String $P1 = "linkage P0;";
      private static final String $P2 = "list Ljava/lang/Double;";
      private static final String $P3 = "linkage P2;";

      public static void test() {
        "P1".intern();
        var pair = new Pair<String, Integer>();

        "P3".intern();
        var pair2 = pair.<Double>replaceSecond();

        assertAll(
            () -> assertEquals(String.class, pair.debug().get(0)),
            () -> assertEquals(Integer.class, pair.debug().get(1)),
            () -> assertEquals(String.class, pair2.debug().get(0)),
            () -> assertEquals(Double.class, pair2.debug().get(1))
        );
      }
    }

    PairTest.test();
  }

  /*@Parametric("P0")
  interface ParametricInterface<T> {
    String $P0 = "mh Lcom/github/forax/civilizer/vm/RT; 'identity (Ljava/lang/Object;)Ljava/lang/Object;";
    String $P1 = "anchor P0;";
    String $P2 = "list.get P1; 0";

    default Object dump() {
      "P2".intern();
      return RT.ldc();
    }
  }

  @SuperType("P2")
  @Parametric("P3")
  class AClass implements ParametricInterface<String> {
    private static final String $P0 = "list Ljava/lang/String;";
    private static final String $P1 = "species Lcom/github/forax/civilizer/species/InheritanceTest$ParametricInterface P0;";
    private static final String $P2 = "super P1;";
    private static final String $P3 = "mh Lcom/github/forax/civilizer/vm/RT; 'identity (Ljava/lang/Object;)Ljava/lang/Object;";
  }

  @Test
  public void testDefaultMethod() {
    var aClass = new AClass();
    assertEquals(String.class, aClass.dump());
  }*/
}
