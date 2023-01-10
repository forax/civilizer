# civilizer
A prototype that transforms primitive types to civilized types by rewriting the bytecode.

```java
  @Value record Person(@NonNull String name, @Nullable Address address, @NonNull Age age) {}
  @Value record Address(@NonNull String address) {}
  @Value @ZeroDefault record Age(int age) {}
```

The prototype defines 4 annotations,
- at declaration site, @Value declares a type with no identity (a value type),
- at declaration site, @ZeroDefault declares that the default value of the value type has all its fields fill with zeroes,
- at use site, @NonNull declares that null is not a possible value,
- at use site, @Nullable declares that null is a possible value (this is also the default if there is no annotation).

On stack, value types are scalarized, on heap, non-null zero-default value type are flattened, i.e. stored without a pointer.

By example, the field age declared below is stored as an int on heap because the value type `Age` is declared as @ZeroDefault and
the field `age` is declared @NonNull. 
```java
  class AgeContainer {
    @NonNull Age age;
  }
  ...
  var container = new AgeContainer();
  System.out.println(container.age);  // Age[age=0]
```

Otherwise, a value type (zero-default or not) works like any object.
```java
  var person = new Person("Bob", new Address("pont-aven"), new Age(16));
  System.out.println(person.name);  // Bob
  System.out.println(person.address); // pont-aven
  System.out.println(person.age);  // Age[age=16]
```

Compared to a classical @NonNull, this one has some teeth, when declared on a parameter of a method, a nullcheck is done at each call. When declared on a field only non-null zero-default is enforced (because in the other case null is a valid default value when the field is not yet initialized).
This behavior is i believe the same as in Kotlin.


## How to build it

You need the latest early access build of Valhalla [jdk.java.net/valhalla/](https://jdk.java.net/valhalla/)
and then launch maven
```bash
export JAVA_HOME=/path/to/jdk
mvn package
```

It will compile, rewrite the bytecode of any packages containing `demo` and run the tests.

## How to run it
```bash
  $JAVA_HOME/bin/java -XX:+EnablePrimitiveClasses -cp target/classes com/github/forax/civilizer/demo/Demo
```
