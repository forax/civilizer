package com.github.forax.civilizer.species;

import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.TypeRestriction;

public class Demo {
  @Parametric("P1")
  static class Foo<T> {
    private static final String $P0 = "list.of Ljava/lang/Object;";
    private static final String $P1 = "mh Lcom/github/forax/civilizer/vm/RT; 'erase (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; P0;";
    private static final String $KP0 = "anchor P1;";
    private static final String $KP1 = "list.get KP0; 0";
    private static final String $KP2 = "restriction KP1;";
    private static final String $KP3 = "linkage KP0;";

    @TypeRestriction("KP2")
    private T t;

    Foo(T t) { this.t = t; }

    T get() { return t; }

    Foo<T> id() {
      "KP3".intern();
      return new Foo<T>(t);
    }
  }

  private static final String $P2= "list.of Ljava/lang/String;";
  private static final String $P3 = "linkage P2;";


  // $JAVA_HOME/bin/java -XX:+EnablePrimitiveClasses -cp target/classes:/Users/forax/.m2/repository/org/ow2/asm/asm/9.4/asm-9.4.jar com/github/forax/civilizer/species/Demo
  public static void main(String[] args) {
    "P3".intern();
    var foo = new Foo<String>("foo");
    var t = foo.get();
    System.out.println(t);
    var bar = foo.id();
  }
}
