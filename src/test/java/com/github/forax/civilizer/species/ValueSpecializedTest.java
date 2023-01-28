package com.github.forax.civilizer.species;

import com.github.forax.civilizer.demo.Complex;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ValueSpecializedTest {
  static class SimpleList<E> {
    private static final String $P0 = "species Ljava/lang/Object;";
    private static final String $P1 = "list.of P0;";
    private static final String $KP0 = "classData P1;";
    private static final String $KP1 = "list.get KP0; 0";

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
}