package org.plumelib.icalavailable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end tests for the ICalAvailable program. Each test runs the program in a new process, in a
 * fresh working directory, and compares everything the program produced -- its exit status, its
 * standard output, its standard error, and the files it wrote -- to goal files stored in the
 * repository.
 *
 * <p>The test data is in {@code src/test/resources/end-to-end}, one subdirectory per test case; see
 * {@code src/test/resources/end-to-end/README.txt} for the layout of a test case and for the
 * environment in which the program runs. Every subdirectory is a test case, so a new test case can
 * be added without changing this file.
 *
 * <p>By default, the tests run this Java implementation of the program. If system property {@code
 * e2e.command} is set, the tests instead run the command that it names, which permits testing
 * another implementation, possibly written in another language. The property's value is split at
 * whitespace into the command and its leading arguments; the test case's arguments follow them. The
 * Gradle build sets the property from the Gradle property of the same name:
 *
 * <pre>{@code ./gradlew test -Pe2e.command="python3 /path/to/icalavailable.py"}</pre>
 */
final class ICalAvailableEndToEndTest {

  /** Creates a new ICalAvailableEndToEndTest. */
  ICalAvailableEndToEndTest() {}

  /** The classpath resource that contains one subdirectory per test case. */
  private static final String testDataResource = "/end-to-end";

  /** The directory that contains one subdirectory per test case. */
  private static final Path testDataDir = findTestDataDir();

  /** The file names that are permitted to appear in a test case directory. */
  private static final Set<String> permittedCaseFiles =
      Set.of(
          "description.txt",
          "input",
          "args.txt",
          "tz.txt",
          "goal-out.txt",
          "goal-out-prefix.txt",
          "goal-out-suffix.txt",
          "goal-err.txt",
          "goal-err-prefix.txt",
          "goal-err-suffix.txt",
          "goal-status.txt");

  /** The value of the TZ environment variable when the test case has no {@code tz.txt} file. */
  private static final String defaultTimeZone = "America/Los_Angeles";

  /** The value of the LC_ALL environment variable, which determines the locale. */
  private static final String locale = "C.UTF-8";

  /**
   * Returns the Java system properties that make this Java implementation behave as the environment
   * variables TZ and LC_ALL request, even on a platform whose JVM ignores those variables.
   *
   * @param timeZone the value of the TZ environment variable, such as "America/New_York"
   * @return command-line arguments for the JVM that set system properties
   */
  private static List<String> javaSystemProperties(String timeZone) {
    return List.of(
        "-Duser.timezone=" + timeZone,
        "-Duser.language=en",
        "-Duser.country=US",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8");
  }

  /**
   * Matches a line of standard error that the logging framework of a library that the Java
   * implementation uses writes; the harness ignores such lines.
   */
  private static final Pattern ignoredStderrLine = Pattern.compile("(?m)^SLF4J: .*(\r?\n|\\z)");

  /** How long to wait for the program to terminate, in seconds. */
  private static final int timeoutSeconds = 120;

  /**
   * Returns the directory that contains the test cases.
   *
   * @return the directory that contains the test cases
   */
  private static Path findTestDataDir() {
    URL url = ICalAvailableEndToEndTest.class.getResource(testDataResource);
    if (url == null) {
      throw new Error("Cannot find resource " + testDataResource + " on the classpath.");
    }
    try {
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new Error("Cannot convert " + url + " to a path.", e);
    }
  }

  /**
   * Returns the name of each test case: that is, of each subdirectory of the test data directory.
   *
   * @return the name of each test case
   * @throws IOException if the test data directory cannot be read
   */
  private static List<String> testCaseNames() throws IOException {
    List<String> result = new ArrayList<>();
    try (Stream<Path> children = Files.list(testDataDir)) {
      for (Path child : children.toList()) {
        if (Files.isDirectory(child)) {
          result.add(child.toFile().getName());
        }
      }
    }
    result.sort(Comparator.naturalOrder());
    assertFalse(result.isEmpty(), "No test cases in " + testDataDir);
    return result;
  }

  /**
   * Runs one end-to-end test case.
   *
   * @param caseName the name of a test case, which is the name of a subdirectory of the test data
   *     directory
   * @param workingDir the directory in which to run the program
   * @param captureDir a directory in which to capture the program's standard output and standard
   *     error; it is separate from {@code workingDir} so that the capture files are not mistaken
   *     for files that the program wrote
   * @throws Exception if the test case cannot be read or the program cannot be run
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("testCaseNames")
  void endToEnd(String caseName, @TempDir Path workingDir, @TempDir Path captureDir)
      throws Exception {
    Path caseDir = testDataDir.resolve(caseName);
    checkCaseDir(caseDir);

    Path inputDir = caseDir.resolve("input");
    if (Files.isDirectory(inputDir)) {
      copyTree(inputDir, workingDir.resolve("input"));
    }

    List<String> args = arguments(caseDir);
    String timeZone = timeZone(caseDir);
    Path stdoutFile = captureDir.resolve("stdout.txt");
    Path stderrFile = captureDir.resolve("stderr.txt");
    int status = runProgram(workingDir, args, timeZone, stdoutFile, stderrFile);

    String context = "Test case " + caseName + ", TZ=" + timeZone + ", arguments " + args + ".";

    assertEquals(
        goalStatus(caseDir),
        status,
        context + "  Wrong exit status.  Standard error:%n%s".formatted(readText(stderrFile)));
    checkOutput(caseDir, "out", "standard output", normalize(readText(stdoutFile)), context);
    checkOutput(caseDir, "err", "standard error", normalizeStderr(readText(stderrFile)), context);
    checkWorkingDir(workingDir, context);
  }

  /**
   * Fails if the test case directory contains a file whose name the test harness does not know
   * about; such a file is probably a typo in a goal file's name, which would otherwise be silently
   * ignored.
   *
   * @param caseDir a test case directory
   * @throws IOException if the directory cannot be read
   */
  private static void checkCaseDir(Path caseDir) throws IOException {
    try (Stream<Path> children = Files.list(caseDir)) {
      for (Path child : children.toList()) {
        String name = child.toFile().getName();
        if (!permittedCaseFiles.contains(name)) {
          fail(
              "Test case "
                  + caseDir.toFile().getName()
                  + " contains unrecognized file "
                  + name
                  + ".  Permitted files are "
                  + new TreeSet<>(permittedCaseFiles)
                  + ".");
        }
      }
    }
    for (String stream : List.of("out", "err")) {
      if (Files.isRegularFile(caseDir.resolve("goal-" + stream + ".txt"))
          && (Files.isRegularFile(caseDir.resolve("goal-" + stream + "-prefix.txt"))
              || Files.isRegularFile(caseDir.resolve("goal-" + stream + "-suffix.txt")))) {
        fail(
            "Test case %s contains goal-%s.txt and also goal-%s-prefix.txt or goal-%s-suffix.txt."
                .formatted(caseDir.toFile().getName(), stream, stream, stream));
      }
    }
  }

  /**
   * Returns the command-line arguments for the given test case: the non-empty lines of the case's
   * {@code args.txt}, or no arguments if the case has no {@code args.txt}.
   *
   * @param caseDir a test case directory
   * @return the command-line arguments for the test case
   * @throws IOException if a file cannot be read
   */
  private static List<String> arguments(Path caseDir) throws IOException {
    Path argsFile = caseDir.resolve("args.txt");
    List<String> result = new ArrayList<>();
    if (Files.isRegularFile(argsFile)) {
      for (String line : Files.readAllLines(argsFile, StandardCharsets.UTF_8)) {
        if (!line.isEmpty()) {
          result.add(line);
        }
      }
    }
    return result;
  }

  /**
   * Returns the time zone in which to run the given test case: the contents of the case's {@code
   * tz.txt}, or the default time zone if the case has no {@code tz.txt}.
   *
   * @param caseDir a test case directory
   * @return the value of the TZ environment variable for the test case, such as "America/New_York"
   * @throws IOException if a file cannot be read
   */
  private static String timeZone(Path caseDir) throws IOException {
    Path tzFile = caseDir.resolve("tz.txt");
    if (!Files.isRegularFile(tzFile)) {
      return defaultTimeZone;
    }
    return readText(tzFile).trim();
  }

  /**
   * Runs the program.
   *
   * @param workingDir the working directory for the program
   * @param args the command-line arguments for the program
   * @param timeZone the value of the TZ environment variable, such as "America/New_York"
   * @param stdoutFile where to write the program's standard output
   * @param stderrFile where to write the program's standard error
   * @return the program's exit status
   * @throws IOException if the program cannot be started
   * @throws InterruptedException if this thread is interrupted while the program runs
   */
  private static int runProgram(
      Path workingDir, List<String> args, String timeZone, Path stdoutFile, Path stderrFile)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    String externalCommand = externalCommand();
    if (externalCommand != null) {
      command.addAll(Arrays.asList(externalCommand.trim().split("\\s+")));
    } else {
      command.add(javaExecutable());
      command.addAll(javaSystemProperties(timeZone));
      command.add("-cp");
      command.add(classpath());
      command.add(ICalAvailable.class.getName());
    }
    command.addAll(args);
    ProcessBuilder pb =
        new ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectOutput(stdoutFile.toFile())
            .redirectError(stderrFile.toFile());
    Map<String, String> env = pb.environment();
    env.put("TZ", timeZone);
    env.put("LC_ALL", locale);
    Process process = pb.start();
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new Error("The program did not terminate within " + timeoutSeconds + " seconds.");
    }
    return process.exitValue();
  }

  /**
   * Returns the command that runs another implementation of the program, or null to run this Java
   * implementation.
   *
   * @return the value of system property {@code e2e.command}, or null if it is unset or empty
   */
  private static @Nullable String externalCommand() {
    String result = System.getProperty("e2e.command");
    if (result == null || result.isBlank()) {
      return null;
    }
    return result;
  }

  /**
   * Returns the java executable of the JVM that is running the tests.
   *
   * @return the java executable of the JVM that is running the tests
   */
  private static String javaExecutable() {
    String javaHome = System.getProperty("java.home");
    if (javaHome == null) {
      return "java";
    }
    Path java = Path.of(javaHome, "bin", "java");
    if (Files.isRegularFile(java)) {
      return java.toString();
    }
    Path javaExe = Path.of(javaHome, "bin", "java.exe");
    if (Files.isRegularFile(javaExe)) {
      return javaExe.toString();
    }
    return "java";
  }

  /**
   * Returns the classpath with which to run the program, which is the classpath of the JVM that is
   * running the tests.
   *
   * @return the classpath with which to run the program
   */
  private static String classpath() {
    String result = System.getProperty("java.class.path");
    if (result == null) {
      throw new Error("Property java.class.path is not set.");
    }
    return result;
  }

  /**
   * Returns the expected exit status of the given test case, which is 0 if the test case does not
   * say.
   *
   * @param caseDir a test case directory
   * @return the expected exit status of the test case
   * @throws IOException if the goal file cannot be read
   */
  private static int goalStatus(Path caseDir) throws IOException {
    Path goalStatusFile = caseDir.resolve("goal-status.txt");
    if (!Files.isRegularFile(goalStatusFile)) {
      return 0;
    }
    return Integer.parseInt(readText(goalStatusFile).trim());
  }

  /**
   * Fails if what the program wrote to standard output or standard error is not what the test case
   * says it should be. For standard output, the test case gives either the whole text (in {@code
   * goal-out.txt}) or its beginning or end or both (in {@code goal-out-prefix.txt} and {@code
   * goal-out-suffix.txt}), for output such as a stack trace that would make a whole-text goal file
   * brittle. If the test case gives none of these files, the program should write nothing. Likewise
   * for standard error, with "err" in place of "out" in the file names.
   *
   * @param caseDir a test case directory
   * @param stream "out" or "err", the part of the goal files' names that names the stream
   * @param streamName "standard output" or "standard error"; used for diagnostics
   * @param actual what the program wrote to the stream, normalized
   * @param context a description of the test case; used for diagnostics
   * @throws IOException if a goal file cannot be read
   */
  private static void checkOutput(
      Path caseDir, String stream, String streamName, String actual, String context)
      throws IOException {
    Path prefixFile = caseDir.resolve("goal-" + stream + "-prefix.txt");
    Path suffixFile = caseDir.resolve("goal-" + stream + "-suffix.txt");
    if (!Files.isRegularFile(prefixFile) && !Files.isRegularFile(suffixFile)) {
      assertEquals(
          goalText(caseDir.resolve("goal-" + stream + ".txt")),
          actual,
          context + "  Wrong " + streamName + ".");
      return;
    }
    if (Files.isRegularFile(prefixFile)) {
      String prefix = goalText(prefixFile);
      assertTrue(
          actual.startsWith(prefix),
          context
              + "  Expected %s to start with:%n%s%nbut was:%n%s"
                  .formatted(streamName, prefix, actual));
    }
    if (Files.isRegularFile(suffixFile)) {
      String suffix = goalText(suffixFile);
      assertTrue(
          actual.endsWith(suffix),
          context
              + "  Expected %s to end with:%n%s%nbut was:%n%s"
                  .formatted(streamName, suffix, actual));
    }
  }

  /**
   * Fails if the program created any file in the working directory, outside the {@code input}
   * directory that the harness created. The program reads its input files but writes no files.
   *
   * @param workingDir the directory in which the program ran
   * @param context a description of the test case; used for diagnostics
   * @throws IOException if a file cannot be read
   */
  private static void checkWorkingDir(Path workingDir, String context) throws IOException {
    Map<String, Path> actualFiles = relativeFileNames(workingDir);
    List<String> unexpected =
        actualFiles.keySet().stream()
            .filter(name -> !name.startsWith("input/"))
            .collect(Collectors.toList());
    assertEquals(
        List.of(), unexpected, context + "  The program created files in the working directory.");
  }

  /**
   * Returns every file (not directory) under the given directory, as a map from the file's name
   * relative to the given directory (using "/" as the separator) to the file.
   *
   * @param dir a directory to walk
   * @return the files under the given directory
   * @throws IOException if the directory cannot be read
   */
  private static @Modifiable Map<String, Path> relativeFileNames(Path dir) throws IOException {
    Map<String, Path> result = new TreeMap<>();
    try (Stream<Path> paths = Files.walk(dir)) {
      for (Path path : paths.toList()) {
        if (Files.isRegularFile(path)) {
          result.put(dir.relativize(path).toString().replace(File.separatorChar, '/'), path);
        }
      }
    }
    return result;
  }

  /**
   * Returns the contents of the given file, as UTF-8 text.
   *
   * @param file a file to read
   * @return the contents of the given file
   * @throws IOException if the file cannot be read
   */
  private static String readText(Path file) throws IOException {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  /**
   * Returns the contents of the given goal file, normalized, or the empty string if the goal file
   * does not exist.
   *
   * @param file a goal file to read
   * @return the contents of the given goal file, or the empty string
   * @throws IOException if the file cannot be read
   */
  private static String goalText(Path file) throws IOException {
    return Files.isRegularFile(file) ? normalize(readText(file)) : "";
  }

  /**
   * Returns the given text with Windows line separators replaced by Unix ones, so that a goal file
   * matches program output on any platform. (The program terminates each line it writes with the
   * platform's line separator.)
   *
   * @param text the text to normalize
   * @return the given text, with Windows line separators replaced by Unix ones
   */
  private static String normalize(String text) {
    return text.replace("\r\n", "\n");
  }

  /**
   * Returns the given standard error text, normalized and without the lines that the harness
   * ignores.
   *
   * @param text what the program wrote to standard error
   * @return the given text, normalized and without the lines that the harness ignores
   */
  private static String normalizeStderr(String text) {
    return normalize(ignoredStderrLine.matcher(text).replaceAll(""));
  }

  /**
   * Copies a directory tree.
   *
   * @param from a directory to copy
   * @param to the directory to copy it to; it and its parents are created if necessary
   * @throws IOException if a file cannot be copied
   */
  private static void copyTree(Path from, Path to) throws IOException {
    Files.createDirectories(to);
    for (Map.Entry<String, Path> file : relativeFileNames(from).entrySet()) {
      Path target = to.resolve(file.getKey().replace('/', File.separatorChar));
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.copy(file.getValue(), target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
