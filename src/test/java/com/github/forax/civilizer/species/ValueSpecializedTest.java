package com.github.forax.civilizer.species;

import com.github.forax.civilizer.demo.Complex;
import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.RT;
import com.github.forax.civilizer.vm.TypeRestriction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ValueSpecializedTest {
  @Parametric("P2")
  static class SimpleList<E> {
    private static final String $P0 = "species Ljava/lang/Object;";
    private static final String $P1 = "list.of P0;";
    private static final String $P2 = "mh Lcom/github/forax/civilizer/vm/RT; \"erase\" (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; P1;";
    private static final String $KP0 = "classData";
    private static final String $KP1 = "list.get KP0; 0";
    private static final String $KP2 = "linkage Lcom/github/forax/civilizer/species/ValueSpecializedTest$SimpleList; V KP1;";
    private static final String $KP3 = "linkage Lcom/github/forax/civilizer/species/ValueSpecializedTest$SimpleList; KP1; I";

    private E[] elements;
    private int size;

    SimpleList() {
      super(); // otherwise the annotation below will be attached to super()
      "KP1".intern();
      @SuppressWarnings("unchecked")
      var elements = (E[]) new Object[16];
      this.elements = elements;
    }

    public int size() {
      return size;
    }

    @TypeRestriction("KP2")
    public void add(E element) {
      if (size == elements.length) {
        elements = Arrays.copyOf(elements, elements.length << 1);
      }
      elements[size++] = element;
    }

    @TypeRestriction("KP3")
    public E get(int index) {
      Objects.checkIndex(index, size);
      return elements[index];
    }
  }

  private static final String $P0 = "species Qcom/github/forax/civilizer/demo/Complex;";
  private static final String $P1 = "list.of P0;";
  private static final String $P2 = "species Lcom/github/forax/civilizer/species/ValueSpecializedTest$SimpleList; P1;";
  private static final String $P3 = "linkage P2; P2;";
  private static final String $P4 = "linkage P2; V P0;";
  private static final String $P5 = "linkage P2; P0; I";

  @Test
  public void specializedComplexList() {
    "P3".intern();
    var list = new SimpleList<Complex>();

    "P4".intern();
    list.add(Complex.of(2.0, 4.0));

    "P5".intern();
    var element = list.get(0);

    assertEquals(Complex.of(2.0, 4.0), element);
  }

  @Test
  public void specializedComplexListAndNull() {
    "P3".intern();
    var list = new SimpleList<Complex>();

    assertThrows(NullPointerException.class, () -> {
      "P4".intern();
      list.add(null);
    });
  }

  @Test
  public void specializedComplexListRawAndNull() {
    "P3".intern();
    var list = new SimpleList<Complex>();

    assertThrows(NullPointerException.class, () -> list.add(null));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void specializedComplexAndRawAdd() {
    "P3".intern();
    var list = new SimpleList();

    list.add(Complex.of(2, 4));

    assertThrows(ClassCastException.class, () -> list.add(3));
    assertEquals(Complex.of(2, 4), list.get(0));
  }


  @Parametric("")
  record MethodData() {
    private static final String $P0 = "list.of Ljava/lang/Object;";
    private static final String $KP0 = "methodData";
    private static final String $KP1 = "list.get KP0; 0";

    @Parametric("")
    @SuppressWarnings("unchecked")
    static <T> T defaultValue() {
      "KP1".intern();
      var argument = (Class<?>) RT.ldc();
      return (T) Array.get(Array.newInstance(argument, 1), 0);
    }
  }

  @Test
  public void specializedStringDefaultValue() {
    class TestWithString {
      private static final String $P0 = "list.of Ljava/lang/String;";
      private static final String $P1 = "linkaze Lcom/github/forax/civilizer/species/ValueSpecializedTest$MethodData; P0; Ljava/lang/Object;";

      static void test() {
        "P1".intern();
        var value = MethodData.defaultValue();

        assertNull(value);
      }
    }
    TestWithString.test();
  }

  @Test
  public void specializedComplexDefaultValue() {
    class TestWithComplex {
      private static final String $P0 = "list.of Qcom/github/forax/civilizer/demo/Complex;";
      private static final String $P1 = "linkaze Lcom/github/forax/civilizer/species/ValueSpecializedTest$MethodData; P0; Ljava/lang/Object;";

      static void test() {
        "P1".intern();
        var value = MethodData.defaultValue();

        assertEquals(Complex.of(0.0, 0.0), value);
      }
    }
    TestWithComplex.test();
  }
}