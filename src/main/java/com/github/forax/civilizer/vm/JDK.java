package com.github.forax.civilizer.vm;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

@SuppressWarnings("unused")  // used by reflection
public final class JDK {
  private JDK() {
    throw new AssertionError();
  }

  private static final ConcurrentHashMap<Location.Key, Object> CACHE = new ConcurrentHashMap<>();

  @SuppressWarnings("unused")  // used by reflection
  public static Object erase(/*Location*/Object locationObj, /*List<?>*/Object parametersObj, /*List<?>*/Object defaultsObj) {
    //System.out.println("erase " + locationObj + " " + parametersObj + " " + defaultsObj);
    var location = (Location) locationObj;
    var parameterList = (List<?>) parametersObj;
    var defaultList = (List<?>) defaultsObj;
    List<?> erasedList;
    if (parameterList == null) {
      erasedList = defaultList;
    } else {
      if (parameterList.size() != defaultList.size()) {
        throw new LinkageError("instantiation arguments " + parameterList + " and default arguments " + defaultList + " have no the same size");
      }
      erasedList = IntStream.range(0, parameterList.size())
          .mapToObj(i -> {
            var parameter = parameterList.get(i);
            return parameter instanceof Class<?> clazz && com.github.forax.civilizer.runtime.RT.isSecondaryType(clazz) ? clazz : defaultList.get(i);
          }).toList();
    }
    var key = location.key(erasedList);
    return CACHE.computeIfAbsent(key, k -> location.specialize(erasedList));
  }

  @SuppressWarnings("unused")  // used by reflection
  public static Object identity(/*Location*/Object locationObj, Object parameters) {
    var location = (Location) locationObj;
    var key = location.key(parameters);
    return CACHE.computeIfAbsent(key, k -> location.specialize(parameters));
  }
}
