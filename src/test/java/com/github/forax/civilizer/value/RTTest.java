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
  public void asSecondaryType() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> RT.asSecondaryType(int.class)),
        () -> assertThrows(UnsupportedOperationException.class, () -> RT.asSecondaryType(String.class)),
        () -> assertTrue(RT.asSecondaryType(Complex.class).isValue())
    );
  }

  @Test
  public void isSecondaryType() {
    assertAll(
        () -> assertFalse(RT.isSecondaryType(int.class)),
        () -> assertFalse(RT.isSecondaryType(String.class)),
        () -> assertFalse(RT.isSecondaryType(Complex.class)),
        () -> assertTrue(RT.isSecondaryType(RT.asSecondaryType(Complex.class)))
    );
  }

  @Test
  public void defaultValue() {
    assertAll(
        () -> assertEquals(0, RT.defaultValue(int.class)),
        () -> assertNull(RT.defaultValue(String.class)),
        () -> assertNull(RT.defaultValue(Complex.class)),
        () -> assertEquals(new Complex(0.0, 0.0), RT.defaultValue(RT.asSecondaryType(Complex.class)))
    );
  }

  @Test
  public void newNonNullArray() {
    var array = RT.newNonNullArray(Complex.class, 3);
    assertThrows(NullPointerException.class, () -> array[1] = null);
  }
}
