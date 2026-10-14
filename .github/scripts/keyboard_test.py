#!/usr/bin/env python3
"""
Android 15+ keyboard check. From Android 15, Android no longer resizes pop-ups for the keyboard, and the app
has to place its forms itself. On a newer Android, open Add client, scroll to Notes and type word by word:
the form must not bounce and Save must stay above the keyboard. Uses the main walk-through's helpers.
"""
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import smoke_test as t

t.OUT = "keyboard"
os.makedirs(t.OUT, exist_ok=True)


def main(apk):
    t.adb("wait-for-device", check=True)
    t.adb("install", "-r", apk, check=True, timeout=300)
    t.adb("shell", "settings", "put", "global", "window_animation_scale", "0")
    t.adb("shell", "pm", "disable-user", "--user", "0", "com.google.android.apps.nexuslauncher")
    time.sleep(25)
    t.adb("logcat", "-c")
    t.adb("logcat", "-b", "crash", "-c")
    t.note("Android " + t.adb("shell", "getprop", "ro.build.version.release").strip())
    t.adb("shell", "monkey", "-p", t.PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(6)
    t.tap(r"I have read and agree", scroll=6)
    t.tap(r"^I agree$")
    for _ in range(4):
        if t.find(r"Explore with sample data"):
            break
        if not t.tap(r"^Next$", wait=1.2):
            break
    t.tap(r"Explore with sample data", wait=3)
    t.section("Add client: type notes at the bottom of the form")
    t.tap(r"^Clients$", wait=1.5)
    if not t.tap(r"^Add client$", wait=1.5):
        t.fail("no Add client button")
    t.tap(r"^First name$", wait=0.8)
    t.type_text("Kay")
    t.check_above_keyboard(r"^Save$", "Save button on the new client form")
    t.check_typing_notes("new client form")
    t.section("Log a sale: type notes at the bottom of the form")
    t.hide_keyboard()
    t.tap(r"^Cancel$", wait=1.2)
    if t.tap(r"^Log a sale or add a client$", wait=1.2):
        t.tap(r"^Log sale$", wait=2)
        t.check_typing_notes("sale form")
    if t.crashed():
        t.fail("crash")
    t.note("PASSED: forms stay put above the keyboard")
    t.finish(0)


if __name__ == "__main__":
    try:
        main(sys.argv[1])
    except SystemExit:
        raise
    except Exception as e:
        t.note(f"SCRIPT ERROR: {e!r}")
        t.finish(2)
