package com.github.forax.civilizer.value;

import com.github.forax.civilizer.vrt.RT;
import com.github.forax.civilizer.vrt.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValueTest {
  @Value record Foo(int value) {}

  static class FooContainer {
    @Nullable Foo fooNullable;
    @NonNull Foo fooNonNull;
  }

  private static final int ACC_IDENTITY = 0x0020;

  @Test
  public void valueClass() {
    assertTrue((Foo.class.getModifiers() & ACC_IDENTITY) == 0);
    assertTrue(RT.isValue(Foo.class));
  }

  @Test
  public void defaultValue() {
    assertNull(RT.defaultValue(Foo.class));
  }

  @Test
  public void container() {
     var container = new FooContainer();
     assertAll(
         () -> assertNull(container.fooNullable),
         () -> assertNull(container.fooNonNull)
     );
  }

  @Test
  public void containerWrite() {
    var container = new FooContainer();
    container.fooNullable = null;
    container.fooNonNull = null;
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
