package com.github.forax.civilizer;

import com.github.forax.civilizer.prt.Parametric;
import com.github.forax.civilizer.prt.RT;
import com.github.forax.civilizer.prt.SuperType;
import com.github.forax.civilizer.prt.TypeRestriction;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_Void;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;

/*
 This rewriter works in two passes on the whole Java classes and uses close world assumption
 The first pass goes through all final static fields typed String starts with "$P", all the annotations
  @Parametric and @TypeRestrictions and record the resulting info in the Analysis class.
  The second pass rewrite the bytecode using the Analysis information.

  Analysis Pass:
  - gather the restriction for fields (ClassData.fieldRestrictionMap) and methods (ClassData.methodRestrictionMap)
  - gather the anchors from the annotations @Parametric
    - store the parametric classes (ClassData.parametric)
    - store the parametric methods (ClassData.methodParametricSet)
  - for each constant $P,
     if it's an anchor/parameter create a root with the name (and the parent name if it exists)
     otherwise
       if there is no dependency it's a constant (const),
       for each dependency, find the corresponding root and reduce the roots with
         RootInfo.merge(root, root2):
           const, const -> const
           const, anchor -> anchor
           anchor, const -> anchor
           anchor(name, parent), anchor2(name2, parent2) ->
             name == name2 -> anchor
             name == parent2.name -> name
             parent.name == name2 -> anchor2

  - mark all constants depending on an anchor + the constant bootstrap methods referenced by @Parametric,
    as requiring an accessor at runtime (ClassData.condyFieldAccessors)
  - for all constants, create the corresponding tree of constant dynamics and record if the constant
    should be stored in a kiddy pool or not (ClassData.condyMap)

  Rewriting Pass:
   - Parametric constructors/methods takes a supplementary Object parameter at the end, to be binary backward compatible,
     a variant without the Object is added and delegates to the parametric method with a raw kiddy pool
     (a kiddy pool with the parameters set to null)
   - Parametric constructor initialize an instance field $kiddyPool.
   - String constant + calls to .intern() stores the constant as constant pool/kiddy pool constant
   - the opcodes NEW+INNVOKESPECIAL, INVOKEVIRTUAL, INVOKSTATIC and ANEWARRAY are rewritten as invoke dynamic
     that take the constant pool/kiddy pool constant.
     - the constant pool constant is a constant dynamic if the constant is a constant pool constant
     - the constant pool constant is a string reference to a kiddy pool constant
       the invoke dynamic method takes a supplementary parameter (the kiddy pool class).
       - The kiddy pool class is passed as the last parameter of invoke dynamic (see loadKiddyPool)
         - inside an instance method, the kiddy pool class is stored as an instance field $kiddyPool
         - inside a parametric static method, the kiddy pool class is the last parameter
   - TypeRestricted (not final) fields are initialized to their default value in the constructor,
     and putValue on a TypeRestricted field checks if the value is restricted
   - TypeRestricted method starts with a prolog that to a call that checks the arguments are restricted

   All the creation of the kiddy pool classes, all the type checking of the TypeRestrictions are deferred at runtime,
   see RT.
 */
public final class ParametricRewriter {
  private ParametricRewriter() {
    throw new AssertionError();
  }

  private record Field(String name, String descriptor) {}
  private record Method(String name, String descriptor) {}
  private record FieldRestriction(int access, String constant) {}
  enum AnchorKind {
    ClASS, METHOD;

    String text() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
  private record CondyInfo(boolean inKiddyPool, ConstantDynamic constantDynamic) {}
  @SuppressWarnings("CollectionDeclaredAsConcreteClass")
  private record ClassData(String internalName,
                           boolean parametric,
                           HashMap<String, CondyInfo> condyMap,
                           HashSet<String> condyFieldAccessors,
                           LinkedHashMap<Field,FieldRestriction> fieldRestrictionMap,
                           HashSet<Method> methodParametricSet,
                           HashMap<Method, String> methodRestrictionMap) {}
  private record Analysis(HashMap<String,ClassData> classDataMap) { }

  private static final class RewriterException extends RuntimeException {
    RewriterException(String message) {
      super(message);
    }
  }


  private static final int ACC_IDENTITY = Opcodes.ACC_SUPER;

  private static final String RT_INTERNAL = RT.class.getName().replace('.', '/');
  private static final Handle BSM_LDC = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_ldc",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_NEW_FLATTABLE_ARRAY = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_newFlattableArray",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_STATIC = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_static",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_VIRTUAL = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_virtual",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_NEW = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_new",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_NEW_ARRAY = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_new_array",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_INIT_DEFAULT = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_init_default",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_PUT_VALUE = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_put_value",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_METHOD_RESTRICTION = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_method_restriction",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);

  private static final Handle BSM_CONDY = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_condy",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
      false);
  private static final Handle BSM_RAW_KIDDY_POOL = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_raw_kiddy_pool",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
      false);
  private static final Handle BSM_RAW_METHOD_KIDDY_POOL = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_raw_method_kiddy_pool",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;",
      false);
  private static final Handle BSM_CLASS_DATA = new Handle(H_INVOKESTATIC, MethodHandles.class.getName().replace('.', '/'),
      "classData",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
      false);
  private static final Handle BSM_SUPER_KIDDY_POOL = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_super_kiddy_pool",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_INTERFACE_KIDDY_POOL = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_interface_kiddy_pool",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_TYPE = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_type",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
      false);

  private static ClassData analyze(byte[] buffer) {
    record ProtoCondy(String condyName, String action, List<String> args) {}

    var anchorKindMap = new HashMap<String, AnchorKind>();
    var protoCondies = new ArrayList<ProtoCondy>();
    var condyMap = new LinkedHashMap<String, CondyInfo>();
    var condyFieldAccessors = new HashSet<String> ();
    var fieldRestrictionMap = new LinkedHashMap<Field,FieldRestriction>();
    var methodParametricSet = new HashSet<Method>();
    var methodRestrictionMap = new HashMap<Method, String>();

    var reader = new ClassReader(buffer);
    var access = reader.getAccess();
    if ((access & ACC_ABSTRACT) == 0 && (access & ACC_IDENTITY) == 0) {
      throw new IllegalStateException("class " + reader.getClassName() + " is already declared as a value class");
    }
    var cv = new ClassVisitor(ASM9) {
      private static final String PARAMETRIC_DESCRIPTOR = "L" + Parametric.class.getName().replace('.', '/') + ";";
      private static final String SUPER_TYPE_DESCRIPTOR = "L" + SuperType.class.getName().replace('.', '/') + ";";
      private static final String TYPE_RESTRICTION_DESCRIPTOR = "L" + TypeRestriction.class.getName().replace('.', '/') + ";";

      private String internalName;
      private boolean parametric;

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        internalName = name;
      }

      private AnnotationVisitor parametricAnnotationVisitor(AnchorKind anchorKind) {
        return new AnnotationVisitor(ASM9) {
          @Override
          public void visit(String name, Object value) {
            if (!name.equals("value") || !(value instanceof String constant)) {
              throw new AssertionError("Parametric is malformed !");
            }
            if (!constant.isEmpty()) {   // FIXME ??
              anchorKindMap.merge(constant, anchorKind, (k1, k2) -> {
                if (k1 != k2) {
                  throw new RewriterException("anchor " + constant + " defined as both a class and a method anchor");
                }
                return k1;
              });
              // an accessor must be generated
              condyFieldAccessors.add("$" + constant);
            }
          }
        };
      }

      private AnnotationVisitor superTypeAnnotationVisitor() {
        return new AnnotationVisitor(ASM9) {
          @Override
          public void visit(String name, Object value) {
            if (!name.equals("value") || !(value instanceof String constant)) {
              throw new AssertionError("SuperType is malformed !");
            }
            // an accessor must be generated
            condyFieldAccessors.add("$" + constant);
          }
        };
      }

      private AnnotationVisitor restrictionAnnotationVisitor(Consumer<String> valueConsumer) {
        return new AnnotationVisitor(ASM9) {
          @Override
          public void visit(String name, Object value) {
            if (!name.equals("value")) {
              throw new AssertionError("TypeRestriction is malformed !");
            }
            valueConsumer.accept((String) value);
          }
        };
      }

      @Override
      public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (descriptor.equals(PARAMETRIC_DESCRIPTOR)) {
          parametric = true;
          return parametricAnnotationVisitor(AnchorKind.ClASS);
        }
        if (descriptor.equals(SUPER_TYPE_DESCRIPTOR)) {
          return superTypeAnnotationVisitor();
        }
        return null;
      }

      @Override
      public FieldVisitor visitField(int access, String fieldName, String fieldDescriptor, String signature, Object value) {
        if (fieldName.startsWith("$P") && value instanceof String s) {
          // constant pool description
          System.out.println("  constant pool constant " + fieldName + " value: " + value);

          var tokens = s.split(" ");
          if (tokens.length < 1) {
            throw new RewriterException("malformed constant value, it should start with an action, " + value);
          }
          var action = tokens[0];
          var args = Arrays.stream(tokens).skip(1).toList();
          var condyName = fieldName.substring(1);
          protoCondies.add(new ProtoCondy(condyName, action, args));
        }

        return new FieldVisitor(ASM9) {
          @Override
          public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(TYPE_RESTRICTION_DESCRIPTOR)) {
              return restrictionAnnotationVisitor(value -> fieldRestrictionMap.put(new Field(fieldName, fieldDescriptor), new FieldRestriction(access, value)));
            }
            return null;
          }
        };
      }

      @Override
      public MethodVisitor visitMethod(int access, String methodName, String methodDescriptor, String signature, String[] exceptions) {
        return new MethodVisitor(ASM9) {
          @Override
          public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(PARAMETRIC_DESCRIPTOR)) {
              methodParametricSet.add(new Method(methodName, methodDescriptor));
              return parametricAnnotationVisitor(AnchorKind.METHOD);
            }
            if (descriptor.equals(TYPE_RESTRICTION_DESCRIPTOR)) {
              return restrictionAnnotationVisitor(value -> methodRestrictionMap.put(new Method(methodName, methodDescriptor), value));
            }
            return null;
          }
        };
      }

      private Object condyArgument(String arg) {
        return switch (arg.charAt(0)) {
          case 'V', 'Z', 'B', 'C', 'S', 'I', 'J', 'F', 'D' -> new ConstantDynamic(arg, "Ljava/lang/Object;", BSM_TYPE);
          case 'L' -> Type.getType(arg);
          case 'P' -> {
            var condyInfo = condyMap.get(arg.substring(0, arg.length() - 1));
            if (condyInfo == null) {
              throw new IllegalStateException("undefined condy " + arg);
            }
            yield condyInfo.constantDynamic;
          }
          case '(' -> Type.getMethodType(arg);
          case '\'' -> arg.substring(1);
          default -> {
            if (arg.indexOf('.') != -1) {
              yield Double.parseDouble(arg);
            }
            yield Integer.valueOf(arg);
          }
        };
      }

      private void populateCondyMap(Set<ProtoCondy> kiddyPoolConstants) {
        for (var protoCondy : protoCondies) {
          var condyName = protoCondy.condyName;
          var action = protoCondy.action;
          var protoArgs = protoCondy.args;
          var args = action.equals("anchor") ?
              List.of(decodeAnchorAction(condyName, protoArgs).text()) :
              protoArgs.stream().map(this::condyArgument).toList();
          var bsmConstants = Stream.concat(Stream.of(action), args.stream()).toArray();
          var constantDynamic = new ConstantDynamic(condyName, "Ljava/lang/Object;", BSM_CONDY, bsmConstants);
          var inKiddyPool = kiddyPoolConstants.contains(protoCondy);
          var condyInfo = new CondyInfo(inKiddyPool, constantDynamic);
          condyMap.put(condyName, condyInfo);
        }
      }

      private AnchorKind decodeAnchorAction(String condyName, List<String> args) {
        assert args.isEmpty() || args.size() > 2;
        var argument = condyArgument(args.getFirst());
        if (!(argument instanceof ConstantDynamic reference)) {  // FIXME, it can be a reference to the parent kiddy pool
          throw new RewriterException("anchor argument is not a reference to a constant " + condyName);
        }
        var anchorKind = anchorKindMap.get(reference.getName());
        if (anchorKind == null) {
          throw new RewriterException("constant " + condyName + ", anchor reference " + reference + " is not referenced by @Parametric");
        }
        return anchorKind;
      }



      private record RootInfo(RootKind kind, String name, String parentName) {
        private enum RootKind { ANCHOR, CONST }

        RootInfo merge(RootInfo root, ProtoCondy protoCondy) {
          return switch (kind) {
            case ANCHOR -> switch (root.kind) {
              case ANCHOR -> {
                if (name.equals(root.name) || parentName.equals(root.name)) {
                  yield this;
                }
                if (name.equals(root.parentName)) {
                  yield root;
                }
                throw new RewriterException("constant " + protoCondy.condyName + " depends on two anchors " + name +  " and " + root.name +
                    " and one is not parent of the other");
              }
              case CONST -> this;
            };
            case CONST -> switch (root.kind) {
              case ANCHOR -> root;
              case CONST -> this;  // choose the first const
            };
          };
        }
      }

      private static RootInfo anchorRoot(ProtoCondy protoCondy, Map<String, ProtoCondy> protoCondyMap) {
        if (protoCondy.args.isEmpty() || protoCondy.args.size() > 2) {
          throw new RewriterException("anchor " + protoCondy.condyName + " has not the right number of arguments");
        }
        String parentName;
        if (protoCondy.args.size() == 2) {  // a parent root is specified
          var parentArg = protoCondy.args.get(1);
          if (!parentArg.endsWith(";") || !parentArg.startsWith("P")) {
            throw new RewriterException("parent ref of anchor " + protoCondy.condyName + " is not a reference");
          }
          var parentRef = parentArg.substring(0, parentArg.length() - 1);
          var parent = protoCondyMap.get(parentRef);
          if (parent == null) {
            throw new RewriterException("unknown reference to parent anchor " + parentRef + " when parsing constant " + protoCondy.condyName);
          }
          if (!parent.action.equals("anchor")) {
            throw new RewriterException("parent anchor " + parentRef + " of " + protoCondy.condyName + " does not reference an anchor");
          }
          parentName = parent.condyName;
        } else {
          parentName = "";    // no parent
        }
        return new RootInfo(RootInfo.RootKind.ANCHOR, protoCondy.condyName, parentName);
      }

      private static RootInfo findRoot(ProtoCondy protoCondy, Map<String, ProtoCondy> protoCondyMap, Map<ProtoCondy, RootInfo> rootMap) {
        var cachedRoot = rootMap.get(protoCondy);
        if (cachedRoot != null) {
          return cachedRoot;
        }
        // an anchor is a root
        if (protoCondy.action.equals("anchor")) {
          var root =  anchorRoot(protoCondy, protoCondyMap);
          rootMap.put(protoCondy, root);
          return root;
        }
        var root = (RootInfo) null;
        for(var arg: protoCondy.args) {
          // dependency ?
          if (arg.endsWith(";") && arg.startsWith("P")) {
            var dependencyRef = arg.substring(0, arg.length() - 1);
            var dependency = protoCondyMap.get(dependencyRef);
            if (dependency == null) {
              throw new RewriterException("unknown reference " + dependencyRef + " when parsing constant " + protoCondy.condyName);
            }
            var dependencyRoot = findRoot(dependency, protoCondyMap, rootMap);
            if (root == null) {
              root = dependencyRoot;
            } else {
              root = root.merge(dependencyRoot, dependency);
            }
          }
        }
        if (root == null) {
          root = new RootInfo(RootInfo.RootKind.CONST, protoCondy.condyName, "");
        }
        rootMap.put(protoCondy, root);
        return root;
      }

      private static void dumpDependencyAnalysis(Map<ProtoCondy, RootInfo> rootMap) {
        var dependencyMap = rootMap.entrySet().stream()
            .filter(rootEntry -> rootEntry.getValue().kind == RootInfo.RootKind.ANCHOR)
            .collect(groupingBy(Entry::getValue, mapping(Entry::getKey, toList())));
        if (dependencyMap.isEmpty()) {
          return;
        }
        System.out.println("  dependencies:");
        for(var dependencyEntry: dependencyMap.entrySet()) {
          System.out.println("    anchor " + dependencyEntry.getKey().name + ": " + dependencyEntry.getValue().stream().map(ProtoCondy::condyName).collect(joining(", ")));
        }
      }

      private Set<ProtoCondy> analyzeCondyDependencies() {
        var protoCondyMap = protoCondies.stream().collect(toMap(ProtoCondy::condyName, p -> p));
        var rootMap = new LinkedHashMap<ProtoCondy, RootInfo>();
        for (var protoCondy : protoCondies) {
          findRoot(protoCondy, protoCondyMap, rootMap);
        }
        dumpDependencyAnalysis(rootMap);

        var kiddyPoolConstants = rootMap.entrySet().stream()
            .filter(entry -> entry.getValue().kind == RootInfo.RootKind.ANCHOR)
            .map(Entry::getKey)
            .collect(Collectors.toSet());
        // an accessor must be generated ?
        for(var protoCondy: kiddyPoolConstants) {
          var fieldName = "$" + protoCondy.condyName;
          condyFieldAccessors.add(fieldName);
        }
        return kiddyPoolConstants;
      }

      @Override
      public void visitEnd() {
        var kiddyPoolConstants = analyzeCondyDependencies();
        populateCondyMap(kiddyPoolConstants);
      }
    };
    reader.accept(cv, 0);

    return new ClassData(cv.internalName, cv.parametric, condyMap, condyFieldAccessors, fieldRestrictionMap, methodParametricSet, methodRestrictionMap);
  }

  private static void rewrite(List<Path> classes, Analysis analysis) throws IOException {
    for(var path: classes) {
      try(var input = Files.newInputStream(path)) {
        System.out.println("rewrite " + path);
        var dataOpt = rewrite(input.readAllBytes(), analysis);
        if (dataOpt.isEmpty()) {
          System.out.println("  skip as value class");
          continue;
        }
        Files.write(path, dataOpt.orElseThrow());
      }
    }
  }


  private static Optional<byte[]> rewrite(byte[] buffer, Analysis analysis) {
    var reader = new ClassReader(buffer);
    var isInterface = (reader.getAccess() & ACC_INTERFACE) != 0;
    var internalName = reader.getClassName();
    var supername = reader.getSuperName();
    var classDataMap = analysis.classDataMap;
    var classData = analysis.classDataMap.get(internalName);
    if (classData == null) {
      return Optional.empty();
    }
    var writer = new ClassWriter(0);
    var cv = new ClassVisitor(ASM9, writer) {
      private CondyInfo findCondyInfo(String ldcConstant) {
        var condyInfo =  classData.condyMap.get(ldcConstant);
        if (condyInfo == null) {
          throw new RewriterException("unknown constant pool constant '" + ldcConstant + "' in class " + internalName);
        }
        return condyInfo;
      }

      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (name.startsWith("$P")) {
          var condyName = name.substring(1);
          var condyInfo = findCondyInfo(condyName);
          if (classData.condyFieldAccessors.contains(name)) {
            // we need accessors for the constant dynamic referenced from outside
            var mv = cv.visitMethod(ACC_STATIC | ACC_PRIVATE | ACC_SYNTHETIC, name, "()Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitLdcInsn(condyInfo.constantDynamic);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 0);
            mv.visitEnd();
          }
          return null;
        }
        return super.visitField(access, name, descriptor, signature, value);
      }

      private void delegateMethod(int access, String name, String methodDescriptor, String newMethodDescriptor, Handle bsm, Object... bsmConstants) {
        var mv = super.visitMethod(access, name, methodDescriptor, null, null);
        mv.visitCode();
        int slot;
        int opcode;
        if ((access & ACC_STATIC) != 0) {
          slot = 0;
          opcode = INVOKESTATIC;
        } else {
          slot = 1;
          opcode = name.equals("<init>") ? INVOKESPECIAL: INVOKEVIRTUAL;
          mv.visitVarInsn(ALOAD, 0);
        }
        for(var type: Type.getArgumentTypes(methodDescriptor)) {
          mv.visitVarInsn(type.getOpcode(ILOAD), slot);
          slot += type.getSize();
        }
        mv.visitLdcInsn(new ConstantDynamic("rawKiddyPool", "Ljava/lang/Object;", bsm, bsmConstants));
        mv.visitMethodInsn(opcode, internalName, name, newMethodDescriptor, false);
        mv.visitInsn(Type.getReturnType(newMethodDescriptor).getOpcode(IRETURN));
        mv.visitMaxs(slot + 1, slot);
        mv.visitEnd();
      }

      @Override
      public MethodVisitor visitMethod(int access, String methodName, String methodDescriptor, String signature, String[] exceptions) {
        var parametricMethod = classData.methodParametricSet.contains(new Method(methodName, methodDescriptor));
        MethodVisitor delegate;
        if (classData.parametric && methodName.equals("<init>")) { // need to initialize the kiddy pool
          var desc = MethodTypeDesc.ofDescriptor(methodDescriptor);
          desc = desc.insertParameterTypes(desc.parameterCount(), CD_Object);
          var newMethodDescriptor = desc.descriptorString();
          delegateMethod(access, "<init>", methodDescriptor, newMethodDescriptor, BSM_RAW_KIDDY_POOL);
          var mv = super.visitMethod(access | ACC_SYNTHETIC, methodName, newMethodDescriptor, null, null);
          delegate = new MethodVisitor(ASM9, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
              if (opcode == INVOKESPECIAL && owner.equals(supername) && name.equals("<init>")) {
                var kiddyPoolSlot = Type.getArgumentsAndReturnSizes(methodDescriptor) >> 2;  // class is parametric
                var superIsParametric = Optional.ofNullable(classDataMap.get(supername)).map(ClassData::parametric).orElse(false);
                if (superIsParametric) {
                  // we need to compute the kiddy pool of the super class
                  mv.visitVarInsn(ALOAD, kiddyPoolSlot);
                  mv.visitInvokeDynamicInsn("super", "(Ljava/lang/Object;)Ljava/lang/Object;", BSM_SUPER_KIDDY_POOL);
                  var desc = MethodTypeDesc.ofDescriptor(descriptor);
                  desc = desc.insertParameterTypes(desc.parameterCount(), CD_Object);
                  super.visitMethodInsn(opcode, owner, name, desc.descriptorString(), isInterface);
                } else {
                  super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }

                // init kiddy pool field
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, kiddyPoolSlot);
                mv.visitFieldInsn(PUTFIELD, internalName, "$kiddyPool", "Ljava/lang/Object;");

                // init default values of fields depending on the type restrictions
                for(var fieldEntry: classData.fieldRestrictionMap.entrySet()) {
                  var fieldRestriction = fieldEntry.getValue();
                  if ((fieldRestriction.access & ACC_FINAL) != 0) {
                    continue;  // no need to initialize final field
                  }
                  var field = fieldEntry.getKey();
                  var constant = fieldRestriction.constant;
                  mv.visitVarInsn(ALOAD, 0);
                  var condyInfo = findCondyInfo(constant);
                  var desc = MethodTypeDesc.of(ClassDesc.ofDescriptor(field.descriptor));
                  if (condyInfo.inKiddyPool) {
                    mv.visitVarInsn(ALOAD, kiddyPoolSlot); // load $kiddyPool
                    desc = desc.insertParameterTypes(0, CD_Object);
                  }
                  mv.visitInvokeDynamicInsn("initDefault", desc.descriptorString(), BSM_INIT_DEFAULT, constant);
                  mv.visitFieldInsn(PUTFIELD, internalName, field.name, field.descriptor);
                }
                return;
              }
              super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
              super.visitMaxs(Math.max(maxStack, 2) , maxLocals + 1);
            }
          };
        } else {
          if (parametricMethod) {
            var desc = MethodTypeDesc.ofDescriptor(methodDescriptor);
            desc = desc.insertParameterTypes(desc.parameterCount(), CD_Object);
            var newMethodDescriptor = desc.descriptorString();
            var tag = ((access & ACC_STATIC) != 0) ? H_INVOKESTATIC : H_INVOKEVIRTUAL;
            var methodHandle = new Handle(tag, internalName, methodName, newMethodDescriptor, false);
            delegateMethod(access, methodName, methodDescriptor, newMethodDescriptor, BSM_RAW_METHOD_KIDDY_POOL, methodHandle);
            delegate = super.visitMethod(access| ACC_SYNTHETIC, methodName, newMethodDescriptor, signature, exceptions);
          } else {
            delegate = super.visitMethod(access, methodName, methodDescriptor, signature, exceptions);
          }
        }

        var isStatic = (access & ACC_STATIC) != 0;
        var kiddyPoolSlot = (isStatic? -1 : 0) + (Type.getArgumentsAndReturnSizes(methodDescriptor) >> 2);  // if the method is parametric

        return new MethodVisitor(ASM9, delegate) {
          private String ldcConstant;
          private Object constantValue;
          private boolean removeDUP;

          @Override
          public void visitCode() {
            super.visitCode();
            var constant = classData.methodRestrictionMap.get(new Method(methodName, methodDescriptor));
            if (constant != null) {
              var slot = isStatic? 0 : 1;
              for(var type : Type.getArgumentTypes(methodDescriptor)) {
                mv.visitVarInsn(type.getOpcode(ILOAD), slot);
                slot += type.getSize();
              }
              var condyInfo = findCondyInfo(constant);
              var desc = MethodTypeDesc.ofDescriptor(methodDescriptor).changeReturnType(CD_void);
              if (condyInfo.inKiddyPool) {
                loadKiddyPool();
                desc = desc.insertParameterTypes(1, CD_Object);
              }
              mv.visitInvokeDynamicInsn(methodName, desc.descriptorString(), BSM_METHOD_RESTRICTION, constant);
            }
          }

          private void loadKiddyPool() {
            if (parametricMethod) {
              mv.visitVarInsn(ALOAD, kiddyPoolSlot);
            } else {
              if (isInterface) {
                mv.visitVarInsn(ALOAD, 0);
                var desc = MethodTypeDesc.of(CD_Object, ClassDesc.ofInternalName(internalName));
                mv.visitInvokeDynamicInsn("$kiddyPool", desc.descriptorString(), BSM_INTERFACE_KIDDY_POOL);
              } else {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, internalName, "$kiddyPool", "Ljava/lang/Object;");
              }
            }
          }

          @Override
          public void visitVarInsn(int opcode, int varIndex) {
            if (parametricMethod && varIndex >= kiddyPoolSlot) {
              varIndex++;
            }
            super.visitVarInsn(opcode, varIndex);
          }

          @Override
          public void visitLdcInsn(Object value) {
            if (value instanceof String s) {
              ldcConstant = s;
            }
            super.visitLdcInsn(value);
          }

          @Override
          public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            var classData = classDataMap.get(owner);
            if (opcode == PUTFIELD && classData != null && classData.parametric) {
              var fieldRestriction = classData.fieldRestrictionMap.get(new Field(name, descriptor));
              if (fieldRestriction != null) {
                var constant = fieldRestriction.constant;
                var condyInfo = findCondyInfo(constant);
                var desc = MethodTypeDesc.of(CD_Void, ClassDesc.ofInternalName(owner), ClassDesc.ofDescriptor(descriptor));
                if (condyInfo.inKiddyPool) {
                  loadKiddyPool();
                  desc = desc.insertParameterTypes(1, CD_Object);
                }
                mv.visitInvokeDynamicInsn(name, desc.descriptorString(), BSM_PUT_VALUE, constant);
                return;
              }
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
          }

          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (owner.equals("java/lang/String") && name.equals("intern") && descriptor.equals("()Ljava/lang/String;") && ldcConstant != null) {
              // record constant
              var constant = ldcConstant;
              ldcConstant = null;
              var condyInfo = findCondyInfo(constant);
              constantValue = condyInfo.inKiddyPool ?  constant : condyInfo.constantDynamic;
              mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              return;
            }
            if (owner.equals(RT_INTERNAL) && name.equals("ldc") && descriptor.equals("()Ljava/lang/Object;")) {
              var constant = constantValue;
              if (constant == null) {
                throw new RewriterException("no constant info for ldc");
              }
              constantValue = null;
              if (constant instanceof ConstantDynamic condy) {
                mv.visitLdcInsn(condy);
              } else {
                loadKiddyPool();
                mv.visitInvokeDynamicInsn("ldc", "(Ljava/lang/Object;)Ljava/lang/Object;", BSM_LDC, constant);
              }
              return;
            }
            if (owner.equals(RT_INTERNAL) && name.equals("newFlattableArray") && descriptor.equals("(I)[Ljava/lang/Object;")) {
              var constant = constantValue;
              if (constant == null) {
                throw new RewriterException("no constant info for newFlattableArray");
              }
              constantValue = null;
              var desc = MethodTypeDesc.ofDescriptor(descriptor);
              if (!(constant instanceof ConstantDynamic)) {
                loadKiddyPool();
                desc = desc.insertParameterTypes(desc.parameterCount(), CD_Object);
              }
              mv.visitInvokeDynamicInsn("newFlattableArray", desc.descriptorString(), BSM_NEW_FLATTABLE_ARRAY, constant);
              return;
            }


            var parametricOwner = (boolean) Optional.ofNullable(classDataMap.get(owner)).map(ClassData::parametric).orElse(false);
            var parametricCall = (boolean) Optional.ofNullable(classDataMap.get(owner)).map(cd -> cd.methodParametricSet.contains(new Method(name, descriptor))).orElse(false);

            if ((parametricOwner || parametricCall) && isInterface) {
              throw new RewriterException("parametric call to interfaces is not supported");
            }

            switch (opcode) {
              case INVOKESPECIAL -> {
                if (parametricOwner && constantValue != null) {
                  var constant = constantValue;
                  constantValue = null;
                  var desc = MethodTypeDesc.ofDescriptor(descriptor);
                  desc = desc.changeReturnType(ClassDesc.ofInternalName(owner));
                  if (!(constant instanceof ConstantDynamic)) {
                    loadKiddyPool();
                    desc = desc.insertParameterTypes(desc.parameterCount(), CD_Object);
                  }
                  mv.visitInvokeDynamicInsn("new", desc.descriptorString(), BSM_NEW, constant);
                  return;
                }
              }
              case INVOKESTATIC -> {
                if (parametricCall && constantValue != null) {
                  var constant = constantValue;
                  constantValue = null;
                  var desc = MethodTypeDesc.ofDescriptor(descriptor);
                  if (!(constant instanceof ConstantDynamic)) {
                    loadKiddyPool();
                    desc = desc.insertParameterTypes(desc.parameterCount(), CD_Object);
                  }
                  mv.visitInvokeDynamicInsn(name, desc.descriptorString(), BSM_STATIC, Type.getObjectType(owner), constant);
                  return;
                }
              }
              case INVOKEVIRTUAL -> {
                if (parametricCall && constantValue != null) {
                  var constant = constantValue;
                  constantValue = null;
                  var desc = MethodTypeDesc.ofDescriptor(descriptor);
                  desc = desc.insertParameterTypes(0, ClassDesc.ofInternalName(owner));
                  if (!(constant instanceof ConstantDynamic)) {
                    loadKiddyPool();
                    desc = desc.insertParameterTypes(desc.parameterCount(), CD_Object);
                  }
                  mv.visitInvokeDynamicInsn(name, desc.descriptorString(), BSM_VIRTUAL, constant);
                  return;
                }
              }
              case INVOKEINTERFACE -> {
                // calling a parametric interface is not supported, so no rewrite
              }
              default -> throw new AssertionError("invalid opcode " + opcode);
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
          }

          @Override
          public void visitTypeInsn(int opcode, String type) {
            switch (opcode) {
              case NEW -> {
                if (constantValue != null) {  // remove NEW DUP
                  removeDUP = true;
                  return;  // skip NEW
                }
              }
              case ANEWARRAY -> {
                if (constantValue != null) {
                  var constant = constantValue;
                  constantValue = null;
                  var desc = MethodTypeDesc.of(ClassDesc.ofInternalName(type).arrayType(), CD_int);
                  if (!(constant instanceof ConstantDynamic)) {
                    loadKiddyPool();
                    desc = desc.insertParameterTypes(desc.parameterCount(), CD_Object);
                  }
                  mv.visitInvokeDynamicInsn("newArray", desc.descriptorString(), BSM_NEW_ARRAY, constant);
                  return;
                }
              }
            }
            super.visitTypeInsn(opcode, type);
          }

          @Override
          public void visitInsn(int opcode) {
            if (opcode == DUP && removeDUP) {
              removeDUP = false;
              return;  // skip DUP
            }
            super.visitInsn(opcode);
          }

          @Override
          public void visitMaxs(int maxStack, int maxLocals) {
            maxStack++;
            maxLocals++;
            var constant = classData.methodRestrictionMap.get(new Method(methodName, methodDescriptor));
            if (constant != null) {  // need to fix the stack
              maxStack = Math.max(maxStack, Type.getArgumentsAndReturnSizes(methodDescriptor) - 1);
            }
            super.visitMaxs(maxStack, maxLocals);
          }

          @Override
          public void visitEnd() {
            if (constantValue != null) {
              throw new RewriterException("in method " + internalName + "." + methodName + methodDescriptor + ", unbounded constant value " + constantValue);
            }
            super.visitEnd();
          }
        };
      }

      @Override
      public void visitEnd() {
        if (classData.parametric) {  // parametric class
          if (!isInterface) {
            var fv = cv.visitField(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, "$kiddyPool", "Ljava/lang/Object;", null, null);
            fv.visitEnd();
          }

          if (!classData.methodParametricSet.isEmpty()) {  //FIXME better to check if there is an anchor with a parent anchor
            var mv = cv.visitMethod(ACC_STATIC | ACC_PRIVATE | ACC_SYNTHETIC, "$classData", "()Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitLdcInsn(new ConstantDynamic("_", "Ljava/lang/Object;", BSM_CLASS_DATA));
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 0);
            mv.visitEnd();
          }
        }
        super.visitEnd();
      }
    };
    reader.accept(cv, 0);

    return Optional.of(writer.toByteArray());
  }



  private static Analysis analyze(List<Path> classes) throws IOException {
    var classDataMap = new HashMap<String, ClassData>();
    for (var path : classes) {
      try (var input = Files.newInputStream(path)) {
        var bytecode = input.readAllBytes();
        System.out.println("analyze " + path);
        var classData = analyze(bytecode);
       classDataMap.put(classData.internalName, classData);
      }
    }
    return new Analysis(classDataMap);
  }

  private static List<Path> classes(Path folder) throws IOException {
    if (!Files.exists(folder)) {
      return List.of();
    }
    try(var paths = Files.walk(folder)) {
      return paths
          .filter(p -> p.toString().endsWith(".class"))
          .toList();
    }
  }

  public static void main(String[] args) throws IOException {
    var main = classes(Path.of("target/classes"));
    var test = classes(Path.of("target/test-classes"));
    var classes = Stream.concat(main.stream(), test.stream()).toList();

    var analysis = analyze(classes);
    //analysis.dump();
    rewrite(classes, analysis);
  }
}
