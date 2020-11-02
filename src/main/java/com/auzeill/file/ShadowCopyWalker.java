package com.auzeill.file;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ShadowCopyWalker {

  private static final Path RELATIVE_BASE_DIRECTORY = Paths.get(".");

  enum Action {
    CREATE_COPY,
    DIFF_COPY,
    //CLEAN_BACKUP
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
    ShadowCopyFilter filter) {

    this.options = options;
    this.sourceBaseDirectory = sourceBaseDirectory;
    this.shadowBaseDirectory = shadowBaseDirectory;
    this.lastShadowBaseDirectory = lastShadowBaseDirectory;
    this.action = options.action;
    this.filter = filter;
  }

  public void walk() throws IOException, InterruptedException {
    if (action == Action.DIFF_COPY) {
      walkDiff(RELATIVE_BASE_DIRECTORY);
    } else {
      walkCopy(RELATIVE_BASE_DIRECTORY);
    }
    terminateBackgroundProcess();
  }

  public void walkDiff(Path relativePath) throws IOException {
    boolean isBaseDirectory = relativePath.equals(RELATIVE_BASE_DIRECTORY);
    Path sourceDirectory = isBaseDirectory ? sourceBaseDirectory : sourceBaseDirectory.resolve(relativePath);
    Path shadowDirectory = isBaseDirectory ? shadowBaseDirectory : shadowBaseDirectory.resolve(relativePath);
    Set<Path> childPaths = new TreeSet<>(Comparator.comparing(Path::getFileName));
    if (Files.isDirectory(sourceDirectory, LinkOption.NOFOLLOW_LINKS)) {
      try (Stream<Path> fileList = Files.list(sourceDirectory)) {
        fileList.forEach(childPaths::add);
      }
    }
    if (Files.isDirectory(shadowDirectory, LinkOption.NOFOLLOW_LINKS)) {
      try (Stream<Path> fileList = Files.list(shadowDirectory)) {
        fileList.forEach(path -> childPaths.add(sourceDirectory.resolve(path.getFileName())));
      }
    }
    for (Path sourceAbsolutePath : childPaths) {
      Path fileName = sourceAbsolutePath.getFileName();
      Path childRelativePath = isBaseDirectory ? fileName : relativePath.resolve(fileName);
      RelativeFile relativeFile = new RelativeFile(sourceAbsolutePath, childRelativePath);
      if (filter.filter(relativeFile)) {
        Path shadowAbsolutePath = shadowDirectory.resolve(fileName);
        boolean isDirectory;
        if (!Files.exists(sourceAbsolutePath, LinkOption.NOFOLLOW_LINKS)) {
          isDirectory = Files.isDirectory(shadowAbsolutePath, LinkOption.NOFOLLOW_LINKS);
          options.out.println("[DELETED ] " + RelativeFile.suffixDirectory(childRelativePath.toString(), isDirectory));
        } else if (!Files.exists(shadowAbsolutePath, LinkOption.NOFOLLOW_LINKS)) {
          isDirectory = Files.isDirectory(sourceAbsolutePath, LinkOption.NOFOLLOW_LINKS);
          options.out.println("[NEW     ] " + RelativeFile.suffixDirectory(childRelativePath.toString(), isDirectory));
        } else {
          PosixFileAttributes sourceAttributes = Files.readAttributes(sourceAbsolutePath, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          PosixFileAttributes shadowAttributes = Files.readAttributes(shadowAbsolutePath, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          isDirectory = sourceAttributes.isDirectory() || shadowAttributes.isDirectory();
          if (isContentModified(sourceAbsolutePath, sourceAttributes, shadowAbsolutePath, shadowAttributes)) {
            options.out.println("[MODIFIED] " + RelativeFile.suffixDirectory(childRelativePath.toString(), isDirectory));
          } else if (isAttributesModified(sourceAttributes, shadowAttributes)) {
            options.out.println("[CHANGED ] " + RelativeFile.suffixDirectory(childRelativePath.toString(), isDirectory));
          }
        }
        if (isDirectory) {
          walkDiff(childRelativePath);
        }
      }
    }
  }

  private static boolean isContentModified(Path sourceAbsolutePath, PosixFileAttributes sourceAttributes, Path shadowAbsolutePath, PosixFileAttributes shadowAttributes)
    throws IOException {
    if (sourceAttributes.isSymbolicLink()) {
      return !shadowAttributes.isSymbolicLink() ||
        !Files.readSymbolicLink(sourceAbsolutePath).equals(Files.readSymbolicLink(shadowAbsolutePath));
    } else if (sourceAttributes.isRegularFile()) {
      if (!shadowAttributes.isRegularFile() || sourceAttributes.size() != shadowAttributes.size()) {
        return true;
      }
      // fast comparison
      if (sourceAttributes.lastModifiedTime().equals(shadowAttributes.lastModifiedTime())) {
        return false;
      }
      // slow comparison
      return !hasSameContent(sourceAbsolutePath, shadowAbsolutePath);
    } else if (sourceAttributes.isDirectory()) {
      return !shadowAttributes.isDirectory();
    } else {
      // Unsupported content comparison
      return false;
    }
  }

  private static boolean isAttributesModified(PosixFileAttributes sourceAttributes, PosixFileAttributes shadowAttributes) {
    return !Objects.equals(sourceAttributes.group(), shadowAttributes.group()) ||
           !Objects.equals(sourceAttributes.owner(), shadowAttributes.owner()) ||
           !PosixFilePermissions.toString(sourceAttributes.permissions()).equals(PosixFilePermissions.toString(shadowAttributes.permissions()));
  }

  static boolean hasSameContent(Path path1, Path path2) throws IOException {
    if (Files.size(path1) != Files.size(path2)) {
      return false;
    }
    try (
      InputStream input1 = new BufferedInputStream(new FileInputStream(path1.toFile()));
      InputStream input2 = new BufferedInputStream(new FileInputStream(path2.toFile()))) {
      byte[] buffer1 = new byte[4096];
      byte[] buffer2 = new byte[buffer1.length];
      int count1 = input1.read(buffer1);
      int count2 = input2.read(buffer2);
      while (count1 != -1 && count2 != -1) {
        if (!Arrays.equals(buffer1, 0, count1, buffer2, 0, count2)) {
          return false;
        }
        count1 = input1.read(buffer1);
        count2 = input2.read(buffer2);
      }
      return count1 == count2;
    }
  }

  public void walkCopy(Path relativePath) throws IOException, InterruptedException {
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
          copySymbolicLink(childAbsolutePath, shadowAbsolutePath, srcAttributes);
        } else if (srcAttributes.isRegularFile()) {
          copyRegularFile(childAbsolutePath, childRelativePath, srcAttributes, shadowAbsolutePath);
        } else if (srcAttributes.isDirectory()) {
          copyDirectory(childRelativePath, shadowAbsolutePath, srcAttributes);
        } else {
          copyUnsupportedFile(srcAttributes, shadowAbsolutePath);
        }
      }
    }
  }

  private void copyUnsupportedFile(PosixFileAttributes srcAttributes, Path shadowAbsolutePath) throws IOException {
    Files.writeString(shadowAbsolutePath, "Unsupported file type, lastModifiedTime: " + srcAttributes.lastModifiedTime(), UTF_8);
    copyAttributes(srcAttributes, shadowAbsolutePath);
  }

  private void copyRegularFile(Path childAbsolutePath, Path childRelativePath, PosixFileAttributes srcAttributes, Path shadowAbsolutePath)
    throws IOException, InterruptedException {
    Path identicalShadowFile = findLastShadowIdenticalRegularFile(srcAttributes, childRelativePath);
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

  private Path findLastShadowIdenticalRegularFile(PosixFileAttributes srcAttributes, Path relativePath) throws IOException {
    if (lastShadowBaseDirectory == null) {
      return null;
    }
    Path lastShadowPath = lastShadowBaseDirectory.resolve(relativePath);
    if (!Files.isRegularFile(lastShadowPath, LinkOption.NOFOLLOW_LINKS)) {
      return null;
    }
    BasicFileAttributes lastAttributes = Files.readAttributes(lastShadowPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!srcAttributes.lastModifiedTime().equals(lastAttributes.lastModifiedTime()) || srcAttributes.size() != lastAttributes.size()) {
      return null;
    }
    return lastShadowPath;
  }

  private void copyDirectory(Path relativePath, Path shadowAbsolutePath, PosixFileAttributes srcAttributes) throws IOException, InterruptedException {
    Files.createDirectory(shadowAbsolutePath);
    copyAttributes(srcAttributes, shadowAbsolutePath);
    walkCopy(relativePath);
  }

  private void copySymbolicLink(Path childAbsolutePath, Path shadowAbsolutePath, PosixFileAttributes srcAttributes) throws IOException {
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
