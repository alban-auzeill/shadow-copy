package com.auzeill.shadow.copy.utils;

import com.auzeill.shadow.copy.ShadowCopyError;
import com.auzeill.shadow.copy.arguments.Arguments;
import com.auzeill.shadow.copy.arguments.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public final class ActionUtils {

  public static final String DEFAULT_SHADOW_DIRECTORY_NAME = ".shadow-copy";
  public static final Pattern SHADOW_COPY_FORMAT = Pattern.compile("\\d{4,}\\.\\d{2}\\.\\d{2}-\\d{2}h\\d{2}-\\d+");
  public static final Path DOT_DIRECTORY = Paths.get(".");

  private ActionUtils() {
    // utility
  }

  public static Path resolveSourceDirectory(Arguments arguments, int sourceDirectoryIndex) {
    if (sourceDirectoryIndex >= 0 && sourceDirectoryIndex < arguments.actionArguments.size()) {
      return Paths.get(arguments.actionArguments.get(sourceDirectoryIndex));
    }
    return Paths.get(".");
  }

  public static Path resolveShadowDirectoryPath(Arguments arguments, int sourceDirectoryIndex) {
    String shadowDirectory = arguments.options.get(Option.SHADOW_DIRECTORY);
    if (shadowDirectory != null) {
      return Paths.get(shadowDirectory);
    }
    return resolve(resolveSourceDirectory(arguments, sourceDirectoryIndex), DEFAULT_SHADOW_DIRECTORY_NAME);
  }

  public static int getShadowIndex(Arguments arguments) {
    String shadowIndex = arguments.options.get(Option.SHADOW_INDEX);
    if (shadowIndex != null) {
      try {
        if (shadowIndex.equals("none")) {
          return -1;
        }
        return Integer.parseInt(shadowIndex);
      } catch (NumberFormatException ex) {
        throw new ShadowCopyError("Invalid " + Option.SHADOW_INDEX.flag + " value: " + shadowIndex);
      }
    } else {
      return 1;
    }
  }

  public static List<Path> shadowCopyHistory(Path shadowDirectory) throws IOException {
    if (!Files.isDirectory(shadowDirectory)) {
      return Collections.emptyList();
    }
    try (Stream<Path> fileList = Files.list(shadowDirectory)) {
      return fileList
        .filter(path -> SHADOW_COPY_FORMAT.matcher(path.getFileName().toString()).matches())
        .sorted(Comparator.comparing(Path::getFileName))
        .collect(Collectors.toList());
    }
  }

  @Nullable
  public static Path findLastShadowCopy(Path shadowDirectory, Arguments arguments) throws IOException {
    return findLastShadowCopy(shadowDirectory, getShadowIndex(arguments));
  }

  @Nullable
  public static Path findLastShadowCopy(Path shadowDirectory, int fromIndex) throws IOException {
    if (fromIndex < 1) {
      return null;
    }
    List<Path> childPaths = shadowCopyHistory(shadowDirectory);
    int index = childPaths.size() - fromIndex;
    return index >= 0 ? childPaths.get(index) : null;
  }

  public static Path resolve(Path parent, String child) {
    return resolve(parent, Paths.get(child));
  }

  public static Path resolve(Path parent, Path child) {
    if (parent.equals(DOT_DIRECTORY)) {
      return child;
    } else if (child.equals(DOT_DIRECTORY)) {
      return parent;
    } else {
      return parent.resolve(child);
    }
  }

}
