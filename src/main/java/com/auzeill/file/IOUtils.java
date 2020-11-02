package com.auzeill.file;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class IOUtils {

  private IOUtils() {
    // Utility class
  }

  public static String readUtf8String(InputStream input) throws IOException {
    return new String(readBytes(input), UTF_8);
  }

  public static byte[] readBytes(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length = input.read(buffer);
    while (length != -1) {
      output.write(buffer, 0, length);
      length = input.read(buffer);
    }
    return output.toByteArray();
  }

}
