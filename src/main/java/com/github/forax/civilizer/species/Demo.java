package com.github.forax.civilizer.species;

public class Demo {
  static class Foo<T> {
    private static final String $KP0 = "classData";
    private static final String $KP1 = "list.get KP0; C0;";
    private static final String $KP2 = "list.of KP1;";
    private static final String $KP3 = "species Lcom/github/forax/civilizer/species/Demo$Foo; KP2;";
    private static final String $KP4 = "linkage KP3; KP3; KP1;";

    private T t;

    Foo(T t) { this.t = t; }

    T get() { return t; }

    Foo<T> id() {
      "KP4".intern();
      return new Foo<T>(t);
    }
  }

  private static final String $P0 = "species Ljava/lang/String;";
  private static final String $P1 = "list.of P0;";
  private static final String $P2 = "species Lcom/github/forax/civilizer/species/Demo$Foo; P1;";
  private static final String $P3 = "linkage P2; P2; P0;";
  private static final String $P4 = "linkage P2; P0;";
  private static final String $P5 = "linkage P2; P2;";

  public static void main(String[] args) {
    "P3".intern();
    var foo = new Foo<String>("foo");
    "P4".intern();
    var t = foo.get();
    System.out.println(t);
    "P5".intern();
    var bar = foo.id();
  }
}
