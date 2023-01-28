package com.github.forax.civilizer.species;

import java.util.Objects;

public class SimpleList<E> {
  private static final String $P0 = "species Ljava/lang/Object;";
  private static final String $P1 = "list.of P0;";
  private static final String $KP0 = "classData P1;";
  private static final String $KP1 = "list.get KP0; 0";

  private E[] elements;
  private int size;

  SimpleList() {
    super();  // otherwise the annotation below will be attached to super()
    "KP1".intern();
    var elements = (E[]) new Object[16];
    this.elements = elements;
  }

  public int size() {
    return size;
  }

  public void add(E element) {
    Objects.requireNonNull(element);
    elements[size++] = element;
  }

  public E get(int index) {
    Objects.checkIndex(index, size);
    return elements[index];
  }


  private static final String $P2 = "species Ljava/lang/String;";
  private static final String $P3 = "list.of P2;";
  private static final String $P4 = "species Lcom/github/forax/civilizer/species/SimpleList; P3;";
  private static final String $P5 = "linkage P4; P4;";
  private static final String $P6 = "linkage P4; V P2;";
  private static final String $P7 = "linkage P4; P2; I";

  public static void main(String[] args) {
    "P5".intern();
    var list = new SimpleList<String>();

    "P6".intern();
    list.add("foo");

    "P7".intern();
    var element = list.get(0);
    System.out.println(element);

    var list2 = new SimpleList<String>();
    list2.add("bar");
    System.out.println(list2.get(0));
  }
}
