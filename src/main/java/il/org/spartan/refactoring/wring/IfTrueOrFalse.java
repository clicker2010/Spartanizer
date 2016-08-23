package il.org.spartan.refactoring.wring;

import static il.org.spartan.refactoring.utils.Funcs.*;
import static il.org.spartan.refactoring.utils.Is.*;

import org.eclipse.jdt.core.dom.*;

/** convert
 *
 * <pre>
 * if (true) x; else {y;}
 * </pre>
 *
 * into
 *
 * <pre>
 * x;
 * </pre>
 ***********************************
 * <pre>
 * if (false) x; else {y;}
 * </pre>
 *
 * into
 *
 * <pre>
 * y;
 * </pre>
 *
 * .
 * @author Alex Kopzon
 * @author Dan Greenstein
 * @since 2016 */
public final class IfTrueOrFalse extends Wring.ReplaceCurrentNode<IfStatement> implements Kind.NOP {
  @Override String description(@SuppressWarnings("unused") final IfStatement __) {
    return "if the condition is 'true'  convert to 'then' statement," + " if the condition is 'false' convert to 'else' statement";
  }

  @Override Statement replacement(final IfStatement s) {
    //Prior test in scopeIncludes makes sure that only IfStatements containing a 'true' or 'false'
    //get into the replace.
    return isLiteralTrue(s.getExpression()) ? then(s) : elze(s);
  }

  @Override boolean scopeIncludes(final IfStatement s) {
    return s != null && (isLiteralTrue(s.getExpression()) || isLiteralFalse(s.getExpression()));
  }
}