package com.github.forax.civilizer.demo;

import com.github.forax.civilizer.runtime.RT;
import com.github.forax.civilizer.runtime.Value;
import com.github.forax.civilizer.runtime.ZeroDefault;
import org.junit.jupiter.api.Disabled;
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
    @ZeroDefault @Value record ZeroDefaultFoo() {}

    var array = new ZeroDefaultFoo[3];
    assertAll(
        () -> assertNull(array[0]),
        () -> array[1] = null
    );
  }

  @Test
  public void arrayOfNonNullZeroBasedClas() {
    @ZeroDefault @Value record ZeroDefaultFoo() {}

    var array = RT.newNonNullArray(ZeroDefaultFoo.class, 3);
    assertAll(
        () -> assertEquals(new ZeroDefaultFoo(), array[0]),
        () -> assertThrows(NullPointerException.class, () -> array[1] = null)
    );
  }
}
