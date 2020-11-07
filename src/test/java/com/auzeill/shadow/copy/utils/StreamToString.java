package com.auzeill.shadow.copy.utils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class StreamToString extends PrintStream {

  public StreamToString() {
    super(new ByteArrayOutputStream(), true, UTF_8);
  }

  public void reset() {
    ((ByteArrayOutputStream) this.out).reset();
  }

  @Override
  public String toString() {
    ByteArrayOutputStream stream = (ByteArrayOutputStream) out;
    return stream.toString(UTF_8);
  }

}
