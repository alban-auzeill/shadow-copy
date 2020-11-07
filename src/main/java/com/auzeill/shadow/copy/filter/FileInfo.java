package com.auzeill.shadow.copy.filter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileInfo {

  public final boolean isDirectory;
  public final Path absolutePath;
  public final String absolute;
  public final String relative;
  public final String filename;

  public FileInfo(Path absolute, Path relative) {
    this.isDirectory = Files.isDirectory(absolute);
    this.absolutePath = absolute;
    this.absolute = suffixDirectory(absolute.toString(), isDirectory);
    this.relative = suffixDirectory(relative.toString(), isDirectory);
    this.filename = suffixDirectory(absolute.getFileName().toString(), isDirectory);
  }

  public static String suffixDirectory(String path, boolean isDirectory) {
    if (isDirectory && !path.endsWith(File.separator)) {
      return path + File.separator;
    }
    return path;
  }

}
