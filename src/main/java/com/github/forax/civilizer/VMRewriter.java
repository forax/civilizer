package com.github.forax.civilizer;

import com.github.forax.civilizer.Rewriter.ClassData;
import com.github.forax.civilizer.Rewriter.TypeKind;
import com.github.forax.civilizer.vm.RT;
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
import java.lang.invoke.MethodType;
import java.lang.invoke.TypeDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;

public class VMRewriter {
  record Signature(String desc) {
    Signature {
      Objects.requireIdentity(desc);
    }
  }
  record ClassData(String internalName, Signature signature, HashMap<String, ConstantDynamic> condyMap, HashMap<String, Signature> methodData) {}
  record Analysis(HashMap<String,ClassData> classDataMap) {
    void dump() {
      for(var classData: classDataMap.values()) {
        System.out.println("class " + classData.internalName);
        System.out.println("  signature: " + classData.signature);
        for(var methodEntry: classData.methodData.entrySet()) {
          System.out.println("    method: " + methodEntry.getKey());
          System.out.println("      signature: " + methodEntry.getValue());
        }
      }
    }
  }

  private static final Handle BSM_STATIC = new Handle(H_INVOKESTATIC, RT.class.getName().replace('.', '/'),
      "bsm_static",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_VIRTUAL = new Handle(H_INVOKESTATIC, RT.class.getName().replace('.', '/'),
      "bsm_virtual",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_NEW = new Handle(H_INVOKESTATIC, RT.class.getName().replace('.', '/'),
      "bsm_new",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_CONDY = new Handle(H_INVOKESTATIC, RT.class.getName().replace('.', '/'),
      "bsm_condy",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
      false);

  private static ClassData analyze(byte[] buffer) {
    var condyMap = new HashMap<String, ConstantDynamic>();
    var methodData = new HashMap<String, Signature>();

    var reader = new ClassReader(buffer);
    var cv = new ClassVisitor(ASM9) {
      private String internalName;
      private Signature signature;

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        internalName = name;
        if (signature != null) {
          this.signature = new Signature(signature);
        }
      }

      private Object condyArgument(String arg) {
        return switch (arg.charAt(0)) {
          case 'L' -> Type.getType(arg);
          case 'K', 'P' -> {
            var condy = condyMap.get(arg.substring(0, arg.length() - 1));
            if (condy == null) {
              throw new IllegalStateException("undefined condy " + arg);
            }
            yield condy;
          }
          case 'C' -> Integer.valueOf(arg.substring(1, arg.length() - 1));
          default -> throw new IllegalStateException("unknown encoding for condy arg " + arg);
        };
      }

      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if ((name.startsWith("$KP") || name.startsWith("$P")) && value instanceof String s) {
          // constant pool description
          System.out.println("  constant pool constant " + name + " value: " + value);
          var tokens = s.split(" ");
          var args = Arrays.stream(tokens).skip(1).map(this::condyArgument).toList();
          var bsmConstants = Stream.concat(Stream.of(tokens[0]), args.stream()).toArray();
          var condyName = name.substring(1);
          var condy = new ConstantDynamic(condyName, "Ljava/lang/Object;", BSM_CONDY, bsmConstants);
          //System.out.println("  condy: " + condy);
          condyMap.put(condyName, condy);
        }
        return null;
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        System.out.println("method signature " + signature);
        if (signature != null) {
          methodData.put(name + descriptor, new Signature(signature));
        }
        return null;
      }
    };
    reader.accept(cv, 0);

    return new ClassData(cv.internalName, cv.signature, condyMap, methodData);
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
    var classDataMap = analysis.classDataMap;
    var condyMap = analysis.classDataMap.get(internalName).condyMap;
    var writer = new ClassWriter(reader, 0);
    var cv = new ClassVisitor(ASM9, writer) {
      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if ((name.startsWith("$KP") || name.startsWith("$P"))) {
          var condyName = name.substring(1);
          var condy = condyMap.get(condyName);
          if (name.startsWith("$KP")) {
            // we also need accessors
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

      @Override
      public MethodVisitor visitMethod(int access, String methodName, String methodDescriptor, String signature, String[] exceptions) {
        var mv = super.visitMethod(access, methodName, methodDescriptor, signature, exceptions);

        System.out.println("visit " + methodName + methodDescriptor);

        return new MethodVisitor(ASM9, mv) {
          private String constant;
          private final ArrayDeque<Object> constantStack = new ArrayDeque<>();
          private boolean removeDUP;

          @Override
          public void visitLdcInsn(Object value) {
            if (value instanceof String s) {
              constant = s;
            }
            super.visitLdcInsn(value);
          }

          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (owner.equals("java/lang/String") && name.equals("intern") && descriptor.equals("()Ljava/lang/String;")) {
              // record constant
              var constant = this.constant;
              var condy =  condyMap.get(constant);
              if (condy == null) {
                throw new IllegalStateException("unknown constant pool constant " + constant);
              }
              var constantValue = constant.startsWith("P") ? condy : constant;
              constantStack.push(constantValue);
              this.constant = null;
              super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              return;
            }

            var classSignature = Optional.ofNullable(classDataMap.get(owner))
                .map(ClassData::signature)
                .orElse(null);
            var methodSignature = Optional.ofNullable(classDataMap.get(owner))
                .flatMap(classData -> Optional.ofNullable(classData.methodData.get(name + descriptor)))
                .orElse(null);

            switch (opcode) {
              case INVOKESPECIAL -> {
                if (name.equals("<init>") && !methodName.equals("<init>") && (classSignature != null || methodSignature != null)) {
                  var desc = MethodTypeDesc.ofDescriptor(descriptor);
                  desc = desc.changeReturnType(ClassDesc.ofInternalName(owner));
                  var methodConstant = methodSignature != null? constantStack.pop(): "";
                  var classConstant = classSignature != null? constantStack.pop(): "";
                  mv.visitInvokeDynamicInsn("new", desc.descriptorString(), BSM_NEW, classConstant, methodConstant);
                  return;
                }
              }
              case INVOKESTATIC -> {
                if (methodSignature != null) {
                  var constant = constantStack.pop();
                  mv.visitInvokeDynamicInsn(name, descriptor, BSM_STATIC, Type.getObjectType(owner), constant);
                  return;
                }
              }
              case INVOKEVIRTUAL, INVOKEINTERFACE -> {
                if (methodSignature != null) {
                  mv.visitVarInsn(ALOAD, 0);
                  var desc = MethodTypeDesc.ofDescriptor(descriptor);
                  desc = desc.insertParameterTypes(desc.parameterCount(), ConstantDescs.CD_Object);
                  var constant = constantStack.pop();
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
            if (opcode == NEW) {
              removeDUP = true;
              return;  // skip NEW
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
          public void visitEnd() {
            if (!constantStack.isEmpty()) {
              throw new IllegalStateException("in method " + internalName + "." + methodName + methodDescriptor + ", non empty constant stack " +constantStack);
            }
            super.visitEnd();
          }
        };
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
    analysis.dump();
    rewrite(classes, analysis);
  }
}
