package com.github.forax.civilizer.value;

import com.github.forax.civilizer.vrt.RT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RTTest {
  @Test
  public void isValue() {
    assertAll(
        () -> assertFalse(RT.isValue(int.class)),
        () -> assertFalse(RT.isValue(String.class)),
        () -> assertTrue(RT.isValue(Complex.class))
    );
  }

  @Test
  public void isImplicitlyConstructible() {
    assertAll(
        () -> assertFalse(RT.isImplicitlyConstructible(int.class)),
        () -> assertFalse(RT.isImplicitlyConstructible(String.class)),
        () -> assertTrue(RT.isImplicitlyConstructible(Complex.class))
    );
  }

  @Test
  public void defaultValue() {
    assertAll(
        () -> assertEquals(0, RT.defaultValue(int.class)),
        () -> assertNull(RT.defaultValue(String.class)),
        () -> assertEquals(new Complex(0,0), RT.defaultValue(Complex.class))
    );
  }

  @Test
  public void newFltattableArray() {
    var array = RT.newNullRestrictedArray(Complex.class, 3);
    assertThrows(NullPointerException.class, () -> array[1] = null);
  }

  @Test
  public void isNullRestrictedArray() {
    var array = RT.newNullRestrictedArray(Complex.class, 3);
    assertTrue(RT.isNullRestrictedArray(array));
  }
}
