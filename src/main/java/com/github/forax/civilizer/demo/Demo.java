package com.github.forax.civilizer.demo;

import com.github.forax.civilizer.runtime.NonNull;
import com.github.forax.civilizer.runtime.Nullable;
import com.github.forax.civilizer.runtime.RT;
import com.github.forax.civilizer.runtime.Value;
import com.github.forax.civilizer.runtime.ZeroDefault;

// $JAVA_HOME/bin/java -XX:+EnablePrimitiveClasses -cp target/classes com/github/forax/civilizer/demo/Demo
// $JAVA_HOME/bin/javap -verbose -private target/classes/com/github/forax/civilizer/demo/Demo.class
public interface Demo {
  @Value record Person(@NonNull String name, @Nullable Address address, @NonNull Age age) {}
  @Value record Address(@NonNull String address) {}
  @Value @ZeroDefault record Age(int age) {}

  class AgeContainer {
    @NonNull Age age;
  }

  static void main(String[] args) {
    var age = new Age(32);
    System.out.println(age);
    System.out.println(age.getClass().isValue());
    System.out.println(RT.isZeroDefault(age.getClass()));
    System.out.println(RT.defaultValue(age.getClass()));

    var person = new Person("Bob", new Address("pont-aven"), new Age(16));
    System.out.println(person.name);
    System.out.println(person.address);
    System.out.println(person.age);

    var container = new AgeContainer();
    System.out.println(container.age);
  }
}
