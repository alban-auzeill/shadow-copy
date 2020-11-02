package com.auzeill.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CopyWalker {

  private static final Path RELATIVE_BASE_DIRECTORY = Paths.get(".");

  final Path sourceBaseDirectory;
  final Path shadowBaseDirectory;
  final ShadowCopyFilter filter;
  @Nullable
  final Path lastShadowBaseDirectory;

  Command backgroundCommand = null;

  public CopyWalker(Path sourceBaseDirectory, Path shadowBaseDirectory,
    @Nullable Path lastShadowBaseDirectory, ShadowCopyFilter filter) {
    this.sourceBaseDirectory = sourceBaseDirectory;
    this.shadowBaseDirectory = shadowBaseDirectory;
    this.lastShadowBaseDirectory = lastShadowBaseDirectory;
    this.filter = filter;
  }

  public void walk() throws IOException, InterruptedException {
    walk(RELATIVE_BASE_DIRECTORY);
    if (backgroundCommand != null) {
      backgroundCommand.waitFor();
      backgroundCommand = null;
    }
  }

  private void walk(Path relativePath) throws IOException, InterruptedException {
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
      exec("/bin/cp", "--reflink=auto", "--preserve=all", "--no-target-directory",
        childAbsolutePath.toString(), shadowAbsolutePath.toString());
    }
  }

  void exec(String... command) throws InterruptedException, IOException {
    if (backgroundCommand != null) {
      backgroundCommand.waitFor();
      backgroundCommand = null;
    }
    backgroundCommand = Command.exec(command);
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
    walk(relativePath);
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
