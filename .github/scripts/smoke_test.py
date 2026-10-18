#!/usr/bin/env python3
"""
Installs the shrunk release build on an emulator and taps through the app the way a person would:
agreement, welcome with sample data, every tab, the + menu, a client's profile, the timer, a
password-protected PDF report, an encrypted backup, Security & privacy, crash reports and a theme change,
then restarts the app so it has to decrypt and load its saved data again.

Fails if the app crashes at any point. Screenshots of every step go to smoke/ for review.
"""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

PKG = "com.quotavault.myapp"
OUT = "smoke"
# A receipt-like photo the walk-through adds through Android's photo picker.
RECEIPT_JPG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "receipt-test.jpg")
os.makedirs(OUT, exist_ok=True)
log = []
step_no = 0


def adb(*args, check=False, timeout=120):
    r = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout


def note(msg):
    print(msg, flush=True)
    log.append(msg)
    # Written after every step, so a run that dies part-way still says how far it got.
    with open(f"{OUT}/summary.txt", "w") as f:
        f.write("\n".join(log) + "\n")


def in_app():
    return PKG in adb("shell", "dumpsys", "window", "displays").split("mCurrentFocus", 1)[-1].split("\n", 1)[0]


def section(title):
    """Each part of the walk-through starts inside the app, even if an earlier step left it."""
    note(title)
    if not in_app():
        note("  (app was not in front; reopening it)")
        adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
        time.sleep(4)
        if crashed():
            fail(f"crash reopening the app before: {title}")


def crashed():
    out = adb("logcat", "-d", "-b", "crash")
    return PKG in out or "com.salestracker" in out


def shot(name):
    global step_no
    step_no += 1
    path = f"{OUT}/{step_no:02d}-{name}.png"
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)


def dump():
    for _ in range(6):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        if xml.strip().startswith("<?xml"):
            try:
                return ET.fromstring(xml)
            except ET.ParseError:
                pass
        time.sleep(1)
    return None


def clear_system_dialogs(root):
    """The emulator's own apps (e.g. its launcher) sometimes freeze on a slow CI machine and Android puts up an
    "isn't responding" box over everything. That isn't Quota Vault; press Wait and carry on."""
    for node in root.iter("node"):
        if "isn't responding" in (node.get("text") or ""):
            for b in root.iter("node"):
                if (b.get("text") or "") == "Wait":
                    x1, y1, x2, y2 = map(int, re.findall(r"\d+", b.get("bounds")))
                    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                    note("  (dismissed a system 'isn't responding' box: " + (node.get("text") or "") + ")")
                    time.sleep(1.5)
                    return True
    return False


def find(pattern):
    root = dump()
    if root is not None and clear_system_dialogs(root):
        root = dump()
    if root is None:
        return None
    rx = re.compile(pattern)
    for node in root.iter("node"):
        for attr in ("text", "content-desc"):
            if rx.search(node.get(attr) or ""):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(pattern, name=None, wait=1.5, scroll=0):
    pos = find(pattern)
    if pos is None and "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
        # Forms shrink above the keyboard, so a field further down can be scrolled out of view: close the
        # keyboard (as a person would scroll) and look again.
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)
        pos = find(pattern)
    tries = 0
    while pos is None and tries < scroll:
        adb("shell", "input", "swipe", "540", "1700", "540", "900", "300")
        time.sleep(0.8)
        pos = find(pattern)
        tries += 1
    if pos is None:
        note(f"  not found: {pattern}")
        return False
    adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
    time.sleep(wait)
    note(f"  tapped: {pattern}")
    if name:
        shot(name)
    if crashed():
        fail(f"crash after tapping {pattern}")
    return True


def toggle_near(pattern, name=None):
    """Flip the switch on the same row as the given label (tapping the label alone may not)."""
    root = dump()
    if root is None:
        return False
    rx = re.compile(pattern)
    label = next((n for n in root.iter("node") if rx.search(n.get("text") or "")), None)
    if label is None:
        note(f"  not found: {pattern}")
        return False
    ly = sum(map(int, re.findall(r"\d+", label.get("bounds"))[1::2])) // 2
    for n in root.iter("node"):
        if n.get("checkable") == "true":
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            if abs((y1 + y2) // 2 - ly) < 80:
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(1.2)
                note(f"  switched on: {pattern}")
                if name:
                    shot(name)
                return True
    note(f"  no switch next to: {pattern}")
    return False


def type_text(text):
    time.sleep(1.2)  # let the keyboard finish opening, or the first letters can be lost
    adb("shell", "input", "text", text)
    time.sleep(0.8)


def hide_keyboard():
    """Close the on-screen keyboard (Back closes only the keyboard while it is showing)."""
    if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)


def tap_first_image():
    """In Android's photo picker, tap the first photo thumbnail."""
    root = dump()
    if root is None:
        return False
    for node in root.iter("node"):
        rid = node.get("resource-id") or ""
        if "thumbnail" in rid or "icon_thumbnail" in rid or ("photopicker" in rid and node.get("clickable") == "true" and "item" in rid):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            note("  tapped the first photo in the picker")
            return True
    return False


def find_scrolling(pattern, tries=5):
    """Like find, but scrolls down the list until the text shows up (long lists don't fit on one screen)."""
    pos = find(pattern)
    while pos is None and tries > 0:
        adb("shell", "input", "swipe", "540", "1700", "540", "1000", "300")
        time.sleep(0.8)
        pos = find(pattern)
        tries -= 1
    return pos


def keyboard_top():
    """Where the visible keys start (pixels from the top), or None if the keyboard isn't showing or can't be read.
    The keyboard's window covers most of the screen; its "given content insets" say where the keys begin."""
    if "mInputShown=true" not in adb("shell", "dumpsys", "input_method"):
        return None
    out = adb("shell", "dumpsys", "window", "windows")
    for block in re.split(r"\n\s*Window #", out):
        if "InputMethod}" not in block.split("\n", 1)[0]:
            continue
        frame = re.search(r"[mF]?[fF]rame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", block)
        inset = re.search(r"mGivenContentInsets=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", block) or \
            re.search(r"mGivenContentInsets=Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)", block)
        if frame and inset and int(inset.group(2)) > 0:
            return int(frame.group(2)) + int(inset.group(2))
    return None


def check_above_keyboard(pattern, what):
    """With the keyboard open, the form's button must sit above it (the form shrinks and scrolls)."""
    top = keyboard_top()
    pos = find(pattern)
    note(f"  keyboard starts at y={top}; {what} at {pos}")
    if top is None:
        note("  (keyboard position not readable here; skipped)")
        return
    if pos is None or pos[1] >= top:
        shot("keyboard-covers-" + what.replace(" ", "-"))
        fail(f"the keyboard covers the {what}")


def check_typing_notes(form):
    """In an open form: scroll to Notes and type word by word, as a person would. The form must not move
    (no bounce) and its Save button must stay above the keyboard after every word."""
    hide_keyboard()
    if not find_scrolling(r"^Notes$", tries=6):
        fail(f"no Notes box on the {form}")
    tap(r"^Notes$", wait=1.2)
    ys = []
    for w in ["Prefers", "morning", "calls", "about", "the", "new", "roof", "quote", "and", "financing", "options"]:
        adb("shell", "input", "text", w + "%s")
        time.sleep(0.7)
        pos = find(r"^Save$")
        ys.append(pos[1] if pos else None)
    top = keyboard_top()
    note(f"  {form}: Save button y while typing notes: {ys}; keyboard starts at y={top}")
    shot("notes-typing-" + form.replace(" ", "-"))
    if None in ys:
        fail(f"the Save button went out of view while typing notes on the {form}")
    if max(ys) - min(ys) > 12:
        fail(f"the {form} moved while typing notes (bounce): Save went from y={min(ys)} to y={max(ys)}")
    if top is not None and max(ys) >= top:
        fail(f"the keyboard covers the Save button while typing notes on the {form}")


def bottom_bar_top():
    """Where the phone's bottom bar (navigation buttons / gesture line) starts, or None if this phone has none."""
    out = adb("shell", "dumpsys", "window", "windows")
    for block in re.split(r"\n\s*Window #", out):
        if "NavigationBar" not in block.split("\n", 1)[0]:
            continue
        m = re.search(r"[mF]?[fF]rame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", block)
        if m and int(m.group(4)) > int(m.group(2)):
            return int(m.group(2))
    return None


def check_clear_of_bottom_bar(pattern, what):
    """With the keyboard closed, the form's button must sit above the phone's bottom bar (not behind it)."""
    limit = bottom_bar_top()
    pos = find(pattern)
    note(f"  {what} at {pos}; bottom bar starts at y={limit}")
    note("  insets seen by the app: " + " | ".join(l.split("QVInsets: ")[-1] for l in adb("logcat", "-d", "-s", "QVInsets:I").strip().splitlines()[-2:]))
    if pos is None:
        fail(f"the {what} is not on screen")
    if limit is not None and pos[1] >= limit:
        fail(f"the {what} is behind the phone's bottom bar")


def back(wait=1.0):
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(wait)


def save_in_picker(name):
    """The system 'save file' screen: press Save."""
    time.sleep(2.5)
    shot(name + "-picker")
    if not tap(r"^(SAVE|Save)$", wait=3):
        note("  picker Save button not found")
    if crashed():
        fail(f"crash while saving {name}")


def fail(why):
    note(f"FAILED: {why}")
    shot("failure")
    crash = adb("logcat", "-d", "-b", "crash")
    if crash.strip():
        with open(f"{OUT}/crash-log.txt", "w") as f:
            f.write(crash)
    finish(1)


def finish(code):
    with open(f"{OUT}/logcat.txt", "w") as f:
        f.write(adb("logcat", "-d", "-v", "time", "*:W"))
    with open(f"{OUT}/summary.txt", "w") as f:
        f.write("\n".join(log) + "\n")
    sys.exit(code)


def main(apk):
    adb("wait-for-device", check=True)
    adb("install", "-r", apk, check=True, timeout=300)
    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    adb("shell", "settings", "put", "global", "window_animation_scale", "0")
    # The emulator's own home screen (Pixel Launcher) often freezes on slow CI machines and covers the app with
    # "isn't responding" boxes. The walk-through opens the app directly, so the launcher is switched off.
    adb("shell", "pm", "disable-user", "--user", "0", "com.google.android.apps.nexuslauncher")
    # Let a freshly booted emulator finish starting its own apps before ours, so they don't stall it.
    time.sleep(25)
    adb("logcat", "-c")
    adb("logcat", "-b", "crash", "-c")

    note("Launch")
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(6)
    shot("agreement")
    if crashed():
        fail("crash on launch")

    note("Agreement and welcome")
    tap(r"I have read and agree", scroll=6)
    tap(r"^I agree$", "agreed")
    for _ in range(4):
        if find(r"Explore with sample data"):
            break
        if not tap(r"^Next$", wait=1.2):
            break
    tap(r"Explore with sample data", "sample-data-loaded", wait=3)

    section("Every tab")
    for label in ["Sales", "Expenses", "Goals", "Clients", "Schedule", "Tasks", "Calc", "Charts", "Stats"]:
        tap(rf"^{label}$", f"tab-{label.lower()}", wait=2)

    section("The + menu")
    if tap(r"^Log a sale or add a client$", "plus-menu"):
        tap(r"^Log sale$", "log-sale-form", wait=2)
        tap(r"^Cancel$", wait=1.2)

    section("A brand-new client from inside a sale")
    if tap(r"^Log a sale or add a client$", wait=1.2):
        tap(r"^Log sale$", wait=2)
        if not tap(r"^Add client$", "sale-add-client", wait=1.5):
            fail("no Add client button on the sale form")
        tap(r"^First name$", wait=0.8)
        type_text("Riley")
        check_above_keyboard(r"^Save$", "Save button on the new client form")
        tap(r"^Last name$", wait=0.8)
        type_text("Quinn")
        hide_keyboard()
        tap(r"^Save$", wait=1.5)
        shot("sale-with-new-client")
        if not find(r"Riley Quinn"):
            fail("the new client was not chosen on the sale")
        tap(r"^Sale amount$", wait=0.8)
        type_text("500")
        hide_keyboard()
        tap(r"^Save$", wait=1.5)
    tap(r"^Sales$", wait=1.5)
    if not find(r"Riley Quinn"):
        fail("the sale with the new client is not in the sales log")
    shot("sales-log-new-client")

    section("Schedule: tap a date for its overview, add an appointment, delete one; plan a task and tick it off")
    tap(r"^Schedule$", wait=1.5)
    # Today's date in the month (its spoken name starts with e.g. "Wednesday, October 14").
    today_name = adb("shell", "date", "+%A,_%B_%-d").strip().replace("_", " ")
    if not tap("^" + re.escape(today_name), "schedule-day-overview", wait=1.5):
        fail("could not tap today's date in the month")
    if not find(r"^Add on "):
        fail("tapping a date didn't open the day's overview")
    tap(r"^Add on ", wait=1.5)
    tap(r"^Title$", wait=0.8)
    type_text("Smoke%sdemo")
    check_above_keyboard(r"^Save$", "Save button on the appointment form")
    hide_keyboard()
    tap(r"^Save$", wait=1.5)
    if not find(r"^Smoke demo$"):
        fail("the new appointment isn't in the day's overview")
    shot("schedule-overview-added")
    # Quick delete from the overview, then Undo.
    if not tap(r"^Delete appointment Smoke demo$", wait=1.0):
        fail("no trash can next to the appointment")
    gone = find(r"^Smoke demo$") is None
    if not tap(r"^Undo$", wait=1.2):
        fail("no Undo after deleting an appointment")
    if not gone:
        fail("the trash can didn't delete the appointment")
    if not find(r"^Smoke demo$"):
        fail("Undo didn't bring the appointment back")
    # Highlighting the day is a drop-down inside the overview now.
    if not tap(r"^Highlight this day$", "schedule-highlight-menu", wait=1.0, scroll=3):
        fail("no Highlight this day menu in the day's overview")
    else:
        tap(r"^Red$", wait=1.0)
        if not find(r"^Red$"):
            fail("picking a highlight color in the overview didn't stick")
    tap(r"^Done$", wait=1.2)
    if not find_scrolling(r"^Smoke demo$", tries=3):
        fail("the appointment is not on the schedule")
    if find(r"^Add task$"):
        fail("Add task should no longer be on the Schedule page")
    # Plan a task from the Tasks tab.
    tap(r"^Tasks$", wait=1.5)
    if not tap(r"^Add task$", wait=1.5):
        fail("no Add task button on the Tasks page")
    tap(r"^Task$", wait=0.8)
    type_text("Call%ssmoke%sclient")
    hide_keyboard()
    tap(r"^Save$", wait=1.5)
    tap(r"^Tasks$", "tasks-tab", wait=1.5)
    if not find_scrolling(r"^Call smoke client$", tries=3):
        fail("the new task is not on the Tasks tab")
    if not toggle_near(r"^Call smoke client$", "task-ticked"):
        fail("no check box next to the task")
    if not find(r"^Done \(\d+\)$") and not find_scrolling(r"^Done \(\d+\)$", tries=3):
        fail("ticking the box didn't cross the task off")
    if not find_scrolling(r"^Call smoke client$", tries=3):
        fail("a crossed-off task should stay on the list")
    tap(r"^Upcoming$", "tasks-upcoming", wait=1.5)
    tap(r"^Day$", wait=1.2)
    for _ in range(3):   # back to the top of the list
        adb("shell", "input", "swipe", "540", "900", "540", "1900", "300")
        time.sleep(0.6)
    if not find(r"^Add task$"):
        fail("no Add task button on the Tasks page")
    if not tap(r"^Delete task Call smoke client$", wait=0.8, scroll=2):
        fail("no trash can next to the task")
    gone = find(r"^Call smoke client$") is None
    if not tap(r"^Undo$", wait=1.5):
        fail("no Undo after deleting a task")
    if not gone:
        fail("the trash can didn't delete the task")
    if not find_scrolling(r"^Call smoke client$", tries=3):
        fail("Undo didn't bring the task back")
    # Clear list: asks first (some tasks aren't done), empties the day, and Undo brings everything back.
    for _ in range(3):
        adb("shell", "input", "swipe", "540", "900", "540", "1900", "300")
        time.sleep(0.6)
    if not tap(r"^Clear list$", "tasks-clear-confirm", wait=1.2, scroll=2):
        fail("no Clear list button on the Tasks page")
    if not find(r"^Clear this list\?$"):
        fail("Clear list didn't ask first although some tasks aren't done")
    tap(r"^Clear list$", wait=0.8)   # the dialog's button
    emptied = find(r"^Nothing planned") is not None
    if not tap(r"^Undo$", wait=1.5):
        fail("no Undo after clearing the list")
    if not emptied:
        fail("Clear list didn't empty the day's list")
    if not find_scrolling(r"^Call smoke client$", tries=3):
        fail("Undo didn't bring the cleared tasks back")

    section("Sales report button on the Sales tab")
    tap(r"^Sales$", wait=1.5)
    if not tap(r"^Sales report$", "sales-tab-report", wait=2, scroll=1):
        fail("no Sales report button on the Sales tab")
    tap(r"^Cancel$", wait=1.2)
    if not tap(r"^Log sale$", "sales-log-add", wait=1.5):
        fail("no Log sale button at the top of the Sales log")
    elif not find(r"^Sale amount$"):
        fail("the Log sale button didn't open the sale form")
    tap(r"^Cancel$", wait=1.2)

    section("Add client button on the Clients page")
    tap(r"^Clients$", wait=1.5)
    shot("clients-add-button")
    if not tap(r"^Add client$", "clients-add-client", wait=1.5):
        fail("no Add client button on the Clients page")
    tap(r"^First name$", wait=0.8)
    type_text("Jordan")
    tap(r"^Last name$", wait=0.8)
    type_text("Ellis")
    check_typing_notes("new client form")
    hide_keyboard()
    tap(r"^Save$", wait=1.5)
    if not find(r"Jordan Ellis"):
        fail("the client added from the Clients page is not in the list")
    shot("clients-new-client-listed")

    section("Nothing can be copied out of the app")
    tap(r"^Clients$", wait=1.5)
    if tap(r"^Search name", wait=1):
        type_text("Alex")
        hide_keyboard()
        pos = find(r"^Alex$")
        if pos:
            adb("shell", "input", "swipe", str(pos[0]), str(pos[1]), str(pos[0]), str(pos[1]), "900")  # long-press
            time.sleep(1.5)
            note("  text menu: " + ("Select all " if find(r"^Select all$") else "") + ("Paste " if find(r"^Paste$") else ""))
            if find(r"^(Copy|Cut)$"):
                fail("client text could be copied out of the app")
            # Clear the search for the steps that follow.
            if not tap(r"^Select all$", wait=0.8):
                adb("shell", "input", "swipe", str(pos[0]), str(pos[1]), str(pos[0]), str(pos[1]), "900")
                time.sleep(1)
                tap(r"^Select all$", wait=0.8)
            adb("shell", "input", "keyevent", "KEYCODE_DEL")
            time.sleep(0.8)
            hide_keyboard()
        else:
            note("  typed search text not found")

    section("A client's profile with stats")
    tap(r"^Clients$", wait=1.5)
    tap(r"Alex Morgan", "client-profile", wait=2)
    adb("shell", "input", "swipe", "540", "1700", "540", "500", "300")
    time.sleep(1)
    shot("client-stats")
    back()

    section("Timer (on the Clients tab)")
    tap(r"^Clients$", wait=1.5)
    if not tap(r"^Timer$", "clients-timer", wait=1.5):
        fail("no Timer on the Clients tab")
    tap(r"^Start$", wait=2.5)
    shot("timer-running")
    tap(r"^Pause$", wait=1.2)   # Reset only works once the timer is paused
    tap(r"^Reset$", wait=1.5)

    section("Calculator")
    tap(r"^Calc$", wait=1.5)
    for key in ["7", "×", "8"]:
        tap(rf"^{re.escape(key)}$", wait=0.5)
    tap(r"^=$", "calculator", wait=1)

    section("Password-protected PDF sales report (pdfbox and its encryption)")
    tap(r"^Stats$", wait=1.5)
    if not find(r"^Sales report$"):
        fail("could not reach the Sales report button")
    if tap(r"^Sales report$", "report-options", wait=2):
        toggle_near(r"^Protect with a password$", "report-password-on")
        if not find(r"^Password$"):
            fail("could not turn on the report password")
        if tap(r"^Password$", wait=0.8):
            type_text("SmokeTest2026x")
            # The keyboard can cover the second box on this taller form: close it before moving on.
            hide_keyboard()
            if not tap(r"^Confirm password$", wait=0.8, scroll=2):
                fail("could not reach the report's Confirm password box")
            type_text("SmokeTest2026x")
        hide_keyboard()
        if find(r"match"):
            fail("the two report passwords did not match (typing problem in the test)")
        tap(r"^Create PDF$", wait=2)
        save_in_picker("report")
        time.sleep(4)
        shot("report-done")
        files = adb("shell", "ls", "-l", "/sdcard/Download/")
        note("  Downloads: " + " | ".join(l for l in files.splitlines() if "SalesReport" in l))
        if "SalesReport" not in files:
            fail("the PDF report was not created")
        enc = adb("shell", "grep -a -c /Encrypt /sdcard/Download/SalesReport-*.pdf").strip()
        note(f"  PDF encryption entries: {enc}")
        if not enc or enc == "0":
            fail("the PDF report was saved without its password")

    section("Sales report spreadsheet: job / work order #s and dates, never client details")
    tap(r"^Stats$", wait=1.5)
    if tap(r"^Sales report$", wait=2):
        tap(r"^Spreadsheet \(CSV\)$", wait=1)
        shot("sales-report-csv-options")
        tap(r"^Create file$", wait=2)
        save_in_picker("sales-csv")
        time.sleep(4)
    files = adb("shell", "ls -l /sdcard/Download/")
    note("  Downloads: " + " | ".join(l for l in files.splitlines() if "SalesReport" in l))
    if not re.search(r"SalesReport-.*\.csv", files):
        fail("the sales report spreadsheet was not created")
    csv = adb("shell", "cat /sdcard/Download/SalesReport-*.csv")
    note("  CSV starts: " + csv.replace("\r\n", " | ")[:300])
    if not re.search(r"WO-21\d\d", csv) or not re.search(r"\d{4}-\d{2}-\d{2}", csv):
        fail("the sales spreadsheet doesn't carry dates and job / work order numbers")
    for name in ["Alex Morgan", "Morgan", "Jordan Lee", "Sam Patel", "Casey Rivera", "Riley", "@example.com", "555-01"]:
        if name in csv:
            fail(f"client details leaked into the sales spreadsheet: {name}")

    section("Settings and Security & privacy")
    tap(r"^Settings$", "settings", wait=1.5)
    tap(r"^Security & privacy$", "security", wait=2)

    section("Encrypted backup: saved to Documents/Quota Vault Backups with no file picker")
    folder = "'/sdcard/Documents/Quota Vault Backups/'"
    files = adb("shell", f"ls -l {folder}")
    note("  backup folder at first run: " + " | ".join(files.splitlines()))
    if "About this folder" not in files:
        fail("the backup folder was not made when the app was first set up")
    def back_up():
        if not tap(r"^Back up now$", "backup-password", wait=2, scroll=4):
            fail("could not reach the Back up now button")
        else:
            tap(r"^Password$", wait=0.8)
            type_text("SmokeTest2026x")
            hide_keyboard()
            tap(r"^Confirm password$", wait=0.8)
            type_text("SmokeTest2026x")
            hide_keyboard()
            if find(r"match"):
                fail("the two backup passwords did not match (typing problem in the test)")
            tap(r"^Back up$", wait=2)
            for _ in range(8):   # the password is stretched on purpose (slow to guess), so give it time
                if find(r"^Backup saved$"):
                    break
                time.sleep(2)
            shot("backup-saved")
            if not find(r"^Backup saved$"):
                root = dump()
                note("  on screen: " + " | ".join(n.get("text") for n in (root.iter("node") if root is not None else []) if n.get("text"))[:600])
            if not find(r"^Backup saved$"):
                fail("no 'Backup saved' message after backing up")
            files = adb("shell", f"ls -l {folder}")
            note("  backup folder: " + " | ".join(l for l in files.splitlines() if "stbackup" in l))
            if not re.search(r"QuotaVault-backup-.*\.stbackup", files):
                fail("the encrypted backup is not in Documents/Quota Vault Backups")
            tap(r"^Done$", wait=1.5)
            shot("backup-listed")
            if not find(r"^Newest$"):
                fail("the new backup is not listed under Your backups")

    back_up()

    section("Crash reports and privacy info")
    tap(r"^Crash reports$", "crash-reports", wait=1.5, scroll=6)
    tap(r"^Done$", wait=1)
    tap(r"^How your data is handled$", "privacy-info", wait=1.5, scroll=2)
    back()
    back(wait=1.5)

    section("A holiday theme")
    tap(r"^Settings$", wait=1.5)
    tap(r"^Appearance$", wait=1.5)
    tap(r"Christmas", "theme-christmas", wait=1.5, scroll=3)
    back()
    back()

    section("Expenses and mileage")
    tap(r"^Sales$", wait=1.5)
    tap(r"^Expenses$", "expenses", wait=1.5)
    if not find(r"^Expenses & mileage$"):
        fail("the Expenses tab did not open its own page")
    if tap(r"^Add expense$", "add-expense", wait=1.5):
        tap(r"^Amount$", wait=0.8)
        type_text("42.50")
        hide_keyboard()
        tap(r"^Save$", wait=1.5)
    if tap(r"^Log a trip$", "log-trip", wait=1.5):
        tap(r"^Distance", wait=0.8)
        type_text("25")
        tap(r"^Rate per", wait=0.8)
        type_text("0.70")
        hide_keyboard()
        tap(r"^Save$", wait=1.5)
    shot("expenses-after")
    if not find(r"Trip · 25"):
        fail("the logged trip did not appear")

    section("Expense report: PDF and spreadsheet")
    if tap(r"^Expense report$", "expense-report", wait=1.5):
        tap(r"^Create PDF$", wait=2)
        save_in_picker("expense-pdf")
        time.sleep(4)
    if tap(r"^Expense report$", wait=1.5):
        tap(r"^Spreadsheet \(CSV\)$", wait=1)
        toggle_near(r"^Include job / work order #s$", "expense-csv-job-numbers")
        tap(r"^Create file$", wait=2)
        save_in_picker("expense-csv")
        time.sleep(4)
    files = adb("shell", "ls", "-l", "/sdcard/Download/")
    note("  Downloads: " + " | ".join(l for l in files.splitlines() if "Expenses" in l))
    if not re.search(r"Expenses-.*\.pdf", files):
        fail("the expense PDF was not created")
    if not re.search(r"Expenses-.*\.csv", files):
        fail("the expense spreadsheet was not created")
    head = adb("shell", "head -c 400 /sdcard/Download/Expenses-*.csv")
    note("  CSV starts: " + head.replace("\r\n", " | ")[:300])
    if "42.50" not in adb("shell", "cat /sdcard/Download/Expenses-*.csv"):
        fail("the spreadsheet is missing the expense that was added")
    csv = adb("shell", "cat /sdcard/Download/Expenses-*.csv")
    if "Job / work order #" not in csv or not re.search(r"WO-10\d\d", csv):
        fail("the spreadsheet doesn't carry job / work order numbers")
    for name in ["Alex Morgan", "Jordan Lee", "Sam Patel", "Casey Rivera", "@example.com", "555-01"]:
        if name in csv:
            fail(f"client details leaked into the expense spreadsheet: {name}")
    if "Proposal walkthrough" in csv:
        fail("notes went into the spreadsheet although Include notes is off by default")

    section("Receipt photo: photo picker, encrypted, locked behind the phone's PIN")
    # A receipt-like picture in the emulator's gallery for the picker to offer.
    adb("push", RECEIPT_JPG, "/sdcard/Pictures/receipt-test.jpg")
    adb("shell", "content call --method scan_volume --uri content://media --arg external_primary")
    time.sleep(2)
    tap(r"^Expenses$", wait=1.5)
    if tap(r"^Add expense$", wait=1.5):
        tap(r"^Amount$", wait=0.8)
        type_text("18.75")
        hide_keyboard()
        if not tap(r"^Choose photo$", "receipt-picker", wait=3, scroll=3):
            fail("no Choose photo button for receipts")
        # Android's photo picker: tap the newest photo.
        if not (tap(r"^Photo taken on", wait=3) or tap(r"receipt-test", wait=3) or tap_first_image()):
            fail("could not pick a photo in the photo picker")
        time.sleep(3)
        shot("receipt-added")
        # Back in the form the keyboard may reopen and shrink it: close it and scroll to the photo row.
        hide_keyboard()
        if not find(r"^Receipt 1$") and not find_scrolling(r"^Receipt 1$", tries=3):
            fail("the picked photo was not added as a receipt")
        tap(r"^Save$", wait=1.5)
    # No screen lock on the emulator yet: opening must refuse and explain.
    if tap(r"^View receipt photos", "receipt-needs-lock", wait=2):
        if not find(r"^Set a screen lock first$"):
            fail("a receipt opened without any phone lock")
        tap(r"^Done$", wait=1)
    # With a PIN set, opening asks for it every time, then shows the photo.
    adb("shell", "locksettings set-pin 1357")
    time.sleep(1)
    if tap(r"^View receipt photos", wait=2.5):
        shot("receipt-pin-prompt")
        adb("shell", "input text 1357")
        adb("shell", "input keyevent 66")
        time.sleep(3)
        shot("receipt-viewer")  # blank in the screenshot if screenshots are blocked, as they should be
        if not find(r"^Receipt 1 of 1$"):
            fail("the receipt viewer did not open after the PIN")
        tap(r"^Close$", wait=1.5)
    adb("shell", "locksettings clear --old 1357")
    time.sleep(1)

    section("Tiered commission plan")
    tap(r"^Stats$", wait=1.5)
    if tap(r"^Commission earned$", wait=1.5, scroll=6):
        tap(r"^Tiered$", "plan-tiered", wait=1.2)
        tap(r"^Save$", wait=1.5)
        shot("stats-tiered")
        if not find(r"Tiered plan"):
            fail("the tiered plan did not take effect")
        if find(r"Tiered plan · 0%"):
            fail("the tiered plan started at 0% instead of the rate in use")

    section("Menu tabs: hide Calc")
    tap(r"^Settings$", wait=1.5)
    tap(r"^Menu tabs$", "menu-tabs", wait=1.5)
    toggle_near(r"^Calc$", "menu-tabs-calc-off")
    tap(r"^Done$", wait=1)
    tap(r"^Done$", wait=1.5)
    shot("menu-without-calc")
    if find(r"^Calc$"):
        fail("Calc is still in the menu after hiding it")

    note("Restart: the app must decrypt and reload everything")
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(6)
    shot("after-restart")
    if crashed():
        fail("crash after restart")
    if not find(r"^Stats$"):
        note("  WARNING: main screen not showing after restart")
    tap(r"^Clients$", "clients-after-restart", wait=2)
    if not find_scrolling(r"Alex Morgan"):
        fail("saved data did not load after restart")

    section("Erase everything, then restore the backup from the welcome screen")
    tap(r"^Settings$", wait=1.5)
    tap(r"^Security & privacy$", wait=2)
    # A fresh backup now that an expense has a receipt photo: the photo must travel inside it.
    back_up()
    files = adb("shell", f"ls -l {folder}")
    note("  backups before erasing: " + " | ".join(l for l in files.splitlines() if "stbackup" in l))
    if not tap(r"^Erase all data$", "erase-dialog", wait=2, scroll=10):
        fail("could not reach Erase all data")
    if not find(r"are kept"):
        fail("the erase dialog doesn't say the backups are kept")
    tap(r"^Erase everything$", wait=4)
    shot("after-erase")
    if crashed():
        fail("crash while erasing")
    tap(r"I have read and agree", scroll=6)
    tap(r"^I agree$", wait=2)
    if not tap(r"^Have a backup\? Restore it$", "restore-list", wait=3):
        fail("no restore option on the welcome screen")
    if not find(r"^Newest$"):
        fail("the restore list didn't find the backup in the backup folder by itself")
    tap(r"^Newest$", wait=1.5)
    tap(r"^Password$", wait=0.8)
    type_text("SmokeTest2026x")
    hide_keyboard()
    tap(r"^Open$", wait=6)
    shot("restore-confirm")
    if not find(r"^Restore this backup\?$"):
        fail("the backup didn't open with its password")
    if not find(r"Clients: 8 "):
        fail("the backup doesn't hold all 8 clients")
    tap(r"^Restore$", wait=4)
    shot("restored")
    if crashed():
        fail("crash while restoring")
    tap(r"^Clients$", "clients-after-restore", wait=2)
    for name in ["Alex Morgan", "Jordan Ellis", "Riley Quinn"]:
        if not find_scrolling(name):
            fail(f"{name} is missing after restoring the backup")
    shot("clients-after-restore-scrolled")

    section("The receipt photo came back with the backup")
    tap(r"^Expenses$", "expenses-after-restore", wait=2)
    adb("shell", "locksettings set-pin 1357")
    time.sleep(1)
    if not tap(r"^View receipt photos", wait=2.5, scroll=4):
        fail("the expense with a receipt has no photos after restoring")
    else:
        adb("shell", "input text 1357")
        adb("shell", "input keyevent 66")
        time.sleep(3)
        shot("receipt-after-restore")
        if find(r"isn't on this phone"):
            fail("the receipt photo was not restored from the backup")
        if not find(r"^Receipt 1 of 1$"):
            fail("the restored receipt photo did not open")
        tap(r"^Close$", wait=1.5)
    adb("shell", "locksettings clear --old 1357")
    time.sleep(1)

    section("Import clients from a spreadsheet (CSV) the user picks")
    adb("shell", "printf 'Name,Mobile,E-mail,Work order #\\nDakota Lindqvist,555-010-0977,dakota@example.com,WO-2001\\nAlex Morgan,,,\\n' > /sdcard/Download/import-test.csv")
    adb("shell", "content call --method scan_volume --uri content://media --arg external_primary")
    time.sleep(2)
    tap(r"^Settings$", wait=1.5)
    if not tap(r"^Import from a spreadsheet$", "import-dialog", wait=1.5, scroll=6):
        fail("no Import from a spreadsheet in Settings")
    tap(r"^Choose CSV file$", "import-picker", wait=3)

    def wait_for_match(seconds=12):
        for _ in range(int(seconds / 1.5)):
            if find(r"^Match your columns$"):
                return True
            time.sleep(1.5)
        return False

    def pick(name):
        """Picks a file in Android's file picker: a tap, then a long press and Select if the tap didn't take."""
        pos = find(name)
        if pos is None:
            return False
        adb("shell", f"input tap {pos[0]} {pos[1]}")
        note(f"  tapped file {name}")
        if wait_for_match():
            return True
        pos = find(name)
        if pos is None:
            return wait_for_match(4)
        adb("shell", f"input swipe {pos[0]} {pos[1]} {pos[0]} {pos[1]} 900")
        time.sleep(1.5)
        shot("import-picker-selected")
        tap(r"^Select$|^SELECT$|^Open$|^OPEN$", wait=2)
        return wait_for_match()

    if not pick(r"import-test"):
        # Try the same file from the phone's storage root.
        shot("import-picker-retry")
        if find(r"^Choose CSV file$"):  # the picker closed: open it again
            tap(r"^Choose CSV file$", wait=3)
        tap(r"Show roots", wait=1.5)
        if not tap(r"sdk_gphone|Android SDK|^SDK|Internal storage|^Phone$", wait=2):
            note("  storage root not found in the picker's menu")
        tap(r"^Download$", wait=2, scroll=4)
        pick(r"import-test")
    shot("import-match")
    if not find(r"^Match your columns$"):
        fail("the CSV file did not open for matching")
    if not find(r"^Rows found: 2$"):
        fail("the importer didn't read both rows")
    tap(r"^Import$", wait=3)
    shot("import-done")
    if not find(r"^Clients added: 1$"):
        fail("the new client from the CSV was not added")
    if not find(r"skipped: 1$"):
        fail("the client already in the app was not skipped")
    tap(r"^Done$", wait=1.5)
    tap(r"^Done$", wait=1.5)
    tap(r"^Clients$", wait=2)
    if not find_scrolling(r"Dakota Lindqvist"):
        fail("the imported client is not in the client list")

    section("Home-screen widgets: Quick add buttons and Follow-ups open the right place")
    act = f"{PKG}/com.salestracker.app.MainActivity"
    widgets = adb("shell", "dumpsys package " + PKG)
    for cls in ["QuickAddWidget", "FollowUpsWidget", "CommissionWidget"]:
        if cls not in widgets:
            fail(f"the {cls} home-screen widget is not registered")
    adb("shell", f"am start -n {act} --es quickAdd sale")
    time.sleep(3)
    shot("widget-quick-sale")
    if not find(r"^Sale amount$"):
        fail("Quick add > Log sale did not open the sale form")
    tap(r"^Cancel$", wait=1.5)
    adb("shell", f"am start -n {act} --es quickAdd client")
    time.sleep(3)
    shot("widget-quick-client")
    if not find(r"^First name$"):
        fail("Quick add > Add client did not open the client form")
    tap(r"^Cancel$", wait=1.5)
    adb("shell", f"am start -n {act} --es quickAdd expense")
    time.sleep(3)
    shot("widget-quick-expense")
    if not find(r"^Amount$"):
        fail("Quick add > Expense did not open the expense form")
    tap(r"^Cancel$", wait=1.5)
    adb("shell", f"am start -n {act} --es quickAdd timer")
    time.sleep(3)
    shot("widget-quick-timer")
    if not find(r"^Pause$"):
        fail("Quick add > Timer did not start the timer")
    tap(r"^Pause$", wait=1.2)
    tap(r"^Reset$", wait=1.5)
    adb("shell", f"am start -n {act} --ez openClients true")
    time.sleep(3)
    shot("widget-followups-open")
    if not find_scrolling(r"Dakota Lindqvist|Alex Morgan|Follow-ups due"):
        fail("the Follow-ups widget did not open the client list")

    section("Automatic follow-ups: off by default, turned on in Settings > Follow-ups")
    tap(r"^Settings$", wait=1.5)
    if not tap(r"^Follow-ups$", "followups-settings", wait=1.5, scroll=6):
        fail("no Follow-ups page in Settings")
    if not find(r"^Automatic follow-ups off$|^Schedule follow-ups automatically$"):
        fail("the Follow-ups page didn't open")
    tap(r"^Schedule follow-ups automatically$", wait=1.2)
    shot("followups-on")
    if not find_scrolling(r"^Stage changes to Proposal$"):
        fail("the follow-up rules don't include pipeline stages")
    tap(r"^Done$", wait=1.2)
    if not find(r"^Automatic follow-ups on$"):
        fail("automatic follow-ups did not turn on")
    tap(r"^Done$", wait=1.5)
    tap(r"^Clients$", wait=1.5)
    adb("shell", "input swipe 540 600 540 1800 300")  # back to the top of the list
    tap(r"^Add client$", wait=1.5)
    tap(r"^First name$", wait=0.8)
    type_text("Zed")
    tap(r"^Last name$", wait=0.8)
    type_text("Fairweather")
    hide_keyboard()
    tap(r"^Save$", wait=2)
    shot("followups-new-client")
    if not find_scrolling(r"Zed Fairweather"):
        fail("the new client is not in the list")

    def done_under(name):
        """The follow-up's Done button on the card of the client called [name]."""
        root = dump()
        if root is None:
            return None
        nodes = list(root.iter("node"))
        top = None
        for n in nodes:
            if re.search(name, n.get("text") or ""):
                top = int(re.findall(r"\d+", n.get("bounds"))[1])
        if top is None:
            return None
        best = None
        for n in nodes:
            if (n.get("text") or "") == "Done":
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
                if y1 > top and (best is None or y1 < best[1]):
                    best = ((x1 + x2) // 2, y1, (y1 + y2) // 2)
        return best

    pos = done_under(r"^Zed Fairweather$")
    if pos is None:
        fail("adding a client didn't schedule its first follow-up")
    else:
        adb("shell", f"input tap {pos[0]} {pos[2]}")
        time.sleep(2)
        shot("followups-offer-next")
        if not find(r"^Schedule the next follow-up\?$"):
            fail("marking the follow-up done didn't offer the next one")
        tap(r"^Schedule$", wait=2)
        if done_under(r"^Zed Fairweather$") is None:
            fail("the offered follow-up was not scheduled")

    section("Clients: long-press to choose several, then delete them together after confirming")
    tap(r"^Clients$", wait=1.5)
    if find(r"^Clients$") and find(r"^Timer$"):
        tap(r"^Clients$", wait=1.0)
    for _ in range(4):  # back to the top of the list (Dakota sorts before Zed)
        adb("shell", "input swipe 540 700 540 1900 250")
        time.sleep(0.4)
    pos = find_scrolling(r"^Dakota Lindqvist$")
    if not pos:
        fail("can't find a client to choose")
    else:
        adb("shell", f"input swipe {pos[0]} {pos[1]} {pos[0]} {pos[1]} 900")
        time.sleep(1.5)
        shot("clients-choosing")
        if not find(r"^1 selected$"):
            fail("long-pressing a client didn't start choosing clients")
        if not tap(r"^Zed Fairweather$", wait=1.0, scroll=8):
            fail("couldn't add a second client to the selection")
        if not find(r"^2 selected$"):
            fail("tapping another client didn't add it to the selection")
        tap(r"^Delete selected$", "clients-delete-many", wait=1.5)
        if not find(r"^Delete the selected clients \(2\)\?$"):
            fail("deleting several clients didn't ask first")
        tap(r"^Delete$", wait=2)
        shot("clients-deleted-many")
        if find_scrolling(r"^Zed Fairweather$|^Dakota Lindqvist$", tries=3):
            fail("the chosen clients were not deleted")

    if crashed():
        fail("crash")
    note("PASSED: no crashes")
    finish(0)


if __name__ == "__main__":
    try:
        main(sys.argv[1])
    except SystemExit:
        raise
    except Exception as e:  # a broken script is not an app crash, but still fails the check
        note(f"SCRIPT ERROR: {e!r}")
        finish(2)
