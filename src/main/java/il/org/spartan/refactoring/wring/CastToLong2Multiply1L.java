package il.org.spartan.refactoring.wring;

import static il.org.spartan.idiomatic.*;
import static il.org.spartan.refactoring.utils.extract.*;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.*;

import org.eclipse.jdt.core.dom.*;

import il.org.spartan.refactoring.utils.*;

/** Replace <code>(double)X</code> by <code>1.*X</code>
 * @author Alex Kopzon
 * @author Dan Greenstein
 * @since 2016 */
public final class CastToLong2Multiply1L extends Wring.ReplaceCurrentNode<CastExpression> implements Kind.NOP {
  @Override String description(final CastExpression e) {
    return "Use 1L*" + expression(e) + " instead of (long)" + expression(e);
  }

  @Override ASTNode replacement(final CastExpression e) {
    return eval(//
        () -> replacement(expression(e))//
    ).when(//
        type(e).isPrimitiveType() && "long".equals("" + type(e)) //
    );
  }

  private static InfixExpression replacement(final Expression $) {
    return subject.pair(literal($), $).to(TIMES);
  }

  private static NumberLiteral literal(final Expression e) {
    final NumberLiteral $ = e.getAST().newNumberLiteral();
    $.setToken("1L");
    return $;
  }
}