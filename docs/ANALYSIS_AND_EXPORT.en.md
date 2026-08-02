# Session Analysis and Export

[Deutsch](ANALYSIS_AND_EXPORT.md)

AimTracer 0.2 analyzes the shot windows stored in a session. The metrics are
intended for comparing your own movement sequence. They calculate neither the
aiming point nor a score.

## Programs

Five programs are available when starting a session:

- `LP20` with 20 planned shots,
- `LP40` with 40 planned shots,
- `LP60` with 60 planned shots,
- dry-fire training without a fixed shot count,
- free training without a fixed shot count.

Historical comparisons include only earlier sessions of the same program.
Legacy sessions from AimTracer 0.1 remain readable and are treated as free
training.

## Three metrics

All metrics are root mean square values of the three-dimensional gyroscope
angular velocity in degrees per second:

```text
RMS = sqrt(mean(gx² + gy² + gz²))
```

Lower values indicate less angular movement in the evaluated time window.
Because all three axes contribute to the magnitude, the fixed mounting-axis
transform does not change these RMS metrics. It does determine the visible
left/right and up/down direction of the trace.

### Hold stability

The hold window ends approximately 167 ms before the trigger. It describes
general pistol stability before the immediate release phase.

### Trigger behavior

The trigger window extends from about 150 ms before to 80 ms after the
trigger. It therefore responds to movement around the instant of release.

### Follow-through

The follow-through window covers the first 250 ms from the trigger.

Exact boundaries are derived from the actual IMU sample rate. The windows are
defined in `MotionAnalysis.swift` (iOS) and `MotionAnalysis.kt` (Android) and
can later be adjusted based on practical measurements.

## Ranking

For each metric, all shots in a session are sorted in ascending order:

- the lowest RMS value receives rank 1,
- identical values receive the same rank,
- each rank is converted into a relative value from 0 to 100.

The comparison index is currently the equally weighted mean of the three
relative values:

```text
comparison index = (hold + trigger + follow-through) / 3
```

The overall rank is then calculated from this index. The weighting is marked
`CALIBRATION:` in the code. Do not change the weighting until enough real
LP40 recordings and corresponding Meyton results are available.

The index is valid only within one session. A value of 90 in session A cannot
be compared directly with 90 in session B.

False triggers should be swiped left and deleted from the shot list;
otherwise they distort averages and rankings. This removes only the locally
stored shot.

## Trends and long-term comparison

Starting at eight shots, AimTracer compares the first group with the last
group. Each group contains at most ten shots. A positive percentage means
that the mean RMS value was lower in the final group.

The long-term comparison evaluates the active session against the mean of up
to five earlier sessions of the same program. Here too, a positive percentage
means less angular movement than in the comparison sessions.

## Excel export

The table export is a UTF-8 CSV with a byte-order mark, semicolon separators,
and German decimal commas. It therefore opens directly in a German Excel
installation.

It contains:

- session metadata, program, and optional Meyton total,
- statistics for each metric,
- comparison with earlier sessions,
- one row per shot with rank, comparison index, and raw peaks,
- an interpretation note.

## PDF export

The A4 report contains:

- session and program data,
- mean and best values,
- a colored progress chart for the three metrics,
- comparison with earlier sessions,
- the Meyton total entered when the session was stopped,
- a complete shot table with automatic page breaks.

An LP40 series occupies exactly one A4 table page. Longer sessions continue
on additional pages containing up to 40 shots each.

## Raw JSON export

The JSON export uses the `aimtracer-raw-session` format with `schemaVersion`
1. It contains:

- session data including the program and Meyton total,
- trigger time, sample rate, and peaks for every shot,
- every unmodified `gx/gy/gz`, `ax/ay/az`, and microphone sample,
- a derived sample time relative to the trigger in milliseconds,
- the fixed mounting profile
  `xiao-sense-component-side-down-usb-toward-shooter`.

Raw values remain in sensor coordinates. The metadata records the display
mapping as `roll = gy`, `right = -gz`, and `up = gx`. Future scoring formulas
can therefore be recalculated without changing the measured data.

Files are generated locally on the phone. iOS uses the share sheet; Android
uses the system document picker. AimTracer performs no cloud transfer.
