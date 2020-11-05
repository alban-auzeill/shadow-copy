package com.auzeill.shadow.copy.arguments;

import com.auzeill.shadow.copy.ShadowCopyError;
import com.auzeill.shadow.copy.Version;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class Arguments {

  public final Action action;
  public final Map<Option, String> options;
  public final List<String> actionArguments;

  public Arguments(String... arguments) {
    options = new EnumMap<>(Option.class);
    actionArguments = new ArrayList<>();
    if (arguments.length == 0) {
      options.put(Option.HELP, "");
    }
    boolean acceptMoreOptions = true;
    int i = 0;
    while (i < arguments.length) {
      Option option = null;
      if (arguments[i].equals("--")) {
        acceptMoreOptions = false;
      } else {
        option = acceptMoreOptions ? appendOption(options, arguments, i) : null;
        if (option == null) {
          appendArgument(actionArguments, arguments[i], acceptMoreOptions);
        }
      }
      i += (option != null && option.hasOneArgument) ? 2 : 1;
    }
    action = validateActionAndOptions(actionArguments, options);
  }

  private static void appendArgument(List<String> actionArguments, String arg, boolean acceptOptions) {
    if (acceptOptions && arg.startsWith("--")) {
      throw new ShadowCopyError("Unknown option '" + arg + "', use '--' to separate options and arguments");
    }
    actionArguments.add(arg);
  }

  @Nullable
  private static Option appendOption(Map<Option, String> options, String[] args, int index) {
    String arg = args[index];
    Option option = Option.find(arg);
    if (option == null) {
      return null;
    }
    String value = "";
    if (option.hasOneArgument) {
      if (index + 1 >= args.length || args[index + 1].isBlank()) {
        throw new ShadowCopyError("Missing argument for " + option.flag);
      }
      value = args[index + 1];
    }
    options.put(option, value);
    return option;
  }

  private static Action validateActionAndOptions(List<String> actionArguments, Map<Option, String> options) {
    if (actionArguments.isEmpty()) {
      if (!options.containsKey(Option.HELP) && !options.containsKey(Option.VERSION)) {
        throw new ShadowCopyError("Missing action, list valid action with --help");
      }
      return null;
    } else {
      String actionName = actionArguments.get(0);
      actionArguments.remove(0);
      Action action = Action.find(actionName);
      if (action == null) {
        throw new ShadowCopyError("Unknown action '" + actionName + "', list valid action with --help");
      } else {
        options.keySet().stream()
          .filter(option -> !option.equals(Option.HELP) && !action.validOptions.contains(option))
          .findFirst().ifPresent(option -> {
            throw new ShadowCopyError("Option '" + option.flag + "' can not be used with action '" + action.command + "'");
          });
      }
      return action;
    }
  }

  public static String help() {
    StringBuilder out = new StringBuilder();
    out.append("Shadow Copy ").append(Version.get()).append("\n");
    out.append("Syntax: shadow-copy <action> <arguments>\n");
    out.append("\n");
    out.append("Available actions:\n");
    for (Action action : Action.values()) {
      out.append("- ").append(action.command).append(action.help).append("\n");
    }
    out.append("\n");
    out.append("Available options:\n");
    for (Option option : Option.values()) {
      out.append("  ").append(option.flag).append(option.help).append("\n");
    }
    return out.toString();
  }

}
