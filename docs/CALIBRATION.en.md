# Field Calibration

[Deutsch](CALIBRATION.md)

The default values are intentionally conservative placeholders. Microphone
levels and structure-borne sound depend strongly on the enclosure, clamping
force, mounting orientation, and firearm. A supposedly finished threshold
without a measurement series on the mounted LP300XT would provide false
precision.

## 1. Mounting and axes

1. Attach the enclosure rigidly and reproducibly.
2. It must not flex on the cylinder or strike another part during a shot.
3. Connect in the app and wait for `Sensor ready`.
4. Slowly move the pistol left/right and up/down.
5. Verify that the trace moves in the expected direction.

AimTracer uses one fixed mounting profile: PCB underside up, sensor and
component side down, and USB-C facing the shooter. A practical test on the
mounted device identifies `gx` as roll around the barrel, `+gz` as right,
and `+gy` as up. Stored and exported raw values are not
modified.

Starting with app 0.7.2, the horizontal X and vertical Y graph axes can be
inverted independently in Settings. This supports alternate mounting
orientations and changes only the graph. RMS metrics, rankings, and the
technique index remain unaffected.

The three RMS metrics use pitch and yaw (`gy` and `gz`). Roll (`gx`) remains
in raw data but is excluded from the technique index, keeping the rating
focused on movement of the sight line.

## 2. Gyro zero

Select `Recalibrate gyro zero` and leave the pistol completely motionless for
about one second. The firmware averages 256 raw samples. Shot detection is
disarmed during calibration and automatically rearmed afterward if a session
is active.

Recalibrate:

- after every mechanical remount,
- after a significant temperature change,
- if the live trace visibly drifts while the firearm is motionless.

## 3. Record a trigger dataset

First use `Manual test trigger` to verify that complete shots arrive in the
app. Then record separate groups:

- at least 20 dry-fire trigger releases,
- at least 20 live air-pistol shots,
- at least 20 deliberately similar external noises at the range,
- several normal aiming and lowering movements.

Record the audio, accelerometer, and gyro peak for each group. The values are
visible in the shot-detail view.

## 4. Select thresholds

Starting values:

| Parameter | Starting value |
|---|---:|
| Microphone | 7000 |
| Accelerometer delta | 1500 |
| Gyro | 2000 |
| Coincidence | 40 ms |
| Refractory period | 1200 ms |

Recommended procedure:

1. Temporarily set the trigger to `Audio only` and adjust its threshold so
   that external noises are rejected whenever possible.
2. Switch to `Motion only` and tune accelerometer/gyro thresholds so normal
   hold movement does not trigger while the release is detected reliably.
3. Finally enable `Microphone + motion`.
4. Increase the coincidence window only if real shots are missed; an
   unnecessarily large window increases false triggers.
5. Change only one parameter at a time and repeat ten to twenty trials.

## 5. Evaluation

The app's three RMS values are measurements in degrees per second, not score
predictions. The AimTracer 0.7 technique index uses the first three sessions
of each program as a personal reference. Until then it runs in a learning
phase with 50 as the neutral starting point. Time windows, exponent, and
weights remain marked `CALIBRATION:` and should only change after more paired
range data.

The sensor knows neither the target nor the sight line. It can show relative
muzzle movement, the release impulse, and follow-through, but it cannot
determine the actual hit.
