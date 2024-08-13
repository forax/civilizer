package com.github.forax.civilizer.parametric;

import com.github.forax.civilizer.value.Complex;
import com.github.forax.civilizer.prt.Linkage;
import com.github.forax.civilizer.prt.Location;
import com.github.forax.civilizer.prt.Parametric;
import com.github.forax.civilizer.prt.RT;
import com.github.forax.civilizer.prt.Species;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"ReturnValueIgnored", "UnusedVariable"})
public class CondyLispTest {
  @Test
  public void ldcInteger() {
    final class Foo {
      private static final String $P0 = "list 42";

      static void test() {
        "P0".intern();
        var value = RT.ldc();
        assertEquals(42, ((List<?>) value).getFirst());
      }
    }
    Foo.test();
  }

  @Test
  public void ldcDouble() {
    final class Foo {
      private static final String $P0 = "list 4.0";

      static void test() {
        "P0".intern();
        var value = RT.ldc();
        assertEquals(4.0, ((List<?>) value).getFirst());
      }
    }
    Foo.test();
  }

  @Test
  public void ldcString() {
    final class Foo {
      private static final String $P0 = "list 'foo";

      static void test() {
        "P0".intern();
        var value = RT.ldc();
        assertEquals("foo", ((List<?>) value).getFirst());
      }
    }
    Foo.test();
  }

  @Test
  public void ldcBasicType() {
    final class Foo {
      private static final String $P0 = "list Z";
      private static final String $P1 = "list B";
      private static final String $P2 = "list C";
      private static final String $P3 = "list S";
      private static final String $P4 = "list I";
      private static final String $P5 = "list J";
      private static final String $P6 = "list F";
      private static final String $P7 = "list D";

      static void test() {
        "P0".intern();
        var v0 = RT.ldc();
        "P1".intern();
        var v1 = RT.ldc();
        "P2".intern();
        var v2 = RT.ldc();
        "P3".intern();
        var v3 = RT.ldc();
        "P4".intern();
        var v4 = RT.ldc();
        "P5".intern();
        var v5 = RT.ldc();
        "P6".intern();
        var v6 = RT.ldc();
        "P7".intern();
        var v7 = RT.ldc();

        assertAll(
            () -> assertEquals(List.of(boolean.class), v0),
            () -> assertEquals(List.of(byte.class), v1),
            () -> assertEquals(List.of(char.class), v2),
            () -> assertEquals(List.of(short.class), v3),
            () -> assertEquals(List.of(int.class), v4),
            () -> assertEquals(List.of(long.class), v5),
            () -> assertEquals(List.of(float.class), v6),
            () -> assertEquals(List.of(double.class), v7)
        );
      }
    }
    Foo.test();
  }

  @Test
  public void ldcReferenceType() {
    final class Foo {
      private static final String $P0 = "list Ljava/lang/String;";
      private static final String $P1 = "list Lcom/github/forax/civilizer/value/Complex;";

      static void test() {
        "P0".intern();
        var v0 = RT.ldc();
        "P1".intern();
        var v1 = RT.ldc();

        assertAll(
            () -> assertEquals(List.of(String.class), v0),
            () -> assertEquals(List.of(Complex.class), v1)
        );
      }
    }
    Foo.test();
  }

  @Test
  public void ldcArrayType() {
    final class Foo {
      private static final String $P0 = "array Ljava/lang/String;";

      static void test() {
        "P0".intern();
        var v0 = RT.ldc();

        assertEquals(String[].class, v0);
      }
    }
    Foo.test();
  }

  @Test
  public void condyLispSpecies() {
    final class Foo {
      private static final String $P0 = "species Ljava/lang/String;";

      static void test() {
        "P0".intern();
        var value = RT.ldc();

        assertEquals(new Species(String.class, null), value);
      }
    }
    Foo.test();
  }

  @Test
  public void condyLispSpeciesParameters() {
    final class Foo {
      private static final String $P0 = "species Ljava/util/List; Ljava/lang/Integer;";
      private static final String $P1 = "species.parameters P0;";

      static void test() {
        "P1".intern();
        var value = RT.ldc();

        assertEquals(Integer.class, value);
      }
    }
    Foo.test();
  }

  @Test
  public void condyLispSpeciesRaw() {
    final class Foo {
      private static final String $P0 = "species Ljava/util/List; Ljava/lang/Integer;";
      private static final String $P1 = "species.raw P0;";

      static void test() {
        "P1".intern();
        var value = RT.ldc();

        assertEquals(List.class, value);
      }
    }
    Foo.test();
  }

  @Test
  public void condyLispListOf() {
    final class Foo {
      private static final String $P0 = "list Ljava/lang/String; I";

      static void test() {
        "P0".intern();
        var value = RT.ldc();

        assertEquals(List.of(String.class, int.class), value);
      }
    }
    Foo.test();
  }

  @Test
  public void condyLispListGet() {
    final class Foo {
      private static final String $P0 = "list Ljava/lang/String; I";
      private static final String $P1 = "list.get P0; 1";

      static void test() {
        "P1".intern();
        var value = RT.ldc();

        assertEquals(int.class, value);
      }
    }
    Foo.test();
  }

  @Test
  public void condyLispLinkage() {
    final class Foo {
      private static final String $P0 = "linkage Ljava/lang/String;";

      static void test() {
        "P0".intern();
        var value = RT.ldc();

        assertEquals(new Linkage(String.class), value);
      }
    }
    Foo.test();
  }

  @Test
  public void condyLispEval() {
    final class Foo {
      private static final String $P0 = "mh Ljava/lang/Integer; 'sum (II)I";
      private static final String $P1 = "eval P0; 2 3";

      static void test() {
        "P1".intern();
        var value = RT.ldc();

        assertEquals(5, value);
      }
    }
    Foo.test();
  }


  @SuppressWarnings("UnusedMethod")  // used by reflection
  private static Object bsmValue(Location location, Integer value) {
    return location.specialize(value == null ? 42 : value);
  }

  @Test
  public void ldcClassDataNoArgument() {
    @Parametric("P0")
    final class Foo<T> {
      private static final String $P0 = "mh Lcom/github/forax/civilizer/parametric/CondyLispTest; 'bsmValue (Lcom/github/forax/civilizer/prt/Location;Ljava/lang/Integer;)Ljava/lang/Object;";
      private static final String $P1 = "anchor P0;";

      Object value() {
        "P1".intern();
        return RT.ldc();
      }
    }

    var foo = new Foo<>();
    assertEquals(42, foo.value());
  }

  @Parametric("P0")
  @SuppressWarnings("UnusedTypeParameter")
  record Data<T>() {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/parametric/CondyLispTest; 'bsmValue (Lcom/github/forax/civilizer/prt/Location;Ljava/lang/Integer;)Ljava/lang/Object;";
    private static final String $P1 = "anchor P0;";
    private static final String $P2 = "linkage 77";

    Object value() {
      "P1".intern();
      return RT.ldc();
    }

    static void test() {
      "P2".intern();
      var data = new Data<>();

      var data2 = new Data<>();

      assertAll(
          () -> assertEquals(77, data.value()),
          () -> assertEquals(42, data2.value())
      );
    }
  }

  @Test
  public void ldcClassData() {
    Data.test();
  }


  @Parametric("P0")
  @SuppressWarnings("UnusedTypeParameter")
  record Data3<T>() {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/prt/JDK; 'identity (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String $P1 = "anchor P0;";
    private static final String $P2 = "list Ljava/lang/String;";
    private static final String $P3 = "linkage P2;";
    private static final String $P4 = "list Ljava/lang/String;";
    private static final String $P5 = "linkage P4;";

    Object value() {
      "P1".intern();
      return RT.ldc();
    }

    static void test() {
      "P3".intern();
      var data = new Data3<>();
      "P5".intern();
      var data2 = new Data3<>();

      assertSame(data.value(), data2.value());
    }
  }

  @Test
  public void kiddyPoolConstantsAreConstant() {
    Data3.test();
  }


  final static class MethodData {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/prt/JDK; 'identity (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String $P1 = "anchor P0;";
    private static final String $P2 = "linkage 42";

    @Parametric("P0")
    static Object value() {
      "P1".intern();
      return RT.ldc();
    }

    static void test() {
      "P2".intern();
      var value = MethodData.value();

      assertEquals(42, value);
    }
  }

  @Test
  public void ldcMethodData() {
    MethodData.test();
  }
}
