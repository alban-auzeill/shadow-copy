package com.auzeill.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class ShadowCopy {

  static Clock clock = Clock.systemDefaultZone();

  private final ShadowCopyOptions options;

  public ShadowCopy(ShadowCopyOptions options) {
    this.options = options;
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    try {
      ShadowCopyOptions options = new ShadowCopyOptions(args);
      ShadowCopy shadowCopy = new ShadowCopy(options);
      for (String sourceDirectory : options.sourceDirectories) {
        Path sourceDirectoryPath = Paths.get(sourceDirectory);
        if (!Files.isDirectory(sourceDirectoryPath)) {
          throw new ShadowErrorException("Directory not found: " + sourceDirectory);
        }
        switch (options.action) {
          case CREATE_COPY:
            options.out.println(shadowCopy.copy(sourceDirectoryPath));
            break;
          case DIFF_COPY:
            shadowCopy.diff(sourceDirectoryPath);
            break;
        }
      }
    } catch (InterruptedException ex) {
      System.err.println("[INTERRUPTED] " + ex.getMessage());
      System.exit(1);
    } catch (ShadowErrorException ex) {
      System.err.println("[ERROR] " + ex.getMessage());
      System.exit(1);
    }
  }

  void diff(Path sourceDirectoryPath) throws IOException, InterruptedException {
    ShadowCopyFilter filter = createFilter(sourceDirectoryPath);
    Path oldBaseDirectory = findLastShadowBaseDirectory(sourceDirectoryPath, options.lastShadowIndex);
    if (oldBaseDirectory == null) {
      throw new ShadowErrorException("No previous shadow copy to match with.");
    }
    new DiffWalker(oldBaseDirectory, sourceDirectoryPath, filter, options.out)
      .walk();
  }

  Path copy(Path sourceDirectoryPath) throws IOException, InterruptedException {
    ShadowCopyFilter filter = createFilter(sourceDirectoryPath);
    Path lastShadowDirectoryPath = findLastShadowBaseDirectory(sourceDirectoryPath, options.lastShadowIndex);
    Path shadowDirectoryPath = createShadowBaseDirectory(sourceDirectoryPath);
    new CopyWalker(sourceDirectoryPath, shadowDirectoryPath, lastShadowDirectoryPath, filter)
      .walk();
    return shadowDirectoryPath;
  }

  ShadowCopyFilter createFilter(Path sourceDirectoryPath) throws IOException {
    Path filterPath = sourceDirectoryPath.resolve(options.filterFilename);
    ShadowCopyFilter filter;
    if (Files.exists(filterPath)) {
      filter = ShadowCopyFilter.load(filterPath);
    } else {
      filter = new ShadowCopyFilter();
    }
    filter.addIgnoredFilename(options.shadowDirectoryName + File.separator);
    filter.addIgnoredFilename(options.filterFilename);
    return filter;
  }

  /**
   * @param sourceDirectoryPath
   * @param fromIndex (if < 1): no backup, 1: the last, 2: the before last, ...
   * @return null if there's no backup at this index
   * @throws IOException only if shadowDirectoryParent can not be listed
   */
  @Nullable
  Path findLastShadowBaseDirectory(Path sourceDirectoryPath, int fromIndex) throws IOException {
    if (fromIndex < 1) {
      return null;
    }
    Path shadowDirectoryParent = sourceDirectoryPath.resolve(options.shadowDirectoryName);
    if (!Files.isDirectory(shadowDirectoryParent)) {
      return null;
    }
    List<Path> childPaths;
    try (Stream<Path> fileList = Files.list(shadowDirectoryParent)) {
      childPaths = fileList
        .sorted(Comparator.comparing(Path::getFileName))
        .collect(Collectors.toList());
    }
    int index = childPaths.size() - fromIndex;
    return index >= 0 ? childPaths.get(index) : null;
  }

  Path createShadowBaseDirectory(Path sourceDirectoryPath) throws IOException {
    Path shadowDirectoryParent = sourceDirectoryPath.resolve(options.shadowDirectoryName);
    if (!Files.isDirectory(shadowDirectoryParent)) {
      Files.createDirectory(shadowDirectoryParent);
    }
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd-HH'h'mm");
    String date = dateFormat.format(LocalDateTime.now(clock));
    int index = 1;
    Path shadowDirectory = shadowDirectoryParent.resolve(date + "-" + index);
    while (Files.isDirectory(shadowDirectory)) {
      index++;
      shadowDirectory = shadowDirectoryParent.resolve(date + "-" + index);
    }
    Files.createDirectory(shadowDirectory);
    return shadowDirectory;
  }

}
