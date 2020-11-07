package com.auzeill.shadow.copy.action;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class CreateActionTest {

  @Test
  void create_shadow_directories(@TempDir Path shadowDirectory) throws IOException {
    Clock clock = Clock.fixed(Instant.parse("2018-08-19T16:45:42.00Z"), ZoneId.of("GMT"));
    Path shadowBaseDirectory1 = CreateAction.createShadowCopyDirectory(shadowDirectory, clock);
    Path shadowBaseDirectory2 = CreateAction.createShadowCopyDirectory(shadowDirectory, clock);
    Path shadowBaseDirectory3 = CreateAction.createShadowCopyDirectory(shadowDirectory, clock);
    assertThat(shadowBaseDirectory1).isDirectory();
    assertThat(shadowBaseDirectory2).isDirectory();
    assertThat(shadowBaseDirectory3).isDirectory();
    assertThat(shadowBaseDirectory1).endsWith(Paths.get("2018.08.19-16h45-1"));
    assertThat(shadowBaseDirectory2).endsWith(Paths.get("2018.08.19-16h45-2"));
    assertThat(shadowBaseDirectory3).endsWith(Paths.get("2018.08.19-16h45-3"));
  }

}
