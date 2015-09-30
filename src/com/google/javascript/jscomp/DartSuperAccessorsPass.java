/*
 * Copyright 2014 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Converts {@code super} getter and setter calls in order to support the output
 * of the Dart Dev Compiler. This has to run before the {@link Es6ConvertSuper} pass.
 *
 * @author ochafik@google.com (Olivier Chafik)
 */
public final class DartSuperAccessorsPass implements NodeTraversal.Callback,
    HotSwapCompilerPass {
  static final String CALL_SUPER_GET = "$jscomp.superGet";
  static final String CALL_SUPER_SET = "$jscomp.superSet";

  private final AbstractCompiler compiler;

  public DartSuperAccessorsPass(AbstractCompiler compiler) {
    this.compiler = compiler;
    CompilerOptions options = compiler.getOptions();
    // We currently rely on JSCompiler_renameProperty, which is not type-aware.
    // We would need support for something like goog.reflect.object (using parent class type).
    Preconditions.checkState(!options.ambiguateProperties && !options.disambiguateProperties,
        "Dart super accessors pass is not compatible with property (de)ambiguation yet");
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (isSuperGet(n)) {
      visitSuperGet(n);
      return false;
    } else if (isSuperSet(n)) {
      visitSuperSet(n);
      return false;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {}

  private static boolean isSuperGet(Node n) {
    return (n.isGetProp() || n.isGetElem())
        && !n.getParent().isCall()
        && n.getFirstChild().isSuper()
        && !isEnclosedByStaticMember(n);
  }

  private static boolean isSuperSet(Node n) {
    // TODO(ochafik): Handle Token.ASSIGN_ADD (super.x += 1).
    return n.isAssign() && isSuperGet(n.getFirstChild());
  }

  /**
   * Returns true if this node is or is enclosed by a static member definition (static method,
   * getter or setter).
   */
  private static boolean isEnclosedByStaticMember(Node n) {
    while (n != null) {
      if (n.isMemberFunctionDef() && n.isStaticMember()) {
        return true;
      }
      if (n.isClass()) {
        // Stop at the first enclosing class.
        return false;
      }
      n = n.getParent();
    }
    return false;
  }

  private void visitSuperGet(Node superGet) {
    Preconditions.checkArgument(isSuperGet(superGet));

    Node name = superGet.getLastChild().cloneTree();
    Node callSuperGet = IR.call(
        NodeUtil.newQName(compiler, CALL_SUPER_GET),
        IR.thisNode(),
        superGet.isGetProp() ? renameProperty(name) : name);
    callSuperGet.srcrefTree(superGet);

    superGet.getParent().replaceChild(superGet, callSuperGet);

    compiler.needsEs6DartRuntime = true;
    compiler.reportCodeChange();
  }

  private void visitSuperSet(Node superSet) {
    Preconditions.checkArgument(isSuperSet(superSet));
    
    // Recurse on the assignment right-hand-side.
    NodeTraversal.traverse(compiler, superSet.getLastChild(), this);
    Node rhs = superSet.getLastChild();
    Node superGet = superSet.getFirstChild();

    Node name = superGet.getLastChild().cloneTree();
    Node callSuperSet = IR.call(
        NodeUtil.newQName(compiler, CALL_SUPER_SET),
        IR.thisNode(),
        superGet.isGetProp() ? renameProperty(name) : name,
        rhs.cloneTree().srcrefTree(rhs));
    callSuperSet.srcrefTree(superSet);

    superSet.getParent().replaceChild(superSet, callSuperSet);

    compiler.needsEs6DartRuntime = true;
    compiler.reportCodeChange();
  }

  /**
   * Wraps a property string in a JSCompiler_renameProperty call.
   *
   * <p>Should only be called in phases running before {@link RenameProperties}.
   */
  private static Node renameProperty(Node propertyName) {
    Preconditions.checkArgument(propertyName.isString());
    Node call = IR.call(IR.name(NodeUtil.JSC_PROPERTY_NAME_FN), propertyName).srcrefTree(propertyName);
    call.putBooleanProp(Node.FREE_CALL, true);
    return call;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, this);
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }
}
