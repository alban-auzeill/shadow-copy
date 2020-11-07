package com.auzeill.shadow.copy.action;

import com.auzeill.shadow.copy.ShadowCopyError;
import com.auzeill.shadow.copy.arguments.Action;
import com.auzeill.shadow.copy.arguments.Arguments;
import com.auzeill.shadow.copy.filter.FileFilter;
import com.auzeill.shadow.copy.utils.ActionUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public class DiffAction implements Action.Execute {

  @Override
  public void execute(PrintStream out, Arguments arguments) throws IOException {
    int sourceDirectoryIndex = -1;
    String firstCopy = null;
    String secondCopy = null;
    List<String> args = arguments.actionArguments;
    if (args.size() > 3) {
      throw new ShadowCopyError("Expects at most 3 parameter.");
    } else if (args.size() == 3) {
      sourceDirectoryIndex = 0;
      firstCopy = args.get(1);
      secondCopy = args.get(2);
    } else if (args.size() == 2 && args.get(0).matches("[0-9]++")) {
      firstCopy = args.get(0);
      secondCopy = args.get(1);
    } else if (args.size() == 2) {
      sourceDirectoryIndex = 0;
      firstCopy = args.get(1);
    } else if (args.size() == 1 && args.get(0).matches("[0-9]++")) {
      firstCopy = args.get(0);
    } else if (args.size() == 1) {
      sourceDirectoryIndex = 0;
    }
    Path shadowDirectory = ActionUtils.resolveShadowDirectoryPath(arguments, sourceDirectoryIndex);
    Path oldBaseDirectory;
    if (firstCopy == null) {
      oldBaseDirectory = ActionUtils.findLastShadowCopy(shadowDirectory, arguments);
    } else {
      oldBaseDirectory = ActionUtils.findLastShadowCopy(shadowDirectory, Integer.parseInt(firstCopy));
    }
    Path newBaseDirectory;
    if (secondCopy == null) {
      newBaseDirectory = ActionUtils.resolveSourceDirectory(arguments, sourceDirectoryIndex);
    } else {
      newBaseDirectory = ActionUtils.findLastShadowCopy(shadowDirectory, Integer.parseInt(secondCopy));
    }
    FileFilter filter = FileFilter.loadFromShadowDirectory(shadowDirectory);
    if (oldBaseDirectory == null || newBaseDirectory == null) {
      throw new ShadowCopyError("No previous shadow copy to match with.");
    }
    new DiffWalker(oldBaseDirectory, newBaseDirectory, filter, out).walk();
  }

}
