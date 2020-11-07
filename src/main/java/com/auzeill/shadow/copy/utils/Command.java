package com.auzeill.shadow.copy.utils;

import com.auzeill.shadow.copy.ShadowCopyError;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.lang.ProcessBuilder.Redirect;

public class Command {

  private final String[] command;
  private final Process process;

  private Command(String[] command, Process process) {
    this.command = command;
    this.process = process;
  }

  public static Command exec(String... command) throws IOException {
    Process process = new ProcessBuilder(command)
      .redirectInput(Redirect.PIPE)
      .redirectError(Redirect.PIPE)
      .redirectOutput(Redirect.DISCARD)
      .start();
    process.getOutputStream().close();
    return new Command(command, process);
  }

  public void waitFor() throws IOException, InterruptedException {
    String error = errorStream();
    int exitValue = process.waitFor();
    if (exitValue != 0) {
      throw new ShadowCopyError("Unexpected exit value " + exitValue + " for command: " + command() + "\n" + error);
    }
  }

  private String errorStream() throws IOException {
    try (InputStream stream = process.getErrorStream()) {
      return IOUtils.readUtf8String(stream);
    }
  }

  private String command() {
    return Arrays.stream(command)
      .map(elem -> elem.replace("\\", "\\\\").replace("\"", "\\\""))
      .collect(Collectors.joining("\" \"", "\"", "\""));
  }

}
