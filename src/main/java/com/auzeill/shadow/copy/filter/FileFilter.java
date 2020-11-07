package com.auzeill.shadow.copy.filter;

import com.auzeill.shadow.copy.ShadowCopyError;
import com.auzeill.shadow.copy.utils.ActionUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FileFilter {

  @FunctionalInterface
  interface IgnoreMatcher {
    boolean matches(FileInfo file) throws IOException;
  }

  enum Subject {
    ABSOLUTE("absolute:"),
    RELATIVE("relative:"),
    FILENAME("filename:");

    public final String prefix;

    Subject(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public String toString() {
      return prefix;
    }
  }

  enum Type {
    EQUALS("equals:"),
    END_WITH("end-with:"),
    REGEX("reg-ex:"),
    SYMBOLIC_LINKS("symbolic-link"),
    MAX_SIZE("max-size:"),
    HAS_SIBLING("has-sibling:");

    public final String prefix;

    Type(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public String toString() {
      return prefix;
    }
  }

  Map<String, List<IgnoreMatcher>> ignoreByFileName = new HashMap<>();
  Map<String, List<IgnoreMatcher>> ignoreByRelativePath = new HashMap<>();
  Map<String, List<IgnoreMatcher>> ignoreByAbsolutePath = new HashMap<>();
  List<IgnoreMatcher> notIndexedIgnoreMatchers = new ArrayList<>();


  public static FileFilter loadFromShadowDirectory(Path shadowDirectory) throws IOException {
    Path filterPath = shadowDirectory.resolve("ignore");
    FileFilter filter;
    if (Files.exists(filterPath)) {
      filter = FileFilter.load(filterPath);
    } else {
      filter = new FileFilter();
    }
    filter.addIgnoredFilename(ActionUtils.DEFAULT_SHADOW_DIRECTORY_NAME + File.separator);
    filter.addIgnoredAbsolutePath(shadowDirectory.toString() + File.separator);
    return filter;
  }

  public static FileFilter load(Path ignoreFile) throws IOException {
    return load(Files.readString(ignoreFile, UTF_8));
  }

  public static FileFilter load(String ignoreConfiguration) {
    FileFilter filter = new FileFilter();
    ignoreConfiguration.lines()
      .map(line -> line.replace("^[ \t]+", ""))
      .filter(line -> !line.isEmpty() && !(line.startsWith("#") || line.startsWith("//")))
      .map(FileFilter::parseExpression)
      .forEach(filter::add);
    return filter;
  }

  private static <T> T findPrefix(T[] values, String expression, int start) {
    for (T value : values) {
      if (expression.startsWith(value.toString(), start)) {
        return value;
      }
    }
    String valueList = Arrays.stream(values).map(Object::toString).collect(Collectors.joining(", "));
    throw new ShadowCopyError("Missing prefix (" + valueList + ") at " + start + " in expression: " + expression);
  }

  private void add(Expression expression) {
    IgnoreMatcher ignoreMatcher = expression.ignoreMatcher();
    if (!expression.index(this, ignoreMatcher)) {
      notIndexedIgnoreMatchers.add(ignoreMatcher);
    }
  }

  public void addIgnoredFilename(String filename) {
    ignoreByFileName.put(filename, Collections.singletonList(file -> true));
  }

  public void addIgnoredAbsolutePath(String path) {
    ignoreByAbsolutePath.put(path, Collections.singletonList(file -> true));
  }

  public boolean filter(FileInfo file) throws IOException {
    return noneMatch(ignoreByFileName.get(file.filename), file) &&
      noneMatch(ignoreByRelativePath.get(file.relative), file) &&
      noneMatch(ignoreByAbsolutePath.get(file.absolute), file) &&
      noneMatch(notIndexedIgnoreMatchers, file);
  }

  private static boolean noneMatch(@Nullable List<IgnoreMatcher> ignoreMatchers, FileInfo file) throws IOException {
    if (ignoreMatchers == null) {
      return true;
    }
    for (IgnoreMatcher ignoreMatcher : ignoreMatchers) {
      if (ignoreMatcher.matches(file)) {
        return false;
      }
    }
    return true;
  }

  public interface Expression {
    int end();

    boolean index(FileFilter filter, IgnoreMatcher ignoreMatcher);

    IgnoreMatcher ignoreMatcher();
  }

  /**
   * Expr ::= OrExpression
   * OrExpression ::= AndExpression ('||' AndExpression)*
   * AndExpression ::= DelimitedOrMatcher ('&&' DelimitedOrMatcher)*
   * DelimitedOrMatcher ::= Delimited | Matcher
   * Delimited ::= '(' Expr ')' | '[' Expr ']' | '{' Expr '}' | '<' Expr '>' | '"' Expr '"' | '\'' Expr '\''
   * Matcher ::= ('absolute:' | 'relative:' | 'filename:') ('equals:' | 'end-with:' | 'reg-ex:') Pattern
   * Pattern ::= not empty, all remaining character up to the delimiter character provided by the parent rule
   */

  public static Expression parseExpression(String code) {
    Expression expression = parseExpression(code, 0, Collections.emptyList());
    if (expression.end() != code.length()) {
      throw new ShadowCopyError("Unexpected character at " + expression.end() + " in: " + code);
    }
    return expression;
  }

  static Expression parseExpression(String code, int start, List<String> endChars) {
    return parseOrExpression(code, start, endChars);
  }

  static Expression parseOrExpression(String code, int start, List<String> endChars) {
    String operatorToken = "||";
    endChars = new ArrayList<>(endChars);
    endChars.add(operatorToken);
    Expression leftOperand = parseAndExpression(code, start, endChars);
    int operator = leftOperand.end();
    while (code.startsWith(operatorToken, operator)) {
      int rightStart = operator + 2;
      Expression rightOperand = parseAndExpression(code, rightStart, endChars);
      leftOperand = new BinaryExpression(start, rightOperand.end(), leftOperand, BinaryExpression.Operator.OR, rightOperand);
      operator = nonSpace(code, rightOperand.end());
    }
    return leftOperand;
  }

  static Expression parseAndExpression(String code, int start, List<String> endChars) {
    String operatorToken = "&&";
    endChars = new ArrayList<>(endChars);
    endChars.add(operatorToken);
    Expression leftOperand = parseDelimitedOrMatcher(code, start, endChars);
    int operator = leftOperand.end();
    while (code.startsWith(operatorToken, operator)) {
      int rightStart = operator + 2;
      Expression rightOperand = parseDelimitedOrMatcher(code, rightStart, endChars);
      leftOperand = new BinaryExpression(start, rightOperand.end(), leftOperand, BinaryExpression.Operator.AND, rightOperand);
      operator = nonSpace(code, rightOperand.end());
    }
    return leftOperand;
  }

  static Expression parseDelimitedOrMatcher(String code, int start, List<String> endChars) {
    Expression expression = parseDelimited(code, start);
    if (expression != null) {
      return expression;
    }
    return parseMatcher(code, start, endChars);
  }

  static class Delimiter {
    public final char openChar;
    public final char closeChar;

    Delimiter(char openChar, char closeChar) {
      this.openChar = openChar;
      this.closeChar = closeChar;
    }
  }

  static final Delimiter[] DELIMITERS = {
    new Delimiter('(', ')'),
    new Delimiter('[', ']'),
    new Delimiter('{', '}'),
    new Delimiter('<', '>'),
    new Delimiter('"', '"'),
    new Delimiter('\'', '\'')
  };

  @Nullable
  static DelimitedExpression parseDelimited(String code, int start) {
    for (Delimiter delimiter : DELIMITERS) {
      DelimitedExpression expression = parseDelimited(delimiter, code, start);
      if (expression != null) {
        return expression;
      }
    }
    return null;
  }

  @Nullable
  static DelimitedExpression parseDelimited(Delimiter delimiter, String code, int start) {
    int leftSeparator = nonSpace(code, start);
    if (leftSeparator >= code.length() || code.charAt(leftSeparator) != delimiter.openChar) {
      return null;
    }
    List<String> endChars = Collections.singletonList(String.valueOf(delimiter.closeChar));
    Expression content = parseExpression(code, leftSeparator + 1, endChars);
    int rightSeparator = nonSpace(code, content.end());
    if (rightSeparator >= code.length() || code.charAt(rightSeparator) != delimiter.closeChar) {
      throw new ShadowCopyError("Missing delimiter '" + delimiter.closeChar + "' at " + rightSeparator + " in: " + code);
    }
    int end = nonSpace(code, rightSeparator + 1);
    return new DelimitedExpression(start, end, content);
  }

  static MatcherExpression parseMatcher(String code, int start, List<String> endChars) {
    int pos = nonSpace(code, start);
    Subject subject;
    Type type;
    if (code.startsWith(Type.SYMBOLIC_LINKS.toString(), pos)) {
      subject = Subject.ABSOLUTE;
      type = Type.SYMBOLIC_LINKS;
    } else if (code.startsWith(Type.MAX_SIZE.toString(), pos)) {
      subject = Subject.ABSOLUTE;
      type = Type.MAX_SIZE;
    } else if (code.startsWith(Type.HAS_SIBLING.toString(), pos)) {
      subject = Subject.ABSOLUTE;
      type = Type.HAS_SIBLING;
    } else {
      subject = findPrefix(Subject.values(), code, pos);
      pos += subject.toString().length();
      type = findPrefix(Type.values(), code, pos);
    }
    pos += type.toString().length();
    int patternStart = pos;
    int patternEnd = findEndOfPattern(code, patternStart, endChars);
    if (patternEnd == patternStart && type != Type.SYMBOLIC_LINKS) {
      throw new ShadowCopyError("Empty pattern at " + patternStart + " in: " + code);
    } else if (patternEnd != patternStart && type == Type.SYMBOLIC_LINKS) {
      throw new ShadowCopyError("None empty pattern at " + patternStart + " in: " + code);
    }
    return new MatcherExpression(start, patternEnd, subject, type, code.substring(patternStart, patternEnd));
  }

  static int findEndOfPattern(String code, int start, List<String> endChars) {
    if (!endChars.isEmpty()) {
      for (int i = start; i < code.length(); i++) {
        for (String endChar : endChars) {
          if (code.startsWith(endChar, i)) {
            return i;
          }
        }
      }
    }
    return code.length();
  }

  static int nonSpace(String expression, int start) {
    for (int i = start; i < expression.length(); i++) {
      char ch = expression.charAt(i);
      if (ch != ' ' && ch != '\t') {
        return i;
      }
    }
    return expression.length();
  }

  public static class BinaryExpression implements Expression {

    enum Operator {
      AND, OR
    }

    final int start;
    final int end;
    final Expression leftOperand;
    final Operator operator;
    final Expression rightOperand;

    public BinaryExpression(int start, int end, Expression leftOperand, Operator operator, Expression rightOperand) {
      this.start = start;
      this.end = end;
      this.leftOperand = leftOperand;
      this.operator = operator;
      this.rightOperand = rightOperand;
    }

    @Override
    public int end() {
      return end;
    }

    @Override
    public boolean index(FileFilter filter, IgnoreMatcher ignoreMatcher) {
      if (operator == Operator.AND) {
        return leftOperand.index(filter, ignoreMatcher) || rightOperand.index(filter, ignoreMatcher);
      } else {
        return false;
      }
    }

    @Override
    public IgnoreMatcher ignoreMatcher() {
      IgnoreMatcher leftMatcher = leftOperand.ignoreMatcher();
      IgnoreMatcher rightMatcher = rightOperand.ignoreMatcher();
      if (operator == Operator.AND) {
        return file -> leftMatcher.matches(file) && rightMatcher.matches(file);
      } else {
        return file -> leftMatcher.matches(file) || rightMatcher.matches(file);
      }
    }

  }

  public static class MatcherExpression implements Expression {

    final int start;
    final int end;
    final Subject subject;
    final Type type;
    final String pattern;

    public MatcherExpression(int start, int end, Subject subject, Type type, String pattern) {
      this.start = start;
      this.end = end;
      this.subject = subject;
      this.type = type;
      this.pattern = pattern;
    }

    @Override
    public int end() {
      return end;
    }

    @Override
    public boolean index(FileFilter filter, IgnoreMatcher ignoreMatcher) {
      if (subject == Subject.FILENAME && type == Type.EQUALS) {
        filter.ignoreByFileName.computeIfAbsent(pattern, p -> new ArrayList<>()).add(ignoreMatcher);
        return true;
      } else if (subject == Subject.RELATIVE && type == Type.EQUALS) {
        filter.ignoreByRelativePath.computeIfAbsent(pattern, p -> new ArrayList<>()).add(ignoreMatcher);
        return true;
      } else if (subject == Subject.ABSOLUTE && type == Type.EQUALS) {
        filter.ignoreByAbsolutePath.computeIfAbsent(pattern, p -> new ArrayList<>()).add(ignoreMatcher);
        return true;
      }
      return false;
    }

    @Override
    public IgnoreMatcher ignoreMatcher() {
      if (subject == Subject.FILENAME && type == Type.EQUALS) {
        return file -> file.filename.equals(pattern);
      } else if (subject == Subject.RELATIVE && type == Type.EQUALS) {
        return file -> file.relative.equals(pattern);
      } else if (subject == Subject.ABSOLUTE && type == Type.EQUALS) {
        return file -> file.absolute.equals(pattern);
      } else if (subject == Subject.FILENAME && type == Type.END_WITH) {
        return file -> file.filename.endsWith(pattern);
      } else if (subject == Subject.RELATIVE && type == Type.END_WITH) {
        return file -> file.relative.endsWith(pattern);
      } else if (subject == Subject.ABSOLUTE && type == Type.END_WITH) {
        return file -> file.absolute.endsWith(pattern);
      } else if (subject == Subject.FILENAME && type == Type.REGEX) {
        Pattern regex = Pattern.compile(pattern);
        return file -> regex.matcher(file.filename).find();
      } else if (subject == Subject.RELATIVE && type == Type.REGEX) {
        Pattern regex = Pattern.compile(pattern);
        return file -> regex.matcher(file.relative).find();
      } else if (subject == Subject.ABSOLUTE && type == Type.REGEX) {
        Pattern regex = Pattern.compile(pattern);
        return file -> regex.matcher(file.absolute).find();
      } else if (type == Type.SYMBOLIC_LINKS) {
        return file -> Files.isSymbolicLink(file.absolutePath);
      } else if (type == Type.MAX_SIZE) {
        long maxSize = Long.parseLong(pattern);
        return file -> !file.isDirectory && Files.size(file.absolutePath) > maxSize;
      } else if (type == Type.HAS_SIBLING) {
        Path expectedSibling = Paths.get(pattern);
        return file -> {
          Path parent = file.absolutePath.getParent();
          return parent != null && Files.exists(parent.resolve(expectedSibling));
        };
      } else {
        throw new ShadowCopyError("Unsupported expression: " + subject + type + pattern);
      }
    }

  }

  public static class DelimitedExpression implements Expression {

    final int start;
    final int end;
    final Expression content;

    public DelimitedExpression(int start, int end, Expression content) {
      this.start = start;
      this.end = end;
      this.content = content;
    }

    @Override
    public int end() {
      return end;
    }

    @Override
    public boolean index(FileFilter filter, IgnoreMatcher ignoreMatcher) {
      return content.index(filter, ignoreMatcher);
    }

    @Override
    public IgnoreMatcher ignoreMatcher() {
      return content.ignoreMatcher();
    }

  }

}
