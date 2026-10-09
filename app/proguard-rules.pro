# R8 rules for the release build.
# App data is stored with org.json (part of Android) using fixed key names, so nothing relies on reflection.

# Keep file names and line numbers so an optional crash report points at real lines.
# Class and method names are restored with the build's mapping.txt (attached to each GitHub release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# PDF reports: pdfbox-android loads fonts, encodings and encryption classes by name. Keep it whole.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
