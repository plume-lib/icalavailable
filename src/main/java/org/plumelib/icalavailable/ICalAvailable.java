package org.plumelib.icalavailable;

import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VFreeBusy;
import net.fortuna.ical4j.model.parameter.FbType;
import net.fortuna.ical4j.model.property.FreeBusy;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.checker.regex.qual.Regex;
import org.checkerframework.dataflow.qual.Pure;
import org.plumelib.options.Option;
import org.plumelib.options.Options;

// TODO:  Fix "Problem:  any all-day events will be treated as UTC." (see below)

/**
 * Given one or more calendars in <a href="https://en.wikipedia.org/wiki/ICalendar">iCalendar
 * format</a>, produces a textual summary of available times. This is useful for sending someone a
 * list of acceptable times for a meeting. Also see the {@code ical-available} Emacs function, which
 * inserts the output of this program.
 *
 * <p>The command-line options are as follows:
 * <!-- start options doc (DO NOT EDIT BY HAND) -->
 *
 * <ul>
 *   <li id="option:date"><b>--date=</b><i>string</i>. First date to summarize. [default: today]
 *   <li id="option:days"><b>--days=</b><i>int</i>. Number of calendar days to summarize. [default:
 *       8]
 *   <li id="option:iCal-URL"><b>--iCal-URL=</b><i>url</i> {@code [+]}. For a Google calendar: go to
 *       settings, then click on the green "ICAL" icon for the "private address".
 *   <li id="option:business-hours"><b>--business-hours=</b><i>string</i>. A list of time ranges,
 *       expressed as a String. Example: 9am-5pm,7:30pm-9:30pm [default: 9am-5pm]
 *   <li id="option:timezone1"><b>--timezone1=</b><i>timezone</i>. Time zone as an Olson timezone
 *       ID, e.g.: America/New_York. Available times are printed in this time zone. It defaults to
 *       the system time zone.
 *   <li id="option:timezone2"><b>--timezone2=</b><i>timezone</i>. Time zone as an Olson timezone
 *       ID, e.g.: America/New_York. If set, then free times are printed in two time zones.
 *   <li id="option:debug"><b>--debug=</b><i>boolean</i>. If true, enable debugging output.
 *       [default: false]
 * </ul>
 *
 * {@code [+]} means option can be specified multiple times
 * <!-- end options doc -->
 * If you are perplexed because of odd results, maybe it is because of the transparency of your iCal
 * items (this shows up as "available/busy" in Google calendar).
 */
@SuppressWarnings("PMD") // todo
public final class ICalAvailable {

  /** This class is a collection of methods; it does not represent anything. */
  private ICalAvailable() {
    throw new Error("do not instantiate");
  }

  // User options

  /** First date to summarize. */
  @Option("first date to summarize")
  public static String date = "today";

  /** First date to summarize, parsed. */
  public static DateTime start_date = new DateTime();

  /** Number of calendar days to summarize. */
  @Option("number of calendar days to summarize")
  public static int days = 8;

  /**
   * For a Google calendar: go to settings, then click on the green "ICAL" icon for the "private
   * address".
   */
  @Option("<url> schedule in iCal format")
  public static List<String> iCal_URL = new ArrayList<>();

  /** A list of time ranges, expressed as a String. Example: 9am-5pm,7:30pm-9:30pm. */
  @Option("time ranges during which appointments are permitted")
  public static String business_hours = "9am-5pm";

  /** The business hours, outside of which all times are unavailable. */
  static List<Period> businessHours = new ArrayList<>();

  /** The business days, outside of which all times are unavailable. */
  static List<Integer> businessDays = new ArrayList<>();

  // initialize business days to Mon-Fri
  static {
    businessDays.add(1);
    businessDays.add(2);
    businessDays.add(3);
    businessDays.add(4);
    businessDays.add(5);
  }

  /** The time zone registry, for looking up time zone names. */
  static TimeZoneRegistry tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry();

  /**
   * Time zone as an Olson timezone ID, e.g.: America/New_York. Available times are printed in this
   * time zone. It defaults to the system time zone.
   */
  // don't need "e.g.: America/New_York" in message:  the default is an example
  @Option(value = "<timezone> time zone, e.g.: America/New_York", noDocDefault = true)
  public static String timezone1 = TimeZone.getDefault().getID();

  // Either of these initializations causes a NullPointerException
  // at net.fortuna.ical4j.model.TimeZone.<init>(TimeZone.java:67)
  // static TimeZone tz1 = new TimeZone(new VTimeZone());
  // static TimeZone tz1 = tzRegistry.getTimeZone(canonicalizeTimezone(timezone1));
  /** The TimeZone represented by string {@link #timezone1}. */
  static @MonotonicNonNull TimeZone tz1;

  // If I'm outputting in a different timezone, then my notion of a "day"
  // may be different than the other timezone's notion of a "day".  This
  // doesn't seem important enough to fix right now.
  /**
   * Time zone as an Olson timezone ID, e.g.: America/New_York. If set, then free times are printed
   * in two time zones.
   */
  @Option("<timezone> optional second time zone, e.g.: America/New_York")
  public static @Nullable String timezone2;

  /** The TimeZone represented by string {@link #timezone2}. */
  static @Nullable TimeZone tz2;

  // Other variables

  /** If true, enable debugging output. */
  @Option("enable debugging output")
  public static boolean debug = false;

  /** The appointments (the times that are unavailable for a meeting). */
  static List<Calendar> calendars = new ArrayList<>();

  /** The time format. */
  static DateFormat tf = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US);

  /** The date format. */
  static DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US);

  /** The day-of-week format. */
  static DateFormat dfDayOfWeek = new SimpleDateFormat("EEE");

  // Procedures

  /**
   * Reads command-line options and sets fields.
   *
   * @param args the command-line options
   */
  @SuppressWarnings({
    "deprecation", // for iCal4j's use of Date.{get,set}Minutes
  })
  @EnsuresNonNull("tz1")
  static void processOptions(String[] args) {
    Options options = new Options("ICalAvailable [options]", ICalAvailable.class);
    String[] remaining_args = options.parse(true, args);
    if (remaining_args.length != 0) {
      System.err.println("Unrecognized arguments: " + Arrays.toString(remaining_args));
      System.exit(1);
    }
    if (iCal_URL.isEmpty()) {
      System.err.println("Option iCal_URL must be specified.");
      System.exit(1);
    }

    // Convert Strings to TimeZones
    tz1 = tzRegistry.getTimeZone(canonicalizeTimezone(timezone1));
    if (tz1 == null) {
      System.err.println(
          "Unrecognized time zone (see http://php.net/manual/en/timezones.php): " + timezone1);
      System.exit(1);
    }
    if (timezone2 != null) {
      tz2 = tzRegistry.getTimeZone(canonicalizeTimezone(timezone2));
      if (tz2 == null) {
        System.err.println(
            "Unrecognized time zone (see http://php.net/manual/en/timezones.php): " + timezone2);
        System.exit(1);
      }
    }

    try {
      if (!date.equals("today")) {
        start_date = new DateTime(parseDate(date));
      }
    } catch (Exception e) {
      if (Pattern.matches(".*/.*", date) && !Pattern.matches(".*/.*/", date)) {
        System.err.println("Could not parse date (missing year?): " + date);
        System.exit(1);
      } else {
        System.err.println("Could not parse date: " + date);
        System.exit(1);
      }
    }
    if (start_date == null) {
      System.err.println("Could not parse date: " + date);
      System.exit(1);
    }

    // Problem:  this may change the actual time?
    start_date.setTimeZone(tz1);
    start_date.setMinutes((start_date.getMinutes() / 15) * 15);

    for (String URL : iCal_URL) {
      try {
        URL url = new URL(URL);
        CalendarBuilder builder = new CalendarBuilder();
        Calendar c;
        try (InputStream urlStream = url.openStream()) {
          try {
            c = builder.build(urlStream);
          } catch (ParserException pe) {
            if ("Error at line 1: Expected [BEGIN], read [<HTML>]".equals(pe.getMessage())) {
              System.out.println();
              System.out.println("It is possible that the calendar has moved.");
              // Debugging: write the URL contents to standard output
              URL url2 = new URL(URL);
              try (InputStream url_is = url2.openStream()) {
                System.out.printf("URL: %s%n", url2);
                System.out.println("Contents:");
                byte[] buffer = new byte[1024];
                int len = url_is.read(buffer);
                while (len != -1) {
                  System.out.write(buffer, 0, len);
                  len = url_is.read(buffer);
                }
                System.out.println();
              }
            }
            throw pe;
          }
        }
        calendars.add(c);
      } catch (Exception e) {
        e.printStackTrace(System.err);
        System.err.println("Could not read calendar from " + URL);
        System.exit(1);
      }
    }

    for (String range : business_hours.split(",", -1)) {
      String[] startEnd = range.split("-", -1);
      if (startEnd.length != 2) {
        System.err.println("Bad time range: " + range);
        System.exit(1);
      }
      DateTime busStart = parseTime(startEnd[0]);
      DateTime busEnd = parseTime(startEnd[1]);
      businessHours.add(new Period(busStart, busEnd));
    }
  }

  /** Maps a short name to a canonical name, for commonly-used time zones. */
  static Map<String, String> canonicalTimezones = new HashMap<>();

  /** Maps a long time zone name to a shorter one. */
  static Map<String, String> printedTimezones = new HashMap<>();

  // Yuck, this should really be a separate configuration file.
  static {
    canonicalTimezones.put("eastern", "America/New_York");
    canonicalTimezones.put("est", "America/New_York");
    canonicalTimezones.put("edt", "America/New_York");
    canonicalTimezones.put("boston", "America/New_York");
    canonicalTimezones.put("america/boston", "America/New_York");
    canonicalTimezones.put("central", "America/Chicago");
    canonicalTimezones.put("mountain", "America/Denver");
    canonicalTimezones.put("arizona", "America/Phoenix");
    canonicalTimezones.put("pacific", "America/Los_Angeles");
    canonicalTimezones.put("pst", "America/Los_Angeles");
    canonicalTimezones.put("pacific standard time", "America/Los_Angeles");
    canonicalTimezones.put("pdt", "America/Los_Angeles");
    canonicalTimezones.put("india", "Asia/Calcutta");
    canonicalTimezones.put("china", "Asia/Shanghai");
    canonicalTimezones.put("berlin", "Europe/Berlin");
    canonicalTimezones.put("israel", "Asia/Tel_Aviv");
    canonicalTimezones.put("art", "America/Buenos_Aires");

    printedTimezones.put("Eastern Standard Time", "Eastern");
    printedTimezones.put("Central Standard Time", "Central");
    // Don't do this due to Arizona wierdness; we want to know MST vs. MDT
    // printedTimezones.put("Mountain Standard Time", "Mountain");
    printedTimezones.put("Pacific Standard Time", "Pacific");
  }

  /**
   * Converts a time zone abbreviation to a canonical form, if possible.
   *
   * @param timezone a time zone abbreviation to canonicalize
   * @return either the argument, or its canonical name if possible
   */
  static String canonicalizeTimezone(String timezone) {
    return canonicalTimezones.getOrDefault(timezone.toLowerCase(Locale.getDefault()), timezone);
  }

  /**
   * Converts a time zone's printed representation to a shorter one, if possible.
   *
   * @param tz a printed representation to canonicalize
   * @return either the argument, or a shorter representation if possible
   */
  @Pure
  static String printedTimezone(TimeZone tz) {
    String tzString = tz.getDisplayName();
    return printedTimezones.getOrDefault(tzString, tzString);
  }

  /** Matches a printed representation of a time. */
  static @Regex(4) Pattern timeRegexp =
      Pattern.compile("([0-2]?[0-9])(:([0-5][0-9]))?([aApP][mM])?");

  /**
   * Parse a time like "9:30pm". The result's time zone is {@code tz1}.
   *
   * @param time the string to parse as a time
   * @return the time represented by {@code time}
   */
  @SuppressWarnings("deprecation") // for iCal4j
  @RequiresNonNull("tz1")
  static DateTime parseTime(String time) {

    Matcher m = timeRegexp.matcher(time);
    if (!m.matches()) {
      System.err.println("Bad time: " + time);
      System.exit(1);
    }
    @SuppressWarnings("nullness") // for this regex, matches() guarantees that group 1 matched
    @NonNull String hourString = m.group(1);
    String minuteString = m.group(3);
    String ampmString = m.group(4);

    int hour = Integer.parseInt(hourString);
    if ((ampmString != null) && ampmString.toLowerCase(Locale.getDefault()).equals("pm")) {
      hour += 12;
    }
    int minute = 0;
    if (minuteString != null) {
      minute = Integer.parseInt(minuteString);
    }

    DateTime result = new DateTime();
    result.setTimeZone(tz1);
    result.setHours(hour);
    result.setMinutes(minute);
    result.setSeconds(0);

    return result;
  }

  /** Dump the options. For debugging. */
  static void printOptions() {
    System.out.println("business_hours: " + business_hours);
    System.out.println("businessHours: " + businessHours);
    System.out.println("businessDays: " + businessDays);
    System.out.println("timezone1: " + timezone1);
    System.out.println("timezone2: " + timezone2);
    System.out.println("start_date: " + start_date);
    System.out.println("days: " + days);
    System.out.println("iCal_URL: " + iCal_URL);
  }

  /**
   * Main entry point; see class documentation.
   *
   * @param args command-line arguments; see class documentation
   */
  public static void main(String[] args) {

    processOptions(args);

    if (debug) {
      System.err.printf("Testing %d days%n", days);
    }

    List<Period> available = new ArrayList<>();
    for (int i = 0; i < days; i++) {
      available.addAll(oneDayAvailable(start_date, calendars));
      start_date = new DateTime(start_date.getTime() + 1000 * 60 * 60 * 24);
      start_date.setTimeZone(tz1);
    }

    if (tz2 != null) {
      System.out.printf(
          "Timezone: %s  [Timezone: %s]%n", printedTimezone(tz1), printedTimezone(tz2));
    }
    String lastDateString = null;
    for (Period p : available) {
      String dateString = formatDate(p.getStart(), tz1);
      if (!dateString.equals(lastDateString)) {
        lastDateString = dateString;
        System.out.println();
        System.out.println(dateString + ":");
      }

      String rangeString = rangeString(p, tz1);

      if (tz2 == null) {
        System.out.println(rangeString);
      } else {
        String rangeString2 = rangeString(p, tz2);
        System.out.printf("%-20s[%s]%n", rangeString, rangeString2);
      }
    }
  }

  /**
   * Formats a period as a range of times.
   *
   * @param p the period to format
   * @param tz the time zone
   * @return a range of times, from the beginning to the end of {@code p}
   */
  static String rangeString(Period p, TimeZone tz) {
    tf.setTimeZone(tz);
    DateTime pstart = p.getStart();
    DateTime pend = p.getEnd();
    String rangeString = tf.format(pstart) + " to " + tf.format(pend);
    rangeString = rangeString.replace(" AM", "am");
    rangeString = rangeString.replace(" PM", "pm");
    return rangeString;
  }

  /**
   * Formats a period as multiple time ranges.
   *
   * @param pl the periods
   * @param tz the time zone
   * @return a string representing multiple ranges of times
   */
  static String periodListString(PeriodList pl, TimeZone tz) {
    tf.setTimeZone(tz);
    StringBuilder result = new StringBuilder();
    // "Object" because PeriodList extends raw TreeSet, but it really ought to
    // extend TreeSet<@NonNull Period>
    for (Object p : pl) {
      assert p != null
          : "@AssumeAssertion(nullness): non-generic container class; elements are non-null";
      result.append(rangeString((Period) p, tz));
      result.append('\n');
    }
    return result.toString();
  }

  /**
   * Creates a new DateTime with date taken from the first argument and time taken from the second
   * argument.
   *
   * @param date supplies the date for the result
   * @param time supplies the time for the result
   * @return the merged DateTime
   */
  @SuppressWarnings("deprecation") // for iCal4j
  static DateTime mergeDateAndTime(DateTime date, DateTime time) {
    if (!date.getTimeZone().equals(time.getTimeZone())) {
      throw new Error(
          String.format(
              "non-matching timezones: %s %s",
              date.getTimeZone().getDisplayName(), time.getTimeZone().getDisplayName()));
    }
    DateTime result = new DateTime(date);
    result.setHours(time.getHours());
    result.setMinutes(time.getMinutes());
    result.setSeconds(time.getSeconds());
    return result;
  }

  // TODO:  don't propose times that are before the current moment.

  /**
   * Returns a all the times that are available on a single day.
   *
   * @param day the day on which to look for availability
   * @param calendars the calendars that might contain conflicts
   * @return the available times on the given day
   */
  // Process day-by-day because otherwise weekends and evenings are included.
  @SuppressWarnings("unchecked") // for iCal4j
  @RequiresNonNull("tz1")
  static List<Period> oneDayAvailable(DateTime day, List<Calendar> calendars) {
    if (debug) {
      System.err.printf("oneDayAvailable(%s, ...)%n", day);
    }
    List<Period> result = new ArrayList<>();
    @SuppressWarnings("deprecation") // for iCal4j
    int dayOfWeek = day.getDay();
    if (!businessDays.contains(dayOfWeek)) {
      return result;
    }

    for (Period bh : businessHours) {
      DateTime start = mergeDateAndTime(day, bh.getStart());
      DateTime end = mergeDateAndTime(day, bh.getEnd());

      VFreeBusy request = new VFreeBusy(start, end, new Dur(0, 0, 0, 1));
      if (debug) {
        System.out.println("Request = " + request);
      }
      ComponentList<CalendarComponent> busyTimes = new ComponentList<>();
      // Problem:  any all-day events will be treated as UTC.
      // Instead, they should be converted to local time (tz1).
      // But VFreeBusy does not support this, so I may need to convert
      // daily events into a different format before inserting them.
      for (Calendar calendar : calendars) {
        // getComponents() returns a raw ArrayList.  Expose its element type.
        ArrayList<@NonNull CalendarComponent> clist = calendar.getComponents();
        for (CalendarComponent c : clist) {
          /* TODO
          if (c instanceof VEvent) {
            VEvent v = (VEvent) c;
            DtStart dts = v.getStartDate();
            Parameter dtsValue = dts.getParameter("VALUE");
            boolean allDay = (dtsValue != null) && dtsValue.getValue().equals("DATE");
            // TODO: convert to the proper timezone.
            // Tricky: must deal with the possibility of RRULE:FREQ=
          }
          */
          busyTimes.add(c);
        }
      }
      VFreeBusy response = new VFreeBusy(request, busyTimes);
      if (debug) {
        System.out.println("Response = " + response);
      }
      FreeBusy freefb = (FreeBusy) response.getProperty("FREEBUSY");
      if (freefb == null) {
        if (debug) {
          System.out.println("FREEBUSY property is null");
        }
        continue;
      }
      @SuppressWarnings("interning") // interned fields from a library, but not annotated so
      boolean isFree = (freefb.getParameter(Parameter.FBTYPE) == FbType.FREE);
      assert isFree;
      PeriodList freePeriods = freefb.getPeriods();
      if (debug) {
        System.out.printf("Free periods: %n%s%n", periodListString(freePeriods, tz1));
      }
      result.addAll(freePeriods);
    }
    if (debug) {
      System.err.printf("oneDayAvailable(%s, ...) => %s elements%n", day, result.size());
    }
    return result;
  }

  /** The date formats supported by {@link #parseDate}. */
  static SimpleDateFormat[] dateFormats = {
    new SimpleDateFormat("yyyy/MM/dd"),
    new SimpleDateFormat("MM/dd/yyyy"),
    new SimpleDateFormat("MM/dd/yy"),
    // Bad idea:  sets year to 1970.  So require the year, at least for now.
    // new SimpleDateFormat("MM/dd"),
  };

  /**
   * Parses a date when formatted in several common formats.
   *
   * @param strDate the string to parse as a date
   * @return a Date read from the given string
   * @throws ParseException when the argument does not represent a date, in one of the supported
   *     formats
   * @see dateFormats
   */
  static java.util.Date parseDate(String strDate) throws ParseException {
    if (Pattern.matches("^[0-9][0-9]?/[0-9][0-9]?$", date)) {
      @SuppressWarnings("deprecation") // for iCal4j
      int year = new Date().getYear() + 1900;
      strDate = strDate + "/" + year;
    }
    for (DateFormat this_df : dateFormats) {
      this_df.setLenient(false);
      try {
        java.util.Date result = this_df.parse(strDate);
        return result;
      } catch (ParseException e) {
        // Try the next format in the list.
      }
    }
    throw new ParseException("bad date " + strDate, 0);
  }

  /**
   * Format a date.
   *
   * @param d the date
   * @param tz the time zone
   * @return a formatted representation of the date
   */
  static String formatDate(DateTime d, TimeZone tz) {
    df.setTimeZone(tz);
    String result = df.format(d);
    // Don't remove trailing year; it's a good double-check.
    // Prepend day of week.
    result = dfDayOfWeek.format(d) + " " + result;
    return result;
  }
}
