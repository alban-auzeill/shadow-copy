package com.auzeill.shadow.copy;

import com.auzeill.shadow.copy.arguments.Arguments;
import com.auzeill.shadow.copy.arguments.Option;
import java.io.IOException;
import java.io.PrintStream;

public class ShadowCopy {

  @SuppressWarnings("java:S106")
  public static void main(String[] args) throws IOException, InterruptedException {
    int exitValue = exec(System.out, System.err, args);
    if (exitValue != 0) {
      System.exit(exitValue);
    }
  }

  public static int exec(PrintStream out, PrintStream err, String... args) throws IOException, InterruptedException {
    try {
      exec(out, args);
      return 0;
    } catch (ShadowCopyError ex) {
      err.println("[ERROR] " + ex.getMessage());
      return 1;
    }
  }

  public static void exec(PrintStream out, String... args) throws IOException, InterruptedException {
    Arguments arguments = new Arguments(args);
    if (arguments.options.containsKey(Option.VERSION)) {
      out.println(Version.get());
    } else if (arguments.options.containsKey(Option.HELP)) {
      out.println(Arguments.help());
    } else {
      arguments.action.factory.get().execute(out, arguments);
    }
  }

}
