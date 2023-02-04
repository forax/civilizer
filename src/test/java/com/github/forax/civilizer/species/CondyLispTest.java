package com.github.forax.civilizer.species;

import com.github.forax.civilizer.demo.Complex;
import com.github.forax.civilizer.vm.Linkage;
import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.RT;
import com.github.forax.civilizer.vm.Species;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class CondyLispTest {
  @Test
  public void ldcInteger() {
    class Foo {
      private static final String $P0 = "42";

      static void test() {
        "P0".intern();
        var value = RT.ldc();
        assertEquals(42, value);
      }
    }
  }

  @Test
  public void ldcDouble() {
    class Foo {
      private static final String $P0 = "4.0";

      static void test() {
        "P0".intern();
        var value = RT.ldc();
        assertEquals(4.0, value);
      }
    }
  }

  @Test
  public void ldcString() {
    class Foo {
      private static final String $P0 = """
          "foo"\
          """;

      static void test() {
        "P0".intern();
        var value = RT.ldc();
        assertEquals("foo", value);
      }
    }
  }

  @Test
  public void ldcBasicType() {
    class Foo {
      private static final String $P0 = "Z";
      private static final String $P1 = "B";
      private static final String $P2 = "C";
      private static final String $P3 = "S";
      private static final String $P4 = "I";
      private static final String $P5 = "J";
      private static final String $P6 = "F";
      private static final String $P7 = "D";

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
            () -> assertEquals(boolean.class, v0),
            () -> assertEquals(byte.class, v1),
            () -> assertEquals(char.class, v2),
            () -> assertEquals(short.class, v3),
            () -> assertEquals(int.class, v4),
            () -> assertEquals(long.class, v5),
            () -> assertEquals(float.class, v6),
            () -> assertEquals(double.class, v7)
        );
      }
    }
  }

  @Test
  public void ldcReferenceType() {
    class Foo {
      private static final String $P0 = "Ljava/lang/String;";
      private static final String $P1 = "Qcom/github/forax/civilizer/demo/Complex;";


      static void test() {
        "P0".intern();
        var v0 = RT.ldc();
        "P1".intern();
        var v1 = RT.ldc();

        assertAll(
            () -> assertEquals(String.class, v0),
            () -> assertEquals(com.github.forax.civilizer.runtime.RT.asSecondaryType(Complex.class), v1)
        );
      }
    }
  }

  @Test
  public void condyLispSpecies() {
    class Foo {
      private static final String $P0 = "species Ljava/lang/String;";

      static void test() {
        "P0".intern();
        var value = RT.ldc();

        assertEquals(new Species(String.class, null), value);
      }
    }
  }

  @Test
  public void condyLispSpeciesParameters() {
    class Foo {
      private static final String $P0 = "species Ljava/util/List; Ljava/lang/Integer;";
      private static final String $P1 = "species.parameters P0;";

      static void test() {
        "P0".intern();
        var value = RT.ldc();

        assertEquals(Integer.class, value);
      }
    }
  }

  @Test
  public void condyLispSpeciesRaw() {
    class Foo {
      private static final String $P0 = "species Ljava/util/List; Ljava/lang/Integer;";
      private static final String $P1 = "species.raw P0;";

      static void test() {
        "P0".intern();
        var value = RT.ldc();

        assertEquals(List.class, value);
      }
    }
  }

  @Test
  public void condyLispListOf() {
    class Foo {
      private static final String $P0 = "list.of Ljava/lang/String; I";

      static void test() {
        "P0".intern();
        var value = RT.ldc();

        assertEquals(List.of(String.class, int.class), value);
      }
    }
  }

  @Test
  public void condyLispListGet() {
    class Foo {
      private static final String $P0 = "list.get Ljava/lang/String; I";
      private static final String $P1 = "list.get P0; 1";

      static void test() {
        "P1".intern();
        var value = RT.ldc();

        assertEquals(int.class, value);
      }
    }
  }

  @Test
  public void condyLispLinkage() {
    class Foo {
      private static final String $P0 = "linkage Ljava/lang/String;";

      static void test() {
        "P0".intern();
        var value = RT.ldc();

        assertEquals(new Linkage(String.class), value);
      }
    }
  }

  private static int bsmValue(Integer value) {
    return value == null ? 42 : value;
  }

  @Test
  public void ldcClassDataNoArgument() {
    @Parametric("P0")
    class Foo<T> {
      private static final String $P0 = "mh Lcom/github/forax/civilizer/species/CondyLispTest; 'bsmValue (Ljava/lang/Integer;)I";
      private static final String $KP0 = "anchor P0;";

      Object value() {
        "KP0".intern();
        return RT.ldc();
      }
    }

    var foo = new Foo<>();
    assertEquals(42, foo.value());
  }

  @Parametric("P0")
  record Data<T>() {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/species/CondyLispTest; 'bsmValue (Ljava/lang/Integer;)I";
    private static final String $KP0 = "anchor P0;";

    Object value() {
      "KP0".intern();
      return RT.ldc();
    }

    private static final String $P1 = "linkage 77";

    static void test() {
      "P1".intern();
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
  record Data3<T>() {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/vm/RT; 'identity (Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String $KP0 = "anchor P0;";

    Object value() {
      "KP0".intern();
      return RT.ldc();
    }

    private static final String $P1 = "list.of Ljava/lang/String;";
    private static final String $P2 = "linkage P1;";
    private static final String $P3 = "list.of Ljava/lang/String;";
    private static final String $P4 = "linkage P3;";

    static void test() {
      "P2".intern();
      var data = new Data3<>();
      "P4".intern();
      var data2 = new Data3<>();

      assertSame(data.value(), data2.value());
    }
  }

  @Test
  public void kiddyPoolConstantsAreConstant() {
    Data3.test();
  }


  class MethodData {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/vm/RT; 'identity (Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String $KP0 = "anchor P0;";

    @Parametric("P0")
    static Object value() {
      "KP0".intern();
      return RT.ldc();
    }

    private static final String $P1 = "linkage 42";

    static void test() {
      "P1".intern();
      var value = MethodData.value();

      assertEquals(42, value);
    }
  }

  @Test
  public void ldcMethodData() {
    MethodData.test();
  }
}
