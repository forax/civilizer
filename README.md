# civilizer
A prototype that transforms primitive like classes to civilized classes by rewriting the bytecode.

This repository contains two rewriters, one (`ValueRewriter`) transforms classical Java object into value class 
and another (`ParametricRewriter`) transforms generics to support parametric generics
(so a list of values is fully specialized).

## ValueRewriter

```java
  @Value record Person(@NonNull String name, @Nullable Address address, @NonNull Age age) {}
  @Value record Address(@NonNull String address) {}
  @Value @ImplicitlyConstructible record Age(int age) {}
```

The prototype uses 2 specific annotations and 4 nullabilty related annotations from [jspecify.org](),
- at declaration site, @Value declares a type with no identity (a value type),
- at declaration site, @ImplicitlyConstructible declares that the default value of the value type has all its fields fill with zeroes,
- at use site, org.jspecify.annotations.@NonNull declares that null is not a possible value,
- at use site, org.jspecify.annotations.@Nullable declares that null is a possible value (this is also the default if @NullMarked is not used).

On stack, value types are scalarized, on heap, non-null implcitly constructible value type are flattened, i.e. stored without a pointer.

For example, the field age declared below is stored as an int on heap because the value type `Age` is declared
as @ImplicitlyConstructible and the field `age` is declared @NonNull. 
```java
  @NullMarked   // implcitly non-null
  class AgeContainer {
    Age age;  // non-null, must be initialized in the constructor or implicitly constructible
    ...  
  }
  ...
  var container = new AgeContainer();
  System.out.println(container.age);  // Age[age=0]
```

Otherwise, a value type (implicitly constructible or not) works like any object.
```java
  var person = new Person("Bob", new Address("pont-aven"), new Age(16));
  System.out.println(person.name);  // Bob
  System.out.println(person.address); // pont-aven
  System.out.println(person.age);  // Age[age=16]
```

Compared to a classical @NonNull, this one has some teeth, when declared on a parameter of a method, a nullcheck is performed for each call.
When declared on a field only non-null implicitly constructible is enforced (because in the other case null is a valid default value when the field is not yet initialized).


## ParametricRewriter

This rewriter allows to declare new constant pool constants (that starts with `$P`) and to specialize some operations/opcodes.

To specify a new constant, the rewriter recognize `static final String` that starts with `$P` and
transforms them to constant (constant dynamic constant).
Each constant is initialized with a kind of LISP (condy-LISP) that recognizes the instructions:
- `anchor` &lt;ref&gt; &lt;parent-ref?&gt;, parameters of the parametric class/method,
- `array` &lt;ref&gt;, creates an array type,
- `eval` &lt;mh&gt; &lt;args&lt;..., evaluate a method handle with arguments,
- `linkage` &lt;ref&gt;  specifies a parameters of one of the parametrized opcodes,
- `list` &lt;args&gt;... creates a list,
- `list.get` &lt;list&gt; &lt;index&gt; extracts the nth item of a list,
- `mh` &lt;class&gt; &lt;name&gt; &lt;descriptor&gt; &lt;constArgs...&gt; creates a method handle (the constant arguments are inserted at the end),
- `restriction` &lt;refs...&gt;  specifies the classes of the method parameters/field type,
- `species` &lt;raw&gt; &lt;arg&gt; creates a species,
- `species.raw` &lt;species&gt; returns the raw part of a species,
- `species.parameters` &lt;species&gt; returns the parameters part of a species,
- `super` &lt;species&gt;... specifies the parametrized supers interfaces.

The atoms of a condy-LISP expression are
- a primitive type (Z, B, C, S, I, J, F, D) or void (V),
- a type descriptor, starts with 'L' , ends with a semicolon,
- a zero default value type descriptor, starts with 'Q', ends with a semicolon,
- a method type descriptor, starts with '(',
- a reference to another constant pool entry, starts with a 'P', ends with a semicolon,
- a string, starts with a quote ('),
- an integer, starts with a digit,
- a double, starts with a digit and has a dot (.) in the middle.

Runtime objects used by condy-LISP:
- [Linkage](src/main/java/com/github/forax/civilizer/prt/Linkage.java): specify the type parameters for
  the opcodes `new`, `anewarray`, `invokespecial`, `invokevirtual`, `invokeinterface` and `invokestatic`.
- [Restriction](src/main/java/com/github/forax/civilizer/prt/Restriction.java): specify
  the class of each method parameters.
- [Species](src/main/java/com/github/forax/civilizer/prt/Species.java): a pair raw Class + an argument (arguments).
- [Super](src/main/java/com/github/forax/civilizer/prt/Super.java): specify the super species (superclass + interfaces
  with their parameters).

To specify the linkage of a specialized operation, the rewriter recognize the pattern
```java
  "ref".intern();
  operation
```
with ref a reference to a $P constant (with no semicolon at the end) and the operation one of the operation above.

In the following code, we first declare the class `SimpleList` as parametric using the annotation `@Parametric`,
the parameter P1 means that the lambda will be executed on the parameters (here to erase the parameters).
We then extract the argument of the class (in `$KP0`) (or use the java/lang/Object if not defined) and
extract the zeroth argument (in `$KP1`).
In the constructor, the linkage `KP2` is used to specialize the array creation.
```java
@Parametric("P1")
class SimpleList<E> {
  private static final String $P0 = "list Ljava/lang/Object;";
  private static final String $P1 = "mh Lcom/github/forax/civilizer/vm/JDK; 'erase (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; P0;";
  private static final String $P2 = "mh Lcom/github/forax/civilizer/vm/JDK; 'erase (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; P0;";
  private static final String $P3 = "anchor P1;";
  private static final String $P4 = "list.get P3; 0";
  private static final String $P5 = "linkage P4;";
  private static final String $P6 = "restriction P4;";
  private static final String $P7 = "anchor P2;";
  private static final String $P8 = "linkage P7;";

  private E[] elements;
  private int size;

  @SuppressWarnings("unchecked")
  public SimpleList() {
    super();
    "P5".intern();
    elements = (E[]) new Object[16];  // anewarray is specialized by P5
  }
  
  @TypeRestriction("P6")
  public void add(E element) {
    if (size == elements.length) {
      elements = Arrays.copyOf(elements, elements.length << 1);
    }
    elements[size++] = element;
  }
  ...
}
```

To use the `SimpleList` defined above, we need to specialize the creation (`new`) by providing a linkage
saying that the type argument is the class `Complex`.
```java
  private static final String $P0 = "list.of Qcom/github/forax/civilizer/value/Complex;";
  private static final String $P1 = "linkage P0;";
  
  public static void main(String[] args) {
    "P1".intern();
    var list = new SimpleList<Complex>();  // new is specialized by P1

    list.add(Complex.of(2.0, 4.0));

    var element = list.get(0);
    System.out.println(element);
  }
```

All the specialized operations that takes a Linkage as parameter are re-written as invokedynamic calls
with the correspondant constant pool constant as parameter.
The values of the type arguments inside the constant pools are computed when asked.

There are more [examples in the tests](src/test/java/com/github/forax/civilizer/parametric/)

Parametric class instantiation works, static and instance parametric methods instantiation works,
specialization of super (with `@SuperType`), interfaces, and default methods works,
array specialization works, use site method specialization works,
raw types are supported (using the bsm referenced by the annotation `@Parametric`).
Type restriction (with `@TypeRestriction`) on fields and methods are implemented
(specialization of field storage is not implemented !).


## How to build it

You need the latest early access build of Valhalla [jdk.java.net/valhalla/](https://jdk.java.net/valhalla/)
and then launch maven
```bash
export JAVA_HOME=/path/to/jdk
mvn package
```

It will compile, rewrite the bytecode of any packages containing either `value` or `parametric` and run the tests.

## How to play with it ?

The simple way is to check the tests and add new ones :)

