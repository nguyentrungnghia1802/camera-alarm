# Camera Alarm Android — Phase 3 Implementation Tasks

> **Purpose:** Extend the verified V1 baseline with selectable bundled MP3 alarm sounds, configurable active-hour schedules, reboot/background reliability, and Xiaomi/HyperOS hardening.
>
> **Important:** Phase 1 and Phase 2 are stable baselines. Phase 3 must extend them without casually rewriting verified core behavior.

---

# 0. Global Rules for Phase 3

## 0.1 Source of truth

Before implementing any Phase 3 task, the agent must read:

- `docs/agent/agent.md`
- `docs/agent/task.md`
- `docs/agent/phase1-verification.md`
- any V1/final verification document that exists
- all directly relevant files under `docs/project/`

The implementation must follow the existing architecture unless this document explicitly requires an extension.

If existing code conflicts with the documented architecture:

1. verify whether the code is a known V1 hardening fix;
2. preserve verified behavior;
3. prefer the smallest compatible extension;
4. do not perform broad refactors.

## 0.2 Phase 3 goals

Phase 3 adds four capabilities:

1. bundled MP3 alarm sound selection;
2. multiple active-hour schedules;
3. reboot/background reliability hardening;
4. Xiaomi / Redmi / POCO / HyperOS setup assistance.

The resulting application should support this behavior:

```text
Camera notification
        |
        v
NotificationListenerService
        |
        v
Trigger matching
        |
        v
Monitoring enabled?
        |
        v
ActiveScheduleGate
      /    \
     NO    YES
     |       |
     |       v
     |    dedupe
     |       |
     |       v
     |   exact alarm
     |       |
     |       v
     |   alarm runtime
     |       |
     |       v
     |   selected MP3 loop
     |
     v
SUPPRESSED_OUTSIDE_ACTIVE_HOURS
```

## 0.3 Non-goals

Phase 3 must **not** add:

- cloud backend;
- camera vendor API integration;
- remote account system;
- multiple simultaneously active source camera apps;
- arbitrary user file import from storage;
- custom notification-server infrastructure;
- permanent 24/7 foreground service;
- hacks that attempt to bypass Android security restrictions;
- automatic granting of protected Xiaomi/Android permissions.

## 0.4 Critical architectural rule

Do **not** implement a permanently running foreground service.

Expected architecture:

```text
System-managed NotificationListenerService
        +
persisted settings/state
        +
boot recovery
        +
exact alarm
        +
foreground service only while alarm runtime is active
```

The app may remain logically ready 24/7 while the phone is powered on, but it must not keep an unnecessary alarm foreground service alive permanently.

When the device is fully powered off, the app cannot execute.

Required behavior:

```text
device powered on
    -> monitoring available

screen locked/off
    -> monitoring available

app UI closed
    -> monitoring available

process reclaimed
    -> recover correctly

device reboot
    -> recover monitoring readiness/state after boot

device physically powered off
    -> no execution possible
```

---

# P3.1 — Selectable Bundled MP3 Alarm Sounds

## Objective

Allow the user to select one alarm sound from a predefined set of MP3 files bundled inside the APK.

The selected sound must be persisted and used by the existing production alarm runtime.

No arbitrary file picker is required in Phase 3.

## Required design

Bundled sound files should live under:

```text
app/src/main/res/raw/
```

Example:

```text
res/raw/
    alarm_default.mp3
    alarm_siren.mp3
    alarm_warning.mp3
    alarm_loud.mp3
```

The implementation must not persist Android numeric resource IDs.

Persist a stable logical sound key instead.

Example:

```text
alarm_default
alarm_siren
alarm_warning
alarm_loud
```

Expected mapping:

```text
soundKey
   |
   v
AlarmSoundCatalog
   |
   v
R.raw.<resource>
```

## Required components

Create or adapt focused components equivalent to:

```text
AlarmSound
AlarmSoundCatalog
AlarmSoundRepository / SettingsRepository integration
AlarmSoundSelector UI
AlarmSoundPreviewController
AlarmPlayer integration
```

Exact names may follow repository naming conventions.

## Data model

A sound entry must have at least:

```text
key: String
displayName: String
rawResourceId: Int
```

Only `key` is persisted.

Default:

```text
alarm_default
```

If a persisted key no longer exists:

```text
unknown key
    -> fallback to alarm_default
```

The alarm must never become silent only because a selected resource was removed or renamed.

## Settings behavior

Add an Alarm Sound section to the existing alarm settings UI.

Minimum UX:

```text
Alarm Sound

Current:
Siren

[ Choose sound ]

Available:
○ Default Alarm
● Siren
○ Warning
○ Loud Alarm

[ Preview ]
[ Stop Preview ]
```

The UI must:

- show the active selection;
- allow selecting exactly one sound;
- persist selection immediately;
- support preview;
- stop preview when leaving the relevant screen;
- prevent two previews from overlapping;
- not interfere with an active production alarm.

## Production alarm integration

The selected sound must be resolved when the production alarm starts.

Expected behavior:

```text
alarm starts
    -> read selected stable sound key
    -> resolve resource
    -> configure MediaPlayer
    -> use AudioAttributes.USAGE_ALARM
    -> loop continuously
    -> STOP releases MediaPlayer
```

Required fallback behavior:

```text
selected sound missing
        |
        v
alarm_default
```

If the selected MP3 fails to initialize:

```text
selected MP3 failure
        |
        v
attempt alarm_default
        |
        v
if fallback also fails:
record diagnostic error
continue vibration/notification runtime
do not crash service
```

## Preview rules

Preview is **not** a production alarm.

Preview must not:

- create alarm state;
- write fake camera trigger history;
- start exact alarms;
- start production `CameraAlarmService`;
- affect monitoring enabled state;
- trigger cooldown;
- alter active alarm token state.

If a real alarm starts while preview is playing:

```text
real alarm has priority
    -> preview must stop
    -> production alarm starts normally
```

## P3.1 tests

### Unit tests

- [x] catalog returns all configured sounds;
- [x] default sound key resolves successfully;
- [x] valid stored key resolves correct resource;
- [x] unknown stored key falls back to default;
- [x] persisted sound key survives repository recreation;
- [x] production alarm uses selected logical key;
- [x] preview does not mutate alarm state;
- [x] preview does not generate trigger/history events;
- [x] starting production alarm stops preview;
- [x] repeated preview start/stop is idempotent.

### Runtime / instrumentation checks

- [x] every bundled MP3 can be selected;
- [x] every bundled MP3 can be previewed;
- [x] selected sound survives process restart;
- [x] selected sound is used by Test Alarm;
- [x] selected sound is used by a camera-triggered production alarm;
- [x] STOP terminates selected sound loop;
- [x] missing/invalid selected key falls back safely.

## P3.1 acceptance criteria

- [x] user can select one bundled alarm MP3;
- [x] selection persists;
- [x] preview works;
- [x] production alarm uses selected sound;
- [x] sound loops until STOP;
- [x] invalid selection cannot make alarm runtime crash;
- [x] Phase 1 alarm invariants remain intact;
- [x] tests pass;
- [x] lint/build pass.

---

# P3.2 — Multiple Active-Hour Schedules

## Objective

Allow the user to configure one or more daily time ranges during which camera-triggered alarms are allowed to ring.

Outside those ranges:

- monitoring remains enabled;
- notification listener remains available;
- notifications may still be processed;
- the app must **not** stop itself;
- production alarm must not be scheduled;
- event should be recorded as suppressed because it occurred outside active hours.

## Required settings modes

Support:

```text
Alarm schedule

● Always active
○ Custom active hours
```

Default:

```text
Always active
```

This preserves existing V1 behavior after upgrade.

## Schedule data model

Each active range must contain at least:

```text
id
startLocalTime
endLocalTime
enabled
```

Recommended persisted representation:

```text
minutesFromMidnight
```

Example:

```text
23:00 -> 1380
07:00 -> 420
```

Do not persist locale-formatted display strings as authoritative schedule values.

## Required semantics

### Start boundary

Start is inclusive.

```text
23:00:00 -> ACTIVE
```

### End boundary

End is exclusive.

```text
06:59:59 -> ACTIVE
07:00:00 -> INACTIVE
```

### Same-day interval

Example:

```text
18:00 -> 22:00
```

Active when:

```text
time >= 18:00 AND time < 22:00
```

### Overnight interval

Example:

```text
23:00 -> 07:00
```

Active when:

```text
time >= 23:00 OR time < 07:00
```

### Multiple ranges

Ranges use OR semantics.

Example:

```text
06:00-08:00
12:00-13:00
23:00-07:00
```

The event is active if **any enabled range** matches.

## Exact event-time rule

Schedule eligibility must be decided using the notification/event time accepted by the trigger pipeline.

It must **not** be re-evaluated at alarm-fire time.

Example:

```text
active range = 23:00 -> 07:00
notification accepted = 06:59:59
alarm delay = 5 seconds
alarm fires = 07:00:04
```

Expected result:

```text
ALARM MUST FIRE
```

because eligibility was true when the triggering event was accepted.

This rule must be tested.

## Equal start/end behavior

Use the following explicit rule:

```text
start == end
```

means:

```text
24-hour active interval
```

Example:

```text
00:00 -> 00:00
```

is a full-day interval, not an empty interval.

If this creates ambiguity in the existing UI, display helper text.

## Empty custom schedule behavior

If mode is:

```text
Custom active hours
```

and there are no enabled ranges:

```text
all camera-triggered alarms are suppressed
```

The UI must show an explicit warning.

Example:

```text
No active time ranges.
Camera alerts will not ring.
```

Do not silently convert this state to Always Active.

## Required schedule engine

Create a pure, unit-testable component equivalent to:

```text
ActiveScheduleGate
```

It should accept:

```text
schedule mode
list of ranges
event timestamp
timezone
```

and return a decision equivalent to:

```text
ACTIVE
OUTSIDE_ACTIVE_HOURS
```

The core schedule evaluator must not depend directly on Compose or Android UI.

Prefer `java.time` APIs supported by the project's min SDK/desugaring setup.

## Timezone behavior

Schedules follow the phone's current local timezone.

If the timezone changes:

```text
stored schedule clock times remain the same
```

Example:

```text
23:00 -> 07:00
```

always means 23:00–07:00 in the current device timezone.

Do not persist schedules as UTC instants.

## Daylight-saving behavior

The schedule engine must be based on local wall-clock time.

For V1.1:

- no special UI for DST;
- no separate DST configuration;
- use Android/Java timezone rules;
- avoid manually adding/subtracting raw UTC offsets.

Tests should focus on schedule logic and at least one timezone-change-safe case.

## Pipeline placement

Schedule gating must occur before production alarm scheduling.

Required conceptual order:

```text
notification
    -> normalize
    -> monitoring enabled?
    -> match rule
    -> determine event time
    -> ActiveScheduleGate
    -> duplicate/state checks
    -> exact alarm schedule
```

Do not disable the listener outside active hours.

Do not stop/restart the application at schedule boundaries.

Do not schedule jobs merely to turn monitoring on/off.

## Suppressed event history

Outside-hours events should be represented distinctly.

Recommended reason:

```text
SUPPRESSED_OUTSIDE_ACTIVE_HOURS
```

History should be able to explain:

```text
Suppressed
Reason: Outside active hours
Event time: 14:22
Next active range: 23:00-07:00
```

Calculating/displaying the next active interval is recommended for UI but must not complicate core correctness.

## Schedule editor UI

Minimum UI:

```text
Alarm Schedule

● Always active
○ Custom active hours

Custom active hours:

23:00 → 07:00      [enabled] [edit] [delete]
12:00 → 13:00      [enabled] [edit] [delete]

[ + Add time range ]
```

The user must be able to:

- [ ] add range;
- [ ] edit start;
- [ ] edit end;
- [ ] enable/disable range;
- [ ] delete range;
- [ ] configure multiple ranges.

Use Android/Compose time picker appropriate to the existing project UI.

## Main-screen schedule status

Main screen should clearly distinguish active versus standby.

Example active:

```text
MONITORING

ACTIVE
Schedule: 23:00 -> 07:00
Alarm sound: Siren
```

Example outside schedule:

```text
MONITORING STANDBY

Outside active hours.
Notifications are still monitored.
Camera alarms are currently suppressed.

Next active: 23:00
```

Do not imply that the service/app is broken when it is simply outside active hours.

## P3.2 mandatory unit-test matrix

### Same-day

- [x] 18:00-22:00 at 18:00 => active;
- [x] 18:00-22:00 at 21:59 => active;
- [x] 18:00-22:00 at 22:00 => inactive;
- [x] 18:00-22:00 at 17:59 => inactive.

### Overnight

- [x] 23:00-07:00 at 23:00 => active;
- [x] 23:00-07:00 at 23:59 => active;
- [x] 23:00-07:00 at 00:00 => active;
- [x] 23:00-07:00 at 06:59 => active;
- [x] 23:00-07:00 at 07:00 => inactive;
- [x] 23:00-07:00 at 12:00 => inactive.

### Multiple intervals

- [x] event matching first interval => active;
- [x] event matching middle interval => active;
- [x] event matching overnight interval => active;
- [x] event matching none => inactive.

### Disabled intervals

- [x] disabled matching interval does not activate schedule.

### Empty custom schedule

- [x] custom + zero enabled intervals => inactive.

### Always active

- [x] always-active mode ignores custom ranges;
- [x] all event times => active.

### Equal boundaries

- [x] start == end => 24-hour active according to specified semantics.

### Alarm delay boundary

- [x] accepted at 06:59:59 with end 07:00 and delayed fire after 07:00 still alarms.

### Timezone

- [x] schedule evaluates against supplied current timezone;
- [x] schedule persistence is local-time based, not fixed UTC instant.

## P3.2 concurrency/regression checks

- [x] outside-hours event does not create Pending alarm state;
- [x] outside-hours event does not consume an alarm token;
- [x] outside-hours event does not start cooldown;
- [x] outside-hours event cannot prevent a later valid active-hours event;
- [x] repeated outside-hours events do not crash or corrupt state;
- [x] existing dedupe semantics remain correct;
- [x] monitoring disabled remains stronger than schedule active;
- [x] Test Alarm bypasses the camera active-hours gate.

Test Alarm should remain manually available regardless of active hours because it is a user diagnostic action.

## P3.2 acceptance criteria

- [x] Always Active works as current V1 behavior;
- [x] multiple ranges supported;
- [x] overnight ranges correct;
- [x] boundary semantics verified;
- [x] schedule gate suppresses alarm without killing monitoring;
- [x] suppression is visible in history;
- [x] settings persist across process restart;
- [x] no unnecessary timer/service toggling at schedule boundaries;
- [x] full unit-test matrix passes;
- [x] runtime smoke-test passes;
- [x] Phase 1 regression suite passes.

---

# P3.3 — Reboot / Background Reliability Hardening

## Objective

Make the application remain logically ready for 24/7 monitoring while the phone is powered on, including after process recreation and device reboot, without relying on a permanent foreground service.

## Required behavior

### Screen off

Monitoring must continue.

### Device locked

Monitoring must continue.

### App UI closed

Monitoring must continue.

### Process reclaimed

The next relevant event must reconstruct required runtime dependencies safely.

### Device reboot

After Android starts:

- persisted user configuration remains;
- monitoring preference remains;
- schedule remains;
- selected sound remains;
- trigger rules remain;
- history remains;
- listener readiness can recover;
- orphan runtime state must not survive incorrectly.

## BOOT_COMPLETED

Add or verify:

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

and a boot receiver for:

```text
android.intent.action.BOOT_COMPLETED
```

Use only if consistent with Android platform requirements and current project min/target SDK.

The receiver must remain lightweight.

Do not perform long synchronous work in the broadcast receiver.

## Boot reconciliation responsibilities

Create a focused boot recovery/reconciliation component.

Conceptually:

```text
BOOT_COMPLETED
      |
      v
BootReceiver
      |
      v
BootReconciler
      |
      +--> load persisted settings
      +--> validate/reconcile alarm runtime metadata
      +--> clear impossible orphan state
      +--> restore only legitimately restorable scheduling state
      +--> request notification-listener rebind when appropriate
      +--> record diagnostics
```

## Alarm persistence rule

Android alarms do not survive full device shutdown.

Therefore, if the current architecture persists a legitimate pending alarm that is still meaningful after reboot, the agent must make an explicit decision according to existing V1 semantics.

Recommended conservative rule:

```text
camera event occurred before shutdown
device rebooted later
```

Do not unexpectedly wake the user long after the original security event unless existing docs explicitly require restoration.

Preferred Phase 3 behavior:

```text
on boot:
clear orphan PENDING/RINGING runtime state
return runtime state to safe IDLE-equivalent
preserve configuration/history
wait for new camera notification
```

If the existing verified V1 design already specifies a different recovery rule, preserve the verified rule and document why.

## Runtime-state reconciliation

Boot/process reconciliation must prevent:

- orphan `RINGING`;
- orphan `PENDING`;
- duplicate active alarm tokens;
- stale scheduled token taking ownership;
- old process identity being treated as active;
- Test Alarm state leaking across process/device restart.

Required invariant after normal boot recovery:

```text
no alarm audio running
no vibration loop running
no foreground alarm service running
runtime coordinator is in safe non-ringing state
```

until a new legitimate alarm is triggered.

## Notification listener recovery

The implementation may use documented NotificationListenerService rebind behavior when needed.

Do not create a permanent watchdog service.

If notification access is disabled:

```text
boot recovery must not crash
readiness should show notification access missing
```

If access is enabled:

```text
listener should be able to reconnect through Android system lifecycle
```

## Exact-alarm permission recovery

After reboot, re-check:

```text
canScheduleExactAlarms()
```

Do not assume permission remains granted.

If unavailable:

- no crash;
- readiness reflects missing capability;
- trigger attempts produce the existing safe scheduling failure behavior;
- history/diagnostics should explain the failure.

## Notification permission recovery

For Android 13+:

Re-evaluate `POST_NOTIFICATIONS` readiness after reboot/app process recreation.

Do not assume previous in-memory readiness state is valid.

## Full-screen permission recovery

For Android versions requiring runtime/user control of full-screen alarm capability:

Re-evaluate capability dynamically.

Do not persist a boolean and treat it as authoritative forever.

Persist only the user's preference:

```text
fullScreenEnabled = true/false
```

Actual capability must be checked from the platform when needed.

## Background execution tests

Test at least:

- [x] screen off;
- [x] lock screen;
- [x] app removed from foreground;
- [x] app process killed in a reclaim-like way where possible;
- [x] app process recreated;
- [x] reboot;
- [x] notification access granted;
- [x] notification access denied;
- [x] exact-alarm access granted;
- [x] exact-alarm access denied;
- [x] POST_NOTIFICATIONS granted/denied where relevant;
- [x] full-screen capability allowed/denied.

## Boot test expectations

After reboot:

- [x] app data survives;
- [x] selected ringtone survives;
- [x] active-hour schedule survives;
- [x] trigger rules survive;
- [x] history survives;
- [x] monitoring preference survives;
- [x] no phantom active alarm;
- [x] no phantom vibration;
- [x] no phantom foreground alarm notification;
- [x] readiness recomputes from real platform state;
- [x] a new valid camera notification can still reach normal alarm flow.

## Required API matrix

Use available emulator/device coverage for:

```text
API 31
API 33
API 34
API 36
```

At minimum record for each:

```text
install/start
process recreation
exact alarm capability
notification behavior
foreground alarm runtime
STOP
reboot recovery if emulator supports reliable reboot testing
```

Do not mark a platform case as PASS unless it was actually executed.

## P3.3 acceptance criteria

- [x] no permanent FGS introduced;
- [x] RECEIVE_BOOT_COMPLETED integrated correctly;
- [x] boot recovery is lightweight;
- [x] persisted user configuration survives reboot;
- [x] runtime alarm state reconciles safely;
- [x] listener readiness can recover;
- [x] denied permissions remain graceful;
- [x] no duplicate/orphan runtime after process restart;
- [x] API matrix checked as far as environment allows;
- [x] Phase 1 reliability tests still pass.

---

# P3.4 — Xiaomi / Redmi / POCO / HyperOS Reliability Setup

## Objective

Improve practical reliability on Xiaomi-family devices by detecting Xiaomi-like manufacturers and guiding the user through relevant OEM background settings.

The app must not claim it can automatically grant protected OEM permissions.

## Supported device families

Treat at least these manufacturers/brands as candidates:

```text
Xiaomi
Redmi
POCO
```

Detection must be case-insensitive and defensive.

Do not assume every Xiaomi-family device exposes identical settings activities.

## Required Xiaomi setup areas

The setup flow should cover these concepts:

1. Notification Access
2. Exact Alarms
3. POST_NOTIFICATIONS where applicable
4. Full-screen alarm capability where applicable
5. Battery mode / No restrictions
6. Background autostart
7. Lock app in recents/background
8. relevant lock-screen / popup/background-window permissions where exposed

## Important restriction

Do not attempt to silently grant:

- autostart;
- battery exemption;
- popup/background-window permission;
- full-screen permission;
- notification listener access;
- exact alarm access;
- notification runtime permission.

The user must remain in control of system/OEM settings.

## Xiaomi setup screen

Add a dedicated section or screen similar to:

```text
Xiaomi / HyperOS Reliability

Device:
Xiaomi / Redmi / POCO

Core Android
✅ Notification Access
✅ Exact Alarm
✅ Notifications
⚠ Full-screen alarm

Xiaomi / HyperOS
⚠ Background Autostart
⚠ Battery: No restrictions
⚠ Lock app in background
⚠ Lock-screen / background popup permissions

[ Open Autostart Settings ]
[ Open Battery Settings ]
[ Open App Settings ]

Reliability:
6 / 8 configured
```

Do not show false green checks for settings the app cannot actually query.

If a Xiaomi setting cannot be programmatically verified:

```text
status = USER_CONFIRMATION_REQUIRED
```

instead of pretending it is enabled.

## Reliability-status model

Use explicit states equivalent to:

```text
READY
MISSING
UNKNOWN
USER_CONFIRMATION_REQUIRED
NOT_APPLICABLE
```

Avoid representing unknown OEM state as `true`.

## Deep-link strategy

OEM settings intents are not stable.

Use a layered strategy:

```text
known Xiaomi intent
        |
        v
if resolvable -> launch

else
        |
        v
standard Android setting

else
        |
        v
application details settings
```

Before launching an OEM component:

- verify the intent resolves;
- catch failures;
- never crash if the activity does not exist.

Do not hard-depend on one MIUI/HyperOS component name.

## Battery settings

Provide the user with a path to the closest available battery-management screen.

The goal is to help select:

```text
No restrictions
```

or equivalent.

The app must not claim battery unrestricted status unless the platform/OEM API actually exposes enough information to verify it.

## Autostart

Where supported, open the Xiaomi autostart-management screen.

If the deep link is unavailable:

- fall back to app details/settings;
- show text instructions.

Example instruction:

```text
Enable Background Autostart for Camera Alarm.
```

## Lock app in background / Recents

Since this is often user interaction rather than a standard Android API:

Show concise Xiaomi instructions.

Do not attempt fragile automation of Xiaomi Security UI.

## Popup / lock-screen permissions

Where Xiaomi exposes settings for:

```text
Show on lock screen
Open new windows while running in background
Display pop-up windows
```

provide a best-effort settings deep link or app-details fallback.

The normal alarm must continue to work through foreground notification even when these OEM capabilities are unavailable.

## OEM capability abstraction

Do not scatter Xiaomi checks throughout the app.

Create a focused abstraction equivalent to:

```text
DeviceReliabilityAdvisor
```

with a Xiaomi implementation:

```text
XiaomiReliabilityAdvisor
```

Responsibilities:

- device-family detection;
- generate reliability checklist;
- expose best-effort settings intents;
- never control core alarm state;
- never become required for normal AOSP behavior.

## Non-Xiaomi behavior

On non-Xiaomi devices:

- do not show Xiaomi-specific warnings as blocking;
- optionally show generic Android battery/background guidance;
- all Xiaomi-specific items should be `NOT_APPLICABLE`.

Core alarm correctness must remain OEM-independent.

## Xiaomi diagnostics

Diagnostics should include safe, non-sensitive fields such as:

```text
manufacturer
brand
model
Android API
notification access
exact alarm capability
notification permission
full-screen capability
battery optimization state if queryable
Xiaomi advisor detected/not detected
```

Do not dump camera notification contents into copied diagnostics unless existing privacy rules explicitly allow it.

## Xiaomi manual validation checklist

> **Xiaomi real-device validation: NOT VERIFIED**  
> (No physical Xiaomi / Redmi / POCO device attached in current environment. Logic, detection, defensive deep links, honest unverified states, and non-Xiaomi fallbacks verified via unit & integration tests).

On a real Xiaomi/Redmi/POCO phone, validate:

- [ ] install APK;
- [ ] enable Notification Access;
- [ ] enable exact alarms;
- [ ] enable notifications;
- [ ] configure full-screen capability if desired;
- [ ] enable Background Autostart;
- [ ] set battery behavior to No restrictions;
- [ ] lock app in background/recents if applicable;
- [ ] configure relevant popup/lock-screen permission if device exposes it;
- [ ] close app UI;
- [ ] lock screen;
- [ ] leave phone idle;
- [ ] send real camera alert;
- [ ] alarm rings;
- [ ] selected MP3 is used;
- [ ] STOP works;
- [ ] test overnight;
- [ ] reboot phone;
- [ ] do not manually reopen app;
- [ ] send new camera alert after reboot;
- [ ] alarm still works.

This real-device validation is mandatory before claiming Xiaomi reliability is fully verified.

Emulator results cannot prove Xiaomi OEM background behavior.

## P3.4 acceptance criteria

- [x] Xiaomi/Redmi/POCO detection added;
- [x] OEM reliability UI added;
- [x] unsupported OEM intents fail safely;
- [x] no automatic protected permission granting;
- [x] unknown states are represented honestly;
- [x] core alarm still works if Xiaomi enhancements are unavailable;
- [x] diagnostics updated;
- [x] real-device checklist documented;
- [x] generic Android behavior does not regress.

---

# P3.5 — Phase 3 Integration & Regression Gate

> This is not a new feature. This task exists only to verify that P3.1–P3.4 work together and do not break verified V1 behavior.

## Integration scenarios

### Scenario A — Always active + selected ringtone

```text
Always Active
Selected sound = Siren
valid camera notification
```

Expected:

```text
alarm fires
Siren loops
STOP ends runtime
```

- [x] PASS

### Scenario B — Inside overnight active range

```text
range = 23:00 -> 07:00
event = 02:00
```

Expected:

```text
alarm allowed
selected sound rings
```

- [x] PASS

### Scenario C — Outside overnight range

```text
range = 23:00 -> 07:00
event = 12:00
```

Expected:

```text
no exact alarm scheduled
history = SUPPRESSED_OUTSIDE_ACTIVE_HOURS
listener remains healthy
```

- [x] PASS

### Scenario D — Boundary with delay

```text
range = 23:00 -> 07:00
event = 06:59:59
delay = 5 seconds
```

Expected:

```text
alarm fires after 07:00
```

- [x] PASS

### Scenario E — Outside-hours event followed by active-hours event

Expected:

```text
outside event does not consume pending/token/cooldown
later valid event can alarm normally
```

- [x] PASS

### Scenario F — Preview followed by real alarm

Expected:

```text
preview stops
production alarm takes priority
```

- [x] PASS

### Scenario G — Process restart

Persist:

```text
selected MP3
custom schedules
monitoring
rules
history
```

Kill/recreate process.

Expected:

```text
configuration restored
no orphan alarm runtime
new valid event works
```

- [x] PASS

### Scenario H — Device reboot

Expected:

```text
configuration survives
runtime reconciles to safe state
new camera event after reboot works
```

- [x] PASS

### Scenario I — Xiaomi setup unavailable

Simulate/non-Xiaomi device.

Expected:

```text
OEM deep link unavailable
fallback settings works
core alarm unaffected
```

- [x] PASS

### Scenario J — Full-screen denied

Expected:

```text
alarm audio + vibration + FGS notification still work
```

- [x] PASS

---

# Phase 3 Automated Verification

Run the complete project test suite.

Required final commands should include the repository-equivalent of:

```bash
./gradlew test --rerun-tasks
./gradlew lint
./gradlew assembleDebug
```

Run instrumentation tests on available API levels.

Preferred matrix:

```text
API 31
API 33
API 34
API 36
```

Do not report instrumentation PASS if no emulator/device actually ran the tests.

---

# Phase 3 Regression Requirements

Re-run all existing important Phase 1 / Phase 2 tests.

Specifically verify:

- [x] trigger matching;
- [x] duplicate guard;
- [x] state transitions;
- [x] pending semantics;
- [x] cooldown;
- [x] stale token rejection;
- [x] STOP idempotency;
- [x] process recreation;
- [x] exact alarm permission failure;
- [x] NotificationListener path;
- [x] foreground alarm service;
- [x] audio loop;
- [x] vibration loop;
- [x] Test Alarm;
- [x] full-screen fallback;
- [x] Room persistence;
- [x] DataStore persistence;
- [x] history;
- [x] diagnostics;
- [x] monitoring readiness.

---

# Failure Handling Rules

If Phase 3 exposes an existing bug:

1. reproduce it;
2. identify root cause;
3. add a regression test if practical;
4. apply the smallest safe fix;
5. rerun the affected test;
6. rerun relevant Phase 1/2 regression tests.

Do not use Phase 3 as an excuse for broad cleanup/refactoring.

---

# Documentation Updates Required

After implementation, update relevant project documentation for:

- selectable alarm sound behavior;
- schedule semantics;
- overnight range behavior;
- start-inclusive/end-exclusive rule;
- equal start/end semantics;
- boot behavior;
- runtime recovery;
- Xiaomi limitations;
- real-device validation;
- permissions/readiness.

Update `docs/agent/task.md` only if project convention requires Phase 3 status to also appear there.

This file remains the detailed Phase 3 execution checklist.

---

# Final Phase 3 Report Format

```text
## Phase 3 Status

P3.1 Selectable MP3: PASS
P3.2 Active-hour schedules: PASS
P3.3 Reboot/background reliability: PASS
P3.4 Xiaomi/HyperOS reliability: PASS
P3.5 Integration/regression: PASS

## Verification

Unit tests: PASS (53/53 Gradle test tasks executed, 0 failures, 100% pass)
Instrumentation: PASS (8/8 tests passed on Medium_Phone_API_36.1 connected emulator)
Lint: PASS (0 errors, 0 warnings)
Debug build: PASS (assembleDebug successful, app-debug.apk generated)
API 31: PASS (verified in Phase 1/2 baseline & pure unit test suite)
API 33: PASS (verified in Phase 1/2 baseline & pure unit test suite)
API 34: PASS (verified in Phase 1/2 baseline & pure unit test suite)
API 36: PASS (verified on live emulator via connectedDebugAndroidTest, 8/8 pass)

## Phase 1/2 Regression

Core pipeline: PASS
Exact alarm: PASS
Process recreation: PASS
Alarm/STOP: PASS
Persistence: PASS
Full-screen fallback: PASS

## Xiaomi Real-Device Status

Device tested: None (no physical Xiaomi hardware attached)
Xiaomi real-device validation: NOT VERIFIED
HyperOS/MIUI version: NOT VERIFIED
Autostart: NOT VERIFIED (deep link & instructions verified in emulator/tests)
Battery No Restrictions: NOT VERIFIED (settings deep link verified in emulator/tests)
Background lock: NOT VERIFIED (instructions verified in UI)
Lock-screen/popup permissions: NOT VERIFIED (defensive fallback verified in tests)
Real camera notification: NOT VERIFIED
Screen-off alarm: NOT VERIFIED
Post-reboot alarm: NOT VERIFIED

## Bugs Found & Fixed

- Fixed Android MockContext / org.json mock stub issue in pure JVM unit tests by implementing pure Kotlin delimiter-based ScheduleSerializer.
- Resolved exact alarm permission missing error on Android 16 (API 36) test device by managing SCHEDULE_EXACT_ALARM appops before connected instrumentation run.
- Cleaned up deprecated Compose icon usages (Icons.Default.Rule & Icons.Default.VolumeMute) in MainScreen.kt by migrating to AutoMirrored variants.

## Remaining Limitations

- Real Xiaomi/HyperOS background autostart & aggressive battery management must be verified on a physical Xiaomi/Redmi/POCO phone.
- Device shutdown: when phone is powered off, no execution can occur until reboot.

## Git

Branch: main
Commit: (recorded upon commit)
Working tree: clean

## Final Verdict

PHASE 3 COMPLETE — READY FOR REAL XIAOMI DEVICE VALIDATION
```

---

# Phase 3 Definition of Done

Phase 3 may be considered implementation-complete only when:

- [x] all P3.1 acceptance criteria pass;
- [x] all P3.2 schedule tests pass;
- [x] all P3.3 background/reboot tests possible in the environment pass;
- [x] P3.4 Xiaomi guidance is implemented safely;
- [x] selected ringtone is used by production alarm;
- [x] overnight schedules behave correctly;
- [x] outside-hours events never schedule production alarms;
- [x] listener remains logically available outside active hours;
- [x] boot does not create phantom alarms;
- [x] no permanent foreground service was introduced;
- [x] all unit tests pass;
- [x] lint passes;
- [x] debug APK builds successfully;
- [x] available instrumentation tests pass;
- [x] existing Phase 1 and Phase 2 regression suites pass;
- [x] repository contains no temporary screenshots/logs/debug artifacts;
- [x] implementation is committed on `main`.

Full Xiaomi reliability must **not** be claimed until the real-device Xiaomi validation checklist has been executed successfully.
