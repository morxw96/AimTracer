# Troubleshooting

[Deutsch](TROUBLESHOOTING.md)

## `Shot transfer was incomplete`

Firmware 0.1 transferred the roughly 312 packets of a standard shot as
unconfirmed BLE notifications. On some phone/connection combinations, one or
more packets could be lost even though the final packet arrived. The app then
discarded the shot intentionally because metrics derived from an incomplete
time window would not be reliable.

Firmware 0.2 changed the Shot characteristic to confirmed ATT indications.
Each new packet is sent only after confirmation from the phone. The iPhone app
also assembles the growing shot without repeatedly copying dictionaries.

Firmware 0.4 retained the 416 Hz trigger detector but reduced stored shot
windows to 104 Hz. A standard window therefore contains about 78 instead of
312 sample packets, reducing the transfer delay without changing protocol
version 1.

### Upgrade

1. Flash the firmware from `firmware/AimTracerFirmware` onto the XIAO again.
2. Build and install the current iPhone or Android app.
3. Completely restart both the XIAO and the app.
4. Reconnect and verify `Firmware 0.6` in the lower part of the Live screen.
5. Start a dry-fire session and first issue a manual test trigger.
6. Wait until `Transfer` returns to `Ready`.
7. Open the session. With firmware 0.4 or later, the test shot should contain
   about 78 samples; firmware 0.2 and 0.3 used about 312.

Confirmed packets are intentionally flow-controlled. With current firmware, a
standard shot normally appears after the 250 ms post-trigger window plus
roughly one to two seconds of BLE transfer, depending on the connection
interval. Do not end the session during transfer, and wait for `Ready` before
the next test.

If the message occurs again with firmware 0.6, it reports the received and
expected sample counts and the first missing index. Record those three values
together with the displayed firmware version.

Sessions already stored with zero shots cannot be repaired afterward. The
incomplete raw data was deliberately not saved. Empty sessions can be
deleted.
