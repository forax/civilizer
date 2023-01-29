package com.github.forax.civilizer.species;

import com.github.forax.civilizer.vm.Parametric;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SimpleListTest {
  @Parametric
  static class SimpleList<E> {
    private static final String $P0 = "species Ljava/lang/Object;";
    private static final String $P1 = "list.of P0;";
    private static final String $KP0 = "classData P1;";
    private static final String $KP1 = "list.get KP0; 0";
    private static final String $KP2 = "species Lcom/github/forax/civilizer/species/SimpleListTest$SimpleList; KP0;";
    private static final String $KP3 = "linkage KP2; KP2;";

    private E[] elements;
    private int size;

    SimpleList() {
      super(); // otherwise the constant below will be attached to super()
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

    @Parametric
    public static <T> SimpleList<T> of() {
      "KP3".intern();
      return new SimpleList<>();
    }
  }

  @Test
  public void simpleList() {
    var list = new SimpleList<String>();
    list.add("foo");
    assertEquals("foo", list.get(0));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void rawAndUncheckedList() {
    var list = new SimpleList();
    list.add(3);
    assertEquals(3, list.get(0));
  }


  private static final String $P_string_0 = "species Ljava/lang/String;";
  private static final String $P_string_1 = "list.of P_string_0;";
  private static final String $P_string_2 = "species Lcom/github/forax/civilizer/species/SimpleListTest$SimpleList; P_string_1;";
  private static final String $P_string_3 = "linkage P_string_2; P_string_2;";
  private static final String $P_string_4 = "linkage P_string_2; V P_string_0;";
  private static final String $P_string_5 = "linkage P_string_2; P_string_0; I";

  @Test
  public void specializedStringList() {
    "P_string_3".intern();
    var list = new SimpleList<String>();

    "P_string_4".intern();
    list.add("foo");

    "P_string_5".intern();
    var element = list.get(0);

    assertEquals("foo", element);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void specializedAndRawStringList() {
    "P_string_3".intern();
    var list = new SimpleList();

    list.add("foo");

    assertThrows(ArrayStoreException.class, () -> list.add(3));
    assertEquals("foo", list.get(0));
  }


  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void specializedOnlyAddAndRawStringList() {
    var list = new SimpleList();
    list.add("foo");

    assertThrows(ClassCastException.class, () -> {
      "P_string_4".intern();
      list.add(3);
    });
    assertEquals("foo", list.get(0));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void specializedOnlyGetAndRawStringList() {
    var list = new SimpleList();
    list.add(3);

    assertThrows(ClassCastException.class, () -> {
      "P_string_5".intern();
      list.get(0);
    });
    assertEquals(3, list.get(0));
  }


  private static final String $P_integer_0 = "species Ljava/lang/Integer;";
  private static final String $P_integer_1 = "list.of P_integer_0;";
  private static final String $P_integer_2 = "species Lcom/github/forax/civilizer/species/SimpleListTest$SimpleList; P_integer_1;";
  private static final String $P_integer_3 = "linkage P_integer_2; P_integer_2;";
  private static final String $P_integer_4 = "linkage P_integer_2; V P_integer_0;";
  private static final String $P_integer_5 = "linkage P_integer_2; P_integer_0; I";

  @Test
  public void specializedIntegerList() {
    "P_integer_3".intern();
    var list = new SimpleList<Integer>();

    "P_integer_4".intern();
    list.add(744);

    "P_integer_5".intern();
    var element = list.get(0);

    assertEquals(744, element);
  }


  @Test
  public void specializeMethodOfStringList() {
    "P_string_3".intern();
    var list = SimpleList.<String>of();

    "P_string_4".intern();
    list.add("bar");

    "P_string_5".intern();
    var element = list.get(0);

    assertEquals("bar", element);
  }

  @Test
  public void rawMethodOf() {
    var list = SimpleList.<String>of();
    list.add("baz");
    var element = list.get(0);

    assertEquals("baz", element);
  }
}