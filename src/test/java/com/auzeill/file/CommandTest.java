package com.auzeill.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandTest {

  @Test
  void valid_command(@TempDir Path tempDir) throws IOException, InterruptedException {
    Path f1 = tempDir.resolve("f1");
    Path f2 = tempDir.resolve("f2");
    Files.writeString(f1, "data", UTF_8);
    Command command = Command.exec("/bin/cp", f1.toString(), f2.toString());
    command.waitFor();
    assertThat(f2).hasContent("data");
  }

  @Test
  void invalid_command(@TempDir Path tempDir) throws IOException, InterruptedException {
    Path unknown = tempDir.resolve("unknown");
    Path f2 = tempDir.resolve("f2");
    Command command = Command.exec("/bin/cp", unknown.toString(), f2.toString());
    assertThatThrownBy(command::waitFor)
      .isInstanceOf(ShadowErrorException.class)
      .hasMessageStartingWith("Unexpected exit value 1 for command: \"/bin/cp\"")
      .hasMessageContaining("No such file");
  }

}
