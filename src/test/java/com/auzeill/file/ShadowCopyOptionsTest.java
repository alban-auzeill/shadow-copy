package com.auzeill.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShadowCopyOptionsTest {

  @Test
  void no_argument() {
    ShadowCopyOptions options = new ShadowCopyOptions(new String[] {});
    assertThat(options.shadowDirectoryName).isEqualTo(".shadow-copy");
    assertThat(options.filterFilename).isEqualTo(".shadow-copy-filter");
    assertThat(options.sourceDirectories).containsExactly(".");
  }

  @Test
  void one_directory() {
    ShadowCopyOptions options = new ShadowCopyOptions(new String[] {"my-directory"});
    assertThat(options.shadowDirectoryName).isEqualTo(".shadow-copy");
    assertThat(options.filterFilename).isEqualTo(".shadow-copy-filter");
    assertThat(options.sourceDirectories).containsExactly("my-directory");
  }

  @Test
  void override_shadow_directory() {
    ShadowCopyOptions options = new ShadowCopyOptions(new String[] {"--shadow-directory", ".shadow", "--filter", ".shadow-filter", "dir1", "dir2"});
    assertThat(options.shadowDirectoryName).isEqualTo(".shadow");
    assertThat(options.filterFilename).isEqualTo(".shadow-filter");
    assertThat(options.sourceDirectories).containsExactly("dir1", "dir2");
  }

  @Test
  void last_shadow_index() {
    ShadowCopyOptions options = new ShadowCopyOptions(new String[] {"--shadow-index", "2"});
    assertThat(new ShadowCopyOptions(new String[] {}).lastShadowIndex).isEqualTo(1);
    assertThat(new ShadowCopyOptions(new String[] {"--shadow-index", "none"}).lastShadowIndex).isEqualTo(-1);
    assertThat(new ShadowCopyOptions(new String[] {"--shadow-index", "1"}).lastShadowIndex).isEqualTo(1);
    assertThat(new ShadowCopyOptions(new String[] {"--shadow-index", "2"}).lastShadowIndex).isEqualTo(2);
    assertThat(new ShadowCopyOptions(new String[] {"--shadow-index", "42"}).lastShadowIndex).isEqualTo(42);
    assertThatThrownBy(() -> new ShadowCopyOptions(new String[] {"--shadow-index", "-1"}))
      .isInstanceOf(ShadowErrorException.class)
      .hasMessage("--shadow-index should be >= 1");
  }

}
