package com.github.forax.civilizer.species;

import java.util.Objects;

public class SimpleList<E> {
  private static final String $KP0 = "classData";
  private static final String $KP1 = "list.get KP0; 0";
  private static final String $KP2 = "species KP1;";

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


  private static final String $P0 = "species Ljava/lang/String;";
  private static final String $P1 = "list.of P0;";
  private static final String $P2 = "species Lcom/github/forax/civilizer/species/SimpleList; P1;";
  private static final String $P3 = "linkage P2; P2;";
  private static final String $P4 = "linkage P2; V P0;";
  private static final String $P5 = "linkage P2; P0; I";

  public static void main(String[] args) {
    "P3".intern();
    var list = new SimpleList<String>();

    "P4".intern();
    list.add("foo");
    //((SimpleList)list).add(3);

    "P5".intern();
    var element = list.get(0);
    System.out.println(element);
  }
}
