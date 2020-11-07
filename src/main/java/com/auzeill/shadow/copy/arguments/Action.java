package com.auzeill.shadow.copy.arguments;

import com.auzeill.shadow.copy.action.CreateAction;
import com.auzeill.shadow.copy.action.DiffAction;
import com.auzeill.shadow.copy.action.HistoryAction;
import com.auzeill.shadow.copy.action.PurgeAction;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public enum Action {
  CREATE("create", " [ <target-directory> ]\n" +
    "    # Create a shadow copy of the current directory into a new sub-directory of '.shadow-copy':\n" +
    "    shadow-copy create\n" +
    "    # Copy the '/home/paul' directory into a new sub-directory of '/home/paul/.shadow-copy':\n" +
    "    shadow-copy create /home/paul\n" +
    "    # Copy the '/home/paul' directory into a new sub-directory of '/tmp/test':\n" +
    "    shadow-copy create /home/paul --shadow-directory /tmp/test",
    CreateAction::new,
    Option.SHADOW_DIRECTORY, Option.SHADOW_INDEX),
  HISTORY("history", " [ <target-directory> ]\n" +
    "    # Show the sorted list of shadow copy index and path, index 1 is the latest:\n" +
    "    shadow-copy history\n" +
    "    # Show only the latest shadow copy path:\n" +
    "    shadow-copy history -n 1 --no-index\n" +
    "    # Show size of each shadow copies:\n" +
    "    shadow-copy history --no-index | xargs du -hs",
    HistoryAction::new,
    Option.SHADOW_DIRECTORY, Option.NO_INDEX, Option.NUMBER),
  DIFF("diff", " [ <target-directory> ] [ <index> ]  [ <index> ]\n" +
    "    # Compare the current directory with the last shadow copy:\n" +
    "    shadow-copy diff\n" +
    "    # Compare the current directory with the given shadow copy index:\n" +
    "    shadow-copy diff 2\n" +
    "    # Compare two shadow copies:\n" +
    "    shadow-copy diff 2 3",
    DiffAction::new,
    Option.SHADOW_DIRECTORY, Option.SHADOW_INDEX),
  PURGE("purge", " [ <target-directory> ]\n" +
    "    # Only keep the 10 latest shadow copies:\n" +
    "    shadow-copy purge\n" +
    "    # Only keep the 5 latest shadow copies:\n" +
    "    shadow-copy purge -n 5",
    PurgeAction::new,
    Option.SHADOW_DIRECTORY, Option.NUMBER);

  public interface Execute {
    void execute(PrintStream out, Arguments arguments) throws IOException, InterruptedException;
  }

  public final String command;
  public final Supplier<Execute> factory;
  public final List<Option> validOptions;
  public final String help;

  Action(String command, String help, Supplier<Execute> factory, Option... validOptions) {
    this.command = command;
    this.factory = factory;
    this.validOptions = Arrays.asList(validOptions);
    this.help = help;
  }

  public static Action find(String command) {
    return Arrays.stream(Action.values())
      .filter(action -> action.command.equals(command))
      .findFirst().orElse(null);
  }

}
