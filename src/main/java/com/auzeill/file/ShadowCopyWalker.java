package com.auzeill.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ShadowCopyWalker {

  private static final Path RELATIVE_BASE_DIRECTORY = Paths.get(".");

  enum Action {
    CREATE_COPY,
    DIFF_COPY,
    CLEAN_BACKUP
  }

  final ShadowCopyOptions options;
  final Path sourceBaseDirectory;
  final Path shadowBaseDirectory;
  final ShadowCopyFilter filter;
  @Nullable
  final Path lastShadowBaseDirectory;
  final Action action;

  Process backgroundProcess = null;
  String[] backgroundProcessCommand = null;

  public ShadowCopyWalker(ShadowCopyOptions options,
    Path sourceBaseDirectory,
    Path shadowBaseDirectory,
    @Nullable Path lastShadowBaseDirectory,
    Action action,
    ShadowCopyFilter filter) {

    this.options = options;
    this.sourceBaseDirectory = sourceBaseDirectory;
    this.shadowBaseDirectory = shadowBaseDirectory;
    this.lastShadowBaseDirectory = lastShadowBaseDirectory;
    this.action = action;
    this.filter = filter;
  }

  public void walk() throws IOException, InterruptedException {
    walk(RELATIVE_BASE_DIRECTORY);
    terminateBackgroundProcess();
  }

  public void walk(Path relativePath) throws IOException, InterruptedException {
    boolean isBaseDirectory = relativePath.equals(RELATIVE_BASE_DIRECTORY);
    Path sourceDirectory = isBaseDirectory ? sourceBaseDirectory : sourceBaseDirectory.resolve(relativePath);
    List<Path> childPaths;
    try (Stream<Path> fileList = Files.list(sourceDirectory)) {
      childPaths = fileList
        .sorted(Comparator.comparing(Path::getFileName))
        .collect(Collectors.toList());
    }
    for (Path childAbsolutePath : childPaths) {
      Path childRelativePath = isBaseDirectory ? childAbsolutePath.getFileName() : relativePath.resolve(childAbsolutePath.getFileName());
      RelativeFile relativeFile = new RelativeFile(childAbsolutePath, childRelativePath);
      Path shadowAbsolutePath = shadowBaseDirectory.resolve(childRelativePath);
      if (filter.filter(relativeFile)) {
        PosixFileAttributes srcAttributes = Files.readAttributes(childAbsolutePath, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (srcAttributes.isSymbolicLink()) {
          visitSymbolicLink(childAbsolutePath, shadowAbsolutePath, srcAttributes);
        } else if (srcAttributes.isRegularFile()) {
          visitRegularFile(childAbsolutePath, childRelativePath, srcAttributes, shadowAbsolutePath);
        } else if (srcAttributes.isDirectory()) {
          visitDirectory(childRelativePath, shadowAbsolutePath, srcAttributes);
        } else {
          visitUnsupportedFile(srcAttributes, shadowAbsolutePath);
        }
      }
    }
  }

  private void visitUnsupportedFile(PosixFileAttributes srcAttributes, Path shadowAbsolutePath) throws IOException {
    Files.writeString(shadowAbsolutePath, "Unsupported file type, lastModifiedTime: " + srcAttributes.lastModifiedTime(), UTF_8);
    copyAttributes(srcAttributes, shadowAbsolutePath);
  }

  private void visitRegularFile(Path childAbsolutePath, Path childRelativePath, PosixFileAttributes srcAttributes, Path shadowAbsolutePath)
    throws IOException, InterruptedException {
    Path identicalShadowFile = findLastShadowIdenticalRegularFile(childAbsolutePath, srcAttributes, childRelativePath);
    if (identicalShadowFile != null) {
      // Create hardlink
      // Warning: do not "copyAttributes", permissions is in common with identicalShadowFile
      Files.createLink(shadowAbsolutePath, identicalShadowFile);
    } else {
      // # try to perform a lightweight copy where the data blocks are copied only when modified
      // cp --reflink=auto --preserve=all --no-target-directory "${CHILD_ABSOLUTE_PATH}" "${SHADOW_ABSOLUTE_PATH}"
      startBackgroundProcess("/bin/cp", "--reflink=auto", "--preserve=all", "--no-target-directory",
        childAbsolutePath.toString(), shadowAbsolutePath.toString());
    }
  }

  void terminateBackgroundProcess() throws InterruptedException, IOException {
    if (backgroundProcess != null) {
      StringBuilder errorText = new StringBuilder();
      try (InputStream errorStream = backgroundProcess.getErrorStream()) {
        try (Reader reader = new BufferedReader(new InputStreamReader(errorStream, UTF_8), 256)) {
          int ch = reader.read();
          while (ch != -1) {
            errorText.append((char) ch);
            ch = reader.read();
          }
        }
      }
      int exitCode = backgroundProcess.waitFor();
      backgroundProcess = null;
      if (exitCode != 0) {
        String command = Arrays.stream(backgroundProcessCommand).collect(Collectors.joining("\" \"", "\"", "\""));
        throw new ShadowErrorException("Unexpected exit code " + exitCode + " for command: " + command + "\n" + errorText.toString());
      }
    }
  }

  void startBackgroundProcess(String... command) throws InterruptedException, IOException {
    terminateBackgroundProcess();
    backgroundProcessCommand = command;
    backgroundProcess = new ProcessBuilder(backgroundProcessCommand)
      .redirectInput(Redirect.PIPE)
      .redirectError(Redirect.PIPE)
      .redirectOutput(Redirect.DISCARD)
      .start();
    backgroundProcess.getOutputStream().close();
  }

  private Path findLastShadowIdenticalRegularFile(Path srcAbsolutePath, PosixFileAttributes srcAttributes, Path relativePath) throws IOException {
    if (lastShadowBaseDirectory == null) {
      return null;
    }
    Path lastShadowPath = lastShadowBaseDirectory.resolve(relativePath);
    if (!Files.isRegularFile(lastShadowPath)) {
      return null;
    }
    BasicFileAttributes lastAttributes = Files.readAttributes(lastShadowPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!srcAttributes.lastModifiedTime().equals(lastAttributes.lastModifiedTime()) || srcAttributes.size() != lastAttributes.size()) {
      return null;
    }
    return lastShadowPath;
  }

  private void visitDirectory(Path relativePath, Path shadowAbsolutePath, PosixFileAttributes srcAttributes) throws IOException, InterruptedException {
    Files.createDirectory(shadowAbsolutePath);
    copyAttributes(srcAttributes, shadowAbsolutePath);
    walk(relativePath);
  }

  private void visitSymbolicLink(Path childAbsolutePath, Path shadowAbsolutePath, PosixFileAttributes srcAttributes) throws IOException {
    Path target = Files.readSymbolicLink(childAbsolutePath);
    Files.createSymbolicLink(shadowAbsolutePath, target);
    copyAttributes(srcAttributes, shadowAbsolutePath);
  }

  private static void copyAttributes(PosixFileAttributes srcAttributes, Path dstPath) throws IOException {
    PosixFileAttributeView dstAttributeView = Files.getFileAttributeView(dstPath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    PosixFileAttributes dstAttributes = dstAttributeView.readAttributes();
    if (!Objects.equals(srcAttributes.group(), dstAttributes.group())) {
      dstAttributeView.setGroup(srcAttributes.group());
    }
    if (!Objects.equals(srcAttributes.owner(), dstAttributeView.getOwner())) {
      dstAttributeView.setOwner(srcAttributes.owner());
    }
    if (!PosixFilePermissions.toString(srcAttributes.permissions()).equals(PosixFilePermissions.toString(dstAttributes.permissions()))) {
      dstAttributeView.setPermissions(srcAttributes.permissions());
    }

    if (!srcAttributes.isSymbolicLink() &&
      (!srcAttributes.lastModifiedTime().equals(dstAttributes.lastModifiedTime()) ||
        !srcAttributes.lastAccessTime().equals(dstAttributes.lastAccessTime()) ||
        !srcAttributes.creationTime().equals(dstAttributes.creationTime()))) {
      dstAttributeView.setTimes(srcAttributes.lastModifiedTime(), srcAttributes.lastAccessTime(), srcAttributes.creationTime());
    }
  }

}
