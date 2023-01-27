package com.github.forax.civilizer.species;

public class Demo {
  static class Foo<T> {
    private static final String $KP0 = "classData";
    private static final String $KP1 = "list.get KP0; C0;";
    private static final String $KP2 = "list.of KP1;";

    Foo<T> id() {
      "KP2".intern();
      return new Foo<T>();
    }
  }

  private static final String $P3 = "species Ljava/lang/String;";
  private static final String $P4 = "list.of P3;";
  private static final String $P5 = "species Lcom/github/forax/civilizer/species/Demo$Foo; P4;";
  private static final String $P6 = "methodSpecies P5;";

  public static void main(String[] args) {
    "P4".intern();
    var foo = new Foo<String>();
    "P6".intern();
    var bar = foo.id();
  }
}
