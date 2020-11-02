package com.auzeill.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class IOUtilsTest {

  @Test
  void read_bytes() throws IOException {
    byte[] expected = new byte[3210];
    for (int i = 0; i < expected.length; i++) {
      expected[i] = (byte) i;
    }
    byte[] actual = IOUtils.readBytes(new ByteArrayInputStream(expected));
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void read_string() throws IOException {
    StringBuilder expected = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      expected.append("a lot of data...");
    }
    String actual = IOUtils.readUtf8String(new ByteArrayInputStream(expected.toString().getBytes(UTF_8)));
    assertThat(actual).isEqualTo(expected.toString());
  }

}
