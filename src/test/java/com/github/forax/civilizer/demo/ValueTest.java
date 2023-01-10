package com.github.forax.civilizer.demo;

import com.github.forax.civilizer.runtime.NonNull;
import com.github.forax.civilizer.runtime.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValueTest {
  @Value record Foo(int value) {}

  @Test
  public void valueClass() {
    assertTrue(Foo.class.isValue());
  }

  @Test
  public void nonNull() {
    record Bar() {
      public static void bar(@NonNull  Foo foo) {}
    }

    assertThrows(NullPointerException.class, () -> Bar.bar(null));
  }
}
