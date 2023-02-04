package com.github.forax.civilizer.species;

import com.github.forax.civilizer.demo.Complex;
import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.RT;
import com.github.forax.civilizer.vm.TypeRestriction;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ValueSpecializedTest {
  @Parametric("P1")
  static class SimpleList<E> {
    private static final String $P0 = "list Ljava/lang/Object;";
    private static final String $P1 = "mh Lcom/github/forax/civilizer/vm/RT; 'erase (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; P0;";
    private static final String $KP0 = "anchor P1;";
    private static final String $KP1 = "list.get KP0; 0";
    private static final String $KP2 = "linkage KP1;";
    private static final String $KP3 = "restriction KP1;";

    private E[] elements;
    private int size;

    SimpleList() {
      super(); // otherwise the annotation below will be attached to super()
      "KP2".intern();
      @SuppressWarnings("unchecked")
      var elements = (E[]) new Object[16];
      this.elements = elements;
    }

    public int size() {
      return size;
    }

    @TypeRestriction("KP3")
    public void add(E element) {
      if (size == elements.length) {
        elements = Arrays.copyOf(elements, elements.length << 1);
      }
      elements[size++] = element;
    }

    public E get(int index) {
      Objects.checkIndex(index, size);
      return elements[index];
    }
  }

  private static final String $P0 = "list Qcom/github/forax/civilizer/demo/Complex;";
  private static final String $P1 = "linkage P0;";

  @Test
  public void specializedComplexList() {
    "P1".intern();
    var list = new SimpleList<Complex>();

    list.add(Complex.of(2.0, 4.0));
    var element = list.get(0);

    assertEquals(Complex.of(2.0, 4.0), element);
  }

  @Test
  public void specializedComplexListAndNull() {
    "P1".intern();
    var list = new SimpleList<Complex>();

    assertThrows(NullPointerException.class, () -> list.add(null));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void specializedComplexAndRawAdd() {
    "P1".intern();
    var list = new SimpleList();

    list.add(Complex.of(2, 4));

    assertThrows(ClassCastException.class, () -> list.add(3));
    assertEquals(Complex.of(2, 4), list.get(0));
  }


  record MethodData() {
    private static final String $P0 = "mh Lcom/github/forax/civilizer/vm/RT; 'identity (Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String $KP0 = "anchor P0;";
    private static final String $KP1 = "list.get KP0; 0";

    @Parametric("P0")
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
      private static final String $P0 = "list Ljava/lang/String;";
      private static final String $P1 = "linkage P0;";

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
      private static final String $P0 = "list Qcom/github/forax/civilizer/demo/Complex;";
      private static final String $P1 = "linkage P0;";

      static void test() {
        "P1".intern();
        var value = MethodData.defaultValue();

        assertEquals(Complex.of(0.0, 0.0), value);
      }
    }
    TestWithComplex.test();
  }
}