package com.auzeill.shadow.copy.filter;

import com.auzeill.shadow.copy.ShadowCopyError;
import com.auzeill.shadow.copy.filter.FileFilter.BinaryExpression;
import com.auzeill.shadow.copy.filter.FileFilter.BinaryExpression.Operator;
import com.auzeill.shadow.copy.filter.FileFilter.DelimitedExpression;
import com.auzeill.shadow.copy.filter.FileFilter.IgnoreMatcher;
import com.auzeill.shadow.copy.filter.FileFilter.MatcherExpression;
import com.auzeill.shadow.copy.filter.FileFilter.Subject;
import com.auzeill.shadow.copy.filter.FileFilter.Type;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.auzeill.shadow.copy.filter.FileFilter.findEndOfPattern;
import static com.auzeill.shadow.copy.filter.FileFilter.load;
import static com.auzeill.shadow.copy.filter.FileFilter.nonSpace;
import static com.auzeill.shadow.copy.filter.FileFilter.parseAndExpression;
import static com.auzeill.shadow.copy.filter.FileFilter.parseDelimited;
import static com.auzeill.shadow.copy.filter.FileFilter.parseDelimitedOrMatcher;
import static com.auzeill.shadow.copy.filter.FileFilter.parseExpression;
import static com.auzeill.shadow.copy.filter.FileFilter.parseMatcher;
import static com.auzeill.shadow.copy.filter.FileFilter.parseOrExpression;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileFilterTest {

  @Test
  void non_space() {
    assertThat(nonSpace("", 0)).isEqualTo(0);
    assertThat(nonSpace("a", 0)).isEqualTo(0);
    assertThat(nonSpace("a", 1)).isEqualTo(1);
    assertThat(nonSpace("a b", 1)).isEqualTo(2);
    assertThat(nonSpace("a b", 2)).isEqualTo(2);
    assertThat(nonSpace("a  b", 1)).isEqualTo(3);
    assertThat(nonSpace("a\t\tb", 1)).isEqualTo(3);
    assertThat(nonSpace("a   ", 1)).isEqualTo(4);
  }

  @Test
  void find_end_of_pattern() {
    assertThat(findEndOfPattern("aaa", 0, Collections.emptyList())).isEqualTo(3);
    assertThat(findEndOfPattern("||a|a||a&&", 2, Arrays.asList("||", "&&"))).isEqualTo(5);
    assertThat(findEndOfPattern("||a&a&&a||", 2, Arrays.asList("||", "&&"))).isEqualTo(5);
    assertThat(findEndOfPattern("||aaaaaa", 2, Arrays.asList("||", "&&"))).isEqualTo(8);
  }

  @Test
  void parse_matcher() {
    List<String> endChars = Collections.emptyList();
    MatcherExpression expression = parseMatcher("filename:equals:file.txt", 0, endChars);
    assertThat(expression.start).isEqualTo(0);
    assertThat(expression.end).isEqualTo(24);
    assertThat(expression.subject).isEqualTo(Subject.FILENAME);
    assertThat(expression.type).isEqualTo(Type.EQUALS);
    assertThat(expression.pattern).isEqualTo("file.txt");
  }

  @Test
  void parse_matcher_symbolic_link() {
    List<String> endChars = Collections.emptyList();
    MatcherExpression expression = parseMatcher("symbolic-link", 0, endChars);
    assertThat(expression.start).isEqualTo(0);
    assertThat(expression.end).isEqualTo(13);
    assertThat(expression.subject).isEqualTo(Subject.ABSOLUTE);
    assertThat(expression.type).isEqualTo(Type.SYMBOLIC_LINKS);
    assertThat(expression.pattern).isEqualTo("");
  }

  @Test
  void parse_matcher_max_size() {
    List<String> endChars = Collections.emptyList();
    MatcherExpression expression = parseMatcher("max-size:1024", 0, endChars);
    assertThat(expression.start).isEqualTo(0);
    assertThat(expression.end).isEqualTo(13);
    assertThat(expression.subject).isEqualTo(Subject.ABSOLUTE);
    assertThat(expression.type).isEqualTo(Type.MAX_SIZE);
    assertThat(expression.pattern).isEqualTo("1024");
  }

  @Test
  void parse_matcher_before_and() {
    List<String> endChars = Arrays.asList("||", "&&", ")");
    MatcherExpression expression = parseMatcher("xx || relative:end-with:.txt&&yy", 5, endChars);
    assertThat(expression.start).isEqualTo(5);
    assertThat(expression.end).isEqualTo(28);
    assertThat(expression.subject).isEqualTo(Subject.RELATIVE);
    assertThat(expression.type).isEqualTo(Type.END_WITH);
    assertThat(expression.pattern).isEqualTo(".txt");
  }

  @Test
  void parse_matcher_empty_pattern() {
    List<String> endChars = Collections.emptyList();
    assertThatThrownBy(() -> parseMatcher("absolute:reg-ex:", 0, endChars))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Empty pattern at 16 in: absolute:reg-ex:");
  }

  @Test
  void parse_matcher_invalid_prefix() {
    List<String> endChars = Collections.emptyList();

    assertThatThrownBy(() -> parseMatcher("", 0, endChars))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing prefix (absolute:, relative:, filename:) at 0 in expression: ");

    assertThatThrownBy(() -> parseMatcher("xx&&", 4, endChars))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing prefix (absolute:, relative:, filename:) at 4 in expression: xx&&");

    assertThatThrownBy(() -> parseMatcher("xx&&unknown:equals:file.txt", 4, endChars))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing prefix (absolute:, relative:, filename:) at 4 in expression: xx&&unknown:equals:file.txt");

    assertThatThrownBy(() -> parseMatcher("xx&&filename:unknown:file.txt", 4, endChars))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing prefix (equals:, end-with:, reg-ex:, symbolic-link, max-size:, has-sibling:) at 13 in expression: xx&&filename:unknown:file.txt");

  }

  @Test
  void parse_delimited() {
    assertThat(parseDelimited(" ", 0)).isNull();
    assertThat(parseDelimited(" x", 0)).isNull();
    assertThat(parseDelimited("filename:equals:x", 0)).isNull();

    assertThatThrownBy(() -> parseDelimited("[]", 0))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing prefix (absolute:, relative:, filename:) at 1 in expression: []");

    assertThatThrownBy(() -> parseDelimited("(filename:equals:x", 0))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing delimiter ')' at 18 in: (filename:equals:x");

    assertThatThrownBy(() -> parseDelimited("(filename:equals:x}", 0))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing delimiter ')' at 19 in: (filename:equals:x}");

    assertThatThrownBy(() -> parseDelimited("((filename:equals:x)unknown", 0))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing delimiter ')' at 20 in: ((filename:equals:x)unknown");

    DelimitedExpression expression = parseDelimited("    {filename:equals:x}  ", 2);
    assertThat(expression.start).isEqualTo(2);
    assertThat(expression.end).isEqualTo(25);
    assertThat(expression.content).isInstanceOf(MatcherExpression.class);

    assertThat(parseDelimited("(filename:equals:x[a])...", 0).end).isEqualTo(22);
    assertThat(parseDelimited("[filename:equals:(ab)]...", 0).end).isEqualTo(22);
    assertThat(parseDelimited("{filename:equals:(ab)}...", 0).end).isEqualTo(22);
    assertThat(parseDelimited("<filename:equals:x{2}>...", 0).end).isEqualTo(22);
    assertThat(parseDelimited("'filename:equals:\"ab\"'...", 0).end).isEqualTo(22);
    assertThat(parseDelimited("\"filename:equals:'ab'\"...", 0).end).isEqualTo(22);
  }

  @Test
  void parse_delimited_or_matcher() {
    List<String> endChars = Collections.singletonList("&&");
    assertThat(parseDelimitedOrMatcher("filename:equals:x", 0, endChars)).isInstanceOf(MatcherExpression.class);
    assertThat(parseDelimitedOrMatcher("filename:equals:x&&unknown", 0, endChars).end()).isEqualTo(17);
    assertThat(parseDelimitedOrMatcher("(filename:equals:x)", 0, endChars)).isInstanceOf(DelimitedExpression.class);
    assertThat(parseDelimitedOrMatcher("(filename:equals:x) && unknown", 0, endChars).end()).isEqualTo(20);
  }

  @Test
  void parse_and_expression() {
    List<String> endChars = Collections.singletonList("||");
    assertThat(parseAndExpression("filename:equals:x", 0, endChars)).isInstanceOf(MatcherExpression.class);
    assertThat(parseAndExpression("filename:equals:x||unknown", 0, endChars).end()).isEqualTo(17);
    assertThat(parseAndExpression("(filename:equals:x)", 0, endChars)).isInstanceOf(DelimitedExpression.class);
    assertThat(parseAndExpression("(filename:equals:x) || unknown", 0, endChars).end()).isEqualTo(20);

    BinaryExpression expression = (BinaryExpression) parseAndExpression("filename:equals:a&&filename:equals:b||unknown", 0, endChars);
    assertThat(expression.start).isEqualTo(0);
    assertThat(expression.end).isEqualTo(36);
    assertThat(expression.leftOperand).isInstanceOf(MatcherExpression.class);
    assertThat(expression.leftOperand.end()).isEqualTo(17);
    assertThat(expression.operator).isEqualTo(Operator.AND);
    assertThat(expression.rightOperand).isInstanceOf(MatcherExpression.class);
    assertThat(expression.rightOperand.end()).isEqualTo(36);

    expression = (BinaryExpression) parseAndExpression("filename:equals:a&&filename:equals:b&&filename:equals:c&&filename:equals:d", 0, endChars);
    assertThat(expression.start).isEqualTo(0);
    assertThat(expression.end).isEqualTo(74);
    assertThat(expression.leftOperand).isInstanceOf(BinaryExpression.class);
    assertThat(expression.leftOperand.end()).isEqualTo(55);
    assertThat(expression.operator).isEqualTo(Operator.AND);
    assertThat(expression.rightOperand).isInstanceOf(MatcherExpression.class);
    assertThat(expression.rightOperand.end()).isEqualTo(74);
  }

  @Test
  void parse_or_expression() {
    List<String> endChars = Collections.singletonList(")");
    assertThat(parseOrExpression("filename:equals:x", 0, endChars)).isInstanceOf(MatcherExpression.class);
    assertThat(parseOrExpression("(filename:equals:x)", 0, endChars)).isInstanceOf(DelimitedExpression.class);
    assertThat(parseOrExpression("filename:equals:a&&filename:equals:b", 0, endChars)).isInstanceOf(BinaryExpression.class);

    BinaryExpression expression = (BinaryExpression) parseOrExpression("(filename:equals:a||filename:equals:b)", 1, endChars);
    assertThat(expression.start).isEqualTo(1);
    assertThat(expression.end).isEqualTo(37);
    assertThat(expression.leftOperand).isInstanceOf(MatcherExpression.class);
    assertThat(expression.leftOperand.end()).isEqualTo(18);
    assertThat(expression.operator).isEqualTo(Operator.OR);
    assertThat(expression.rightOperand).isInstanceOf(MatcherExpression.class);
    assertThat(expression.rightOperand.end()).isEqualTo(37);

    expression = (BinaryExpression) parseOrExpression("filename:equals:a||filename:equals:b||filename:equals:c||filename:equals:d", 0, endChars);
    assertThat(expression.start).isEqualTo(0);
    assertThat(expression.end).isEqualTo(74);
    assertThat(expression.leftOperand).isInstanceOf(BinaryExpression.class);
    assertThat(expression.leftOperand.end()).isEqualTo(55);
    assertThat(expression.operator).isEqualTo(Operator.OR);
    assertThat(expression.rightOperand).isInstanceOf(MatcherExpression.class);
    assertThat(expression.rightOperand.end()).isEqualTo(74);
  }

  @Test
  void parse_expression() {
    assertThat(parseExpression("filename:equals:x")).isInstanceOf(MatcherExpression.class);
    assertThat(parseExpression("(filename:equals:x)")).isInstanceOf(DelimitedExpression.class);
    assertThat(parseExpression("(filename:equals:a)&&(filename:equals:b)")).isInstanceOf(BinaryExpression.class);
    assertThat(parseExpression("(filename:equals:a)||(filename:equals:b)")).isInstanceOf(BinaryExpression.class);
    assertThat(((BinaryExpression)parseExpression("(filename:equals:a)||(filename:equals:b)&&(filename:equals:c)")).operator).isEqualTo(Operator.OR);
    assertThat(((BinaryExpression)parseExpression("(filename:equals:a)&&(filename:equals:b)||(filename:equals:c)")).operator).isEqualTo(Operator.OR);

    assertThatThrownBy(() -> parseExpression(""))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing prefix (absolute:, relative:, filename:) at 0 in expression: ");

    assertThatThrownBy(() -> parseExpression("unknown"))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing prefix (absolute:, relative:, filename:) at 0 in expression: unknown");

    assertThatThrownBy(() -> parseExpression("(filename:equals:x)filename:equals:x"))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Unexpected character at 19 in: (filename:equals:x)filename:equals:x");
  }

  @Test
  void can_be_indexed() {

    FileFilter filter = load("");

    IgnoreMatcher ignoreMatcher = f -> true;
    assertThat(parseExpression("filename:equals:a").index(filter, ignoreMatcher)).isTrue();
    assertThat(filter.ignoreByFileName.get("a").get(0)).isSameAs(ignoreMatcher);

    ignoreMatcher = f -> true;
    assertThat(parseExpression("(relative:equals:b)").index(filter, ignoreMatcher)).isTrue();
    assertThat(filter.ignoreByRelativePath.get("b").get(0)).isSameAs(ignoreMatcher);

    ignoreMatcher = f -> true;
    assertThat(parseExpression("((absolute:equals:c))").index(filter, ignoreMatcher)).isTrue();
    assertThat(filter.ignoreByAbsolutePath.get("c").get(0)).isSameAs(ignoreMatcher);

    ignoreMatcher = f -> true;
    assertThat(parseExpression("filename:end-with:d").index(filter, ignoreMatcher)).isFalse();
    assertThat(filter.ignoreByFileName.get("d")).isNull();

    ignoreMatcher = f -> true;
    assertThat(parseExpression("filename:reg-ex:e").index(filter, ignoreMatcher)).isFalse();
    assertThat(filter.ignoreByFileName.get("e")).isNull();

    ignoreMatcher = f -> true;
    assertThat(parseExpression("(filename:equals:f) && (filename:equals:g)").index(filter, ignoreMatcher)).isTrue();
    assertThat(filter.ignoreByFileName.get("f").get(0)).isSameAs(ignoreMatcher);
    assertThat(filter.ignoreByFileName.get("g")).isNull();

    ignoreMatcher = f -> true;
    assertThat(parseExpression("(filename:equals:h) && (filename:reg-ex:i)").index(filter, ignoreMatcher)).isTrue();
    assertThat(filter.ignoreByFileName.get("h").get(0)).isSameAs(ignoreMatcher);
    assertThat(filter.ignoreByFileName.get("i")).isNull();

    ignoreMatcher = f -> true;
    assertThat(parseExpression("(filename:reg-ex:j) && (filename:equals:k)").index(filter, ignoreMatcher)).isTrue();
    assertThat(filter.ignoreByFileName.get("j")).isNull();
    assertThat(filter.ignoreByFileName.get("k").get(0)).isSameAs(ignoreMatcher);

    ignoreMatcher = f -> true;
    assertThat(parseExpression("(filename:reg-ex:l) && (filename:reg-ex:m)").index(filter, ignoreMatcher)).isFalse();
    assertThat(filter.ignoreByFileName.get("l")).isNull();
    assertThat(filter.ignoreByFileName.get("m")).isNull();

    ignoreMatcher = f -> true;
    assertThat(parseExpression("(filename:equals:n) || (filename:equals:o)").index(filter, ignoreMatcher)).isFalse();
    assertThat(filter.ignoreByFileName.get("n")).isNull();
    assertThat(filter.ignoreByFileName.get("o")).isNull();

    ignoreMatcher = f -> true;
    assertThat(parseExpression("symbolic-link").index(filter, ignoreMatcher)).isFalse();
    assertThat(parseExpression("max-size:42").index(filter, ignoreMatcher)).isFalse();
  }

  @Test
  void no_prefix() {
    assertThatThrownBy(() -> load("file.txt"))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing prefix (absolute:, relative:, filename:) at 0 in expression: file.txt");
  }

  @Test
  void empty() {
    FileFilter filter = load("");
    assertThat(filter.ignoreByFileName).isEmpty();
    assertThat(filter.ignoreByRelativePath).isEmpty();
  }

  @Test
  void comment_and_end_of_line() {
    FileFilter filter = load("\n# Comment\n\n// Comment\n# Comment\r\n# Comment\r# Comment");
    assertThat(filter.ignoreByFileName).isEmpty();
    assertThat(filter.ignoreByRelativePath).isEmpty();
  }

  @Test
  void filename_equals() throws IOException {
    assertThat(load("filename:equals:file.txt").filter(file("file.txt"))).isFalse();
    assertThat(load("filename:equals:file.txt").filter(file("file.png"))).isTrue();
    assertThat(load("filename:equals:file.txt").filter(file("dir/file.txt"))).isFalse();
    assertThat(load("filename:equals:file.txt").filter(file("dir/file1.txt"))).isTrue();
  }

  @Test
  void relative_equals() throws IOException {
    assertThat(load("relative:equals:file.txt").filter(file("file.txt"))).isFalse();
    assertThat(load("relative:equals:file.txt").filter(file("file.png"))).isTrue();
    assertThat(load("relative:equals:file.txt").filter(file("dir/file.txt"))).isTrue();
    assertThat(load("relative:equals:dir/file.txt").filter(file("file.txt"))).isTrue();
    assertThat(load("relative:equals:dir/file.txt").filter(file("dir/file.txt"))).isFalse();
  }

  @Test
  void absolute_equals() throws IOException {
    assertThat(load("absolute:equals:/tmp/dir/file.txt").filter(file("file.txt"))).isTrue();
    assertThat(load("absolute:equals:/tmp/dir/file.txt").filter(file("dir/file.txt"))).isFalse();
    assertThat(load("absolute:equals:/tmp/dir/file.txt").filter(file("dir2/file.txt"))).isTrue();
    assertThat(load("absolute:equals:/other/dir/file.txt").filter(file("dir/file.txt"))).isTrue();
  }

  @Test
  void filename_end_with() throws IOException {
    assertThat(load("filename:end-with:.txt").filter(file("file.txt"))).isFalse();
    assertThat(load("filename:end-with:.txt").filter(file("file.png"))).isTrue();
  }

  @Test
  void relative_end_with() throws IOException {
    assertThat(load("relative:end-with:dir1/file").filter(file("dir1/file"))).isFalse();
    assertThat(load("relative:end-with:dir1/file").filter(file("parent/dir1/file"))).isFalse();
    assertThat(load("relative:end-with:dir1/file").filter(file("file"))).isTrue();
    assertThat(load("relative:end-with:dir1/file").filter(file("dir2/file"))).isTrue();
  }

  @Test
  void absolute_end_with() throws IOException {
    assertThat(load("absolute:end-with:tmp/dir/file.txt").filter(file("file.txt"))).isTrue();
    assertThat(load("absolute:end-with:tmp/dir/file.txt").filter(file("dir/file.txt"))).isFalse();
    assertThat(load("absolute:end-with:tmp/dir/file.txt").filter(file("dir2/file.txt"))).isTrue();
  }

  @Test
  void filename_reg_ex() throws IOException {
    assertThat(load("filename:reg-ex:foo").filter(file("file-foo.txt"))).isFalse();
    assertThat(load("filename:reg-ex:foo").filter(file("file-bar.txt"))).isTrue();
    assertThat(load("filename:reg-ex:foo$").filter(file("file-foo"))).isFalse();
    assertThat(load("filename:reg-ex:foo$").filter(file("file-foo.txt"))).isTrue();
    assertThat(load("filename:reg-ex:foo").filter(file("foo/file.txt"))).isTrue();
    assertThat(load("filename:reg-ex:^f").filter(file("f/foo.txt"))).isFalse();
    assertThat(load("filename:reg-ex:^f").filter(file("f/bar.txt"))).isTrue();
  }

  @Test
  void relative_reg_ex() throws IOException {
    assertThat(load("relative:reg-ex:foo/bar").filter(file("foo/bar.txt"))).isFalse();
    assertThat(load("relative:reg-ex:foo/bar").filter(file("foo/foo.txt"))).isTrue();
    assertThat(load("relative:reg-ex:foo/bar$").filter(file("foo/bar"))).isFalse();
    assertThat(load("relative:reg-ex:foo/bar$").filter(file("foo/bar.txt"))).isTrue();
    assertThat(load("relative:reg-ex:^foo").filter(file("foo/bar"))).isFalse();
    assertThat(load("relative:reg-ex:^foo").filter(file("bar/foo"))).isTrue();
  }

  @Test
  void absolute_reg_ex() throws IOException {
    assertThat(load("absolute:reg-ex:foo").filter(file("foo"))).isFalse();
    assertThat(load("absolute:reg-ex:foo").filter(file("bar"))).isTrue();
    assertThat(load("absolute:reg-ex:foo").filter(file("foo/file"))).isFalse();
    assertThat(load("absolute:reg-ex:foo").filter(file("bar/file"))).isTrue();
    assertThat(load("absolute:reg-ex:tmp").filter(file("foo"))).isFalse();
    assertThat(load("absolute:reg-ex:etc").filter(file("foo"))).isTrue();
  }

  @Test
  void symbolic_link(@TempDir Path base) throws IOException {
    Path nonLink = base.resolve("non-link");
    Path link  = base.resolve("link");
    Files.createSymbolicLink(link, nonLink);
    assertThat(load("symbolic-link").filter(new FileInfo(nonLink, nonLink.getFileName()))).isTrue();
    assertThat(load("symbolic-link").filter(new FileInfo(link, link.getFileName()))).isFalse();
    assertThat(load("(filename:end-with:nk) && (symbolic-link)").filter(new FileInfo(link, link.getFileName()))).isFalse();
    assertThat(load("(filename:end-with:xx) && (symbolic-link)").filter(new FileInfo(link, link.getFileName()))).isTrue();
  }

  @Test
  void max_size(@TempDir Path base) throws IOException {
    Path small = base.resolve("small");
    Files.writeString(small, "data...data", UTF_8);

    Path big  = base.resolve("big");
    Files.writeString(big, "data...data...data...data...data...data", UTF_8);

    assertThat(load("max-size:30").filter(new FileInfo(small, small.getFileName()))).isTrue();
    assertThat(load("max-size:30").filter(new FileInfo(big, big.getFileName()))).isFalse();
    assertThat(load("(filename:end-with:ig) && (max-size:30)").filter(new FileInfo(big, big.getFileName()))).isFalse();
    assertThat(load("(filename:end-with:xx) && (max-size:30)").filter(new FileInfo(big, big.getFileName()))).isTrue();
  }

  @Test
  void has_sibling(@TempDir Path base) throws IOException {
    Path file1 = base.resolve("file1");
    Files.writeString(file1, "data", UTF_8);

    Path file2 = base.resolve("file2");
    Files.writeString(file2, "data", UTF_8);

    assertThat(load("(filename:equals:file1)&&(has-sibling:file2)").filter(new FileInfo(file1, file1.getFileName()))).isFalse();
    assertThat(load("(filename:equals:file1)&&(has-sibling:file3)").filter(new FileInfo(file1, file1.getFileName()))).isTrue();
  }

  private static FileInfo file(String path) {
    Path relative = Paths.get(normalize(path));
    Path absolute = Paths.get(normalize("/tmp")).resolve(relative);
    return new FileInfo(absolute, relative);
  }

  private static String normalize(String path) {
    return path.replace('/', File.separatorChar);
  }

}
