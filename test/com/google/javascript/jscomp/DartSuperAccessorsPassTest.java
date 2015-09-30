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

import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT;
import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT_YET;
import static com.google.javascript.jscomp.Es6ToEs3Converter.CONFLICTING_GETTER_SETTER_TYPE;

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
        "var x = $jscomp.callSuperGetter(this, 'prop')");
    test("var x = super.prop",
        "var x = $jscomp.superGet(this, JSCompiler_renameProperty('prop'))");
  }

  public void testSuperSet() {
    test("super['prop'] = x",
        "$jscomp.superSet(this, 'prop', x)");
    test("super.prop = x",
        "$jscomp.superSet(this, JSCompiler_renameProperty('prop'), x)");
  }
    // test("class D {} class C extends D { f() { var i = super.c; i = super.foo.bar(); } }",
    //     LINE_JOINER.join(
    //         "/** @constructor @struct */",
    //         "var D = function() {};",
    //         "/** @constructor @struct @extends {D} */",
    //         "var C = function(var_args) { D.apply(this, arguments); };",
    //         "$jscomp.inherits(C, D);",
    //         "C.prototype.f = function() {",
    //         "  var i = $jscomp.callSuperGetter(this, JSCompiler_renameProperty('c'));",
    //         "  i = $jscomp.callSuperGetter(this, JSCompiler_renameProperty('foo')).bar();",
    //         "};"));
    // test("class D {} class C extends D { f() { var i = super['s']; } }",
    //     LINE_JOINER.join(
    //         "/** @constructor @struct */",
    //         "var D = function() {};",
    //         "/** @constructor @struct @extends {D} */",
    //         "var C = function(var_args) { D.apply(this, arguments); };",
    //         "$jscomp.inherits(C, D);",
    //         "C.prototype.f = function() {",
    //         "  var i = $jscomp.callSuperGetter(this, 's');",
    //         "};"));
}