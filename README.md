# My Photo Calendar

A personal Android calendar, like Google Calendar, with your own photo filling the background of each month.

## Installing on your phone

1. Open this repository's **Releases** page on your phone (in the repository, tap **Releases**).
2. On the newest release, tap the `.apk` file to download it.
3. Open the download. The first time, Android asks to allow installs from your browser: allow it.
4. Tap **Install** (or **Update** for a newer version). Your photos and settings are kept.

Every change pushed to `main` builds a new version automatically (see the **Actions** tab).

## What's in this version (phase 1)

- Month view, week starting on Sunday, swipe left/right to change months
- Full-screen photo background for each month
- Add photos (including from Google Photos) through Android's photo picker
- Each month either shuffles through all your photos or uses one photo you choose (repeats every year)
- Shuffle timing: each open, daily, weekly or monthly
- Dim slider, font color, bold and text shadow

Coming next: your Google Calendar events, then week/day/schedule views, editing, reminders and the widget.

## Notes

- Written in Kotlin with Jetpack Compose.
- `keystore/` holds the app's signing key. It's kept in this (private) repository so every build can install over the previous one.
