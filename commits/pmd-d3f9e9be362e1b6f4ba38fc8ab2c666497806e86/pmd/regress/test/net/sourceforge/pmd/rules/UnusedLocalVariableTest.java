/**
 * <copyright>
 *  Copyright 1997-2002 InfoEther, LLC
 *  under sponsorship of the Defense Advanced Research Projects Agency
(DARPA).
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published
by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 *
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
 * </copyright>
 */
package test.net.sourceforge.pmd.rules;

import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.rules.UnusedLocalVariableRule;

public class UnusedLocalVariableTest extends SimpleAggregatorTst {

    private UnusedLocalVariableRule rule;

    public void setUp() {
        rule = new UnusedLocalVariableRule();
        rule.setMessage("Avoid this stuff -> ''{0}''");
    }

    public void testAll() {
       runTests(new TestDescriptor[] {
           new TestDescriptor(TEST1, "unused local with assignment", 1, rule),
           new TestDescriptor(TEST2, "unused local w/o assignment", 1, rule),
           new TestDescriptor(TEST3, "unused local in constructor", 1, rule),
           new TestDescriptor(TEST4, "local used on rhs", 0, rule),
           new TestDescriptor(TEST5, "unused local in static initializer", 1, rule),
           new TestDescriptor(TEST6, "unused field", 0, rule),
           new TestDescriptor(TEST7, "loop indexes are not unused locals", 0, rule),
           new TestDescriptor(TEST8, "", 0, rule),
           new TestDescriptor(TEST9, "", 0, rule),
           new TestDescriptor(TEST10, "", 2, rule),
           new TestDescriptor(TEST11, "", 0, rule),
           new TestDescriptor(TEST12, "", 0, rule),
           new TestDescriptor(TEST13, "", 2, rule),
           new TestDescriptor(TEST14, "", 0, rule)
       });
    }

    private static final String TEST1 =
    "public class Foo {" + PMD.EOL +
    " public foo() {" + PMD.EOL +
    "   String fr = new String();" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String TEST2 =
    "public class Foo {" + PMD.EOL +
    " public void method() {" + PMD.EOL +
    "  int x;" + PMD.EOL +
    " }" + PMD.EOL +
    "}" + PMD.EOL +
    "";

    private static final String TEST3 =
    "import java.util.*;" + PMD.EOL +
    "public class Foo {" + PMD.EOL +
    "    public Foo() {" + PMD.EOL +
    "       List a = new ArrayList();" + PMD.EOL +
    "    }" + PMD.EOL +
    "}" + PMD.EOL +
    "";

    private static final String TEST4 =
    "import java.util.*;" + PMD.EOL +
    "public class Foo {" + PMD.EOL +
    "    public Foo() {" + PMD.EOL +
    "       List a = new ArrayList();" + PMD.EOL +
    "       if (true == true) {" + PMD.EOL +
    "               a.size();" + PMD.EOL +
    "       }" + PMD.EOL +
    "    }" + PMD.EOL +
    "}" + PMD.EOL +
    "";

    private static final String TEST5 =
    "import java.util.*;" + PMD.EOL +
    "public class Foo {" + PMD.EOL +
    "static {" + PMD.EOL +
    "       String x;" + PMD.EOL +
    "}" + PMD.EOL +
    "}" + PMD.EOL +
    "";

    private static final String TEST6 =
    "public class Foo {" + PMD.EOL +
    " public int x;" + PMD.EOL +
    "}";

    private static final String TEST7 =
    "public class Foo {" + PMD.EOL +
    " public void foo() {" + PMD.EOL +
    "  for (int i=0;i<10; i++);" + PMD.EOL +
    "  for (int i=0;i<10; i++);" + PMD.EOL +
    " }" + PMD.EOL +
    "}";


    private static final String TEST8 =
    "public class Foo {" + PMD.EOL +
    " public void foo() {" + PMD.EOL +
    "  final String x = \"baf\";" + PMD.EOL +
    "   new Runnable() {" + PMD.EOL +
    "    public void run() {" + PMD.EOL +
    "     System.out.println(x);" + PMD.EOL +
    "    }" + PMD.EOL +
    "   };  " + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String TEST9 =
    "public interface Foo {" + PMD.EOL +
    " public void foo();" + PMD.EOL +
    " public String bar();" + PMD.EOL +
    "}";

    private static final String TEST10 =
    "public class Foo {" + PMD.EOL +
    " public void foo() {" + PMD.EOL +
    "  String x = \"hi\";" + PMD.EOL +
    "  class Bar {" + PMD.EOL +
    "   public void buz() {" + PMD.EOL +
    "    String x = \"howdy\";" + PMD.EOL +
    "   }" + PMD.EOL +
    "  }" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String TEST11 =
    "public class Foo {" + PMD.EOL +
    " public void foo() {" + PMD.EOL +
    "  for (int x = 0; ; ) { // USED" + PMD.EOL +
    "   x++;" + PMD.EOL +
    "  }" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String TEST12 =
    "public class Foo {" + PMD.EOL +
    " public void foo() {" + PMD.EOL +
    "  final String x = \"hi\";" + PMD.EOL +
    "   new Runnable() {" + PMD.EOL +
    "    public void run() {" + PMD.EOL +
    "     x.toString();" + PMD.EOL +
    "    }" + PMD.EOL +
    "   };" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String TEST13 =
    "public class Foo {" + PMD.EOL +
    " public void foo() {" + PMD.EOL +
    "  int x,y=0;" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

    private static final String TEST14 =
    "public class Foo {" + PMD.EOL +
    " public void bar() {" + PMD.EOL +
    "  try {" + PMD.EOL +
    "   //int x =2;" + PMD.EOL +
    "  } catch (RuntimeException e) {" + PMD.EOL +
    "  } catch (Exception e) {" + PMD.EOL +
    "  }" + PMD.EOL +
    " }" + PMD.EOL +
    "}";

}
