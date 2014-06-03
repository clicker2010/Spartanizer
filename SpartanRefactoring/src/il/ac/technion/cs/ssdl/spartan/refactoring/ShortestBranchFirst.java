package il.ac.technion.cs.ssdl.spartan.refactoring;

import static il.ac.technion.cs.ssdl.spartan.utils.Funcs.countNodes;
import static il.ac.technion.cs.ssdl.spartan.utils.Funcs.makeInfixExpression;
import static il.ac.technion.cs.ssdl.spartan.utils.Funcs.makeParenthesizedConditionalExp;
import static il.ac.technion.cs.ssdl.spartan.utils.Funcs.makeParenthesizedExpression;
import static il.ac.technion.cs.ssdl.spartan.utils.Funcs.makePrefixExpression;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.EQUALS;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.GREATER;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.GREATER_EQUALS;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.LESS;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.LESS_EQUALS;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.NOT_EQUALS;
import il.ac.technion.cs.ssdl.spartan.utils.Range;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * @author Artium Nihamkin (original)
 * @author Boris van Sosin <code><boris.van.sosin [at] gmail.com></code> (v2)
 * 
 * @since 2013/01/01
 */
public class ShortestBranchFirst extends Spartanization {
	/** Instantiates this class */
	public ShortestBranchFirst() {
		super("Shortester first",
				"Negate the expression of a conditional, and change the order of branches so that shortest branch occurs first");
	}

	@Override protected final void fillRewrite(final ASTRewrite r, final AST t, final CompilationUnit cu, final IMarker m) {
		cu.accept(new ASTVisitor() {
			@Override public boolean visit(final IfStatement n) {
				if (!inRange(m, n))
					return true;
				if (longerFirst(n) && transpose(n) != null)
					r.replace(n, transpose(n), null);
				return true;
			}

			@Override public boolean visit(final ConditionalExpression n) {
				if (!inRange(m, n))
					return true;
				if (longerFirst(n))
					r.replace(n, transpose(n), null);
				return true;
			}

			private IfStatement transpose(final IfStatement n) {
				final IfStatement $ = t.newIfStatement();
				final Expression negatedOp = negate(t, r, n.getExpression());
				if (negatedOp == null)
					return null;
				$.setExpression(negatedOp);
				$.setThenStatement((Statement) r.createMoveTarget(n.getElseStatement()));
				$.setElseStatement((Statement) r.createMoveTarget(n.getThenStatement()));
				return $;
			}

			private ParenthesizedExpression transpose(final ConditionalExpression n) {
				return n!=null ? makeParenthesizedConditionalExp(t, r, negate(t, r, n.getExpression()), n.getElseExpression(), n.getThenExpression()) : null;
			}
		});
	}

	/**
	 * @return a prefix expression that is the negation of the provided
	 *         expression.
	 */
	static Expression negate(final AST t, final ASTRewrite r, final Expression e) {

		if (e instanceof InfixExpression) {
			final Expression $ = tryNegateComparison(t, r, (InfixExpression) e);
			return $ == null ? null : $;
		}

		if (e instanceof PrefixExpression) {
			final Expression $ = tryNegatePrefix(r, (PrefixExpression) e);
			return $ == null ? null : $;
		}
		return makePrefixExpression(t, r, makeParenthesizedExpression(t, r, e), PrefixExpression.Operator.NOT);
	}

	private static Expression tryNegateComparison(final AST ast, final ASTRewrite rewrite, final InfixExpression e) {
		final Operator o = negate(e.getOperator());
		return o==null ? null : makeInfixExpression(ast, rewrite, o, e.getLeftOperand(), e.getRightOperand());
	}

	private static Operator negate(final Operator o) {
		return o.equals(EQUALS) ? NOT_EQUALS : o.equals(NOT_EQUALS) ? EQUALS : o.equals(LESS) ? GREATER_EQUALS : o.equals(GREATER) ? LESS_EQUALS : o.equals(LESS_EQUALS) ? GREATER : o.equals(GREATER_EQUALS) ? LESS : null;
	}

	private static Expression tryNegatePrefix(final ASTRewrite rewrite, final PrefixExpression exp) {
		return !exp.getOperator().equals(PrefixExpression.Operator.NOT) ? null : (Expression) rewrite.createCopyTarget(exp.getOperand());
	}

	private static final int threshold = 1;

	@Override protected ASTVisitor fillOpportunities(final List<Range> opportunities) {
		return new ASTVisitor() {
			@Override public boolean visit(final IfStatement n) {
				if (longerFirst(n))
					opportunities.add(new Range(n));
				return true;
			}

			@Override public boolean visit(final ConditionalExpression n) {
				if (longerFirst(n))
					opportunities.add(new Range(n));
				return true;
			}
		};
	}

	static boolean longerFirst(final IfStatement n) {
		return n.getElseStatement() != null && countNodes(n.getThenStatement()) > countNodes(n.getElseStatement()) + threshold;
	}

	static boolean longerFirst(final ConditionalExpression n) {
		return n.getThenExpression().getLength() > n.getElseExpression().getLength() + threshold;
	}
}
