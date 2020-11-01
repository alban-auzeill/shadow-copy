package com.auzeill.file;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class ShadowCopyTest {

  @BeforeAll
  static void beforeAll() {
    ShadowCopy.clock = Clock.fixed(Instant.parse("2018-08-19T16:45:42.00Z"), ZoneId.of("GMT"));
  }

  @AfterAll
  static void afterAll() {
    ShadowCopy.clock = Clock.systemDefaultZone();
  }

  @Test
  void create_shadow_directories(@TempDir Path sourceDirectory) throws IOException {
    ShadowCopyOptions options = new ShadowCopyOptions(new String[] {});
    ShadowCopy shadowCopy = new ShadowCopy(options);
    Path shadowBaseDirectory1 = shadowCopy.createShadowBaseDirectory(sourceDirectory);
    Path shadowBaseDirectory2 = shadowCopy.createShadowBaseDirectory(sourceDirectory);
    Path shadowBaseDirectory3 = shadowCopy.createShadowBaseDirectory(sourceDirectory);
    assertThat(shadowBaseDirectory1).isDirectory();
    assertThat(shadowBaseDirectory2).isDirectory();
    assertThat(shadowBaseDirectory3).isDirectory();
    assertThat(shadowBaseDirectory1).endsWith(Paths.get(".shadow-copy", "2018.08.19-16h45-1"));
    assertThat(shadowBaseDirectory2).endsWith(Paths.get(".shadow-copy", "2018.08.19-16h45-2"));
    assertThat(shadowBaseDirectory3).endsWith(Paths.get(".shadow-copy", "2018.08.19-16h45-3"));
  }

  @Test
  void simple_case() throws IOException, InterruptedException {
    Path base = Paths.get("src", "test", "resources", "simple-case");
    // clean
    deleteIfExists(base.resolve(".shadow-copy"));

    // copy
    ShadowCopy shadowCopy = new ShadowCopy(new ShadowCopyOptions(new String[] {}));
    Path result = shadowCopy.copy(base.toString());

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
    deleteIfExists(base.resolve(".shadow-copy"));

    // copy
    ShadowCopy shadowCopy = new ShadowCopy(new ShadowCopyOptions(new String[] {}));
    Path result = shadowCopy.copy(base.toString());

    // check
    assertThat(base.resolve(".shadow-copy")).isDirectory();
    assertThat(result).isDirectory();
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
    assertThat(result.resolve(Paths.get(".shadow-copy-filter.sh"))).doesNotExist();
    assertThat(result.resolve(Paths.get("f2.json"))).doesNotExist();
    assertThat(result.resolve(Paths.get("link1"))).doesNotExist();
    assertThat(result.resolve(Paths.get("dir1", "tmp", "f1.txt"))).doesNotExist();
    assertThat(result.resolve(Paths.get("dir1", "not-tmp", "f1.txt"))).isRegularFile();
    assertThat(result.resolve(Paths.get("file42"))).doesNotExist();

    // clean
    deleteIfExists(base.resolve(".shadow-copy"));
  }

  @Test
  void hard_links(@TempDir Path base) throws IOException, InterruptedException {
    ShadowCopy shadowCopy = new ShadowCopy(new ShadowCopyOptions(new String[] {}));

    // prepare
    Files.writeString(base.resolve("f1.txt"), "Test data", UTF_8);
    Files.writeString(base.resolve("f2.txt"), "Content1", UTF_8);

    // copy
    Path result1 = shadowCopy.copy(base.toString());
    Files.writeString(base.resolve("f2.txt"), "Content1.1", UTF_8);
    Path result2 = shadowCopy.copy(base.toString());

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

  @Test
  void named_pipe(@TempDir Path base) throws IOException, InterruptedException {
    // prepare
    Files.writeString(base.resolve("regular-file.txt"), "Test data", UTF_8);
    int exit = exec("/usr/bin/mkfifo", base.resolve("fifo-named-pipe").toString());
    assertThat(exit).isEqualTo(0);

    // copy
    ShadowCopy shadowCopy = new ShadowCopy(new ShadowCopyOptions(new String[] {}));
    Path result = shadowCopy.copy(base.toString());

    assertThat(base.resolve(".shadow-copy")).isDirectory();
    assertThat(result).isDirectory();
    assertThat(result.resolve("regular-file.txt")).isRegularFile();
    assertThat(result.resolve("regular-file.txt")).hasContent("Test data");
    assertThat(result.resolve("fifo-named-pipe")).isRegularFile();
    String content = Files.readString(result.resolve("fifo-named-pipe"), UTF_8);
    assertThat(content).matches("Unsupported file type, lastModifiedTime: \\d+.*");
  }

  static void deleteIfExists(Path path) throws IOException {
    if (Files.exists(path)) {
      deleteRecursively(path);
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
