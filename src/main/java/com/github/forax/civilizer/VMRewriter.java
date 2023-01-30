package com.github.forax.civilizer;

import com.github.forax.civilizer.vm.Parametric;
import com.github.forax.civilizer.vm.RT;
import com.github.forax.civilizer.vm.TypeRestriction;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;


import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
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
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

public class VMRewriter {
  record Field(String name, String descriptor) {}
  record Method(String name, String descriptor) {}
  record ClassData(String internalName,
                   boolean parametric,
                   HashMap<String, ConstantDynamic> condyMap,
                   HashSet<String> condyFieldAccessors,
                   LinkedHashMap<Field,String> fieldRestrictionMap,
                   HashSet<Method> methodParametricSet) {}
  record Analysis(HashMap<String,ClassData> classDataMap) {
    void dump() {
      for(var classData: classDataMap.values()) {
        System.out.println("class " + classData.internalName);
        System.out.println("  parametric: " + classData.parametric);
      }
    }
  }


  private static final String RT_INTERNAL = RT.class.getName().replace('.', '/');
  private static final Handle BSM_LDC = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_ldc",
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
  private static final Handle BSM_PUT_VALUE_CHECK = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_put_value_check",
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
  private static final Handle BSM_TYPE = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_type",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
      false);
  private static final Handle BSM_QTYPE = new Handle(H_INVOKESTATIC, RT_INTERNAL,
      "bsm_qtype",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/Object;",
      false);

  private static ClassData analyze(byte[] buffer) {
    var condyMap = new HashMap<String, ConstantDynamic>();
    var condyFieldAccessors = new HashSet<String> ();
    var fieldRestrictionMap = new LinkedHashMap<Field,String>();
    var methodParametricSet = new HashSet<Method>();

    var reader = new ClassReader(buffer);
    var cv = new ClassVisitor(ASM9) {
      private static final String PARAMETRIC_DESCRIPTOR = "L" + Parametric.class.getName().replace('.', '/') + ";";
      private static final String TYPE_RESTRICTION_DESCRIPTOR = "L" + TypeRestriction.class.getName().replace('.', '/') + ";";

      private String internalName;
      private boolean parametric;

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        internalName = name;
      }

      private AnnotationVisitor parametricAnnotationVisitor() {
        return new AnnotationVisitor(ASM9) {
          @Override
          public void visit(String name, Object value) {
            if (!"".equals(value)) {
              // an accessor must be generated
              condyFieldAccessors.add("$" + value);
            }
          }
        };
      }

      @Override
      public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (descriptor.equals(PARAMETRIC_DESCRIPTOR)) {
          parametric = true;
          return parametricAnnotationVisitor();
        }
        return null;
      }

      private Object condyArgument(String arg) {
        return switch (arg.charAt(0)) {
          case 'V', 'Z', 'B', 'C', 'S', 'I', 'J', 'F', 'D' -> new ConstantDynamic(arg, "Ljava/lang/Object;", BSM_TYPE);
          case 'Q' -> new ConstantDynamic("_", "Ljava/lang/Object;", BSM_QTYPE, Type.getType("L" + arg.substring(1)));
          case 'L' -> Type.getType(arg);
          case 'K', 'P' -> {
            var condy = condyMap.get(arg.substring(0, arg.length() - 1));
            if (condy == null) {
              throw new IllegalStateException("undefined condy " + arg);
            }
            yield condy;
          }
          case '(' -> Type.getMethodType(arg);
          case '"' -> arg.substring(1, arg.length() - 1);
          default -> {
            if (arg.indexOf('.') != -1) {
              yield Double.parseDouble(arg);
            }
            yield Integer.valueOf(arg);
          }
        };
      }

      @Override
      public FieldVisitor visitField(int access, String fieldName, String fieldDescriptor, String signature, Object value) {
        if ((fieldName.startsWith("$KP") || fieldName.startsWith("$P")) && value instanceof String s) {
          // constant pool description
          System.out.println("  constant pool constant " + fieldName + " value: " + value);
          var tokens = s.split(" ");
          var args = Arrays.stream(tokens).skip(1).map(this::condyArgument).toList();
          var bsmConstants = Stream.concat(Stream.of(tokens[0]), args.stream()).toArray();
          var condyName = fieldName.substring(1);
          var condy = new ConstantDynamic(condyName, "Ljava/lang/Object;", BSM_CONDY, bsmConstants);
          //System.out.println("  condy: " + condy);
          condyMap.put(condyName, condy);
          // an accessor must be generated ?
          if (fieldName.startsWith("$KP")) {
            condyFieldAccessors.add(fieldName);
          }
        }

        return new FieldVisitor(ASM9) {
          @Override
          public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(TYPE_RESTRICTION_DESCRIPTOR)) {
              return new AnnotationVisitor(ASM9) {
                @Override
                public void visit(String name, Object value) {
                  if (!name.equals("value")) {
                    throw new AssertionError("TypeRestriction is malformed !");
                  }
                  fieldRestrictionMap.put(new Field(fieldName, fieldDescriptor), (String) value);
                }
              };
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
              return parametricAnnotationVisitor();
            }
            return null;
          }
        };
      }
    };
    reader.accept(cv, 0);

    return new ClassData(cv.internalName, cv.parametric, condyMap, condyFieldAccessors, fieldRestrictionMap, methodParametricSet);
  }

  private static void rewrite(List<Path> classes, Analysis analysis) throws IOException {
    for(var path: classes) {
      try(var input = Files.newInputStream(path)) {
        System.out.println("rewrite " + path);
        var data = rewrite(input.readAllBytes(), analysis);
        Files.write(path, data);
      }
    }
  }


  private static byte[] rewrite(byte[] buffer, Analysis analysis) {
    var reader = new ClassReader(buffer);
    var internalName = reader.getClassName();
    var supername = reader.getSuperName();
    var classDataMap = analysis.classDataMap;
    var classData = analysis.classDataMap.get(internalName);
    var writer = new ClassWriter(reader, 0);
    var cv = new ClassVisitor(ASM9, writer) {
      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if ((name.startsWith("$KP") || name.startsWith("$P"))) {
          var condyName = name.substring(1);
          var condy = classData.condyMap.get(condyName);
          if (classData.condyFieldAccessors.contains(name)) {
            // we also need accessors for the constant dynamic referenced from outside
            var mv = cv.visitMethod(ACC_STATIC | ACC_SYNTHETIC | ACC_PRIVATE, name, "()Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitLdcInsn(condy);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 0);
            mv.visitEnd();
          }
          return null;
        }
        return super.visitField(access, name, descriptor, signature, value);
      }

      private void delegateMethod(int access, String name, String methodDescriptor, String newMethodDescriptor) {
        var mv = super.visitMethod(access, name, methodDescriptor, null, null);
        mv.visitCode();
        int slot;
        int opcode;
        if ((access & ACC_STATIC) != 0) {
          slot = 0;
          opcode = INVOKESTATIC;
        } else {
          slot = 1;
          opcode = INVOKESPECIAL;  //TODO deals with instance method other than <init> here
          mv.visitVarInsn(ALOAD, 0);
        }
        for(var type: Type.getArgumentTypes(methodDescriptor)) {
          mv.visitVarInsn(type.getOpcode(ILOAD), slot);
          slot += type.getSize();
        }
        mv.visitLdcInsn(new ConstantDynamic("rawKiddyPool", "Ljava/lang/Object;", BSM_RAW_KIDDY_POOL));
        mv.visitMethodInsn(opcode, internalName, name, newMethodDescriptor, false);
        mv.visitInsn(Type.getReturnType(newMethodDescriptor).getOpcode(IRETURN));
        mv.visitMaxs(slot + 1, slot);
        mv.visitEnd();
      }

      private ConstantDynamic findCondy(String ldcConstant) {
        var condy =  classData.condyMap.get(ldcConstant);
        if (condy == null) {
          throw new IllegalStateException("unknown constant pool constant '" + ldcConstant + "'");
        }
        return condy;
      }

      @Override
      public MethodVisitor visitMethod(int access, String methodName, String methodDescriptor, String signature, String[] exceptions) {
        var parametricMethod = classData.methodParametricSet.contains(new Method(methodName, methodDescriptor));
        MethodVisitor delegate;
        if (classData.parametric && methodName.equals("<init>")) { // need to initialize the kiddy pool
          var desc = MethodTypeDesc.ofDescriptor(methodDescriptor);
          desc = desc.insertParameterTypes(desc.parameterCount(), ConstantDescs.CD_Object);
          var newMethodDescriptor = desc.descriptorString();
          delegateMethod(access, "<init>", methodDescriptor, newMethodDescriptor);
          var mv = super.visitMethod(access | ACC_SYNTHETIC, methodName, newMethodDescriptor, null, null);
          delegate = new MethodVisitor(ASM9, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
              if (opcode == INVOKESPECIAL && owner.equals(supername) && name.equals("<init>")) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                mv.visitVarInsn(ALOAD, 0);
                var kiddyPoolSlot = Type.getArgumentsAndReturnSizes(methodDescriptor) >> 2;  // class is parametric
                mv.visitVarInsn(ALOAD, kiddyPoolSlot);
                mv.visitFieldInsn(PUTFIELD, internalName, "$kiddyPool", "Ljava/lang/Object;");

                // init defaults
                for(var fieldEntry: classData.fieldRestrictionMap.entrySet()) {
                  var field = fieldEntry.getKey();
                  var constant = fieldEntry.getValue();
                  mv.visitVarInsn(ALOAD, 0);
                  var condy = findCondy(constant);
                  var constantValue = constant.startsWith("P") ? condy : constant;
                  var desc = MethodTypeDesc.of(ClassDesc.ofDescriptor(field.descriptor));
                  if (!(constantValue instanceof ConstantDynamic)) {
                    mv.visitVarInsn(ALOAD, kiddyPoolSlot); // load $kiddyPool
                    desc = desc.insertParameterTypes(0, ConstantDescs.CD_Object);
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
            desc = desc.insertParameterTypes(desc.parameterCount(), ConstantDescs.CD_Object);
            var newMethodDescriptor = desc.descriptorString();
            delegateMethod(access, methodName, methodDescriptor, newMethodDescriptor);
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

          private void loadKiddyPool() {
            if (parametricMethod) {
              mv.visitVarInsn(ALOAD, kiddyPoolSlot);
            } else {
              mv.visitVarInsn(ALOAD, 0);
              mv.visitFieldInsn(GETFIELD, internalName, "$kiddyPool", "Ljava/lang/Object;");
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
              var constant = classData.fieldRestrictionMap.get(new Field(name, descriptor));
              if (constant != null) {
                var condy = findCondy(constant);
                var constantValue = constant.startsWith("P") ? condy : constant;
                var desc = MethodTypeDesc.of(ClassDesc.ofDescriptor(descriptor), ClassDesc.ofDescriptor(descriptor));
                if (!(constantValue instanceof ConstantDynamic)) {
                  loadKiddyPool();
                  desc = desc.insertParameterTypes(1, ConstantDescs.CD_Object);
                }
                mv.visitInvokeDynamicInsn(name, desc.descriptorString(), BSM_PUT_VALUE_CHECK, constant);
                // fallthrough
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
              var condy = findCondy(constant);
              constantValue = constant.startsWith("P") ? condy : constant;
              mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              return;
            }
            if (owner.equals(RT_INTERNAL) && name.equals("ldc") && descriptor.equals("()Ljava/lang/Object;")) {
              var constant = constantValue;
              if (constant == null) {
                throw new IllegalStateException("no constant info for ldc");
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

            var parametricOwner = (boolean) Optional.ofNullable(classDataMap.get(owner)).map(ClassData::parametric).orElse(false);
            var parametricCall = (boolean) Optional.ofNullable(classDataMap.get(owner)).map(cd -> cd.methodParametricSet.contains(new Method(name, descriptor))).orElse(false);

            switch (opcode) {
              case INVOKESPECIAL -> {
                if (parametricOwner && constantValue != null) {
                  var constant = constantValue;
                  constantValue = null;
                  var desc = MethodTypeDesc.ofDescriptor(descriptor);
                  desc = desc.changeReturnType(ClassDesc.ofInternalName(owner));
                  if (!(constant instanceof ConstantDynamic)) {
                    loadKiddyPool();
                    desc = desc.insertParameterTypes(desc.parameterCount(), ConstantDescs.CD_Object);
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
                    desc = desc.insertParameterTypes(desc.parameterCount(), ConstantDescs.CD_Object);
                  }
                  mv.visitInvokeDynamicInsn(name, desc.descriptorString(), BSM_STATIC, Type.getObjectType(owner), constant);
                  return;
                }
              }
              case INVOKEVIRTUAL, INVOKEINTERFACE -> {
                if (parametricOwner && constantValue != null) {
                  var constant = constantValue;
                  constantValue = null;
                  var desc = MethodTypeDesc.ofDescriptor(descriptor);
                  desc = desc.insertParameterTypes(0, ClassDesc.ofInternalName(owner));
                  if (!(constant instanceof ConstantDynamic)) {
                    loadKiddyPool();
                    desc = desc.insertParameterTypes(desc.parameterCount(), ConstantDescs.CD_Object);
                  }
                  mv.visitInvokeDynamicInsn(name, desc.descriptorString(), BSM_VIRTUAL, constant);
                  return;
                }
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
                  var desc = MethodTypeDesc.of(ClassDesc.ofInternalName(type).arrayType(), ConstantDescs.CD_int);
                  if (!(constant instanceof ConstantDynamic)) {
                    loadKiddyPool();
                    desc = desc.insertParameterTypes(desc.parameterCount(), ConstantDescs.CD_Object);
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
            super.visitMaxs(maxStack + 1, maxLocals + 1);
          }

          @Override
          public void visitEnd() {
            if (constantValue != null) {
              throw new IllegalStateException("in method " + internalName + "." + methodName + methodDescriptor + ", unbounded constant value " + constantValue);
            }
            super.visitEnd();
          }
        };
      }

      @Override
      public void visitEnd() {
        if (classData.parametric) {  // parametric class
          var fv = cv.visitField(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, "$kiddyPool", "Ljava/lang/Object;", null, null);
          fv.visitEnd();
        }
        super.visitEnd();
      }
    };
    reader.accept(cv, 0);

    return writer.toByteArray();
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
    var main = classes(Path.of("target/classes"), "species");
    var test = classes(Path.of("target/test-classes"), "species");
    List<Path> classes = Stream.concat(main.stream(), test.stream()).toList();

    var analysis = analyze(classes);
    //analysis.dump();
    rewrite(classes, analysis);
  }
}
