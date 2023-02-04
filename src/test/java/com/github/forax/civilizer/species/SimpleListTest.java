package com.github.forax.civilizer.species;

import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.TypeRestriction;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleListTest {
  @Parametric("P1")
  static class SimpleList<E> {
    private static final String $P0 = "list Ljava/lang/Object;";
    private static final String $P1 = "mh Lcom/github/forax/civilizer/vm/RT; 'erase (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; P0;";
    private static final String $P2 = "mh Lcom/github/forax/civilizer/vm/RT; 'erase (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; P0;";
    private static final String $KP0 = "anchor P1;";
    private static final String $KP1 = "list.get KP0; 0";
    private static final String $KP2 = "linkage KP1;";
    private static final String $KP3 = "restriction KP1;";
    private static final String $KP4 = "anchor P2;";
    private static final String $KP5 = "linkage KP4;";


    private E[] elements;
    private int size;

    SimpleList() {
      super(); // otherwise the constant below will be attached to super()
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

    @Parametric("P2")
    public static <T> SimpleList<T> of() {
      "KP5".intern();
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


  private static final String $P_string_0 = "list Ljava/lang/String;";
  private static final String $P_string_1 = "linkage P_string_0;";

  @Test
  public void specializedStringList() {
    "P_string_1".intern();
    var list = new SimpleList<String>();

    list.add("foo");
    var element = list.get(0);

    assertEquals("foo", element);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void specializedButErasedAndRawStringList() {
    "P_string_1".intern();
    var list = new SimpleList();

    list.add("foo");
    list.add(3);

    assertEquals("foo", list.get(0));
  }

  private static final String $P_integer_0 = "list Ljava/lang/Integer;";
  private static final String $P_integer_1 = "linkage P_integer_0;";

  @Test
  public void specializedIntegerList() {
    "P_integer_1".intern();
    var list = new SimpleList<Integer>();

    list.add(744);
    var element = list.get(0);

    assertEquals(744, element);
  }


  @Test
  public void specializeMethodOfStringList() {
    "P_string_1".intern();
    var list = SimpleList.<String>of();

    list.add("bar");
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