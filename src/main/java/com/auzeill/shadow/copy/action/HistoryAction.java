package com.auzeill.shadow.copy.action;

import com.auzeill.shadow.copy.arguments.Action;
import com.auzeill.shadow.copy.arguments.Arguments;
import com.auzeill.shadow.copy.arguments.Option;
import com.auzeill.shadow.copy.utils.ActionUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public class HistoryAction implements Action.Execute {

  @Override
  public void execute(PrintStream out, Arguments arguments) throws IOException {
    Path shadowDirectory = ActionUtils.resolveShadowDirectoryPath(arguments, 0);
    List<Path> history = ActionUtils.shadowCopyHistory(shadowDirectory);
    int start = 0;
    String limit = arguments.options.get(Option.NUMBER);
    if (limit != null) {
      start = Math.max(0, history.size() - Integer.parseInt(limit));
    }
    boolean noIndex = arguments.options.containsKey(Option.NO_INDEX);
    for (int i = start; i < history.size(); i++) {
      if (noIndex) {
        out.println(history.get(i).toString());
      } else {
        int index = history.size() - i;
        out.println(index + ": " + history.get(i).toString());
      }
    }
  }

}
