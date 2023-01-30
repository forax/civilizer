# civilizer
A prototype that transforms primitive types to civilized types by rewriting the bytecode.

This repository contains two rewriters, one (`Rewriter`) transforms classical Java object into value class 
and another (`VMRewriter`) transforms generics to support specialization (so a list of values is fully specialized).

## Rewriter

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

For example, the field age declared below is stored as an int on heap because the value type `Age` is declared as @ZeroDefault and
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


## VMRewriter

This rewriter allows to declare new constant pool constants ($P and $KP) and to specialize some operations.
There are two kinds of constant pool constants,
- the constant pool constant that are accessible from the class ($P constant)
- and the one that are accessible only to instance method ($KP, for kiddy pool constant).

To specify a new constant, the rewriter recognize static final String that starts with $P or $KP and
transforms them to constant.
Each constant can be initialized with a kind of LISP that recognizes the instructions:
- `species` <class> <parameter>? creates a species,
- `list.of` <args>... creates a list,
- `list.get` <list> <index> extracts the nth item of a list,
- `classData` <arg>, extracts the argument of the parametric class,
- `methodData` <arg> extracts the argument of the parametric method,
- `linkage` <owner> <returnType> <parameterTypes...> specifies a method signature,
- `linkaze` <owner> <parameters> <returnType> <parameterTypes...> specifyies a parametric method signature,
- `lambda` <class> <name> <constArgs...> creates a method handle with one parameter (the constant arguments are appended after the parameter).

The arguments of the generics are available at runtime using to forms
- [Species](src/main/java/com/github/forax/civilizer/vm/Species.java) that specify a generics
  with its parameters (as an Object).
  The opcode `anewarray` uses a Species.
- [Linkage](src/main/java/com/github/forax/civilizer/vm/Linkage.java) that specifies the Species of the owner,
  the species of the return type and the species of the parameter of a method call.
  The opcodes `new`, `invokespecial`, `invokevirtual`, `invokeinterface` and `invokestatic`.

To specify a species or a linkage of an operation, the rewriter recognize the pattern
```java
  "ref".intern();
  operation
```
with ref a reference to a $P or a $KP constants and the operation one of the operation above.

In the following code, we first declare the class `SimpleList` as parametric using the annotation `@Parametric`,
the parameter P2 means that the lambda will be executed on the parameters (here to erase the parameters).
We then extract the argument of the class (in `$KP0`) (or use the species java/lang/Object
if not defined) and extract the zeroth argument (in `$KP1`).
In the constructor, the species `KP1` is used to specialize the array creation.
```java
@Parametric("P2")
class SimpleList<E> {
  private static final String $P0 = "species Ljava/lang/Object;";
  private static final String $P1 = "list.of P0;";
  private static final String $P2 = "lambda Lcom/github/forax/civilizer/vm/RT; \"erase\" P1;";
  private static final String $KP0 = "classData P1;";
  private static final String $KP1 = "list.get KP0; 0";

  private E[] elements;
  private int size;

  SimpleList() {
    super(); // otherwise the annotation below will be attached to super()
    "KP1".intern();
    @SuppressWarnings("unchecked")
    var elements = (E[]) new Object[16];
    this.elements = elements;
  }
  ...
}
```

To use the `SimpleList` defined above, we need to specialize the creation (`new`) by providing a linkage indicating
the owner and the return type (`$P3`), the method `add` with a linkage that takes the owner, return void and take
the species `Complex` as parameter (`$P4`) and the method `get` with a linkage that takes the owner, return the species
`Complex` and take an index as parameter (`$P5`).
```java
  private static final String $P0 = "species Qcom/github/forax/civilizer/demo/Complex;";
  private static final String $P1 = "list.of P0;";
  private static final String $P2 = "species Lcom/github/forax/civilizer/species/ValueSpecializedTest$SimpleList; P1;";
  private static final String $P3 = "linkage P2; P2;";
  private static final String $P4 = "linkage P2; V P0;";
  private static final String $P5 = "linkage P2; P0; I";
  
  public static void main(String[] args) {
    "P3".intern();
    var list = new SimpleList<Complex>();

    "P4".intern();
    list.add(Complex.of(2.0, 4.0));

    "P5".intern();
    var element = list.get(0);
    System.out.println(element);
  }
```

This prototype generate a constant dynamic per `$P`, a kiddy constant pool per specialization that contains
all the `$KP`. 
All the specialized operations are re-written as an invokedynamic call with the correspondant constant pool constant
as parameter.
The values of the type arguments inside the constant pools are computed once when asks. a reference to a `$P` is a constant
for the VM, a reference to a `$KP` is a constant once JITed (the implementation uses an inlining cache per operation on `$KP`).
This should ensure proper performance, at the expense of the constant pool being quite bloated.

Parametric class instantiation works, parametric method instantiation work, array specialization works,
use site method specialization works, raw types are supported (using the argument of `classData`/`methodData`).
Type restriction (with `@TypeRestriction`) on fields are implemented (specialization of fields is not implemented).

Inheritance of generic classes and interface default methods are not supported.


## How to build it

You need the latest early access build of Valhalla [jdk.java.net/valhalla/](https://jdk.java.net/valhalla/)
and then launch maven
```bash
export JAVA_HOME=/path/to/jdk
mvn package
```

It will compile, rewrite the bytecode of any packages containing `demo` and run the tests.

## How to play with it ?

The simple way is to check the tests and add new ones :)

