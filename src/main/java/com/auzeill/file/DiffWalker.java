package com.auzeill.file;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public class DiffWalker {

  private static final Path BASE = Paths.get(".");

  final Path oldBaseDirectory;
  final Path newBaseDirectory;
  final ShadowCopyFilter filter;
  final PrintStream out;

  public DiffWalker(Path oldBaseDirectory, Path newBaseDirectory, ShadowCopyFilter filter, PrintStream out) {
    this.oldBaseDirectory = oldBaseDirectory;
    this.newBaseDirectory = newBaseDirectory;
    this.filter = filter;
    this.out = out;
  }

  public void walk() throws IOException {
    walk(BASE);
  }

  private void walk(Path relativePath) throws IOException {
    boolean isBaseDirectory = relativePath.equals(BASE);
    Path oldDirectory = isBaseDirectory ? oldBaseDirectory : oldBaseDirectory.resolve(relativePath);
    Path newDirectory = isBaseDirectory ? newBaseDirectory : newBaseDirectory.resolve(relativePath);
    Set<Path> childPaths = new TreeSet<>(Comparator.comparing(Path::getFileName));
    if (Files.isDirectory(newDirectory, LinkOption.NOFOLLOW_LINKS)) {
      try (Stream<Path> fileList = Files.list(newDirectory)) {
        fileList.forEach(childPaths::add);
      }
    }
    if (Files.isDirectory(oldDirectory, LinkOption.NOFOLLOW_LINKS)) {
      try (Stream<Path> fileList = Files.list(oldDirectory)) {
        fileList.forEach(path -> childPaths.add(newDirectory.resolve(path.getFileName())));
      }
    }
    for (Path newAbsolutePath : childPaths) {
      Path fileName = newAbsolutePath.getFileName();
      Path childRelativePath = isBaseDirectory ? fileName : relativePath.resolve(fileName);
      RelativeFile relativeFile = new RelativeFile(newAbsolutePath, childRelativePath);
      if (filter.filter(relativeFile)) {
        Path oldAbsolutePath = oldDirectory.resolve(fileName);
        boolean isDirectory;
        if (!Files.exists(newAbsolutePath, LinkOption.NOFOLLOW_LINKS)) {
          isDirectory = Files.isDirectory(oldAbsolutePath, LinkOption.NOFOLLOW_LINKS);
          out.println("[DELETED ] " + RelativeFile.suffixDirectory(childRelativePath.toString(), isDirectory));
        } else if (!Files.exists(oldAbsolutePath, LinkOption.NOFOLLOW_LINKS)) {
          isDirectory = Files.isDirectory(newAbsolutePath, LinkOption.NOFOLLOW_LINKS);
          out.println("[NEW     ] " + RelativeFile.suffixDirectory(childRelativePath.toString(), isDirectory));
        } else {
          PosixFileAttributes oldAttributes = Files.readAttributes(oldAbsolutePath, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          PosixFileAttributes newAttributes = Files.readAttributes(newAbsolutePath, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          isDirectory = newAttributes.isDirectory() || oldAttributes.isDirectory();
          if (isContentModified(newAbsolutePath, newAttributes, oldAbsolutePath, oldAttributes)) {
            out.println("[MODIFIED] " + RelativeFile.suffixDirectory(childRelativePath.toString(), isDirectory));
          } else if (isAttributesModified(newAttributes, oldAttributes)) {
            out.println("[CHANGED ] " + RelativeFile.suffixDirectory(childRelativePath.toString(), isDirectory));
          }
        }
        if (isDirectory) {
          walk(childRelativePath);
        }
      }
    }
  }

  private static boolean isContentModified(Path newAbsolutePath, PosixFileAttributes newAttributes, Path oldAbsolutePath, PosixFileAttributes oldAttributes)
    throws IOException {
    if (newAttributes.isSymbolicLink()) {
      return !oldAttributes.isSymbolicLink() ||
        !Files.readSymbolicLink(newAbsolutePath).equals(Files.readSymbolicLink(oldAbsolutePath));
    } else if (newAttributes.isRegularFile()) {
      if (!oldAttributes.isRegularFile() || newAttributes.size() != oldAttributes.size()) {
        return true;
      }
      // fast comparison
      if (newAttributes.lastModifiedTime().equals(oldAttributes.lastModifiedTime())) {
        return false;
      }
      // slow comparison
      return !hasSameContent(newAbsolutePath, oldAbsolutePath);
    } else if (newAttributes.isDirectory()) {
      return !oldAttributes.isDirectory();
    } else {
      // Unsupported content comparison
      return false;
    }
  }

  private static boolean isAttributesModified(PosixFileAttributes newAttributes, PosixFileAttributes oldAttributes) {
    return !Objects.equals(newAttributes.group(), oldAttributes.group()) ||
           !Objects.equals(newAttributes.owner(), oldAttributes.owner()) ||
           !PosixFilePermissions.toString(newAttributes.permissions()).equals(PosixFilePermissions.toString(oldAttributes.permissions()));
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

}
