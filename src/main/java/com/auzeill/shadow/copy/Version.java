package com.auzeill.shadow.copy;

import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class Version {

  private Version() {
    // utility class
  }

  public static String get() {
    try(InputStream input = Version.class.getResourceAsStream("shadow-copy.version")) {
      byte[] buffer = new byte[200];
      int length = input.read(buffer);
      return new String(buffer, 0, length, UTF_8).replace("\n", "");
    } catch (IOException ex) {
      throw new ShadowCopyError("Failed to read shadow-copy.version file.");
    }
  }

}
