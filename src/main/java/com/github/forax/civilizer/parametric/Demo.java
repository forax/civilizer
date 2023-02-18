package com.github.forax.civilizer.parametric;

import com.github.forax.civilizer.prt.Parametric;
import com.github.forax.civilizer.prt.TypeRestriction;

public class Demo {
  @Parametric("P1")
  static class Foo<T> {
    private static final String $P0 = "list Ljava/lang/Object;";
    private static final String $P1 = "mh Lcom/github/forax/civilizer/vm/RT; 'erase (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; P0;";
    private static final String $PA0 = "anchor P1;";
    private static final String $PA1 = "list.get PA0; 0";
    private static final String $PA2 = "restriction PA1;";
    private static final String $PA3 = "linkage PA0;";

    @TypeRestriction("PA2")
    private T t;

    Foo(T t) { this.t = t; }

    T get() { return t; }

    Foo<T> id() {
      "PA3".intern();
      return new Foo<T>(t);
    }
  }

  private static final String $P2= "list Ljava/lang/String;";
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
