package com.auzeill.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShadowCopyWalkerTest {

  @Test
  void start_background_process(@TempDir Path sourceBaseDirectory) throws IOException, InterruptedException {
    ShadowCopyWalker walker = createWalker(sourceBaseDirectory);

    Path srcFile = walker.sourceBaseDirectory.resolve("source-file");
    Path dstFile = walker.sourceBaseDirectory.resolve("dest-file");

    Files.writeString(srcFile, "Test data");

    walker.startBackgroundProcess(
      "/bin/cp", "--reflink=auto" , "--preserve=all", "--no-target-directory",
      srcFile.toString(),
      dstFile.toString());

    walker.terminateBackgroundProcess();

    assertThat(Files.isRegularFile(dstFile)).isTrue();
    assertThat(Files.readString(dstFile, UTF_8)).isEqualTo("Test data");

    walker.startBackgroundProcess(
      "/bin/cp",
      walker.sourceBaseDirectory.resolve("unknow1").toString(),
      walker.sourceBaseDirectory.resolve("unknow2").toString());

    assertThatThrownBy(walker::terminateBackgroundProcess)
      .isInstanceOf(ShadowErrorException.class)
      .hasMessageStartingWith("Unexpected exit code 1 for command: \"/bin/cp\"")
      .hasMessageContaining("No such file");
  }

  ShadowCopyWalker createWalker(Path sourceBaseDirectory, String... copyOptions) {
    ShadowCopyOptions options = new ShadowCopyOptions(copyOptions);
    ShadowCopyFilter filter = new ShadowCopyFilter();
    filter.addIgnoredFilename(options.shadowDirectoryName + File.separator);
    filter.addIgnoredFilename(options.filterFilename);
    return new ShadowCopyWalker(
      options,
      sourceBaseDirectory,
      sourceBaseDirectory.resolve(options.shadowDirectoryName),
      null,
      ShadowCopyWalker.Action.CREATE_COPY,
      filter);
  }

}
