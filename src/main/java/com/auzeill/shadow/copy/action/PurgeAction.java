package com.auzeill.shadow.copy.action;

import com.auzeill.shadow.copy.ShadowCopyError;
import com.auzeill.shadow.copy.arguments.Action;
import com.auzeill.shadow.copy.arguments.Arguments;
import com.auzeill.shadow.copy.arguments.Option;
import com.auzeill.shadow.copy.utils.ActionUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class PurgeAction implements Action.Execute {

  @Override
  public void execute(PrintStream out, Arguments arguments) throws IOException {
    Path shadowDirectory = ActionUtils.resolveShadowDirectoryPath(arguments, 0);
    List<Path> history = ActionUtils.shadowCopyHistory(shadowDirectory);
    int end = history.size() - 10;
    String limit = arguments.options.get(Option.NUMBER);
    if (limit != null) {
      end = Math.max(0, history.size() - Integer.parseInt(limit));
    }
    for (int i = 0; i < end; i++) {
      deleteRecursively(history.get(i));
    }
  }

  static void deleteRecursively(Path path) {
    if (Files.isDirectory(path)) {
      try (Stream<Path> list = Files.list(path)) {
        list.forEach(PurgeAction::deleteRecursively);
      } catch (IOException ex) {
        throw new ShadowCopyError("Failed to list '"+path+"': "+ ex.getMessage());
      }
    }
    try {
      Files.delete(path);
    } catch (IOException ex) {
      throw new ShadowCopyError("Failed to delete '"+path+"': "+ ex.getMessage());
    }
  }

}
