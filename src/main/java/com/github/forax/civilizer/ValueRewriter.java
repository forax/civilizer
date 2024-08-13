package com.github.forax.civilizer;

import com.github.forax.civilizer.vrt.NonNull;
import com.github.forax.civilizer.vrt.Nullable;
import com.github.forax.civilizer.vrt.Value;
import com.github.forax.civilizer.vrt.ImplicitlyConstructible;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V23;

/**
 * This rewriter works in two passes over the classes
 * The first pass gathers
 *  - the class declaration annotations : @Value, @ImplicitlyConstructible
 *  - the field annotations : @Nullable or @NonNull
 *  - the method parameter annotations : @Nullable or @NonNull
 *  - method parameter types, return type and field types of each class
 *
 *  The second pass
 *  - at class declaration, if not annotated with @Value or @ImplicitlyConstructible, set ACC_IDENTITY
 *    set the minor version to PREVIEW (0xFFFF0000)
 *    add annotation jdk.internal.vm.annotation.ImplicitlyConstructible if @ImplicitlyConstructible is set
 *  - at field declaration,
 *    if the class is annotated with @Value or @ImplicitlyConstructible, add ACC_STRICT
 *    (TODO: should verify that all fields are initialized before the call to super())
 *    add annotation jdk.internal.vm.annotation.NullRestricted if @NonNull is set
 *  - at method entry, add Objects.requireNonNull for all @NonNull parameters
 *  - add attribute LoadableDescriptors wih all method parameter types, return type and field types that are
 *    either annotated with @Value or @ImplicitlyConstructible
 */
public final class ValueRewriter {
  private ValueRewriter() {
    throw new AssertionError();
  }

  private enum TypeKind {
    IDENTITY, VALUE, ZERO_DEFAULT;

    boolean isIdentity() {
      return this == IDENTITY;
    }

    boolean isZeroDefault() {
      return this == ZERO_DEFAULT;
    }

    TypeKind max(TypeKind kind) {
      return compareTo(kind) > 0 ? this: kind;
    }
  }
  private enum NullKind { NONNULL, NULLABLE }

  private record ClassData(String internalName, String superName, TypeKind typeKind, Set<String> descriptors, Map<String,FieldData> fieldDataMap, Map<String,MethodData> methodDataMap) { }
  private record FieldData(NullKind nullKind) {}
  private record MethodData(Map<Integer, NullKind> parameterMap) {}


  private record Analysis(Map<String,ClassData> classDataMap) {
    void dump() {
      for(var classDataEntry: classDataMap.entrySet()) {
        var typeName = classDataEntry.getKey();
        var classData = classDataEntry.getValue();
        System.out.println("type " + classData.typeKind + " " + typeName);
        for(var fieldEntry: classData.fieldDataMap.entrySet()) {
          var fieldName = fieldEntry.getKey();
          var fieldData= fieldEntry.getValue();
          System.out.println(" field " + fieldData.nullKind + " " + fieldName);
        }
        for(var methodEntry: classData.methodDataMap.entrySet()) {
          var methodName = methodEntry.getKey();
          var methodData = methodEntry.getValue();
          System.out.println(" method " + methodName + " " + methodData.parameterMap);
        }
      }
    }
  }


  private static final int ACC_IDENTITY = Opcodes.ACC_SUPER;
  private static final int ACC_STRICT = Opcodes.ACC_STRICT;

  private static final String NON_NULL_DESCRIPTOR = NonNull.class.descriptorString();
  private static final String NULLABLE_DESCRIPTOR = Nullable.class.descriptorString();

  private static final String VALUE_DESCRIPTOR = Value.class.descriptorString();
  private static final String ZERO_DEFAULT_DESCRIPTOR = ImplicitlyConstructible.class.descriptorString();

  private static final String IMPLICIT_CONSTRUCTIBLE_DESCRIPTOR = "Ljdk/internal/vm/annotation/ImplicitlyConstructible;";
  private static final String NULL_RESTRICTED_DESCRIPTOR = "Ljdk/internal/vm/annotation/NullRestricted;";

  private static final class LoadableDescriptorsAttribute extends Attribute {
    private final List<String> descriptors ;

    LoadableDescriptorsAttribute(List<String> descriptors) {
      super("LoadableDescriptorsAttribute");
      this.descriptors = descriptors;
    }

    @Override
    protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeOffset, Label[] labels) {
      var classesCount = classReader.readUnsignedShort(offset);
      offset += 2;
      var descriptors = new ArrayList<String>();
      for(var i = 0; i < classesCount; i++) {
        var descriptor = classReader.readUTF8(offset, charBuffer);
        descriptors.add(descriptor);
        offset += 2;
      }
      return new LoadableDescriptorsAttribute(descriptors);
    }

    @Override
    protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
      var byteVector = new ByteVector();
      byteVector.putShort(descriptors.size());
      for (var descriptor : descriptors) {
        byteVector.putShort(classWriter.newUTF8(descriptor));
      }
      return byteVector;
    }
  }

  private static Analysis analyze(List<Path> classes) throws IOException {
    var classDataMap = new HashMap<String, ClassData>();
    for (var path : classes) {
      try (var input = Files.newInputStream(path)) {
        var bytecode = input.readAllBytes();
        System.out.println("analyze " + path);
        var classDataOpt = analyze(bytecode);
        classDataOpt.ifPresent(classData -> classDataMap.put(classData.internalName, classData));
      }
    }

    return new Analysis(classDataMap);
  }

  private static Optional<NullKind> nullKind(String descriptor) {
    if (descriptor.equals(NON_NULL_DESCRIPTOR)) {
      return Optional.of(NullKind.NONNULL);
    }
    if (descriptor.equals(NULLABLE_DESCRIPTOR)) {
      return Optional.of(NullKind.NULLABLE);
    }
    return Optional.empty();
  }

  private static Optional<ClassData> analyze(byte[] buffer) {
    var fieldDataMap = new HashMap<String, FieldData>();
    var methodDataMap = new HashMap<String, MethodData>();

    var cv = new ClassVisitor(ASM9) {
      private String internalName;
      private String superName;
      private TypeKind typeKind;

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if ((access & ACC_IDENTITY) == 0) {
          throw new AssertionError("can not rewrite a class that is already a value class, try mvn clean");
        }
        internalName = name;
        this.superName = superName;
        typeKind = TypeKind.IDENTITY;
      }

      private static TypeKind typeKindFromAnnotation(String descriptor) {
        if (descriptor.equals(VALUE_DESCRIPTOR)) {
          return TypeKind.VALUE;
        }
        if (descriptor.equals(ZERO_DEFAULT_DESCRIPTOR)) {
          return TypeKind.ZERO_DEFAULT;
        }
        return TypeKind.IDENTITY;
      }

      @Override
      public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        // System.out.println("class.visitAnnotation: descriptor = " + descriptor);
        typeKind = typeKind.max(typeKindFromAnnotation(descriptor));
        return null;
      }

      @Override
      public FieldVisitor visitField(int access, String fieldName, String fieldDescriptor, String signature, Object value) {
        return new FieldVisitor(ASM9) {
          @Override
          public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            // System.out.println("field.visitTypeAnnotation: descriptor = " + descriptor);
            nullKind(descriptor).ifPresent(nullKind -> fieldDataMap.put(fieldName + "." + fieldDescriptor, new FieldData(nullKind)));
            return null;
          }
        };
      }

      @Override
      public MethodVisitor visitMethod(int access, String methodName, String methodDescriptor, String signature, String[] exceptions) {
        var parameterMap = new HashMap<Integer, NullKind>();
        methodDataMap.put(methodName + methodDescriptor, new MethodData(parameterMap));
        return new MethodVisitor(ASM9) {
          private int parameterDelta; // number of synthetic parameters at the beginning of the method

          @Override
          public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
            parameterDelta = Type.getArgumentTypes(methodDescriptor).length - parameterCount;
          }

          @Override
          public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            var typeReference = new TypeReference(typeRef);
            int parameter;
            switch (typeReference.getSort()) {
              case TypeReference.METHOD_FORMAL_PARAMETER -> parameter = parameterDelta + typeReference.getFormalParameterIndex();
              case TypeReference.METHOD_RETURN -> parameter = -1;
              default -> {
                return null;
              }
            }
            //System.out.println("parameter = " + parameter + ", descriptor = " + descriptor);
            nullKind(descriptor).ifPresent(nullKind -> parameterMap.put(parameter, nullKind));
            return null;
          }
        };
      }
    };

    var reader = new ClassReader(buffer);
    if ((reader.getAccess() & ACC_IDENTITY) == 0) {  //FIXME (re-evaluate) do not rewrite value class, ASM is not ready for this
      return Optional.empty();
    }
    var descriptors = new HashSet<String>();
    var dependencyVisitor = dependencyCollectorAdapter(descriptors, cv);
    reader.accept(dependencyVisitor,0);
    return Optional.of(new ClassData(cv.internalName, cv.superName, cv.typeKind, descriptors, fieldDataMap, methodDataMap));
  }

  private static Optional<String> loadableDescriptor(Type type) {
    return switch(type.getSort()) {
      case Type.OBJECT -> Optional.of(type.getDescriptor());
      case Type.ARRAY -> loadableDescriptor(type.getElementType());
      default -> Optional.empty();
    };
  }

  private static ClassVisitor dependencyCollectorAdapter(Set<String> descriptors, ClassVisitor cv) {
    return new ClassVisitor(ASM9, cv) {
      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        loadableDescriptor(Type.getType(descriptor)).ifPresent(descriptors::add);
        return super.visitField(access, name, descriptor, signature, value);
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String methodDescriptor, String signature, String[] exceptions) {
        var returnType = Type.getReturnType(methodDescriptor);
        loadableDescriptor(returnType).ifPresent(descriptors::add);
        var parameterTypes = Type.getArgumentTypes(methodDescriptor);
        for(var parameterType : parameterTypes) {
          loadableDescriptor(parameterType).ifPresent(descriptors::add);
        }
        return super.visitMethod(access, name, methodDescriptor, signature, exceptions);
      }
    };
  }

  private static void rewrite(List<Path> classes, Analysis analysis) throws IOException {
    for(var path: classes) {
      try(var input = Files.newInputStream(path)) {
        System.out.println("rewrite " + path);
        var data = rewrite(input.readAllBytes(), analysis);
        if (data.isPresent()) {
          Files.write(path, data.orElseThrow());
        } else {
          System.out.println("  skip rewrite " + path);
        }
      }
    }
  }

  private static Optional<byte[]> rewrite(byte[] buffer, Analysis analysis) {
    var reader = new ClassReader(buffer);
    var classDataMap = analysis.classDataMap;
    var classData = classDataMap.get(reader.getClassName());
    if (classData == null) {  // analysis is not available
      return Optional.empty();
    }
    var descriptors = classData.descriptors;
    var fieldDataMap = classData.fieldDataMap;
    var methodDataMap = classData.methodDataMap;

    var writer = new ClassWriter(reader, 0);
    reader.accept(
        new ClassVisitor(ASM9, writer) {
          @Override
          public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            var kind = classData.typeKind;
            var classVersion = kind.isIdentity() ? version : (V23 | Opcodes.V_PREVIEW);
            var classAccess = kind.isIdentity() ? (access | ACC_IDENTITY) : (access | ACC_FINAL) & ~ACC_IDENTITY;
            if (classVersion != version || classAccess != access) {
              System.out.println("  rewrite class " + name + " " + kind + " " + version + " " + classAccess);
            }
            super.visit(classVersion, classAccess, name, signature, superName, interfaces);

            if (kind.isZeroDefault()) {
              var av = super.visitAnnotation(IMPLICIT_CONSTRUCTIBLE_DESCRIPTOR, true);
              av.visitEnd();
            }
          }

          @Override
          public FieldVisitor visitField(int access, String fieldName, String fieldDescriptor, String signature, Object value) {
            var kind = classData.typeKind;
            var fieldAccess = (access & ACC_STATIC) != 0 || kind.isIdentity() ?
                access :
                access | ACC_FINAL | ACC_STRICT; // FIXME should be verified

            var fv = super.visitField(fieldAccess, fieldName, fieldDescriptor, signature, value);
            var type = Type.getType(fieldDescriptor);
            var typeSort = type.getSort();
            if (typeSort == Type.OBJECT /* || typeSort == Type.ARRAY*/) {
              var nullKind = Optional.ofNullable(fieldDataMap.get(fieldName + "." + fieldDescriptor)).map(FieldData::nullKind).orElse(NullKind.NULLABLE);
              var typeKind = Optional.ofNullable(classDataMap.get(type.getInternalName())).map(ClassData::typeKind).orElse(TypeKind.IDENTITY);
              if (nullKind == NullKind.NONNULL && typeKind == TypeKind.ZERO_DEFAULT) {
                System.out.println("  rewrite field " + fieldName + "." + fieldDescriptor + " " + nullKind + " " + typeKind);
                var av = fv.visitAnnotation(NULL_RESTRICTED_DESCRIPTOR, true);
                av.visitEnd();
              }
            }
            return fv;
          }

          private static final Set<String> ALLOWED_SUPER_NAMES = Set.of("java/lang/Object", "java/lang/Number", "java/lang/Record");

          @Override
          public MethodVisitor visitMethod(int access, String methodName, String methodDescriptor, String signature, String[] exceptions) {
            var mv = super.visitMethod(access, methodName, methodDescriptor, signature, exceptions);
            var kind = classData.typeKind;
            if (!kind.isIdentity() && ALLOWED_SUPER_NAMES.contains(classData.superName)) {
              mv = moveSuperCallToTheEnd(mv, classData.superName);
            }
            var methodData = methodDataMap.get(methodName + methodDescriptor);
            Map<Integer, NullKind> parameterMap;
            if (methodData != null && !(parameterMap = methodData.parameterMap).isEmpty()) {
              System.out.println("  rewrite method " + methodName + "." + methodDescriptor + " " + parameterMap);
              mv = preconditionsAdapter(methodDescriptor, parameterMap, mv);
            }
            return mv;
          }

          @Override
          public void visitInnerClass(String name, String outerName, String innerName, int access) {
            var typeKind = Optional.ofNullable(classDataMap.get(name)).map(ClassData::typeKind).orElse(TypeKind.IDENTITY);
            var innerAccess = typeKind.isIdentity() ?
                access /*| ACC_IDENTITY*/ :
                (access | ACC_FINAL) & ~ ACC_IDENTITY;
            super.visitInnerClass(name, outerName, innerName, innerAccess);
          }

          @Override
          public void visitEnd() {
            var valueDescriptors = descriptors.stream()
                .map(descriptor -> Type.getType(descriptor).getInternalName())
                .filter(internalName -> !internalName.equals(classData.internalName))
                .filter(internalName -> Optional.ofNullable(classDataMap.get(internalName)).map(ClassData::typeKind).orElse(TypeKind.IDENTITY) != TypeKind.IDENTITY)
                .toList();
            if (!valueDescriptors.isEmpty()) {
              super.visitAttribute(new LoadableDescriptorsAttribute(valueDescriptors));
            }
          }
        }, 0);

    return Optional.of(writer.toByteArray());
  }

  private static MethodVisitor moveSuperCallToTheEnd(MethodVisitor mv, String superName) {
    return new MethodVisitor(ASM9, mv) {
      @Override
      public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (name.equals("<init>") && owner.equals(superName)) {
          // here we know that the descriptor is "()V"
          super.visitInsn(POP);
          return;
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }

      @Override
      public void visitInsn(int opcode) {
        if (opcode == RETURN) {
          super.visitVarInsn(ALOAD, 0);
          super.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
        }
        super.visitInsn(opcode);
      }
    };
  }

  private static MethodVisitor preconditionsAdapter(String methodDescriptor, Map<Integer, NullKind> parameterMap, MethodVisitor mv) {
    return new MethodVisitor(ASM9,  mv) {
      private int maxLocals = -1;

      @Override
      public void visitCode() {
        var types = Type.getArgumentTypes(methodDescriptor);
        var slot = 0;
        for(var i = 0; i < types.length; i++) {
          var type = types[i];
          var typeSort = type.getSort();
          if (typeSort == Type.OBJECT /*|| typeSort == Type.ARRAY FIXME*/) {
            var parameterNullKind = parameterMap.getOrDefault(i, NullKind.NULLABLE);
            if (parameterNullKind == NullKind.NONNULL) {
              //var typeKind = Optional.ofNullable(classDataMap.get(type.getInternalName())).map(ClassData::typeKind).orElse(TypeKind.IDENTITY);
              mv.visitVarInsn(ALOAD, slot);
              mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
              mv.visitInsn(POP);

              maxLocals = slot;
            }
          }
          slot += type.getSize();
        }
        super.visitCode();
      }

      @Override
      public void visitMaxs(int maxStack, int maxLocals) {
        if (maxLocals != -1) {
          maxStack = Math.max(maxStack, 2);
          maxLocals = Math.max(maxLocals, this.maxLocals);
        }
        super.visitMaxs(maxStack, maxLocals);
      }
    };
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
    analysis.dump();
    rewrite(classes, analysis);
  }
}
