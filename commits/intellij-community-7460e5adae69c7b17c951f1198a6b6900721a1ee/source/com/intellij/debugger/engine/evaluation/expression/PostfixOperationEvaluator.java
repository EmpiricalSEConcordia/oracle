package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.sun.jdi.Value;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 20, 2004
 * Time: 5:34:50 PM
 * To change this template use File | Settings | File Templates.
 */
public class PostfixOperationEvaluator implements Evaluator{
  private final Evaluator myOperandEvaluator;
  private static final Evaluator myRightEvaluator = new LiteralEvaluator(new Integer(1), "byte");

  private IElementType myOpType;
  private String myExpectedType; // a result of PsiType.getCanonicalText()

  private Object myValue;
  private Modifier myModifier;

  public PostfixOperationEvaluator(Evaluator operandEvaluator, IElementType opType, String expectedType) {
    myOperandEvaluator = operandEvaluator;
    myOpType = opType;
    myExpectedType = expectedType;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    myValue = (Value)myOperandEvaluator.evaluate(context);
    myModifier = myOperandEvaluator.getModifier();

    IElementType opType = myOpType == TokenType.PLUSPLUS ? TokenType.PLUS : TokenType.MINUS;
    Object operationResult = BinaryExpressionEvaluator.evaluateOperation((Value)myValue, opType, myRightEvaluator, myExpectedType, context);
    AssignmentEvaluator.assign(myModifier, operationResult, context);
    return myValue;
  }

  public Modifier getModifier() {
    return myModifier;
  }
}
