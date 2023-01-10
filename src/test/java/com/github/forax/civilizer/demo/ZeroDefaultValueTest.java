package com.github.forax.civilizer.demo;

import com.github.forax.civilizer.demo.ValueTest.FooContainer;
import com.github.forax.civilizer.runtime.NonNull;
import com.github.forax.civilizer.runtime.Nullable;
import com.github.forax.civilizer.runtime.RT;
import com.github.forax.civilizer.runtime.Value;
import com.github.forax.civilizer.runtime.ZeroDefault;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZeroDefaultValueTest {
  @ZeroDefault @Value record Foo(int value) {}

  static class FooContainer {
    @Nullable Foo fooNullable;
    @NonNull Foo fooNonNull;
  }

  @Test
  public void zeroDefaultValueClass() {
    assertAll(
        () -> assertTrue(Foo.class.isValue()),
        () -> assertTrue(RT.isZeroDefault(Foo.class))
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
  public void valueEquality() {
    assertSame(new Foo(72), new Foo(72));
  }

  @Test
  public void valueSynchronized() {
    assertThrows(IllegalMonitorStateException.class, () -> {
      synchronized (new Foo(84)) {
        // empty
      }
    });
  }

  @Test
  public void valueWeakReference() {
    assertThrows(IdentityException.class, () -> {
      new WeakReference<>(new Foo(84));
    });
  }
}
