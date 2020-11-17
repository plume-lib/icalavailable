# ICalAvailable

Given one or more calendars in
[iCalendar format](http://en.wikipedia.org/wiki/ICalendar)
produces a textual summary of available times.
This is useful for sending someone a list of acceptable times for a meeting,
if you need to negotiate a meeting time via text rather than sharing a calendar.

Here is example output:

```
Tue Nov 17, 2020:
8:30am to 10:00am
12:00pm to 12:05pm
12:45pm to 4:00pm

Wed Nov 18, 2020:
8:30am to 11:30am
1:00pm to 3:00pm
3:45pm to 4:15pm

Thu Nov 19, 2020:
8:30am to 10:00am
10:45am to 11:00am
12:00pm to 12:05pm
12:45pm to 2:00pm
3:00pm to 5:00pm
```

You can provide [command-line arguments](http://plumelib.org/icalavailable/api/org/plumelib/icalavailable/ICalAvailable.html) to adjust the date range, indicate your working hours, provide times in multiple time zones, etc.  Here is an example of output for multiple time zones:

```
Timezone: Pacific  [Timezone: Eastern]

Tue Nov 17, 2020:
8:30am to 10:00am   [11:30am to 1:00pm]
12:00pm to 12:05pm  [3:00pm to 3:05pm]
12:45pm to 4:00pm   [3:45pm to 7:00pm]

Wed Nov 18, 2020:
8:30am to 11:30am   [11:30am to 2:30pm]
1:00pm to 3:00pm    [4:00pm to 6:00pm]
3:45pm to 4:15pm    [6:45pm to 7:15pm]
```

Also see the [ical-available Emacs
function](https://github.com/plume-lib/icalavailable/blob/master/src/main/elisp/ical-available.el),
which inserts the output of this program.

See [API documentation](http://plumelib.org/icalavailable/api/org/plumelib/icalavailable/ICalAvailable.html).
