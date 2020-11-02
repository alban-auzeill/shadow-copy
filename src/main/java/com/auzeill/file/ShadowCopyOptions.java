package com.auzeill.file;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShadowCopyOptions {

  public String shadowDirectoryName = ".shadow-copy";
  public String filterFilename = ".shadow-copy-filter";
  public int lastShadowIndex = 1;
  public List<String> sourceDirectories;
  public Action action = Action.CREATE_COPY;
  public final PrintStream out;

  public ShadowCopyOptions(String[] args) {
    this(System.out, args);
  }

  public ShadowCopyOptions(PrintStream out, String... args) {
    this.out = out;
    sourceDirectories = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--shadow-directory")) {
        if (i + 1 >= args.length || args[i + 1].isBlank()) {
          throw new ShadowErrorException("Missing argument for --shadow-directory");
        }
        shadowDirectoryName = args[i + 1];
        i++;
      } else if (args[i].equals("--filter")) {
        if (i + 1 >= args.length || args[i + 1].isBlank()) {
          throw new ShadowErrorException("Missing argument for --filter");
        }
        filterFilename = args[i + 1];
        i++;
      } else if (args[i].equals("--shadow-index")) {
        if (i + 1 >= args.length || args[i + 1].isBlank()) {
          throw new ShadowErrorException("Missing argument for --shadow-index");
        }
        if (args[i + 1].equals("none")) {
          lastShadowIndex = -1;
        } else {
          lastShadowIndex = Integer.parseInt(args[i + 1]);
          if (lastShadowIndex < 1) {
            throw new ShadowErrorException("--shadow-index should be >= 1");
          }
        }
        i++;
      } else if (args[i].equals("--diff")) {
        action = Action.DIFF_COPY;
      } else {
        sourceDirectories.add(args[i]);
      }
    }
    if (sourceDirectories.isEmpty()) {
      sourceDirectories = Collections.singletonList(".");
    }
  }

}
