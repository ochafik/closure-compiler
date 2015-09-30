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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Test case for {@link DartSuperAccessorsPass}.
 *
 * @author ochafik@google.com (Olivier Chafik)
 */
public final class DartSuperAccessorsPassTest extends CompilerTestCase {

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    runTypeCheckAfterProcessing = true;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDartPass(true);
    options.setAmbiguateProperties(false);
    options.setDisambiguateProperties(false);
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

  public void testSuperGet() {
    test("var x = super['prop']",
        "var x = $jscomp.superGet(this, 'prop')");
    test("var x = super.prop",
        "var x = $jscomp.superGet(this, JSCompiler_renameProperty('prop'))");
  }

  public void testSuperSet() {
    test("super['prop'] = x",
        "$jscomp.superSet(this, 'prop', x)");
    test("super.prop = x",
        "$jscomp.superSet(this, JSCompiler_renameProperty('prop'), x)");
  }
  
  public void testSuperSetRecursion() {
    test("super['x'] = super['y']",
        "$jscomp.superSet(this, 'x', $jscomp.superGet(this, 'y'))");
    test("super['x'] = super['y'] = 10",
        "$jscomp.superSet(this, 'x', $jscomp.superSet(this, 'y', 10))");
    test("super['x'] = 1 + super['y']",
        "$jscomp.superSet(this, 'x', 1 + $jscomp.superGet(this, 'y'))");
  }

  public void testUnaffectedCalls() {
    testSame("var x = super()");
    testSame("var x = super.bar()");
    testSame("class X extends Y { static foo() { super.bar() } }");
  }
}