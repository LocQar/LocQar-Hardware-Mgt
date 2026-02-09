# LocQar Locker - Android Parcel Locker Kiosk

Production-ready Android kiosk application for controlling a Winnsen RS485 parcel locker system. Operates fully offline with direct hardware control via USB-to-RS485 adapter.

## Architecture

```
app/src/main/java/com/locqar/locker/
├── hardware/            # Hardware abstraction layer (HAL)
│   ├── codec/           # WinnsenCodec - RS485 frame builder/parser
│   ├── serial/          # SerialManager - USB serial lifecycle
│   ├── controller/      # LockerController interface + impl
│   ├── demo/            # DemoLockerController for testing
│   └── service/         # LockerDaemonService foreground service
├── data/
│   ├── db/              # Room database
│   │   ├── entity/      # BoardEntity, DoorEntity, AccessCodeEntity, etc.
│   │   ├── dao/         # Data access objects
│   │   └── migration/   # DB migrations (future)
│   ├── repository/      # LockerRepository - business logic
│   └── model/           # Data models
├── ui/
│   ├── theme/           # Material 3 theme
│   ├── navigation/      # Compose Navigation setup
│   └── screens/
│       ├── techtool/    # Hardware test utility
│       ├── commissioning/ # Setup wizard
│       ├── admin/       # Admin dashboard
│       └── kiosk/       # Public kiosk flows
│           ├── home/    # Home screen
│           ├── pickup/  # Customer pickup
│           ├── dropoff/ # Courier drop-off
│           └── recall/  # Parcel recall
└── util/                # ExportUtil, helpers
```

## Hardware Wiring

### Components
- **Android device** with USB Host support (tablet recommended)
- **USB-to-RS485 adapter** (CH340, CP2102, FTDI, or PL2303 chipset)
- **Winnsen lock control board** (1 station, 12 doors)

### Wiring
```
Android USB Port → USB-to-RS485 Adapter → RS485 A/B → Winnsen Control Board
```

- Connect USB-to-RS485 adapter's **A+** to board's **A+**
- Connect USB-to-RS485 adapter's **B-** to board's **B-**
- Ensure common ground between adapter and board
- Serial settings: **9600 baud, 8 data bits, 1 stop bit, no parity (9600 8N1)**

### RS485 Protocol

| Command | TX | RX |
|---------|----|----|
| Open Lock | `90 06 05 <station> <lock> 03` (6 bytes) | `90 07 85 <station> <lock> <status> 03` (7 bytes) |
| Poll State | `90 07 02 <station> <lowMask> <highMask> 03` (7 bytes) | `90 07 82 <station> <lowState> <highState> 03` (7 bytes) |

- Status: `01` = success, `00` = failed
- Poll state bits: `1` = OPEN, `0` = CLOSED (bit 0 = lock 1)
- Default poll mask for 12 doors: `0x0FFF`

## USB Permission

The app automatically requests USB permission when connecting. For auto-grant on kiosk devices:

1. The `AndroidManifest.xml` includes a USB device filter (`res/xml/usb_device_filter.xml`) for common adapters
2. On first connection, Android will prompt for permission
3. Check "Always open LocQar Locker" to auto-grant in future

For custom adapters, add vendor/product IDs to `usb_device_filter.xml`.

## Running the Tech Tool

The Tech Tool is the primary hardware diagnostic utility:

1. **Access**: From the kiosk home screen, **tap the logo 5 times** or **long-press the logo** to enter Admin mode
2. **Login**: Enter admin password (default: `admin` before commissioning)
3. **Navigate**: Admin Dashboard → Tech Tool button

### Tech Tool Features
- **Connect/Disconnect**: Manage USB serial connection
- **Station Number**: Set the board station number (default: 1)
- **Poll Now**: Single poll to read all door states
- **Live Poll (2s)**: Continuous polling every 2 seconds
- **Lock tiles**: Tap for Open, long-press for Safe Open
- **Test All 1-12**: Sequential test with options:
  - Confirm Open: polls to verify door opened
  - Wait Close: waits for each door to close before next
- **Export**: Test report JSON and logs CSV to `Downloads/LocQarLocker/`

## Commissioning Steps

First-time setup wizard to map physical locks to door labels:

### Step 1: Station Setup
- Set station number (typically 1)
- Click "Test Poll" to verify board communication

### Step 2: Door Mapping
For each lock 1-12:
1. Click "Open Lock N" to physically open the lock
2. Walk to the locker and identify which door opened
3. Tap the corresponding door label button (1-12)
4. Close the door (app polls to confirm closure)
5. Repeat for all 12 locks
- **Skip**: Disables that lock (e.g., damaged or unused)
- **Undo**: Reverts the last mapping

### Step 3: Door Verification
Tests a sample of doors (1, 6, 12) by their assigned labels to confirm mappings are correct.

### Step 4: Kiosk Settings
- Locker name (displayed on home screen)
- Help phone number
- Polling intervals
- Open confirm timeout
- Open-too-long alert threshold

### Step 5: Admin Security
- **Required**: Set a new admin password (min 6 chars, cannot be "admin")
- This password is used for all admin access going forward

### Step 6: Summary & Save
- Review all mappings and settings
- Click "Complete Commissioning" to save
- Export configuration JSON for backup

## Demo Mode

Toggle demo mode for UI testing without hardware:

1. In app settings or code, set `demo_mode = true`
2. Demo mode simulates:
   - All door opens succeed
   - Doors auto-close after ~8 seconds
   - Polling returns simulated states
3. Real hardware mode is the default

To enable: Set `SettingsKeys.DEMO_MODE` to `"true"` in the database, or modify the initialization in `MainActivity`.

## Known Limitations

### No Physical Sensors
- **No door sensors**: Open/closed state comes solely from the board's poll response bits. If the board reports CLOSED, we trust it.
- **No occupancy sensors**: Cannot detect if a parcel is actually inside a compartment. Occupancy is tracked via workflow state (AVAILABLE → OCCUPIED on drop-off, → AVAILABLE on pickup).
- **Implication**: If someone removes a parcel without using the kiosk, the system won't know.

### Offline-Only
- No cloud sync, push notifications, or remote management (by design for this phase)
- Access codes are stored locally; no SMS/email delivery of pickup codes
- Courier authentication is a placeholder (accepts any non-empty code)

### Single Board
- Supports 1 control board (1 station) with locks 1-12
- Locks 13-16 are ignored per hardware spec
- Multi-station support would require architecture changes to the daemon

## Troubleshooting

### Station Offline
**Symptoms**: Admin dashboard shows "OFFLINE", polls fail with timeout

1. Check USB cable connection (try unplugging and reconnecting)
2. Verify RS485 A/B wiring (swap A and B if necessary)
3. Confirm station number matches the board's DIP switch setting
4. In Tech Tool: Disconnect → Connect → Poll Now
5. Check board power supply
6. After 3 consecutive timeouts, station is marked offline; a successful poll restores it

### Open Failed (status=00)
**Symptoms**: "Lock N open FAILED (status=00)" in Tech Tool

1. The board acknowledged the command but couldn't release the lock
2. Check if the specific lock's solenoid is powered and connected
3. Check for physical obstruction
4. Try the lock again; intermittent failures may indicate wiring issues
5. If persistent, the lock mechanism may need replacement

### Open Not Confirmed
**Symptoms**: "Lock N opened but NOT confirmed" during Safe Open

1. The open command returned success, but polling didn't show the door as OPEN
2. This can mean:
   - The door spring closed faster than the poll interval
   - The board's door sense circuit isn't detecting the open state
3. Try increasing `OPEN_CONFIRM_SECONDS` in settings
4. Check door alignment and lock mechanism

### Mapping Mismatch
**Symptoms**: Opening door "3" opens a different physical door

1. The lock-to-label mapping is incorrect
2. Go to Admin Dashboard → Commissioning
3. Re-run the Door Mapping wizard
4. Pay careful attention to which physical door opens for each lock number
5. Export the new configuration for backup

### USB Permission Denied
**Symptoms**: "USB permission denied" error

1. When prompted, tap "Allow" and check "Always open"
2. If not prompted, go to Android Settings → Apps → LocQar Locker → Permissions
3. Some devices require USB debugging to be enabled
4. Try a different USB-to-RS485 adapter

### Door Left Open Alerts
**Symptoms**: Banner/fullscreen warnings about open doors

- **10s**: Banner notification (configurable)
- **30s**: Full-screen warning (configurable)
- **60s**: Incident created; optionally disables door (configurable)
- Physically close the door to clear alerts
- Admin can dismiss warnings and resolve incidents from dashboard

## Export Formats

### Test Report JSON
```json
{
  "schemaVersion": 1,
  "timestamp": "2024-01-15T10:30:00.000Z",
  "station": 1,
  "maxDoors": 12,
  "results": [
    {
      "lockNumber": 1,
      "openCommandSent": true,
      "openSuccess": true,
      "openConfirmed": true,
      "closedAfterTest": true,
      "errorMessage": null,
      "durationMs": 1234
    }
  ]
}
```

### Configuration JSON
```json
{
  "schemaVersion": 1,
  "exportedAt": "2024-01-15T10:30:00.000Z",
  "locker": { "name": "Building A Locker", "helpPhone": "+1234567890" },
  "board": { "stationNumber": 1, "maxDoors": 12 },
  "doors": [
    { "lockNumber": 1, "doorLabel": "1", "enabled": true }
  ],
  "settings": { ... }
}
```

### Logs CSV
```
id,timestamp,eventType,severity,source,doorId,lockNumber,stationNumber,message,details,synced
```

All exports go to `Downloads/LocQarLocker/` via MediaStore.

## Building

1. Open in Android Studio (Hedgehog or later)
2. Sync Gradle dependencies
3. Build → Make Project
4. Run on device with USB Host support

### Dependencies
- Kotlin 1.9.22
- Jetpack Compose (BOM 2024.01.00)
- Room 2.6.1
- Navigation Compose 2.7.6
- usb-serial-for-android 3.7.3
- Material 3

### Min SDK: 26 (Android 8.0)
### Target SDK: 34 (Android 14)
