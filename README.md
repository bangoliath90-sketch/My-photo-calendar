# My Photo Calendar

A personal Android calendar, like Google Calendar, with your own photo filling the background of each month.

## Installing on your phone

1. Open this repository's **Releases** page on your phone (in the repository, tap **Releases**).
2. On the newest release, tap the `.apk` file to download it.
3. Open the download. The first time, Android asks to allow installs from your browser: allow it.
4. Tap **Install** (or **Update** for a newer version). Your photos and settings are kept.

Every change pushed to `main` builds a new version automatically (see the **Actions** tab).

## What's in this version

- Month view, week starting on Sunday, swipe left/right to change months
- Full-screen photo background for each month
- Add photos (including from Google Photos) through Android's photo picker
- Each month either shuffles through all your photos or uses one photo you choose (repeats every year)
- Shuffle timing: each open, daily, weekly or monthly
- Text style templates, 14 fonts, text size, any font color, bold, text shadow and a dim slider
- Your Google Calendar events shown on the month view (through the phone's synced calendar)
- Tap any day to see its schedule; add, edit and delete events with times, calendar, reminder, location and notes

- Week and schedule views; show or hide calendars; photo option per view
- Repeating events: create them, and change or delete one occurrence, the following ones, or the whole series

- Reminder notifications from the app itself (tap one to open that day)
- Home-screen widget with this month's photo and your next events

- Move & zoom each photo; give any month its own text style

## Notes

- Written in Kotlin with Jetpack Compose.
- `keystore/` holds the app's signing key. It's kept in this (private) repository so every build can install over the previous one.

## Fonts

The extra fonts (Montserrat, Oswald, Bebas Neue, Comfortaa, Playfair Display, Lora, Abril Fatface, Lobster, Pacifico, Dancing Script, Caveat, Great Vibes) come from [Google Fonts](https://github.com/google/fonts) under the SIL Open Font License. `scripts/get-fonts.sh` downloads them during each build.
