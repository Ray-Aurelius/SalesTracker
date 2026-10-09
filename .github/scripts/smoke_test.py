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

PKG = "com.quotavault.app"
OUT = "smoke"
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
    for label in ["Sales", "Expenses", "Goals", "Clients", "Schedule", "Timer", "Calc", "Charts", "Stats"]:
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

    section("Add client button on the Clients page")
    tap(r"^Clients$", wait=1.5)
    shot("clients-add-button")
    if not tap(r"^Add client$", "clients-add-client", wait=1.5):
        fail("no Add client button on the Clients page")
    tap(r"^First name$", wait=0.8)
    type_text("Jordan")
    tap(r"^Last name$", wait=0.8)
    type_text("Ellis")
    hide_keyboard()
    tap(r"^Save$", wait=1.5)
    if not find(r"Jordan Ellis"):
        fail("the client added from the Clients page is not in the list")
    shot("clients-new-client-listed")

    section("A client's profile with stats")
    tap(r"^Clients$", wait=1.5)
    tap(r"Alex Morgan", "client-profile", wait=2)
    adb("shell", "input", "swipe", "540", "1700", "540", "500", "300")
    time.sleep(1)
    shot("client-stats")
    back()

    section("Timer")
    tap(r"^Timer$", wait=1.5)
    tap(r"^Start$", wait=2.5)
    shot("timer-running")
    tap(r"^Reset$", wait=1.5)

    section("Calculator")
    tap(r"^Calc$", wait=1.5)
    for key in ["7", "×", "8"]:
        tap(rf"^{re.escape(key)}$", wait=0.5)
    tap(r"^=$", "calculator", wait=1)

    section("Password-protected PDF report (pdfbox and its encryption)")
    tap(r"^Stats$", wait=1.5)
    if not find(r"^Report \(PDF\)$"):
        fail("could not reach the Report (PDF) button")
    if tap(r"^Report \(PDF\)$", "report-options", wait=2):
        toggle_near(r"^Protect with a password$", "report-password-on")
        if not find(r"^Password$"):
            fail("could not turn on the report password")
        if tap(r"^Password$", wait=0.8):
            type_text("SmokeTest2026x")
            tap(r"^Confirm password$", wait=0.8)
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

    section("Settings and Security & privacy")
    tap(r"^Settings$", "settings", wait=1.5)
    tap(r"^Security & privacy$", "security", wait=2)

    section("Encrypted backup")
    if not tap(r"^Back up to an encrypted file$", "backup-password", wait=2, scroll=4):
        fail("could not reach the backup button")
    else:
        tap(r"^Password$", wait=0.8)
        type_text("SmokeTest2026x")
        tap(r"^Confirm password$", wait=0.8)
        type_text("SmokeTest2026x")
        hide_keyboard()
        tap(r"^Choose where to save$", wait=2)
        save_in_picker("backup")
        time.sleep(5)
        shot("backup-done")
        files = adb("shell", "ls", "-l", "/sdcard/Download/")
        note("  Downloads: " + " | ".join(l for l in files.splitlines() if "backup" in l.lower()))
        if "stbackup" not in files:
            fail("the encrypted backup was not created")

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
    if not find(r"Alex Morgan"):
        fail("saved data did not load after restart")

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
