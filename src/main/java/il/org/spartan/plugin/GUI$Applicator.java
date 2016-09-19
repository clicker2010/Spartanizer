package il.org.spartan.plugin;

import static il.org.spartan.plugin.eclipse.*;
import static il.org.spartan.spartanizer.ast.wizard.*;

import java.util.*;
import java.util.List;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.*;
import org.eclipse.jface.text.*;
import org.eclipse.ltk.core.refactoring.*;
import org.eclipse.ltk.ui.refactoring.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;

import il.org.spartan.spartanizer.engine.*;
import il.org.spartan.utils.*;

/** the base class for all Spartanization Refactoring classes, contains common
 * functionality
 * @author Artium Nihamkin (original)
 * @author Boris van Sosin <boris.van.sosin [at] gmail.com>} (v2)
 * @author Yossi Gil <code><yossi.gil [at] gmail.com></code>: major refactoring
 *         2013/07/10
 * @since 2013/01/01 */
// TODO: Ori, check if we can eliminate this dependency on Refactoring...
public abstract class GUI$Applicator extends Refactoring {
  private static final String APPLY_TO_PROJECT = "Apply suggestion to entire project";
  private static final String APPLY_TO_FUNCTION = "Apply suggestion to enclosing function";
  private static final String APPLY_TO_FILE = "Apply suggestion to compilation unit";

  private static IMarkerResolution getWringCommit(final WringCommit.Type t, final String l) {
    return new IMarkerResolution() {
      @Override public String getLabel() {
        return l;
      }

      @Override public void run(final IMarker m) {
        try {
          WringCommit.go(nullProgressMonitor, m, t);
        } catch (IllegalArgumentException | CoreException e) {
          Plugin.log(e);
        }
      }
    };
  }

  private ITextSelection selection;
  private ICompilationUnit compilationUnit;
  private IMarker marker;
  final Collection<TextFileChange> changes = new ArrayList<>();
  private final String name;
  private int totalChanges;
  IProgressMonitor progressMonitor = nullProgressMonitor;

  /*** Instantiates this class, with message identical to name
   * @param name a short name of this instance */
  protected GUI$Applicator(final String name) {
    this.name = name;
  }

  public boolean apply(final ICompilationUnit cu) {
    return apply(cu, new Range(0, 0));
  }

  public boolean apply(final ICompilationUnit cu, final ITextSelection s) {
    try {
      setCompilationUnit(cu);
      setSelection(s.getLength() > 0 && !s.isEmpty() ? s : null);
      return performRule(cu);
    } catch (final CoreException x) {
      Plugin.log(x);
    }
    return false;
  }

  public boolean apply(final ICompilationUnit cu, final Range r) {
    return apply(cu, r == null || r.isEmpty() ? new TextSelection(0, 0) : new TextSelection(r.from, r.size()));
  }

  @Override public RefactoringStatus checkFinalConditions(final IProgressMonitor pm) throws CoreException, OperationCanceledException {
    changes.clear();
    totalChanges = 0;
    if (marker == null)
      runAsManualCall();
    else {
      innerRunAsMarkerFix(marker, true);
      marker = null; // consume marker
    }
    pm.done();
    return new RefactoringStatus();
  }

  @Override public RefactoringStatus checkInitialConditions(@SuppressWarnings("unused") final IProgressMonitor __) {
    final RefactoringStatus $ = new RefactoringStatus();
    if (compilationUnit == null && marker == null)
      $.merge(RefactoringStatus.createFatalErrorStatus("Nothing to refactor."));
    return $;
  }

  /** Checks a Compilation Unit (outermost ASTNode in the Java Grammar) for
   * spartanization suggestions
   * @param u what to check
   * @return a collection of {@link Suggestion} objects each containing a
   *         spartanization suggestion */
  public final List<Suggestion> collectSuggesions(final CompilationUnit ¢) {
    final List<Suggestion> $ = new ArrayList<>();
    ¢.accept(collectSuggestions(¢, $));
    return $;
  }

  /** Count the number files that would change after Spartanization.
   * <p>
   * This is an slow operation. Do not call light-headedly.
   * @return total number of files with suggestions */
  public int countFilesChanges() {
    // TODO OriRoth: not sure if this function is necessary - if it is, it could
    // be easily optimized when called after countSuggestions()
    setMarker(null);
    try {
      checkFinalConditions(nullProgressMonitor);
    } catch (final OperationCanceledException e) {
      Plugin.log(e);
    } catch (final CoreException e) {
      Plugin.log(e);
    }
    return changes.size();
  }

  /** Count the number of suggestions offered by this instance.
   * <p>
   * This is an slow operation. Do not call light-headedly.
   * @return total number of suggestions offered by this instance */
  public int countSuggestions() {
    setMarker(null);
    try {
      checkFinalConditions(progressMonitor);
    } catch (final OperationCanceledException e) {
      // TODO: what should we do here? This is not an error
    } catch (final CoreException e) {
      Plugin.log(e);
    }
    return totalChanges;
  }

  @Override public final Change createChange(final IProgressMonitor pm) throws OperationCanceledException {
    progressMonitor = pm;
    return new CompositeChange(getName(), changes.toArray(new Change[changes.size()]));
  }

  /** creates an ASTRewrite which contains the changes
   * @param u the Compilation Unit (outermost ASTNode in the Java Grammar)
   * @param m a progress monitor in which the progress of the refactoring is
   *        displayed
   * @return an ASTRewrite which contains the changes */
  public final ASTRewrite createRewrite(final CompilationUnit u) {
    return rewriterOf(u, (IMarker) null);
  }

  public IMarkerResolution disableClassFix() {
    return getToggle(SuppressSpartanizationOnOff.Type.CLASS, "Disable spartanization for class");
  }

  public IMarkerResolution disableFileFix() {
    return getToggle(SuppressSpartanizationOnOff.Type.FILE, "Disable spartanization for file");
  }

  public IMarkerResolution disableFunctionFix() {
    return getToggle(SuppressSpartanizationOnOff.Type.DECLARATION, "Disable spartanization for scope");
  }

  /** @return compilationUnit */
  public ICompilationUnit getCompilationUnit() {
    return compilationUnit;
  }

  /** @return a quick fix for this instance */
  public IMarkerResolution getFix() {
    return getFix(getName());
  }

  /** @param s Spartanization's name
   * @return a quickfix which automatically performs the spartanization */
  public IMarkerResolution getFix(final String s) {
    /** a quickfix which automatically performs the spartanization
     * @author Boris van Sosin <code><boris.van.sosin [at] gmail.com></code>
     * @since 2013/07/01 */
    return new IMarkerResolution() {
      @Override public String getLabel() {
        return s;
      }

      @Override public void run(final IMarker m) {
        try {
          runAsMarkerFix(m);
        } catch (final CoreException e) {
          Plugin.log(e);
        }
      }
    };
  }

  /** @return a quick fix with a preview for this instance. */
  public IMarkerResolution getFixWithPreview() {
    return getFixWithPreview(getName());
  }

  /** @param s Text for the preview dialog
   * @return a quickfix which opens a refactoring wizard with the
   *         spartanization */
  public IMarkerResolution getFixWithPreview(final String s) {
    return new IMarkerResolution() {
      /** a quickfix which opens a refactoring wizard with the spartanization
       * @author Boris van Sosin <code><boris.van.sosin [at] gmail.com></code>
       *         (v2) */
      @Override public String getLabel() {
        return "Show spartanization preview";
      }

      @Override public void run(final IMarker m) {
        setMarker(m);
        try {
          new RefactoringWizardOpenOperation(new Wizard(GUI$Applicator.this)).run(Display.getCurrent().getActiveShell(),
              "Spartan refactoring: " + s + GUI$Applicator.this);
        } catch (final InterruptedException e) {
          // TODO: What should we do here?
          Plugin.log(e);
        }
      }
    };
  }

  @Override public final String getName() {
    return name;
  }

  public IProgressMonitor getProgressMonitor() {
    return progressMonitor;
  }

  /** @return selection */
  public ITextSelection getSelection() {
    return selection;
  }

  public IMarkerResolution getWringCommitDeclaration() {
    return getWringCommit(WringCommit.Type.DECLARATION, APPLY_TO_FUNCTION);
  }

  public IMarkerResolution getWringCommitFile() {
    return getWringCommit(WringCommit.Type.FILE, APPLY_TO_FILE);
  }

  public IMarkerResolution getWringCommitProject() {
    return getWringCommit(WringCommit.Type.PROJECT, APPLY_TO_PROJECT);
  }

  /** .
   * @return True if there are spartanizations which can be performed on the
   *         compilation unit. */
  public final boolean haveSuggestions() {
    return countSuggestions() > 0;
  }

  /** @param m marker which represents the range to apply the Spartanization
   *        within
   * @param n the node which needs to be within the range of
   *        <code><b>m</b></code>
   * @return True if the node is within range */
  public final boolean inRange(final IMarker m, final ASTNode n) {
    return m != null ? !isNodeOutsideMarker(n, m) : !isTextSelected() || !isNodeOutsideSelection(n);
  }

  /** Performs the current Spartanization on the provided compilation unit
   * @param u the compilation to Spartanize
   * @param pm progress monitor for long operations (could be
   *        {@link NullProgressMonitor} for light operations)
   * @throws CoreException exception from the <code>pm</code> */
  public boolean performRule(final ICompilationUnit u) throws CoreException {
    progressMonitor.beginTask("Creating change for a single compilation unit...", IProgressMonitor.UNKNOWN);
    final TextFileChange textChange = new TextFileChange(u.getElementName(), (IFile) u.getResource());
    textChange.setTextType("java");
    final IProgressMonitor m = newSubMonitor(progressMonitor);
    textChange.setEdit(createRewrite((CompilationUnit) Make.COMPILATION_UNIT.parser(u).createAST(m)).rewriteAST());
    final boolean $ = textChange.getEdit().getLength() != 0;
    if ($)
      textChange.perform(progressMonitor);
    progressMonitor.done();
    return $;
  }

  public ASTRewrite rewriterOf(final CompilationUnit u, final IMarker m) {
    progressMonitor.beginTask("Creating rewrite operation...", IProgressMonitor.UNKNOWN);
    final ASTRewrite $ = ASTRewrite.create(u.getAST());
    consolidateSuggestions($, u, m);
    progressMonitor.done();
    return $;
  }

  /** @param pm a progress monitor in which to display the progress of the
   *        refactoring
   * @param m the marker for which the refactoring needs to run
   * @return a RefactoringStatus
   * @throws CoreException the JDT core throws it */
  public RefactoringStatus runAsMarkerFix(final IMarker m) throws CoreException {
    return innerRunAsMarkerFix(m, false);
  }

  /** @param compilationUnit the compilationUnit to set */
  public void setCompilationUnit(final ICompilationUnit ¢) {
    compilationUnit = ¢;
  }

  /** @param marker the marker to set for the refactoring */
  public final void setMarker(final IMarker ¢) {
    marker = ¢;
  }

  public void setProgressMonitor(final IProgressMonitor progressMonitor) {
    this.progressMonitor = progressMonitor;
  }

  /** @param subject the selection to set */
  public void setSelection(final ITextSelection ¢) {
    selection = ¢;
  }

  @Override public String toString() {
    return name;
  }

  protected abstract ASTVisitor collectSuggestions(final CompilationUnit u, final List<Suggestion> $);

  protected abstract void consolidateSuggestions(ASTRewrite r, CompilationUnit u, IMarker m);

  /** Determines if the node is outside of the selected text.
   * @return true if the node is not inside selection. If there is no selection
   *         at all will return false.
   * @DisableSpartan */
  protected boolean isNodeOutsideSelection(final ASTNode ¢) {
    return !isSelected(¢.getStartPosition());
  }

  /** @param u JD
   * @throws CoreException */
  protected void scanCompilationUnit(final ICompilationUnit u, final IProgressMonitor m) throws CoreException {
    m.beginTask("Creating change for a single compilation unit...", IProgressMonitor.UNKNOWN);
    final TextFileChange textChange = new TextFileChange(u.getElementName(), (IFile) u.getResource());
    textChange.setTextType("java");
    final CompilationUnit cu = (CompilationUnit) Make.COMPILATION_UNIT.parser(u).createAST(progressMonitor);
    textChange.setEdit(createRewrite(cu).rewriteAST());
    if (textChange.getEdit().getLength() != 0)
      changes.add(textChange);
    totalChanges += collectSuggesions(cu).size();
    m.done();
  }

  protected void scanCompilationUnitForMarkerFix(final IMarker m, final boolean preview) throws CoreException {
    progressMonitor.beginTask("Creating change(s) for a single compilation unit...", 2);
    final ICompilationUnit u = makeAST.iCompilationUnit(m);
    final TextFileChange textChange = new TextFileChange(u.getElementName(), (IFile) u.getResource());
    textChange.setTextType("java");
    textChange.setEdit(createRewrite(m).rewriteAST());
    if (textChange.getEdit().getLength() != 0)
      if (!preview)
        textChange.perform(progressMonitor);
      else
        changes.add(textChange);
    progressMonitor.done();
  }

  /** Creates a change from each compilation unit and stores it in the changes
   * list
   * @throws IllegalArgumentException
   * @throws CoreException */
  protected void scanCompilationUnits(final List<ICompilationUnit> us) throws IllegalArgumentException, CoreException {
    progressMonitor.beginTask("Iterating over gathered compilation units...", us.size());
    for (final ICompilationUnit ¢ : us)
      scanCompilationUnit(¢, newSubMonitor(progressMonitor));
    progressMonitor.done();
  }

  /** creates an ASTRewrite, under the context of a text marker, which contains
   * the changes
   * @param pm a progress monitor in which to display the progress of the
   *        refactoring
   * @param m the marker
   * @return an ASTRewrite which contains the changes */
  private ASTRewrite createRewrite(final IMarker m) {
    return rewriterOf((CompilationUnit) makeAST.COMPILATION_UNIT.from(m, progressMonitor), m);
  }

  private IMarkerResolution getToggle(final SuppressSpartanizationOnOff.Type t, final String l) {
    return new IMarkerResolution() {
      @Override public String getLabel() {
        return l;
      }

      @Override public void run(final IMarker m) {
        try {
          SuppressSpartanizationOnOff.deactivate(nullProgressMonitor, m, t);
        } catch (IllegalArgumentException | CoreException x) {
          Plugin.log(x);
        }
      }
    };
  }

  private List<ICompilationUnit> getUnits() throws JavaModelException {
    if (!isTextSelected())
      return compilationUnits(compilationUnit != null ? compilationUnit : currentCompilationUnit(), newSubMonitor(progressMonitor));
    final List<ICompilationUnit> $ = new ArrayList<>();
    $.add(compilationUnit);
    return $;
  }

  private RefactoringStatus innerRunAsMarkerFix(final IMarker m, final boolean preview) throws CoreException {
    marker = m;
    progressMonitor.beginTask("Running refactoring...", IProgressMonitor.UNKNOWN);
    scanCompilationUnitForMarkerFix(m, preview);
    marker = null;
    progressMonitor.done();
    return new RefactoringStatus();
  }

  private boolean isSelected(final int offset) {
    return isTextSelected() && offset >= selection.getOffset() && offset < selection.getLength() + selection.getOffset();
  }

  private boolean isTextSelected() {
    return selection != null && !selection.isEmpty() && selection.getLength() != 0;
  }

  private void runAsManualCall() throws JavaModelException, CoreException {
    progressMonitor.beginTask("Checking preconditions...", IProgressMonitor.UNKNOWN);
    scanCompilationUnits(getUnits());
    progressMonitor.done();
  }
}