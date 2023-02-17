package com.github.forax.civilizer;

import com.github.forax.civilizer.runtime.NonNull;
import com.github.forax.civilizer.runtime.Nullable;
import com.github.forax.civilizer.runtime.RT;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

public final class Rewriter {
  private Rewriter() {
    throw new AssertionError();
  }

  private enum TypeKind {
    IDENTITY, VALUE, ZERO_DEFAULT;

    TypeKind max(TypeKind kind) {
      return compareTo(kind) > 0 ? this: kind;
    }
  }
  private enum NullKind { NONNULL, NULLABLE }

  private record ClassData(String internalName, String superName, TypeKind typeKind, Set<String> dependencies, Map<String,FieldData> fieldDataMap, Map<String,MethodData> methodDataMap) { }
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


  private static final int ACC_VALUE = 0x0040;
  private static final int ACC_IDENTITY = 0x0020;
  private static final int ACC_PRIMITIVE = 0x0800;  // see jdk.internal.value.PrimitiveClass

  private static final int ACONST_INIT = 203; // visitTypeInsn
  private static final int WITHFIELD = 204; // visitFieldInsn

  private static final String NON_NULL_DESCRIPTOR = NonNull.class.descriptorString();
  private static final String NULLABLE_DESCRIPTOR = Nullable.class.descriptorString();

  private static final class PreloadAttribute extends Attribute {
    private final List<String> classes ;

    PreloadAttribute(List<String> classes) {
      super("Preload");
      this.classes = classes;
    }

    @Override
    protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeOffset, Label[] labels) {
      var classesCount = classReader.readUnsignedShort(offset);
      offset += 2;
      var classes = new ArrayList<String>();
      for(var i = 0; i < classesCount; i++) {
        var clazz = classReader.readClass(offset, charBuffer);
        classes.add(clazz);
        offset += 2;
      }
      return new PreloadAttribute(classes);
    }

    @Override
    protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
      var byteVector = new ByteVector();
      byteVector.putShort(classes.size());
      for (var clazz : classes) {
        byteVector.putShort(classWriter.newClass(clazz));
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
        internalName = name;
        this.superName = superName;
        typeKind = TypeKind.IDENTITY;
      }

      @Override
      public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        // System.out.println("class.visitAnnotation: descriptor = " + descriptor);
        typeKind = typeKind.max(switch (descriptor) {
          case "Lcom/github/forax/civilizer/runtime/Value;" -> TypeKind.VALUE;
          case "Lcom/github/forax/civilizer/runtime/ZeroDefault;" -> TypeKind.ZERO_DEFAULT;
          default -> TypeKind.IDENTITY;
        });
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
    if ((reader.getAccess() & ACC_VALUE) != 0) {  // do not rewrite value class, ASM is not ready for this
      return Optional.empty();
    }
    var dependencies = new HashSet<String>();
    var dependencyVisitor = dependencyCollectorAdapter(dependencies, cv);
    reader.accept(dependencyVisitor,0);
    return Optional.of(new ClassData(cv.internalName, cv.superName, cv.typeKind, dependencies, fieldDataMap, methodDataMap));
  }

  private static ClassVisitor dependencyCollectorAdapter(HashSet<String> dependencies, ClassVisitor cv) {
    return new ClassRemapper(cv, new Remapper() {
      @Override
      public String map(String internalName) {
        dependencies.add(internalName);
        return internalName;
      }
    });
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
    var dependencies = classData.dependencies;
    var fieldDataMap = classData.fieldDataMap;
    var methodDataMap = classData.methodDataMap;

    var writer = new ClassWriter(reader, 0);
    reader.accept(
        new ClassVisitor(ASM9, writer) {
          @Override
          public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            var kind = classData.typeKind;
            var classAccess = switch (kind) {
              case IDENTITY -> access;
              case VALUE -> (access & ~ ACC_IDENTITY) | ACC_VALUE;
              case ZERO_DEFAULT -> (access & ~ ACC_IDENTITY) | ACC_VALUE | ACC_PRIMITIVE;
            };
            super.visit(version, classAccess, name, signature, superName, interfaces);
          }

          @Override
          public FieldVisitor visitField(int access, String fieldName, String fieldDescriptor, String signature, Object value) {
            var type = Type.getType(fieldDescriptor);
            var typeSort = type.getSort();
            if (typeSort == Type.OBJECT /* || typeSort == Type.ARRAY*/) {
              var nullKind = Optional.ofNullable(fieldDataMap.get(fieldName + "." + fieldDescriptor)).map(FieldData::nullKind).orElse(NullKind.NULLABLE);
              var typeKind = Optional.ofNullable(classDataMap.get(type.getInternalName())).map(ClassData::typeKind).orElse(TypeKind.IDENTITY);
              System.out.println("  rewrite field " + fieldName + "." + fieldDescriptor + " " + nullKind + " " + typeKind);
              if (nullKind == NullKind.NONNULL && typeKind == TypeKind.ZERO_DEFAULT) {
                fieldDescriptor = "Q" + fieldDescriptor.substring(1);
              }
            }
            return super.visitField(access, fieldName, fieldDescriptor, signature, value);
          }

          @Override
          public MethodVisitor visitMethod(int access, String methodName, String methodDescriptor, String signature, String[] exceptions) {
            var rewriteConstructor = classData.typeKind != TypeKind.IDENTITY && methodName.equals("<init>");
            MethodVisitor mv;
            if (rewriteConstructor) {
              mv = super.visitMethod(ACC_STATIC | access, "<vnew>", methodDescriptor.substring(0, methodDescriptor.length() - 1) + "L" + classData.internalName + ";", signature, exceptions);
            } else {
              mv = super.visitMethod(access, methodName, methodDescriptor, signature, exceptions);
            }
            var methodData = methodDataMap.get(methodName + methodDescriptor);
            Map<Integer, NullKind> parameterMap;
            if (methodData != null && !(parameterMap = methodData.parameterMap).isEmpty()) {
              mv = preconditionsAdapter(methodDescriptor, classDataMap, parameterMap, mv);
            }
            mv = newToVNewAdapter(classDataMap, mv);
            mv = fieldAccessAdapter(classDataMap, mv);
            mv = recordObjectMethodsAdapter(classData, mv);
            if (rewriteConstructor) {
              var thisSlot = Arrays.stream(Type.getArgumentTypes(methodDescriptor)).mapToInt(Type::getSize).sum();
              mv = initToFactoryAdapter(classData.internalName, classData.superName, classDataMap, thisSlot, mv);
            }
            return mv;
          }

          @Override
          public void visitInnerClass(String name, String outerName, String innerName, int access) {
            var typeKind = Optional.ofNullable(classDataMap.get(name)).map(ClassData::typeKind).orElse(TypeKind.IDENTITY);
            var innerAccess = switch (typeKind) {
              case IDENTITY -> access | ACC_IDENTITY;
              case VALUE -> (access & ~ ACC_IDENTITY) | ACC_VALUE;
              case ZERO_DEFAULT -> (access & ~ ACC_IDENTITY) | ACC_VALUE | ACC_PRIMITIVE;
            };
            super.visitInnerClass(name, outerName, innerName, innerAccess);
          }

          @Override
          public void visitEnd() {
            var classes = dependencies.stream()
                .filter(internalName -> !classData.internalName.equals(internalName))
                .filter(internalName -> Optional.ofNullable(classDataMap.get(internalName)).map(ClassData::typeKind).orElse(TypeKind.IDENTITY) != TypeKind.IDENTITY)
                .toList();
            if (!classes.isEmpty()) {
              super.visitAttribute(new PreloadAttribute(classes));
            }
          }
        }, 0);

    return Optional.of(writer.toByteArray());
  }

  private static MethodVisitor preconditionsAdapter(String methodDescriptor, Map<String,ClassData> classDataMap, Map<Integer, NullKind> parameterMap, MethodVisitor mv) {
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
              var typeKind = Optional.ofNullable(classDataMap.get(type.getInternalName())).map(ClassData::typeKind).orElse(TypeKind.IDENTITY);
              switch (typeKind) {
                case IDENTITY, VALUE, ZERO_DEFAULT -> {
                  mv.visitVarInsn(ALOAD, slot);
                  mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                  mv.visitInsn(POP);
                }
                //case ZERO_DEFAULT -> {
                //  mv.visitVarInsn(ALOAD, slot);
                //  mv.visitTypeInsn(CHECKCAST, "Q" + type.getDescriptor().substring(1));
                //  mv.visitVarInsn(ASTORE, slot);
                //}
              }
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

  private static Map<Integer, Integer> parameterIndexMap(boolean isInstanceMethod, String descriptor) {
    var map = new HashMap<Integer, Integer>();
    var slot = isInstanceMethod? 1: 0;
    var types = Type.getArgumentTypes(descriptor);
    for (var i = 0; i < types.length; i++) {
      var type = types[i];
      map.put(slot, i);
      slot += type.getSize();
    }
    return map;
  }

  /*
  static class NullInterpreter extends Interpreter<NullInterpreter.NullValue> {
    enum NullValue implements Value {
      NONNULL,
      NULLABLE,
      SIZE2;

      @Override
      public int getSize() {
        return this == SIZE2 ? 2 : 1;
      }
    }

    NullInterpreter() {
      super(ASM9);
    }

    private NullValue fieldValue(FieldInsnNode node) {
      var fieldType = Type.getType(node.desc);
      switch (fieldType.getSort()) {
        case Type.OBJECT -> {}
        case Type.ARRAY -> {
          return NullValue.NULLABLE;
        }
        case Type.DOUBLE, Type.LONG -> {
          return NullValue.SIZE2;
        }
        default -> {
          return NullValue.NONNULL;
        }
      }
      var classData = classDataMap.get(node.owner);
      if (classData == null) {
        return NullValue.NULLABLE;
      }
      var typeKind = classData.typeKind();
      var fieldData = classData.fieldDataMap.get(node.name + node.desc);
      return typeKind == TypeKind.ZERO_DEFAULT && fieldData.nullKind == NullKind.NONNULL
          ? NullValue.NONNULL
          : NullValue.NULLABLE;
    }

    @Override
    public NullValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
      if (isInstanceMethod && local == 0) { // this
        return NullValue.NONNULL;
      }
      return switch (type.getSort()) {
        case Type.ARRAY -> NullValue.NULLABLE;
        case Type.OBJECT -> {
          var parameterIndex = parameterIndexMap.getOrDefault(local, -1);
          yield parameterMap.getOrDefault(parameterIndex, NullKind.NULLABLE) == NullKind.NULLABLE
              ? NullValue.NULLABLE
              : NullValue.NONNULL;
        }
        case Type.LONG, Type.FLOAT -> NullValue.SIZE2;
        default -> NullValue.NONNULL;
      };
    }

    @Override
    public NullValue newValue(Type type) {
      if (type == null) { // uninitialized object
        return NullValue.NONNULL;
      }
      if (type == Type.VOID_TYPE) {
        return null;
      }
      switch (type.getSort()) {
        case Type.OBJECT, Type.ARRAY -> {
          return NullValue.NULLABLE;
        }
        case Type.DOUBLE, Type.LONG -> {
          return NullValue.SIZE2;
        }
        default -> {
          return NullValue.NONNULL;
        }
      }
    }

    @Override
    public NullValue newOperation(AbstractInsnNode node) {
      return switch (node.getOpcode()) {
        case ACONST_NULL -> NullValue.NULLABLE;
        case NEW -> NullValue.NONNULL;
        case LCONST_0, LCONST_1, DCONST_0, DCONST_1 -> NullValue.SIZE2;
        case GETSTATIC -> newValue(Type.getType(((FieldInsnNode) node).desc));
        case LDC -> {
          var value = ((LdcInsnNode) node).cst;
          if (value instanceof Integer || value instanceof Float) {
            yield NullValue.NONNULL;
          }
          if (value instanceof Long || value instanceof Double) {
            yield NullValue.SIZE2;
          }
          yield NullValue.NONNULL;
        }
        default -> throw new AssertionError();
      };
    }

    @Override
    public NullValue copyOperation(AbstractInsnNode node, NullValue v) {
      return v;
    }

    @Override
    public NullValue unaryOperation(AbstractInsnNode node, NullValue v) {
      return switch (node.getOpcode()) {
        case LNEG, DNEG, I2L, I2D, L2D, F2L, F2D, D2L -> NullValue.SIZE2;
        case ARETURN, CHECKCAST -> v;
        case GETFIELD -> fieldValue((FieldInsnNode) node);
        default -> NullValue.NONNULL;
      };
    }

    @Override
    public NullValue binaryOperation(AbstractInsnNode node, NullValue v1, NullValue v2) {
      return switch (node.getOpcode()) {
        case LALOAD,
            DALOAD,
            LADD,
            DADD,
            LSUB,
            DSUB,
            LMUL,
            DMUL,
            LDIV,
            DDIV,
            LREM,
            DREM,
            LSHL,
            LSHR,
            LUSHR,
            LAND,
            LOR,
            LXOR -> NullValue.SIZE2;
        case AALOAD -> NullValue.NULLABLE;
        default -> NullValue.NONNULL;
      };
    }

    @Override
    public NullValue ternaryOperation(
        AbstractInsnNode node, NullValue v1, NullValue v2, NullValue v3) {
      return NullValue.NONNULL;
    }

    @Override
    public NullValue naryOperation(AbstractInsnNode node, List<? extends NullValue> list) {
      return switch (node.getOpcode()) {
        case INVOKEDYNAMIC -> switch (Type.getReturnType(((InvokeDynamicInsnNode) node).desc).getSort()) {
          case Type.OBJECT -> NullValue.NULLABLE;
          case Type.DOUBLE, Type.LONG -> NullValue.SIZE2;
          default -> NullValue.NONNULL;
        };
        case INVOKESPECIAL, INVOKEVIRTUAL, INVOKESTATIC, INVOKEINTERFACE -> switch (Type.getReturnType(((MethodInsnNode) node).desc).getSort()) {
          case Type.OBJECT -> NullValue.NULLABLE;
          case Type.DOUBLE, Type.LONG -> NullValue.SIZE2;
          default -> NullValue.NONNULL;
        };
        case MULTIANEWARRAY -> NullValue.NONNULL;
        default -> throw new AssertionError();
      };
    }

    @Override
    public void returnOperation(AbstractInsnNode node, NullValue v1, NullValue v2) {}

    @Override
    public NullValue merge(NullValue v1, NullValue v2) {
      if (v1 == NullValue.SIZE2 && v2 == NullValue.SIZE2) {
        return NullValue.SIZE2;
      }
      if (v1 == NullValue.NONNULL && v2 == NullValue.NONNULL) {
        return NullValue.NONNULL;
      }
      return NullValue.NULLABLE;
    }
  }*/

  private static MethodVisitor initToFactoryAdapter(String internalName, String superName, Map<String, ClassData> classDataMap, int thisSlot, MethodVisitor mv) {
    return new MethodVisitor(ASM9, mv) {
      private boolean firstALOAD0 = true;

      @Override
      public void visitVarInsn(int opcode, int varIndex) {
        if (opcode == ALOAD && varIndex == 0) {
          if (firstALOAD0) {  // skip first ALOAD_0
            firstALOAD0 = false;
            return;
          }
          super.visitVarInsn(ALOAD, thisSlot);
          return;
        }
        super.visitVarInsn(opcode, varIndex - 1);
      }

      @Override
      public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (opcode == INVOKESPECIAL && name.equals("<init>")) {
          if (owner.equals(superName) && descriptor.equals("()V")) {  // super()
            mv.visitTypeInsn(ACONST_INIT, internalName);
            mv.visitVarInsn(ASTORE, thisSlot);
            return;
          }
          if (owner.equals(internalName)) { // this(...)
            //TODO does not work if this(...) contains a new ont itself !
            mv.visitMethodInsn(INVOKESTATIC, internalName, "<vnew>", descriptor.substring(0, descriptor.length() - 1) + "L" + internalName + ";", false);
            mv.visitVarInsn(ASTORE, thisSlot);
            return;
          }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (opcode == PUTFIELD && owner.equals(internalName)) {
          var descType = Type.getType(descriptor);
          if (descType.getSort() == Type.OBJECT /*|| descType.getSort() == Type.ARRAY*/) {
            var typeKind = Optional.ofNullable(classDataMap.get(descType.getInternalName())).map(ClassData::typeKind).orElse(TypeKind.IDENTITY);
            if (typeKind == TypeKind.ZERO_DEFAULT) {
              descriptor = "Q" + descriptor.substring(1);
              mv.visitTypeInsn(CHECKCAST, descriptor);
            }
          }
          mv.visitFieldInsn(WITHFIELD, owner, name, descriptor);
          mv.visitVarInsn(ASTORE, thisSlot);
          return;
        }
        super.visitFieldInsn(opcode, owner, name, descriptor);
      }

      @Override
      public void visitInsn(int opcode) {
        if (opcode == RETURN) {
          mv.visitVarInsn(ALOAD, thisSlot);
          mv.visitInsn(ARETURN);
          return;
        }
        super.visitInsn(opcode);
      }
    };
  }

  private static MethodVisitor newToVNewAdapter(Map<String, ClassData> classDataMap, MethodVisitor mv) {
    return new MethodVisitor(ASM9, mv) {
      private boolean removeDUP;

      @Override
      public void visitTypeInsn(int opcode, String type) {
        if (opcode == NEW) {
          var typeKind = Optional.ofNullable(classDataMap.get(type)).map(ClassData::typeKind).orElse(TypeKind.IDENTITY);
          if (typeKind != TypeKind.IDENTITY) {
            removeDUP = true;
            return;  // skip NEW
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
      public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        var typeKind = Optional.ofNullable(classDataMap.get(owner)).map(ClassData::typeKind).orElse(TypeKind.IDENTITY);
        if (opcode == INVOKESPECIAL && name.equals("<init>") && typeKind != TypeKind.IDENTITY) {
          super.visitMethodInsn(INVOKESTATIC, owner, "<vnew>", descriptor.substring(0, descriptor.length() - 1) + "L" + owner + ";", false);
          return;
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    };
  }

  private static final Handle BSM_GETFIELD, BSM_PUTFIELD;

  static {
    var runtimeName = RT.class.getName().replace('.', '/');
    BSM_GETFIELD = new Handle(H_INVOKESTATIC, runtimeName, "bsm_getfield",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
        false);
    BSM_PUTFIELD = new Handle(H_INVOKESTATIC, runtimeName, "bsm_putfield",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
        false);
  }

  private static MethodVisitor fieldAccessAdapter(Map<String, ClassData> classDataMap, MethodVisitor mv) {
    return new MethodVisitor(ASM9, mv) {
      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (opcode == WITHFIELD) { // short circuit because Type.getType() does not support Q-types
          super.visitFieldInsn(opcode, owner, name, descriptor);
          return;
        }
        var typeName = Type.getType(descriptor).getInternalName();
        var typeKind = Optional.ofNullable(classDataMap.get(typeName)).map(ClassData::typeKind).orElse(TypeKind.IDENTITY);
        if (typeKind != TypeKind.IDENTITY) {
          switch (opcode) {
            case GETFIELD -> {
              visitInvokeDynamicInsn(name, "(L" + owner + ";)" + descriptor, BSM_GETFIELD);
              return;
            }
            case PUTFIELD -> {
              visitInvokeDynamicInsn(name, "(L" + owner + ";" + descriptor + ")V", BSM_PUTFIELD);
              return;
            }
            default -> {}
          }
        }
        super.visitFieldInsn(opcode, owner, name, descriptor);
      }
    };
  }

  private static MethodVisitor recordObjectMethodsAdapter(ClassData classData, MethodVisitor mv) {
    return new MethodVisitor(ASM9, mv) {
      @Override
      public void visitInvokeDynamicInsn(String name, String descriptor, Handle bsmHandle, Object... bsmArguments) {
        if (bsmHandle.getOwner().equals("java/lang/runtime/ObjectMethods") && classData.typeKind == TypeKind.ZERO_DEFAULT) {
          var bsmRecord = new Handle(H_INVOKESTATIC, RT.class.getName().replace('.', '/'), "bsm_record", bsmHandle.getDesc(), false);
          mv.visitInvokeDynamicInsn(name, descriptor, bsmRecord, bsmArguments);
          return;
        }
        super.visitInvokeDynamicInsn(name, descriptor, bsmHandle, bsmArguments);
      }
    };
  }

  private static List<Path> classes(Path folder, String including) throws IOException {
    if (!Files.exists(folder)) {
      return List.of();
    }
    try(var paths = Files.walk(folder)) {
      return paths
          .filter(p -> p.toString().contains(including) && p.toString().endsWith(".class"))
          .toList();
    }
  }

  public static void main(String[] args) throws IOException {
    var main = classes(Path.of("target/classes"), "demo");
    var test = classes(Path.of("target/test-classes"), "demo");
    List<Path> classes = Stream.concat(main.stream(), test.stream()).toList();

    var analysis = analyze(classes);
    analysis.dump();
    rewrite(classes, analysis);
  }
}
