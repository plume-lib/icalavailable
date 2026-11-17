This directory holds the data for the end-to-end tests of the ICalAvailable program.  The tests
are independent of the implementation language:  each test case is a set of files that say how to
run the program and what it should produce.  The Java test harness
src/test/java/org/plumelib/icalavailable/ICalAvailableEndToEndTest.java runs them, but a harness in
any language can run them by following this file.

Each subdirectory of this directory is one test case; the name of the subdirectory is the name of
the test case.  To add a test case, add a subdirectory; no harness code needs to change.


Files of a test case
====================

A test case directory contains some of the following files and directories, all of them optional:

  description.txt      What the test case checks.  The test harness ignores this file.  A
                       description that starts with "BUG:" describes a way in which the program
                       behaves differently than it should; the goal files record what the program
                       currently does.

  input/               Files that the program reads, such as calendars in iCalendar (.ics)
                       format.  The harness copies this directory to "input/" in the working
                       directory.

  args.txt             The program's command-line arguments, one per line, in UTF-8.  Empty lines
                       are ignored.  Each line is one argument, exactly as written (no shell
                       quoting or expansion), even if it contains spaces.  If this file is absent
                       or empty, the program is run with no arguments.

  tz.txt               The time zone in which to run the program, as an IANA time zone ID such as
                       "America/New_York".  If this file is absent, the time zone is
                       "America/Los_Angeles".

  goal-out.txt         What the program should write to standard output.  If this file is absent,
                       the program should write nothing to standard output.

  goal-err.txt         What the program should write to standard error.  If none of goal-err.txt,
                       goal-err-prefix.txt, and goal-err-suffix.txt is present, the program should
                       write nothing to standard error.

  goal-err-prefix.txt  What the program's standard error should start with.
  goal-err-suffix.txt  What the program's standard error should end with.
                       A test case uses one or both of these files, instead of goal-err.txt, when
                       the rest of standard error is a stack trace or other detail that is specific
                       to one implementation.  A test case that has goal-err.txt has neither
                       of these files.

  goal-status.txt      The program's exit status, as a decimal integer.  If this file is absent,
                       the exit status should be 0.

Any other file in a test case directory is an error, probably a misspelled goal file name.


How to run a test case
======================

1. Create a fresh, empty working directory, and copy the test case's input/ directory, if any, to
   input/ in it.

2. Run the program in that working directory, with the arguments in args.txt, with nothing on
   standard input (the program does not read it), and with these environment variables set (in
   addition to those inherited from the harness):

     TZ=<the contents of tz.txt, without its line terminator, or America/Los_Angeles>
     LC_ALL=C.UTF-8

   The program interprets times in the TZ time zone, unless an argument says otherwise.  LC_ALL
   selects US English names of days, months, and time zones, and UTF-8 output.  The Java harness
   additionally passes the equivalent Java system properties (user.timezone, user.language=en,
   user.country=US, stdout.encoding=UTF-8, stderr.encoding=UTF-8) to the JVM, because some
   platforms' JVMs ignore TZ and LC_ALL.

   The arguments name calendars with "--iCal-URL=file:input/NAME.ics".  That is a relative "file:"
   URL, which refers to a file relative to the working directory.  No test needs network access.

   Every test case passes --date, because the default date is the current date, which would make
   the output change from day to day.

3. Compare, after normalization (see below):
   * the exit status to goal-status.txt (or 0),
   * standard output to goal-out.txt (or the empty string),
   * standard error to goal-err.txt (or the empty string), or check that it starts with
     goal-err-prefix.txt and ends with goal-err-suffix.txt, whichever of those two files exist.
   The test case fails if any comparison fails.

4. Check that the working directory contains no files other than the ones under input/.  (The
   program writes no files.)


Normalization
=============

All files and all output are UTF-8 text.  Before comparing, the harness:

 * replaces each CRLF line terminator by LF, in both the goal files and the program's output,
   because the program ends each line with the platform's line separator; and

 * removes from standard error every line that starts with "SLF4J: ".  The Java implementation's
   logging library writes such lines, which do not come from the program itself.

No other normalization is done; in particular, trailing whitespace and blank lines are significant.
Note that output times contain U+202F NARROW NO-BREAK SPACE before "AM" and "PM"; see test case
time-format-am-pm.


Running the tests
=================

To run the tests against this Java implementation:

  ./gradlew test

To run them against another implementation, give its command as Gradle property e2e.command:

  ./gradlew test -Pe2e.command="python3 /path/to/icalavailable.py"

The value is split at whitespace into the command and its leading arguments (so none of them can
contain a space); the test case's arguments follow them.  Use an absolute path, because the command
runs in the working directory described above.  The harness sets TZ and LC_ALL as described above,
but passes no Java system properties.


Editing these files
===================

Because these files are test data, their exact bytes matter: for example, some calendar files
intentionally use CRLF line terminators, and some goal files have trailing whitespace.  prek.toml
excludes this directory from the hooks that would "fix" such things.
