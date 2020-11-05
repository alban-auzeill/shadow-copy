package com.auzeill.shadow.copy.arguments;

import java.util.Arrays;

public enum Option {
  VERSION("--version", false, "\n" +
    "    Display the shadow-copy version."),
  HELP("--help", false, "\n" +
    "    Show this help."),
  SHADOW_DIRECTORY("--shadow-directory", true, " <directory-path>\n" +
    "    Replace usage of a '.shadow-copy' sub-directory by the given directory path."),
  SHADOW_INDEX("--shadow-index", true, " <index>\n" +
    "    Force the index of last shadow copy to use. index >=1, default: 1"),
  NO_INDEX("--no-index", false, "\n" +
    "    Do not prefix shadow history by index."),
  NUMBER("-n", true, " <size>\n" +
    "    Limit the history list or the purge list to the given number.");

  public final String flag;
  public final boolean hasOneArgument;
  public final String help;

  Option(String flag, boolean hasOneArgument, String help) {
    this.flag = flag;
    this.hasOneArgument = hasOneArgument;
    this.help = help;
  }

  public static Option find(String flag) {
    return Arrays.stream(Option.values())
      .filter(option -> option.flag.equals(flag))
      .findFirst().orElse(null);
  }

}
