# Privacy policy

Hey re is a fork of [Dicio](https://github.com/Stypox/dicio-android); this policy builds on Dicio's.

## What stays on the phone

Hey re stores your preferences, the conversation you had with it and debugging logs, in the app's private storage. None of it is sent anywhere. The logs contain no personal details.

**Words to listen for.** To hear names right, the on-device recognizer is given your favourite and most-contacted contacts' names and your apps' names along with each listen; they stay on the phone.

**Voice.** Speech is recognized on the phone, either by Android's on-device recognizer (Pixel build) or by Vosk (both builds). No audio is ever sent to a server. Vosk's language model is downloaded once, the first time it's needed.

**Gemini Nano** (Pixel build) runs on the phone through Android's AICore. Hey re gives it what you said and the last few exchanges of the conversation. When you summon Hey re over another app, it also gives Nano that app's on-screen text, or a screenshot if there's little text, so you can ask about it. Android only hands Hey re the screen when you summon it. None of this leaves the phone, and none of it is kept after the answer. Over the lock screen, Nano doesn't see your earlier conversation.

**Chats** worth keeping are saved in the app's own storage on the phone, with their images, and never leave it; swipe one away in the history to delete it, or clear the app's storage to delete them all.

**Memory**: the facts Hey re learns from what you say in chats, or that you tell it to remember, are kept in the app's storage on the phone and only ever go to Gemini Nano on the phone. Settings → Memory shows every one; tap to correct, swipe to forget, or forget everything.

**Images** you take, pick or paste are read on the phone (ML Kit text recognition and Gemini Nano) and kept only in the conversation; a photo you take sits in the app's cache until the next one replaces it.

**Contacts** are read to call or message the person you name, and to show your own name and photo on the home screen.

**Health Connect steps** are read to tell you how far you've walked and to nudge you to walk. They're never stored or sent anywhere by Hey re.

**Notifications** are read, with notification access you grant, to count them in the check-in or read them out when you ask. Silent ones are skipped. They're never stored or sent.

**Shizuku**, if you allow it, lets Hey re flip phone switches (Wi-Fi, Bluetooth and so on), and read and change Settings' own readings (battery, storage, brightness). When sending a WhatsApp message, it also reads WhatsApp's screen for a moment to find and press the Send button. Nothing it reads is kept.

## What goes online

Each of these is a separate skill, which you can turn off in Settings → Skills:

- **Google Tasks** (read-only). With your consent on Google's own sign-in sheet, Hey re asks Google's Tasks API for your open tasks to tell you what's due. The tasks are not stored.
- **Find Hub.** Through Shizuku, Hey re asks Google's Find Hub app where your devices are, rings them, and tells it where you put things ("remember my keys are in the drawer"). Find Hub keeps those notes and device locations in your Google account, under its own privacy policy; Hey re keeps none of it.
- **Google Home.** With your consent on Google's own screens, Hey re asks Google's Home APIs for your devices and switches them. The names of your devices are remembered on the phone, so it knows "cooler" is one of them.
- **The whole article.** When you summon Hey re over a page whose app hands over its link (Chrome does), Hey re downloads that page itself, without your cookies or login, and Gemini Nano summarises it on the phone. It skips sign-in, reset, invite and other one-time links, homepages and searches, so a second visit can't use them up. The page isn't kept.
- **Weather, search, lyrics and translation** send what you asked for (the city, the search, the song, the text) to OpenWeatherMap, DuckDuckGo, Genius and Lingva respectively.
- **Calls, messages, Maps and opening apps** hand what you said to the app that does it (the dialer, WhatsApp, SMS, Slack or Google Maps), which then follows its own privacy policy.

Everything else (the time, timers, alarms, the torch, sound mode, phone switches, the calculator, jokes and the check-in's steps and notifications) works without the internet. You can block Hey re's internet access in Android settings and those keep working.

## The lock screen

Over the lock screen, Hey re only does what's harmless without your phone being unlocked: the time, timers, alarms, weather, the torch and similar. Calls, messages, switches, your check-in and notifications wait until you unlock.

## Deleting your data

Clear Hey re's storage in Android settings, or uninstall it. To remove access you gave it, revoke it in Android settings (Health Connect, notification access), in Shizuku, or in your Google account (Google Tasks, Google Home).
