package com.auzeill.shadow.copy;

import com.auzeill.shadow.copy.action.CreateAction;
import com.auzeill.shadow.copy.arguments.Arguments;
import com.auzeill.shadow.copy.utils.StreamToString;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShadowCopyTest {

  StreamToString out = new StreamToString();
  StreamToString err = new StreamToString();

  @Test
  void version() throws IOException, InterruptedException {
    int exitValue = ShadowCopy.exec(out, err, "--version");
    assertThat(exitValue).isZero();
    assertThat(out.toString()).matches("[?\\d.\\-A-Z ()h]{22,32}+\n");
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void help() throws IOException, InterruptedException {
    int exitValue = ShadowCopy.exec(out, err, "--help");
    assertThat(exitValue).isZero();
    assertThat(out.toString()).contains("Available actions:");
    assertThat(out.toString().length()).isGreaterThanOrEqualTo(1000);
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void invalid_argument() throws IOException, InterruptedException {
    int exitValue = ShadowCopy.exec(out, err, "unknown");
    assertThat(exitValue).isOne();
    assertThat(out.toString()).isEmpty();
    assertThat(err).hasToString("[ERROR] Unknown action 'unknown', list valid action with --help\n");
  }

  @Test
  void throw_exception() throws IOException, InterruptedException {
    assertThatThrownBy(() ->  ShadowCopy.exec(out, "unknown"))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Unknown action 'unknown', list valid action with --help");
  }

  @Test
  void simple_case() throws IOException, InterruptedException {
    Path base = Paths.get("src", "test", "resources", "simple-case");
    // clean
    deleteIfExists(base.resolve(".shadow-copy"));

    // copy
    ShadowCopy.exec(out, "create", base.toString());
    Path result = path(out);

    // check
    assertThat(base.resolve(".shadow-copy")).isDirectory();
    assertThat(result.toString().replace(File.separatorChar, '/'))
      .matches("^src/test/resources/simple-case/.shadow-copy/\\d{4}\\.\\d{2}\\.\\d{2}-\\d{2}h\\d{2}-\\d+$");
    assertThat(result).isDirectory();
    assertThat(result.resolve("dir1")).isDirectory();
    assertThat(result.resolve(Paths.get("dir1", "f2.txt"))).isRegularFile();
    assertThat(result.resolve("f1.txt")).isRegularFile();
    assertThat(result.resolve("link1")).isSymbolicLink();

    // clean
    deleteIfExists(base.resolve(".shadow-copy"));
  }

  @Test
  void filter_examples() throws IOException, InterruptedException {
    Path base = Paths.get("src", "test", "resources", "filter-examples");
    // clean
    try(Stream<Path> fileToClean = Files.list(base.resolve(".shadow-copy"))) {
      fileToClean.filter(path -> !path.getFileName().toString().startsWith("ignore"))
        .forEach(ShadowCopyTest::deleteIfExists);
    }

    // copy
    ShadowCopy.exec(out, "create", base.toString());
    Path result = path(out);

    // check
    assertThat(base.resolve(".shadow-copy")).isDirectory();
    assertThat(result).isDirectory();
    assertThat(result.resolve(Paths.get(".shadow-copy"))).doesNotExist();
    assertThat(result.resolve(Paths.get("tmp"))).doesNotExist();
    assertThat(result.resolve(Paths.get("not-tmp"))).isDirectory();
    assertThat(result.resolve(Paths.get("dir1", "tmp"))).doesNotExist();
    assertThat(result.resolve(Paths.get("dir1", "not-tmp"))).isDirectory();
    assertThat(result.resolve(Paths.get("tmp", "f1.txt"))).doesNotExist();
    assertThat(result.resolve(Paths.get("not-a.out"))).isRegularFile();
    assertThat(result.resolve(Paths.get("not-tmp", "f1.txt"))).isRegularFile();
    assertThat(result.resolve(Paths.get("a.out"))).doesNotExist();
    assertThat(result.resolve(Paths.get("big.txt"))).doesNotExist();
    assertThat(result.resolve(Paths.get("f1.txt"))).isRegularFile();
    assertThat(result.resolve(Paths.get("f2.json"))).doesNotExist();
    assertThat(result.resolve(Paths.get("link1"))).doesNotExist();
    assertThat(result.resolve(Paths.get("dir1", "tmp", "f1.txt"))).doesNotExist();
    assertThat(result.resolve(Paths.get("dir1", "not-tmp", "f1.txt"))).isRegularFile();
    assertThat(result.resolve(Paths.get("file42"))).doesNotExist();

    // clean
    try(Stream<Path> fileToClean = Files.list(base.resolve(".shadow-copy"))) {
      fileToClean.filter(path -> !path.getFileName().toString().startsWith("ignore"))
        .forEach(ShadowCopyTest::deleteIfExists);
    }
  }

  @Test
  void hard_links(@TempDir Path base) throws IOException, InterruptedException {
    // prepare
    Files.writeString(base.resolve("f1.txt"), "Test data", UTF_8);
    Files.writeString(base.resolve("f2.txt"), "Content1", UTF_8);

    // copy
    ShadowCopy.exec(out, "create", base.toString());
    Path result1 = path(out);

    Files.writeString(base.resolve("f2.txt"), "Content1.1", UTF_8);

    out.reset();
    ShadowCopy.exec(out, "create", base.toString());
    Path result2 = path(out);

    // check
    assertThat(base.resolve(".shadow-copy")).isDirectory();
    assertThat(result1).isDirectory();
    assertThat(result2).isDirectory();
    assertThat(result1).isNotEqualTo(result2);
    assertThat(result1.resolve("f1.txt")).isRegularFile();
    assertThat(result2.resolve("f1.txt")).isRegularFile();

    assertThat(inode(result1.resolve("f1.txt"))).isEqualTo(inode(result2.resolve("f1.txt")));
    assertThat(inode(result1.resolve("f2.txt"))).isNotEqualTo(inode(result2.resolve("f2.txt")));
  }

  static Object inode(Path path) throws IOException {
    return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
  }

  static Path path(StreamToString out) {
    return Paths.get(out.toString().replaceFirst("\n$", ""));
  }

  @Test
  void named_pipe(@TempDir Path base) throws IOException, InterruptedException {
    // prepare
    Files.writeString(base.resolve("regular-file.txt"), "Test data", UTF_8);
    int exit = exec("/usr/bin/mkfifo", base.resolve("fifo-named-pipe").toString());
    assertThat(exit).isZero();

    // copy
    ShadowCopy.exec(out, "create", base.toString());
    Path result = path(out);

    assertThat(base.resolve(".shadow-copy")).isDirectory();
    assertThat(result).isDirectory();
    assertThat(result.resolve("regular-file.txt")).isRegularFile();
    assertThat(result.resolve("regular-file.txt")).hasContent("Test data");
    assertThat(result.resolve("fifo-named-pipe")).isRegularFile();
    String content = Files.readString(result.resolve("fifo-named-pipe"), UTF_8);
    assertThat(content).matches("Unsupported file type, lastModifiedTime: \\d+.*");
  }

  @Test
  void diff(@TempDir Path base) throws IOException, InterruptedException {
    // prepare
    Files.writeString(base.resolve("f1"), "Test data", UTF_8);
    Files.writeString(base.resolve("f2"), "Test data", UTF_8);
    Files.writeString(base.resolve("f3"), "Test data", UTF_8);
    Files.writeString(base.resolve("f4"), "Test data", UTF_8);

    // copy
    ShadowCopy.exec(out, "create", base.toString());
    Path copyResult = path(out);

    // diff before modify
    out.reset();
    ShadowCopy.exec(out, "diff", base.toString());
    assertThat(out.toString()).isEmpty();

    // modify
    Files.delete(base.resolve("f2"));
    Files.writeString(base.resolve("f4"), "Test data2", UTF_8);
    Files.writeString(base.resolve("f5"), "Test data", UTF_8);

    // diff after modify
    out.reset();
    ShadowCopy.exec(out, "diff", base.toString());
    assertThat(out).hasToString("" +
      "[DELETED ] f2\n" +
      "[MODIFIED] f4\n" +
      "[NEW     ] f5\n");

    out.reset();
    ShadowCopy.exec(out, "diff", base.toString(), "1");
    assertThat(out).hasToString("" +
      "[DELETED ] f2\n" +
      "[MODIFIED] f4\n" +
      "[NEW     ] f5\n");

    out.reset();
    err.reset();
    assertThat(ShadowCopy.exec(out, err, "diff", base.toString(), "1", "2")).isOne();
    assertThat(err).hasToString("[ERROR] No previous shadow copy to match with.\n");

    // copy
    out.reset();
    ShadowCopy.exec(out, "create", base.toString());

    out.reset();
    ShadowCopy.exec(out, "diff", base.toString(), "2", "1");
    assertThat(out).hasToString("" +
      "[DELETED ] f2\n" +
      "[MODIFIED] f4\n" +
      "[NEW     ] f5\n");

    out.reset();
    ShadowCopy.exec(out, "diff", base.toString(), "1", "2");
    assertThat(out).hasToString("" +
      "[NEW     ] f2\n" +
      "[MODIFIED] f4\n" +
      "[DELETED ] f5\n");
  }

  @Test
  void history_and_purge() throws IOException, InterruptedException {
    Path base = Paths.get("src", "test", "resources", "history");

    // clean
    deleteIfExists(base.resolve(".shadow-copy"));

    // copy
    Clock clock = Clock.fixed(Instant.parse("2018-08-19T16:45:42.00Z"), ZoneId.of("GMT"));
    for (int i = 0; i < 15; i++) {
      Arguments arguments = new Arguments("create", base.toString());
      out.reset();
      new CreateAction().execute(out, arguments, Clock.offset(clock, Duration.ofDays(i)));
      Path copyResult = path(out);
      assertThat(copyResult).isDirectory();
    }

    // history
    out.reset();
    ShadowCopy.exec(out, "history", base.toString());
    assertThat(out).hasToString("" +
      "15: src/test/resources/history/.shadow-copy/2018.08.19-16h45-1\n" +
      "14: src/test/resources/history/.shadow-copy/2018.08.20-16h45-1\n" +
      "13: src/test/resources/history/.shadow-copy/2018.08.21-16h45-1\n" +
      "12: src/test/resources/history/.shadow-copy/2018.08.22-16h45-1\n" +
      "11: src/test/resources/history/.shadow-copy/2018.08.23-16h45-1\n" +
      "10: src/test/resources/history/.shadow-copy/2018.08.24-16h45-1\n" +
      "9: src/test/resources/history/.shadow-copy/2018.08.25-16h45-1\n" +
      "8: src/test/resources/history/.shadow-copy/2018.08.26-16h45-1\n" +
      "7: src/test/resources/history/.shadow-copy/2018.08.27-16h45-1\n" +
      "6: src/test/resources/history/.shadow-copy/2018.08.28-16h45-1\n" +
      "5: src/test/resources/history/.shadow-copy/2018.08.29-16h45-1\n" +
      "4: src/test/resources/history/.shadow-copy/2018.08.30-16h45-1\n" +
      "3: src/test/resources/history/.shadow-copy/2018.08.31-16h45-1\n" +
      "2: src/test/resources/history/.shadow-copy/2018.09.01-16h45-1\n" +
      "1: src/test/resources/history/.shadow-copy/2018.09.02-16h45-1\n");

    out.reset();
    ShadowCopy.exec(out, "history", "-n", "5", base.toString());
    assertThat(out).hasToString("" +
      "5: src/test/resources/history/.shadow-copy/2018.08.29-16h45-1\n" +
      "4: src/test/resources/history/.shadow-copy/2018.08.30-16h45-1\n" +
      "3: src/test/resources/history/.shadow-copy/2018.08.31-16h45-1\n" +
      "2: src/test/resources/history/.shadow-copy/2018.09.01-16h45-1\n" +
      "1: src/test/resources/history/.shadow-copy/2018.09.02-16h45-1\n");

    out.reset();
    ShadowCopy.exec(out, "history", "-n", "5", "--no-index", base.toString());
    assertThat(out).hasToString("" +
      "src/test/resources/history/.shadow-copy/2018.08.29-16h45-1\n" +
      "src/test/resources/history/.shadow-copy/2018.08.30-16h45-1\n" +
      "src/test/resources/history/.shadow-copy/2018.08.31-16h45-1\n" +
      "src/test/resources/history/.shadow-copy/2018.09.01-16h45-1\n" +
      "src/test/resources/history/.shadow-copy/2018.09.02-16h45-1\n");

    out.reset();
    ShadowCopy.exec(out, "history", "-n", "1", "--no-index", base.toString());
    assertThat(out).hasToString("src/test/resources/history/.shadow-copy/2018.09.02-16h45-1\n");

    out.reset();
    ShadowCopy.exec(out, "purge", base.toString());
    assertThat(out.toString()).isEmpty();

    out.reset();
    ShadowCopy.exec(out, "history", base.toString());
    assertThat(out).hasToString("" +
      "10: src/test/resources/history/.shadow-copy/2018.08.24-16h45-1\n" +
      "9: src/test/resources/history/.shadow-copy/2018.08.25-16h45-1\n" +
      "8: src/test/resources/history/.shadow-copy/2018.08.26-16h45-1\n" +
      "7: src/test/resources/history/.shadow-copy/2018.08.27-16h45-1\n" +
      "6: src/test/resources/history/.shadow-copy/2018.08.28-16h45-1\n" +
      "5: src/test/resources/history/.shadow-copy/2018.08.29-16h45-1\n" +
      "4: src/test/resources/history/.shadow-copy/2018.08.30-16h45-1\n" +
      "3: src/test/resources/history/.shadow-copy/2018.08.31-16h45-1\n" +
      "2: src/test/resources/history/.shadow-copy/2018.09.01-16h45-1\n" +
      "1: src/test/resources/history/.shadow-copy/2018.09.02-16h45-1\n");

    out.reset();
    ShadowCopy.exec(out, "purge", "-n", "2", base.toString());
    assertThat(out.toString()).isEmpty();

    out.reset();
    ShadowCopy.exec(out, "history", base.toString());
    assertThat(out).hasToString("" +
      "2: src/test/resources/history/.shadow-copy/2018.09.01-16h45-1\n" +
      "1: src/test/resources/history/.shadow-copy/2018.09.02-16h45-1\n");

    // clean
    deleteIfExists(base.resolve(".shadow-copy"));
  }

  static void deleteIfExists(Path path) {
    try {
      if (Files.exists(path)) {
        deleteRecursively(path);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  static void deleteRecursively(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (Stream<Path> list = Files.list(path)) {
        for (Path child : list.collect(Collectors.toList())) {
          deleteRecursively(child);
        }
      }
    }
    Files.delete(path);
  }

  static int exec(String... command) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command)
      .redirectInput(Redirect.PIPE)
      .redirectError(Redirect.INHERIT)
      .redirectOutput(Redirect.DISCARD)
      .start();
    process.getOutputStream().close();
    return process.waitFor();
  }

}
