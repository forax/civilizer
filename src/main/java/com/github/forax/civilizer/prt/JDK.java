package com.github.forax.civilizer.prt;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Bootstrap methods used by the anchors.
 */
@SuppressWarnings("unused")  // used by reflection
public final class JDK {
  private JDK() {
    throw new AssertionError();
  }

  private record SpecializationKey(Location.Key key, Object parameters) {}

  private static final ConcurrentHashMap<SpecializationKey, Object> CACHE = new ConcurrentHashMap<>();

  @SuppressWarnings("unused")  // used by reflection
  public static Object erase(Object locationObj, Object parametersObj, Object defaultsObj) {
    var location = (Location) locationObj;
    var parameterList = (List<?>) parametersObj;
    var defaultList = (List<?>) defaultsObj;
    return erase(location, parameterList, defaultList);
  }

  @SuppressWarnings("WeakerAccess")  // used by reflection
  public static Object erase(Location location, List<?> parameters, List<?> defaults) {
    //System.out.println("erase " + location + " " + parameters + " " + defaults);
    Objects.requireNonNull(location, "location is null");
    Objects.requireNonNull(defaults, "defaults is null");
    List<?> erasedList;
    if (parameters == null) {
      erasedList = defaults;
    } else {
      if (parameters.size() != defaults.size()) {
        throw new LinkageError("instantiation arguments " + parameters + " and default arguments " + defaults + " have no the same size");
      }
      erasedList = IntStream.range(0, parameters.size())
          .mapToObj(i -> {
            var parameter = parameters.get(i);
            return parameter instanceof Class<?> clazz && com.github.forax.civilizer.vrt.RT.isValue(clazz) ? clazz : defaults.get(i);
          }).toList();
    }
    var key = new SpecializationKey(location.key(), erasedList);
    return CACHE.computeIfAbsent(key, k -> location.specialize(erasedList));
  }

  @SuppressWarnings("unused")  // used by reflection
  public static Object identity(Object locationObj, Object parametersObj) {
    var location = (Location) locationObj;
    return identity(location, parametersObj);
  }

  @SuppressWarnings("WeakerAccess")  // used by reflection
  public static Object identity(Location location, Object parameters) {
    Objects.requireNonNull(location, "location is null");
    Objects.requireNonNull(parameters, "parameters is null");
    var key = new SpecializationKey(location.key(), parameters);
    return CACHE.computeIfAbsent(key, k -> location.specialize(parameters));
  }
}
