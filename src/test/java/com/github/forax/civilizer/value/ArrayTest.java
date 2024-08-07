package com.github.forax.civilizer.value;

import com.github.forax.civilizer.vrt.RT;
import com.github.forax.civilizer.vrt.Value;
import com.github.forax.civilizer.vrt.ImplicitlyConstructible;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArrayTest {
  @Test
  public void arrayOfIdentityClass() {
    record IdentityFoo() {}

    var array = new IdentityFoo[3];
    assertAll(
        () -> assertNull(array[0]),
        () -> array[1] = null
    );
  }

  @Test
  public void arrayOfValueClas() {
    @Value record ValueFoo() {}

    var array = new ValueFoo[3];
    assertAll(
        () -> assertNull(array[0]),
        () -> array[1] = null
    );
  }

  @Test
  public void arrayOfZeroBasedClas() {
    @ImplicitlyConstructible
    @Value class ZeroDefaultFoo {}

    var array = new ZeroDefaultFoo[3];
    assertAll(
        () -> assertNull(array[0]),
        () -> array[1] = null
    );
  }

  @Test
  public void arrayOfNonNullZeroBasedClas() {
    @ImplicitlyConstructible
    @Value class ZeroDefaultFoo {}

    var array = RT.newNullRestrictedArray(ZeroDefaultFoo.class, 3);
    assertAll(
        () -> assertEquals(new ZeroDefaultFoo(), array[0]),
        () -> assertThrows(NullPointerException.class, () -> array[1] = null)
    );
  }
}
