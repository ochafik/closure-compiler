/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Test case for {@link DartSuperAccessorsPass}.
 *
 * @author ochafik@google.com (Olivier Chafik)
 */
public final class DartSuperAccessorsPassTest extends CompilerTestCase {

  /** Signature of the member functions / accessors we'll wrap expressions into. */
  private static final ImmutableList<String> MEMBER_SIGNATURES = ImmutableList.of(
      "method()", "constructor()",
      // ES4 getters and setters:
      "get prop()", "set prop(v)",
      // ES6 computed properties:
      "get ['prop']()", "set ['prop'](v)");
  
  private static final ImmutableList<String> ASSIGNABLE_OPS =
      ImmutableList.of("|", "^", "&", "<<", ">>", ">>>", "+", "-", "*", "/", "%");

  private PropertyRenamingPolicy propertyRenaming;
  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    runTypeCheckAfterProcessing = true;
    propertyRenaming = PropertyRenamingPolicy.ALL_UNQUOTED;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDartPass(true);
    options.setAmbiguateProperties(false);
    options.setDisambiguateProperties(false);
    options.setPropertyRenaming(propertyRenaming);
    return options;
  }

  protected final PassFactory makePassFactory(
      String name, final CompilerPass pass) {
    return new PassFactory(name, true/* one-time pass */) {
      @Override
      protected CompilerPass create(AbstractCompiler compiler) {
        return pass;
      }
    };
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null, null);
    optimizer.addOneTimePass(
        makePassFactory("dartSuperAccessors", new DartSuperAccessorsPass(compiler)));
    return optimizer;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testSuperGetElem() {
    checkConversionWithinMembers(
        "return super['prop']",
        "return $jscomp.superGet(this, 'prop')");
  }

  public void testSuperGetProp_renameOff() {
    propertyRenaming = PropertyRenamingPolicy.OFF;
    checkConversionWithinMembers(
        "return super.prop",
        "return $jscomp.superGet(this, 'prop')");
  }

  public void testSuperGetProp_renameAll() {
    propertyRenaming = PropertyRenamingPolicy.ALL_UNQUOTED;
    checkConversionWithinMembers(
        "return super.prop",
        "return $jscomp.superGet(this, JSCompiler_renameProperty('prop'))");
  }

  public void testSuperSetElem() {
    checkConversionWithinMembers(
        "super['prop'] = x",
        "$jscomp.superSet(this, 'prop', x)");
  }

  public void testSuperSetProp_renameOff() {
    propertyRenaming = PropertyRenamingPolicy.OFF;
    checkConversionWithinMembers(
        "super.prop = x",
        "$jscomp.superSet(this, 'prop', x)");
  }

  public void testSuperSetProp_renameAll() {
    propertyRenaming = PropertyRenamingPolicy.ALL_UNQUOTED;
    checkConversionWithinMembers(
        "super.prop = x",
        "$jscomp.superSet(this, JSCompiler_renameProperty('prop'), x)");
  }

  /** Test operators like `super.x += y`. */
  public void testSuperSetAssignableOps() {
    propertyRenaming = PropertyRenamingPolicy.OFF;
    for (String op : ASSIGNABLE_OPS) {
      String assignOp = op + "=";
      checkConversionWithinMembers(
          "super.a " + assignOp + " b",
          "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') " + op + " b)");
      // Also ensure the right-hand-side of these operators is recursed upon.
      checkConversionWithinMembers(
          "super.a " + assignOp + " super.b",
          "$jscomp.superSet(this, 'a', "
              + "$jscomp.superGet(this, 'a') " + op + " $jscomp.superGet(this, 'b'))");
    }
  }

  public void testSuperSetRecursion() {
    checkConversionWithinMembers(
        "super['x'] = super['y']",
        "$jscomp.superSet(this, 'x', $jscomp.superGet(this, 'y'))");
    checkConversionWithinMembers(
        "super['x'] = super['y'] = 10",
        "$jscomp.superSet(this, 'x', $jscomp.superSet(this, 'y', 10))");
    checkConversionWithinMembers(
        "super['x'] = 1 + super['y']",
        "$jscomp.superSet(this, 'x', 1 + $jscomp.superGet(this, 'y'))");
  }


  public void testExpressionsWithoutSuperAccessors() {
    String body = LINE_JOINER.join(
        "foo.bar;",
        "foo.bar();",
        "this.bar;",
        "this.bar();",
        "super();",
        "super.bar();");

    for (String sig : MEMBER_SIGNATURES) {
      testSame(wrap(sig, body));
    }
  }

  public void testSuperAccessorsOutsideInstanceMembers() {
    String body = LINE_JOINER.join(
        "super.x;",
        "super.x = y;");

    testSame(body);

    for (String sig : MEMBER_SIGNATURES) {
      testSame(wrap("static " + sig, body));
    }
  }

  /**
   * Checks that when the provided {@code js} snippet is inside any kind of instance member
   * function (instance method, getter or setter), it is converted to the {@code expected} snippet.
   */
  private void checkConversionWithinMembers(String js, String expected) {
    for (String sig : MEMBER_SIGNATURES) {
      test(wrap(sig, js), wrap(sig, expected));
    }
  }

  /**
   * Wraps a body (statements) in a member function / accessor with the provided signature.
   * (can be static or not).
   */
  private String wrap(String memberSignature, String body) {
    return LINE_JOINER.join(
        "class X extends Y {",
        "  " + memberSignature + " {",
        "    " + body,
        "  }",
        "}");
  }
}
