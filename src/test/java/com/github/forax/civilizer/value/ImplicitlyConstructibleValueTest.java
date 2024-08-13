package com.github.forax.civilizer.value;

import com.github.forax.civilizer.vrt.NonNull;
import com.github.forax.civilizer.vrt.Nullable;
import com.github.forax.civilizer.vrt.RT;
import com.github.forax.civilizer.vrt.Value;
import com.github.forax.civilizer.vrt.ImplicitlyConstructible;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImplicitlyConstructibleValueTest {
  @ImplicitlyConstructible @Value static class Dummy { }

  @ImplicitlyConstructible @Value static class Foo {
    private final int value;

    public Foo(int value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Foo foo && value == foo.value;
    }

    @Override
    public int hashCode() {
      return value;
    }
  }

  static class FooContainer {
    @Nullable Foo fooNullable;
    @NonNull Foo fooNonNull;
  }

  @Test
  public void zeroDefaultDummyValueClass() {
    assertAll(
        () -> assertTrue(RT.isValue(Dummy.class)),
        () -> assertTrue(RT.isImplicitlyConstructible(Dummy.class))
    );
  }

  @Test
  public void zeroDefaultFooValueClass() {
    assertAll(
        () -> assertTrue(RT.isValue(Foo.class)),
        () -> assertTrue(RT.isImplicitlyConstructible(Foo.class))
    );
  }

  @Test
  public void defaultValue() {
    assertEquals(new Foo(0), RT.defaultValue(Foo.class));
  }

  @Test
  public void container() {
    var container = new FooContainer();
    assertAll(
        () -> assertNull(container.fooNullable),
        () -> assertSame(new Foo(0), container.fooNonNull)
    );
  }

  @Test
  public void containerWrite() {
    var container = new FooContainer();
    container.fooNullable = null;
    assertThrows(NullPointerException.class, () -> container.fooNonNull = null);
  }

  @Test
  public void nonNull() {
    record Bar() {
      public static void bar(@NonNull Foo foo) {}
    }

    assertThrows(NullPointerException.class, () -> Bar.bar(null));
  }

  @Test
  public void nonNull2() {
    record Bar() {
      public static void bar(@NonNull Foo foo, double d, @NonNull Foo foo2) {}
    }

    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Bar.bar(null, 2.0, new Foo(27))),
        () -> assertThrows(NullPointerException.class, () -> Bar.bar(new Foo(42), 2.0, null))
    );
  }

  @Test
  public void nullable() {
    record Bar() {
      public static void bar(@Nullable Foo foo) {}
    }

    Bar.bar(null);  // Ok !
  }

  @Test
  public void nullableByDefault() {
    record Bar() {
      public static void bar(long l, Foo foo) {}
    }

    Bar.bar(42L, null);  // Ok !
  }

  @Test
  public void valueIdentityHashCode() {
    assertEquals(
        System.identityHashCode(new Foo(72)),
        System.identityHashCode(new Foo(72)));
  }

  @Test
  public void valueEquality() {
    assertSame(new Foo(72), new Foo(72));
  }

  private static final Class<? extends Throwable> IDENTITY_EXCEPTION;
  static {
    try {
      IDENTITY_EXCEPTION = Class.forName("java.lang.IdentityException")
          .asSubclass(Throwable.class);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void valueSynchronized() {
    assertThrows(IDENTITY_EXCEPTION, () -> {
      synchronized (new Foo(84)) {
        // empty
      }
    });
  }

  @Test
  public void valueWeakReference() {
    assertThrows(IDENTITY_EXCEPTION, () -> {
      new WeakReference<>(new Foo(84));
    });
  }
}
