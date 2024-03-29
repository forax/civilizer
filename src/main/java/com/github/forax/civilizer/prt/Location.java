package com.github.forax.civilizer.prt;

import com.github.forax.civilizer.prt.RT.Anchor;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.Objects;

/**
 * Either a parametric class location or a parametric method location.
 * This class is sent as first parameter of the bootstrap method of an anchor.
 */
public final class Location {
  /**
   * Key that can be used to store the information corresponding to a parametric class/method.
   * <p>
   * #see {@link Location#key()}
   */
  public sealed interface Key {}

  // TODO use weak-ref for classes here
  private record ClassKey(Class<?> raw) implements Key {}
  private record MethodKey(Class<?> raw, Object classParameters, String name, String descriptor) implements Key {}

  private final Lookup speciesLookup;
  private final Class<?> raw;
  private final Object classParameters;
  private final String name;
  private final String descriptor;

  private Location(Lookup speciesLookup, Class<?> raw, Object classParameters, String name, String descriptor) {
    this.speciesLookup = speciesLookup;
    this.raw = raw;
    this.classParameters = classParameters;
    this.name = name;
    this.descriptor = descriptor;
  }

  // DEBUG
  @Override
  public String toString() {
    if (name == null) {
      return "class location " + raw;
    }
    return "method location " + raw + "[" + classParameters + "] " + name + descriptor;
  }

  static Location classLocation(Lookup speciesLookup, Class<?> raw) {
    return new Location(speciesLookup, raw, null, null, null);
  }
  static Location methodLocation(Lookup speciesLookup, Class<?> raw, Object classParameters, String name, String descriptor) {
    return new Location(speciesLookup, raw, classParameters, name, descriptor);
  }

  /**
   * Returns a key that can be safety used to represent a reference to this location.
   * @return a key that can be safety used to represent a reference to this location.
   */
  public Key key() {
    if (name != null) {
      return new MethodKey(raw, classParameters, name, descriptor);
    }
    return new ClassKey(raw);
  }

  /**
   * Returns a specialization opaque object that can be provided as the return value of the anchor boostrap method.
   * @param parameters the parameters
   * @return a specialization opaque object that can be provided as the return value of the anchor boostrap method.
   */
  public Object specialize(Object parameters) {
    Objects.requireNonNull(parameters, "parameters is null");
    var anchor = name == null ?
      new Anchor(parameters, null) :
      new Anchor(classParameters, parameters);
    return RT.createKiddyPoolClass(speciesLookup, raw, anchor);
  }
}
