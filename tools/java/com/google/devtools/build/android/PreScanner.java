/*
 * Copyright (c) 2021 Google LLC
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Google designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Google in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package com.google.devtools.build.android;

import static com.google.common.base.Preconditions.checkState;
import static com.google.devtools.build.android.AsmHelpers.LAMBDA_METAFACTORY_INTERNAL_NAME;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** A preliminary pass of the classes in a jar for information indexing. */
public final class PreScanner {

  private final ImmutableSet<String> intoDesugarExtendedClassAnnotationNames;

  private final Map<String, String> typeReplacements;
  private final Map<String, String> nestHosts;
  private final LinkedHashMultimap<String, String> nestMembers;

  private final Map<ClassMemberKey, ClassMemberKey> fieldReplacements;
  private final Map<ClassMemberKey, ClassMemberKey> methodReplacements;

  /** Maps from an anonymous class to its enclosing method. */
  private final Map<String, ClassMemberKey> anonymousClasses;

  public PreScanner(ImmutableSet<String> intoDesugarExtendedClassAnnotationNames) {
    this.intoDesugarExtendedClassAnnotationNames = intoDesugarExtendedClassAnnotationNames;

    this.typeReplacements = new HashMap<>();
    this.nestHosts = new HashMap<>();
    this.nestMembers = LinkedHashMultimap.create();

    this.methodReplacements = new HashMap<>();
    this.fieldReplacements = new HashMap<>();
    this.anonymousClasses = new HashMap<>();
  }

  /** Obtains fields and methods that are to be supported by this library. */
  public void scan(ClassNode classNode) {
    Map<ClassMemberKey, FieldNode> nonPublicFields = new HashMap<>();
    Map<ClassMemberKey, MethodNode> nonPublicMethods = new HashMap<>();

    if (classNode.outerMethodDesc != null) {
      anonymousClasses.put(
          classNode.name,
          ClassMemberKey.create(
              classNode.outerClass, classNode.outerMethod, classNode.outerMethodDesc));
    }

    if (classNode.nestHostClass != null && !classNode.nestHostClass.isEmpty()) {
      nestHosts.put(classNode.name, classNode.nestHostClass);
    }

    if (classNode.nestMembers != null && !classNode.nestMembers.isEmpty()) {
      nestMembers.putAll(classNode.name, classNode.nestMembers);
    }

    ArrayDeque<ClassMemberKey> stagingMethods = new ArrayDeque<>();
    Map<ClassMemberKey, MethodNode> stagingMethodsMap = new HashMap<>();
    for (FieldNode fieldNode : classNode.fields) {
      ClassMemberKey fk = ClassMemberKey.create(classNode, fieldNode);
      if (AsmHelpers.hasAnyAnnotation(fieldNode, intoDesugarExtendedClassAnnotationNames)) {
        checkState(
            (fieldNode.access & ACC_STATIC) != 0 && (fieldNode.access & ACC_FINAL) != 0,
            "Only static final constant fields can be supported, but gets %s",
            fk);
        fieldReplacements.put(fk, AsmHelpers.getReplacementFieldKey(fk));
      }
      // Record non-public static fields that may be moved due to dependency.
      if ((fieldNode.access & ACC_STATIC) != 0 && (fieldNode.access & 0b111) != ACC_PUBLIC) {
        nonPublicFields.put(fk, fieldNode);
      }
    }
    for (MethodNode methodNode : classNode.methods) {
      ClassMemberKey mk = ClassMemberKey.create(classNode, methodNode);
      if (!AsmHelpers.isCovariantReturnTypedMethod(mk)
          && AsmHelpers.hasAnyAnnotation(methodNode, intoDesugarExtendedClassAnnotationNames)) {
        stagingMethods.add(mk);
        stagingMethodsMap.put(mk, methodNode);
      }
      // Record non-public methods that may be moved due to dependency.
      if (!"<init>".equals(methodNode.name) && (methodNode.access & 0b111) != ACC_PUBLIC) {
        nonPublicMethods.put(mk, methodNode);
      }
    }

    // BFS to find transitive field accesses and method invocations whose declarations also need
    // to be supported.
    while (!stagingMethods.isEmpty()) {
      ClassMemberKey front = stagingMethods.pop();
      MethodNode frontMethodNode = stagingMethodsMap.remove(front);
      methodReplacements.putIfAbsent(
          front, AsmHelpers.getReplacementMethodKey(frontMethodNode.access, front));
      for (AbstractInsnNode insnNode : frontMethodNode.instructions) {
        final ClassMemberKey accessedField;
        final ClassMemberKey invokedMethod;
        switch (insnNode.getType()) {
          case AbstractInsnNode.FIELD_INSN:
            FieldInsnNode fieldInsnNode = (FieldInsnNode) insnNode;
            accessedField =
                ClassMemberKey.create(fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc);
            invokedMethod = null;
            break;
          case AbstractInsnNode.METHOD_INSN:
            MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
            accessedField = null;
            invokedMethod =
                ClassMemberKey.create(
                    methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
            break;
          case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
            InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) insnNode;
            if (LAMBDA_METAFACTORY_INTERNAL_NAME.equals(invokeDynamicInsnNode.bsm.getOwner())) {
              Handle handle = (Handle) invokeDynamicInsnNode.bsmArgs[1];
              accessedField = null;
              invokedMethod =
                  ClassMemberKey.create(handle.getOwner(), handle.getName(), handle.getDesc());
            } else {
              accessedField = null;
              invokedMethod = null;
            }
            break;
          default:
            accessedField = null;
            invokedMethod = null;
            break;
        }
        if (invokedMethod != null
            && nonPublicMethods.containsKey(invokedMethod)
            && !methodReplacements.containsKey(invokedMethod)
            && !stagingMethodsMap.containsKey(invokedMethod)) {
          stagingMethods.addLast(invokedMethod);
          stagingMethodsMap.put(invokedMethod, nonPublicMethods.get(invokedMethod));
        }
        if (accessedField != null
            && nonPublicFields.containsKey(accessedField)
            && !fieldReplacements.containsKey(accessedField)) {
          fieldReplacements.put(accessedField, AsmHelpers.getReplacementFieldKey(accessedField));
        }
      }
    }
  }

  public void close() {
    for (Map.Entry<String, ClassMemberKey> anonymousClass : anonymousClasses.entrySet()) {
      ClassMemberKey enclosingMethod = anonymousClass.getValue();
      if (methodReplacements.containsKey(enclosingMethod)) {
        String anonymousClassInternalName = anonymousClass.getKey();
        typeReplacements.put(
            anonymousClassInternalName,
            AsmHelpers.getReplacementTypeInternalName(anonymousClassInternalName));
      }
    }

    // TODO(b/207007439): Move to full_desugar_jdk_libs.json configuration
    // once the proper Android SDK levels have been accessed.
    typeReplacements.put("jdk/internal/misc/Unsafe", "sun/misc/DesugarUnsafe");
    typeReplacements.put(
        "sun/nio/fs/DefaultFileSystemProvider", "wrapper/adapter/HybridFileSystemProvider");
    typeReplacements.put(
        "sun/nio/fs/DefaultFileTypeDetector", "wrapper/adapter/HybridFileTypeDetector");
  }

  @Nullable
  public String getReplacementType(String typeInternalName) {
    return typeReplacements.get(typeInternalName);
  }

  public String getNestHost(String typeInternalName) {
    return nestHosts.getOrDefault(typeInternalName, "");
  }

  public Set<String> getNestMembers(String typeInternalName) {
    return nestMembers.get(typeInternalName);
  }

  @Nullable
  public ClassMemberKey getReplacementMethod(ClassMemberKey method) {
    return methodReplacements.get(method);
  }

  @Nullable
  public ClassMemberKey getReplacementField(ClassMemberKey field) {
    return fieldReplacements.get(field);
  }

}
