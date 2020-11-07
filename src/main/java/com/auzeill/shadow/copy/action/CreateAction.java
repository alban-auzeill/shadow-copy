package com.auzeill.shadow.copy.action;

import com.auzeill.shadow.copy.arguments.Action;
import com.auzeill.shadow.copy.arguments.Arguments;
import com.auzeill.shadow.copy.filter.FileFilter;
import com.auzeill.shadow.copy.utils.ActionUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CreateAction implements Action.Execute {

  @Override
  public void execute(PrintStream out, Arguments arguments) throws IOException, InterruptedException {
    execute(out, arguments, Clock.systemDefaultZone());
  }

  public void execute(PrintStream out, Arguments arguments, Clock clock) throws IOException, InterruptedException {
    Path sourceDirectory = ActionUtils.resolveSourceDirectory(arguments, 0);
    Path shadowDirectory = ActionUtils.resolveShadowDirectoryPath(arguments, 0);
    FileFilter filter = FileFilter.loadFromShadowDirectory(shadowDirectory);
    Path lastShadowCopy = ActionUtils.findLastShadowCopy(shadowDirectory, arguments);
    Path shadowCopy = createShadowCopyDirectory(shadowDirectory, clock);
    new CreateWalker(sourceDirectory, shadowCopy, lastShadowCopy, filter).walk();
    out.println(shadowCopy.toString());
  }

  static Path createShadowCopyDirectory(Path shadowDirectory, Clock clock) throws IOException {
    if (!Files.isDirectory(shadowDirectory)) {
      Files.createDirectory(shadowDirectory);
    }
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd-HH'h'mm");
    String date = dateFormat.format(LocalDateTime.now(clock));
    int index = 1;
    Path shadowCopy = shadowDirectory.resolve(date + "-" + index);
    while (Files.isDirectory(shadowCopy)) {
      index++;
      shadowCopy = shadowDirectory.resolve(date + "-" + index);
    }
    Files.createDirectory(shadowCopy);
    return shadowCopy;
  }

}
