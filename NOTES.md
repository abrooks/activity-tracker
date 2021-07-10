# Design notes

The aim of this tool is to be able to robustly capture events from multiple
sources and merge them into a single, queryable database.

The robustness should provide useful, consistent data while running and clearly
indicate when the tool was running, without losing data. For example we are
wanting to avoid cases where the tracker crashes or the machine is suspended or
rebooted and data is either lost or misrepresents (or leaves ambiguous)
durations that overlap the unavailability.

Capturing an indefinitely long stream of events needs to be scalable across rate
of events, event size and time.

# Daily files

To support this robustness, daily log files are written, append only with
updates to ongoing events emitted at regular intervals. A crash or outage is
detectable by the lack of events at that time and events that were continuing up
to the time of the crash will be updated with the latest values before the
crash.

For consistency sake, daily files are always demarcated on UTC days and
continuing events are written (with identical event IDs) across both days, to
the degree that they extend into both days.

## File types

There are three types of files employed:
 - daily, append-only log files
 - historic, compacted event files
 - annotation files

A day in-progress will have a daily file. Any days previous to "today" will have
a historic file. Any days that have been annotated will have an annotation file.

## File rewriting

Files with a long series of periodic, append-only updates will be very redundant
and overly large. At the end of each day, a file is read into a database (which
will upsert event updates, deduplicating the redundancy) and is written out to a
new file. Because the tracking program may crash or may not be running during
the day transition, any historic, un-compacted files will be compacted at startup.

## File naming

Daily files are differentiated from historic files and annotation files. Because
there is typically at least one file per day (two in the case where there is an
annotation file for that day), a typical year will have between 365 and 730
files (leap years will have one or two more). To accomodate less scalable
filesystems, files will not be written to a single directory but to a directory
hierarchy.

There are two obvious approaches to this hierarchy — year directories with the
above mentioned ~365-730 day files per year (YYYY/YYYY-MM-DD...) or month
directories with only the files for that month. The first approach is likely to
have very few top-level directories each containing hundreds of daily files. The
second approach is more balanced having 12 directories created every year (120
directories for 10 years) and only 28-62 daily files per directory.

The more balanced approach generally seems more flexible and manageable with the
main drawback that query windows across multiple UTC days (which is almost
assured when looking at a single day, local time) is likely to span not only
files but also directories. Manually, this may be inconvenient but in the
context of our tool, it seems easily accommodated. For this reason, we pick the
format:

    ./YYYY-MM/YYYY-MM-DD...

This file naming scheme is consistent with ISO 8601 date formatting.

## File format

Events and event updates are written in a single line which is easily flushed
for consistency (up to a given buffer size). We would like to use a format that
can be accessed by a broad range of tools. JSON is a universal format but many
readers expect a single entity per file; either an object `{...}` or a list
`[...]` which means that a file is not complete until the trailing punctuation
is written. Because we want to write consistent, robust files and are also,
for that reason, keeping these files, append only, JSON is not a good option.

YAML, when written per-line, is, effectively JSON syntax but, it does allow list
elements to be indicated by a leading `-`. YAML also allows for tagged data
types which allows for more than just string data and accepts compound values as
keys in objects. Unfortunately, tagged reader support is very poor in most
available YAML readers.

EDN is a format that is both simpler in specification than YAML and is richer in
representation. EDN has no top-level element and is also suitable for streaming
applications. Given that this implementation is a Clojure implementation, it is
very convenient to read and write EDN data. While originating in the Clojure
ecosystem, EDN has readers across a broad range of languages.

I've chosen to not consider binary formats. While more compact, they can be
harder to manipulate and are opaque to general purpose tools.

I've also chosen, for now, to not consider compressed files. There are formats
that support streaming compression but these tend to buffer in memory, defeating
our desire to flush the most current data to the filesystem. The space savings
seem likely to be of little practical interest.

For the above reason, daily, historical and annotation files are written in EDN.

## File rotation

Either at startup or at the completion of a UTC day, any open / active files are
closed and any files to be compacted are opened read-only into a clean database.
The upserted elements are queried and written in order of least-to-most recent
start time to a new historical file. This assures that compacted historical
files should be consistently written given the same daily file.

When both a historic and a daily file exist, an initial, simple approach will
read in the daily file and overwrite the historic file. A safer, more advanced
approach can read in both files and difference them, writing a new historic
file, if needed.


# Time and Timezones

All times will be recorded in UTC time. When applicable, each source identifies
its own, local timezone and emits that with its events identified by a unique
start time in millisecond precision in `java.time.Instant` type in memory and
the corresponding EDN #inst format on disk.

To assure uniqueness among simultaneous events in a single system, time based
IDs are checked and, potentially minimally advanced, to a unique millisecond time
to avoid collision.

The recording tracker instance is also identified by its start time in similar
format.

The tracker assumes that there will be no jumps (forward or backward) once the
tracker in started. Handling these jumps is more complicated and difficult to
implement than declaring by fiat that we don't handle them. On most systems,
jumps to time only happen during OS install or early boot which are times that
we don't expect to be running. Any sane NTP clients will only sanely drift the
time incrementally to slowly converge with NTP time.

# Events

Events are intervals of time with a start and an end. An instantaneous event has
identical start and end times. Events can run across an unlimited number of
days but are unique to a running activity tracker instance. Stopping and
starting an activity tracker for whatever reason (crash, upgrade, system reboot,
etc.).

Because multiple instances may, exclusively in sequence, share the same daily file, we
need to be sure that events are identified uniquely across instances (this assures
that unrelated events are never upserted across tracker instances). We'll rely
on UUIDs for identifying unique events.

## Event fields

A every event must have the required fields:

- :evt/id - #uuid
- :evt/start - #inst
- :evt/src - com.foo.bar (max 256)
- :evt/type - source specific (max 256)

Events may also have the following fields:
- :evt/end - #inst
- :evt/name - string (may be truncated to make full payload 1024)
- :evt/truncate - error string indicating truncation to max event size (see below)
- :evt/error - error message
- <source specific events namespaced by event source>


In the database, upsert is done on the single event ID field.

Events are written transacted to the database and written to the daily file as a
series of maps with the above keys.

The activity tracker emits its own event stream indicating its start and
updating its end time periodically as it runs (on the order of every few
seconds). Only the activity tracker is expected to do this. Any sources wishing
to have ongoing events updated on shutdown, close of UTC day or in recovery from
a crashed activity tracker should emit events without event end times.

## Event max size

To keep event writes atomic, writes are less than 4096 bytes (standard PIPE_BUF
setting on Linux). Events that are deemed over-sized are stripped down to the
system defined of source specific fields and are annotated with an :evt/truncate
message indicating how many fields, the total oversized bytes and the byte count
of the largest field. If needed, the event name is trimmed to make the total
write fit within 4096 bytes.

## Event management

Events are routed through a fan-in / fan-out set of core.async channels (mix in,
mult out) in this structure:

```
src01  src02 ... srcNN
  |      |         |
  v      v         v
  +------+---------+
         |
         v
  +------+---------+
  |      |         |
  v      v         v
dst01  dst02 ... dstNN
```

The input sources at the top are processes (threads or go blocks) as are the
destinations at the bottom.

## Input sources

Input sources are pluggable sources that will create a stream of events.

## Input pipelines

## Event filtering

Input pipelines should rate limit their events. Sources like window titles, can
change very rapidly and create very noisy, irrelevant streams. Each input
pipeline should descide what information is relevant.

# Analysis across multiple systems
Multiple systems running the activity tracker locally is not a problem, however,
if we wish to collect the outputs of these systems together in a single place
for analysis, we need to make choices. The chose directory and file name format
does not include any element that would identify system source. Copying or
syncing from one system's file structure would clobber files if copied to the
same file structure.

While we could attempt to handle this through non-colliding file naming
conventions, we'll go a simpler route and require that any use case wanting to do
this keep files in separate file structures. If it becomes useful, we can build
query / analysis tools that know how to pull in source data from various
directories and annotated the incoming events with source information as needed.

# Event sources (notes)
- desktop window focus
- screen saver
- phone log
- calendar
- chat log
- emails sent
- network / wifi location
- geo / geoip location
- system timezone
- local user timezone
- dock / device presence
- stand / sit status
- music
- environment (temp / lux / sound / motion)
