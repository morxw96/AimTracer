# Session Analysis and Export

[Deutsch](ANALYSIS_AND_EXPORT.md)

AimTracer 0.7 analyzes the shot windows stored in a session. The metrics are
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

All metrics are root mean square values of gyroscope angular velocity relevant
to movement of the sight line, in degrees per second:

```text
RMS = sqrt(mean(gy² + gz²))
```

Lower values indicate less angular movement in the evaluated time window.
`gy` represents pitch and `gz` yaw. Roll (`gx`) remains in the complete raw
export but is not rated as a hold or trigger error.

### Hold stability

The hold window ends approximately 180 ms before the trigger. It describes
general pistol stability before the immediate release phase.

### Trigger behavior

The trigger window extends from about 150 ms before the trigger through the
trigger sample. The actual shot impulse is not attributed to trigger control.

### Follow-through

The follow-through window extends from 50 to 250 ms after the trigger. The
first 50 ms are excluded as the pneumatic impulse.

Exact boundaries are derived from the actual IMU sample rate. The windows are
defined in `MotionAnalysis.swift` (iOS) and `MotionAnalysis.kt` (Android) and
can later be adjusted based on practical measurements.

## Personal technique index

The first three completed sessions containing at least three shots form a fixed personal
baseline for each program. AimTracer takes the median of all their shots for
each metric. Until three baseline sessions exist, the app shows “Learning
phase”; without a reference, `50` is the neutral starting value.

Each shot component is mapped around the personal median onto a 0–100 scale:

```text
component = 100 / (1 + (measurement / personal median)³)
technique index = 0.30 × hold + 0.50 × trigger + 0.20 × follow-through
```

50 matches the personal reference. Above 50 means less movement and below 50
means more movement. Unlike the former within-session comparison index, the
technique index can therefore be compared across sessions of the same program.
Weights and exponent are marked `CALIBRATION:` in the code.

False triggers should be swiped left and deleted from the shot list;
otherwise they distort averages and rankings. This removes only the locally
stored shot.

## Trends and long-term comparison

Starting at eight shots, AimTracer compares the first group with the last
group. Each group contains at most ten shots. A positive percentage means
that the mean RMS value was lower in the final group.

The long-term comparison still evaluates the active session against the mean
of up to five earlier sessions of the same program. A bar chart additionally
shows the technique index of the last twelve sessions on the fixed 0–100 scale,
with a reference line at 50.

## Excel export

The table export is a UTF-8 CSV with a byte-order mark, semicolon separators,
and German decimal commas. It therefore opens directly in a German Excel
installation.

It contains:

- session metadata, program, and optional Meyton total,
- statistics for each metric,
- comparison with earlier sessions,
- one row per shot with rank, technique index, and raw peaks,
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
  `xiao-sense-component-side-down-usb-toward-shooter-v3`.

Raw values remain in sensor coordinates. Metadata records the effective
display mapping—by default `roll = gx`, `right = +gz`, and `up = +gy`—plus
the X/Y inversion state. Future scoring formulas can therefore be
recalculated without changing the measured data.

Files are generated locally on the phone. iOS uses the share sheet; Android
uses the system document picker. AimTracer performs no cloud transfer.
