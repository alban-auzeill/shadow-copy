package com.auzeill.shadow.copy.arguments;

import com.auzeill.shadow.copy.ShadowCopyError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class ArgumentsTest {

  @Test
  void help() throws IOException {
    String help = Arguments.help();
    String helpWithoutVersion = help
      .replaceFirst("(?m)^Shadow Copy [?\\d]\\..*+$", "Shadow Copy");

    String readMe = Files.readString(Paths.get("README.md"), UTF_8);
    Pattern extractHelp = Pattern.compile("shadow-copy --help\n```\n```\n([^`]*\n)```\n");
    Matcher extractHelpMatcher = extractHelp.matcher(readMe);
    assertThat(extractHelpMatcher.find()).isTrue();
    assertThat(helpWithoutVersion).isEqualTo(extractHelpMatcher.group(1));
  }

  @Test
  void version() {
    Arguments args = new Arguments("--version");
    assertThat(args.action).isNull();
    assertThat(args.options.keySet()).containsExactly(Option.VERSION);
    assertThat(args.actionArguments).isEmpty();
  }

  @Test
  void no_arguments() {
    Arguments args = new Arguments();
    assertThat(args.action).isNull();
    assertThat(args.options.keySet()).containsExactly(Option.HELP);
    assertThat(args.actionArguments).isEmpty();
  }

  @Test
  void show_help() {
    Arguments args = new Arguments("diff", "--help");
    assertThat(args.action).isEqualTo(Action.DIFF);
    assertThat(args.options.keySet()).containsExactly(Option.HELP);
    assertThat(args.actionArguments).isEmpty();
  }

  @Test
  void unknown_action() {
    assertThatThrownBy(() -> new Arguments("unknown"))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Unknown action 'unknown', list valid action with --help");

    assertThatThrownBy(() -> new Arguments("-n", "2", "unknown"))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Unknown action 'unknown', list valid action with --help");
  }

  @Test
  void missing_action() {
    assertThatThrownBy(() -> new Arguments("-n", "1"))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing action, list valid action with --help");
  }

  @Test
  void missing_option_argument() {
    assertThatThrownBy(() -> new Arguments("history", "-n"))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing argument for -n");

    assertThatThrownBy(() -> new Arguments("history", "-n", " "))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Missing argument for -n");
  }

  @Test
  void unknown_option() {
    assertThatThrownBy(() -> new Arguments("create", "--unknown"))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Unknown option '--unknown', use '--' to separate options and arguments");
  }

  @Test
  void option_like_argument() {
    Arguments args = new Arguments("create", "--", "--unknown");
    assertThat(args.action).isEqualTo(Action.CREATE);
    assertThat(args.options).isEmpty();
    assertThat(args.actionArguments).containsExactly("--unknown");
  }

  @Test
  void incompatible_option() {
    assertThatThrownBy(() -> new Arguments("create", "-n", "1"))
      .isInstanceOf(ShadowCopyError.class)
      .hasMessage("Option '-n' can not be used with action 'create'");
  }

  @Test
  void compatible_option() {
    Arguments args = new Arguments("history", "--no-index", "-n", "1", "dir1");
    assertThat(args.action).isEqualTo(Action.HISTORY);
    assertThat(args.options).containsOnly(entry(Option.NO_INDEX, ""), entry(Option.NUMBER, "1"));
    assertThat(args.actionArguments).containsExactly("dir1");
  }

}
