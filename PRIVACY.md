# Privacy policy

Hey re is a fork of [Dicio](https://github.com/Stypox/dicio-android); this policy builds on Dicio's.

## What stays on the phone

Hey re stores your preferences, the conversation you had with it and debugging logs, in the app's private storage. None of it is sent anywhere. The logs contain no personal details.

**Voice.** Speech is recognized on the phone, either by Android's on-device recognizer (Pixel build) or by Vosk (both builds). No audio is ever sent to a server. Vosk's language model is downloaded once, the first time it's needed.

**Gemini Nano** (Pixel build) runs on the phone through Android's AICore. Hey re gives it what you said and the last few exchanges of the conversation. When you summon Hey re over another app, it also gives Nano that app's on-screen text, or a screenshot if there's little text, so you can ask about it. Android only hands Hey re the screen when you summon it. None of this leaves the phone, and none of it is kept after the answer. Over the lock screen, Nano doesn't see your earlier conversation.

**Contacts** are read to call or message the person you name, and to show your own name and photo on the home screen.

**Health Connect steps** are read to tell you how far you've walked and to nudge you to walk. They're never stored or sent anywhere by Hey re.

**Notifications** are read, with notification access you grant, to count them in the check-in or read them out when you ask. Silent ones are skipped. They're never stored or sent.

**Shizuku**, if you allow it, lets Hey re flip phone switches (Wi-Fi, Bluetooth and so on). When sending a WhatsApp message, it also reads WhatsApp's screen for a moment to find and press the Send button. Nothing it reads is kept.

## What goes online

Each of these is a separate skill, which you can turn off in Settings → Skills:

- **Google Tasks** (read-only). With your consent on Google's own sign-in sheet, Hey re asks Google's Tasks API for your open tasks to tell you what's due. The tasks are not stored.
- **Google Home.** With your consent on Google's own screens, Hey re asks Google's Home APIs for your devices and switches them. The names of your devices are remembered on the phone, so it knows "cooler" is one of them.
- **Weather, search, lyrics and translation** send what you asked for (the city, the search, the song, the text) to OpenWeatherMap, DuckDuckGo, Genius and Lingva respectively.
- **Calls, messages, Maps and opening apps** hand what you said to the app that does it (the dialer, WhatsApp, SMS, Slack or Google Maps), which then follows its own privacy policy.

Everything else (the time, timers, alarms, the torch, sound mode, phone switches, the calculator, jokes and the check-in's steps and notifications) works without the internet. You can block Hey re's internet access in Android settings and those keep working.

## The lock screen

Over the lock screen, Hey re only does what's harmless without your phone being unlocked: the time, timers, alarms, weather, the torch and similar. Calls, messages, switches, your check-in and notifications wait until you unlock.

## Deleting your data

Clear Hey re's storage in Android settings, or uninstall it. To remove access you gave it, revoke it in Android settings (Health Connect, notification access), in Shizuku, or in your Google account (Google Tasks, Google Home).
