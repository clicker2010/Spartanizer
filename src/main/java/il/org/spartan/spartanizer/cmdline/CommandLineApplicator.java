package il.org.spartan.spartanizer.cmdline;

import java.util.*;

import org.eclipse.jdt.core.dom.*;

import il.org.spartan.plugin.*;

/** An {@link Applicator} suitable for the command line.
 * @author Matteo Orru'
 * @since 2016 */
public class CommandLineApplicator extends Applicator {
  private static final int PASSES_FEW = 1;
  private static final int PASSES_MANY = 20;

  public static Applicator defaultApplicator() {
    return new CommandLineApplicator().defaultSettings();
  }

  /** Default listener configuration of {@link GUIBatchLaconizer}. Simple printing to
   * console. 
   * @return this applicator */
  public CommandLineApplicator defaultListenerNoisy() {
    listener(os -> {
      for (final Object ¢ : os)
        System.out.print(¢ + " ");
      System.out.println();
    });
    return this;
  }

  /** @return this */
  private CommandLineApplicator defaultListenerSilent() {
    listener((final Object... __) -> {
      //
    });
    // listener(EventListener.simpleListener(event.class,
    // e -> {
    // // empty
    // },
    // (e, o) -> {
    // // empty
    // }));
    return this;
  }
  // /** Initialize the selection of this applicator.
  // * @param ¢ JD
  // * @return this applicator */
  // public Applicator selection(final AbstractSelection<?> ¢) {
  // selection = ¢;
  // return this;
  // }

  // TODO Matteo: change selection() in Applicator to return AbstractSelection?
  // public CommandLineSelection getSelection() {
  // return selection;
  // }
  /** @return this */
  private CommandLineApplicator defaultPassesFew() {
    passes(PASSES_FEW);
    return this;
  }

  /** Default passes configuration of {@link GUIBatchLaconizer}, with many passes.
   * @return this applicator */
  public CommandLineApplicator defaultPassesMany() {
    passes(PASSES_MANY);
    return this;
  }

  // TODO Matteo: I have changed the "run action" to return number of tips
  // committed instead of whether tips were committed (Boolean -> Integer).
  // Added a quick fix to your code. Also I do not understand why you wrote this
  // - we will inspect it once we meet. --or
  /** @return this */
  public CommandLineApplicator defaultRunAction() {
    // final Trimmer t = new Trimmer();
    final Spartanizer$Applicator s = new Spartanizer$Applicator();
    setRunAction(u -> Integer.valueOf(s.apply(u, selection()) ? 1 : 0));
    return this;
  }

//  // TODO Roth: use Policy / replacement for Trimmer.
//  /** Default run action configuration of {@link GUIBatchLaconizer}. Spartanize the
//   * {@link ICompilationUnit} using received {@link AbstractGUIApplicator}.
//   * @param a JD
//   * @return this applicator */
//  public GUIBatchLaconizer defaultRunAction(final AbstractGUIApplicator a) {
//    setRunAction(¢ -> Integer.valueOf(a.apply(¢, selection())));
//    name(a.getName());
//    return this;
//  }
  
  
  /** Default run action configuration of {@link CommandLineApplicator}.
   * Spartanize the {@link CompilationUnit} using received
   * {@link GUIBatchLaconizer$Applicator}.
   * @param a JD
   * @return this applicator */
  public CommandLineApplicator defaultRunAction(final Spartanizer$Applicator a) {
    setRunAction(u -> Integer.valueOf(a.apply(u, selection()) ? 1 : 0));
    name(a.getName());
    return this;
  }

  /** @return this */
  private CommandLineApplicator defaultRunContext() {
    runContext(r -> r.run());
    return this;
  }
  // private void setSelection(@SuppressWarnings("unused") final
  // AbstractSelection<CommandLineSelection> __) {
  // }

  /// ** Initialize the selection of this applicator.
  // * @param s JD
  // * @return this applicator */
  // public CommandLineApplicator setSelection(final CommandLineSelection $) {
  // selection = $;
  // return this;
  // }
  /** @return this */
  CommandLineApplicator defaultSelection() {
    selection(CommandLineSelection.Util.get());
    return this;
  }

  /** @param ¢ JD
   * @return */
  public CommandLineApplicator defaultSelection(@SuppressWarnings("rawtypes") final AbstractSelection ¢) {
    selection(¢);
    return this;
  }
  // /** @return selection of the applicator, ready to be configured. */
  // public CommandLineSelection selection() {
  // return this.selection;
  // }

  /** @return this */
  private Applicator defaultSettings() {
    return defaultListenerSilent().defaultPassesFew()
                                  .defaultRunContext()
                                  .defaultSelection()
                                  .defaultRunAction(new Spartanizer$Applicator());
  }

  /* (non-Javadoc)
   *
   * @see il.org.spartan.plugin.revision.Applicator#go() */
  @Override public void go() {
    if (selection() == null || listener() == null || passes() <= 0 || selection().isEmpty())
      return;
    // List<CompilationUnit> list = getSelection().getCompilationUnits();
    // for(CompilationUnit cu: list){
    // System.err.println(cu);
    // gUIBatchLaconizer.go(cu);
    // }
    runContext().accept(() -> {
      final int l = passes();
      for (int pass = 0; pass < l; ++pass) {
        final List<CompilationUnit> alive = new LinkedList<>();
        alive.addAll(((CommandLineSelection) selection()).getCompilationUnits());
        @SuppressWarnings("unused") final List<CompilationUnit> dead = new LinkedList<>();
        for (final CompilationUnit ¢ : alive)
          System.out.println(¢);
        // if(!runAction().apply(¢.build()).booleanValue())
        // dead.add(¢);
      }
    });
    System.err.println("go go go!");
  }
}
