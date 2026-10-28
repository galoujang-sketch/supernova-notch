# Supernova Notch

A Dynamic Island–style notch for Android tablets. It sits at the top edge of the screen and turns into a small space scene when idle: two stars orbit each other, merge, and burst into a tiny supernova.

> Built and tested on a **Huawei MatePad 11.5 (HarmonyOS 3.1)**. Other Android tablets (Android 8+) should work but are not yet tested. See [Known limitations](#known-limitations).

**Website:** https://galoujang-sketch.github.io/supernova-notch/ · **[Download APK](https://github.com/galoujang-sketch/supernova-notch/releases/latest/download/SupernovaNotch.apk)**

Inspired by [Nusa](https://rama2555.github.io/nusa/), which was the reference for building this app. / Terinspirasi dari [Nusa](https://rama2555.github.io/nusa/), yang jadi referensi untuk membuat aplikasi ini.

**English** · [Bahasa Indonesia](#bahasa-indonesia)

## Features

- **Music**: cover art, title, artist, seek bar, previous / play / next. Works with Spotify, YouTube Music and other players that show a media notification.
- **Synced lyrics**: lines scroll across the top bar, passing behind the notch like karaoke (lyrics from [LRCLIB](https://lrclib.net)).
- **Clean top bar**: while music plays, a black strip covers the system status bar and its info (time, notifications, Wi-Fi, battery, Bluetooth) moves into the notch. When music stops the strip fades out and the normal status bar comes back.
- **Focus timer**: Pomodoro with adjustable focus and break lengths, countdown shown in the notch.
- **Water reminder** and a week calendar.
- **HUD pop-ups**: charger plugged / unplugged (with a bolt on the battery icon while charging), headphones connected, volume, Wi-Fi lost.
- **Hides in fullscreen** apps (video, games). Can be turned off.
- **Nova**, the mascot: a living idle scene that wakes up with a flash when you come back.

## Install

1. Download `SupernovaNotch.apk` from the [latest release](../../releases/latest).
2. Allow installing from your browser or file manager ("install unknown apps"), then open the APK.
3. Open the app and grant the permissions it lists:
   - **Display over other apps**: to draw the notch.
   - **Notification access**: to read what is playing and show notification icons.
   - **Accessibility** (recommended): draws the notch above the status bar so it can sit flush with the top edge and be tapped. On **Android 13+** you may first need *App info → ⋮ → Allow restricted settings*.
   - **Disable battery optimization**: otherwise the system may stop the notch in the background.

## Privacy

- No accounts, no ads, no analytics.
- The Accessibility service does **not** read the screen or what you type (`canRetrieveWindowContent = false`).
- Network requests are only for lyrics (`lrclib.net`, sending just the song title, artist and duration) and, when your player provides a web link, the cover image.
- Water count and timer settings are stored on the device only.

## FAQ

**Why is there a black strip at the top?** Apps cannot hide the system status bar, only cover it. The strip does that while music plays, so the status info can live inside the notch instead.

**The notch disappears after a while.** Turn off battery optimization for the app. Some Android skins close background apps aggressively.

**The notch hides when it should not (or does not hide in fullscreen).** Fullscreen detection depends on the device. You can turn it off in the app.

**Lyrics are missing for a song.** Lyrics come from LRCLIB; songs that are not in its database show no lyrics.

**Why not on the Play Store?** It relies on an Accessibility service for a display feature, which the Play Store restricts. It is distributed as an APK instead.

**How much RAM does it use?** About 44 MB, measured on a Huawei MatePad 11.5 (HarmonyOS 3.1) in *Settings → Apps → App usage* over 3 hours with music, lyrics and the idle scene in normal use. For comparison, the same list showed Huawei Home at 119 MB, Facebook at 92 MB and Gboard at 72 MB. This is one device and one session, so your numbers may differ. To check on your device: *Settings → Apps → App usage* or Developer options → *Running services*.

**How much battery does it use?** On a Huawei MatePad 11.5 (HarmonyOS 3.1), *Settings → Battery → Battery usage* showed Supernova Notch at 2% of the day's total consumption, after about 6 hours of screen-on time that included music playback. In the same list Spotify was 16% and Chrome 29%. Note that this is a share of total usage, not 2% of the battery itself. It is one device and one day, so your numbers may differ. What affects power:
- Screen off: nothing is drawn.
- Idle scene: 20 frames per second for the first 5 minutes, then frozen to a still image.
- Music playing: the notch refreshes about twice per second and the lyrics scroll at about 30 frames per second while the strip is visible.
- Fullscreen apps: the notch is hidden and stops drawing.

To check on your device: *Settings → Battery → Battery usage* after a day of normal use. If it is too high for you, turn off clean mode in the app.

**How do I update?** Install the newer APK over the old one.

## Known limitations

- Tested on one device so far. Reports from other tablets are very welcome.
- The system volume panel still appears together with the notch volume HUD.
- Bluetooth headphone battery level is not shown (Android does not expose it to normal apps).
- Notch is centered at the top; position and size are not configurable yet.

## Build

```
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK. Releases are built by GitHub Actions on every push to `main`. To sign with your own key, set the repository secrets `KEYSTORE_B64` (base64 of the keystore, alias `supernova`) and `KEYSTORE_PASSWORD`.

## License

[MIT](LICENSE)

---

## Bahasa Indonesia

Notch ala Dynamic Island untuk tablet Android. Menempel di tepi atas layar, dan saat idle berubah jadi pemandangan antariksa mini: dua bintang saling mengorbit, menyatu, lalu meledak jadi supernova kecil.

> Dibuat dan diuji di **Huawei MatePad 11.5 (HarmonyOS 3.1)**. Tablet Android lain (Android 8 ke atas) seharusnya bisa, tapi belum diuji.

**Fitur**: kontrol musik dengan cover, lirik tersinkron yang bergulir di belakang notch, bar atas yang bersih (info status pindah ke notch saat musik main), timer fokus dengan durasi bisa diatur, pengingat air minum, kalender mingguan, HUD (charger, headphone, volume, Wi-Fi), sembunyi saat layar penuh, dan maskot Nova.

**Pasang**
1. Unduh `SupernovaNotch.apk` dari [rilis terbaru](../../releases/latest).
2. Izinkan pemasangan dari sumber tidak dikenal, lalu buka APK.
3. Buka aplikasi dan beri izin: tampil di atas aplikasi lain, akses notifikasi, Aksesibilitas (disarankan; di Android 13+ buka *Info aplikasi → ⋮ → Izinkan setelan terbatas* dulu), dan matikan hemat baterai.

**Privasi**: tanpa akun, iklan, atau pelacakan. Layanan Aksesibilitas tidak membaca layar maupun ketikan. Permintaan jaringan hanya untuk lirik (`lrclib.net`, hanya mengirim judul, artis, dan durasi lagu) dan, bila pemutar memberi tautan web, gambar cover. Data air minum dan timer disimpan di tablet saja.

**FAQ singkat**
- *Kenapa ada strip hitam?* Aplikasi tidak bisa menyembunyikan status bar, hanya menutupnya. Strip muncul saat musik main.
- *Notch hilang sendiri?* Matikan hemat baterai untuk aplikasi ini.
- *Lirik tidak muncul?* Lagu tidak ada di database LRCLIB.
- *Berapa RAM yang dipakai?* Sekitar 44 MB, diukur di Huawei MatePad 11.5 (HarmonyOS 3.1) pada *Pengaturan → Aplikasi → Penggunaan aplikasi* selama 3 jam dengan musik, lirik, dan adegan idle dipakai seperti biasa. Sebagai pembanding, di daftar yang sama Beranda Huawei 119 MB, Facebook 92 MB, Gboard 72 MB. Ini hasil satu perangkat dan satu sesi, jadi angka di perangkat lain bisa berbeda. Cek di *Pengaturan → Aplikasi → Penggunaan aplikasi* atau Opsi pengembang → *Layanan yang berjalan*.
- *Berapa persen baterai yang dipakai?* Di Huawei MatePad 11.5 (HarmonyOS 3.1), *Pengaturan → Baterai → Penggunaan baterai* menunjukkan Supernova Notch 2% dari total konsumsi hari itu, setelah sekitar 6 jam layar menyala termasuk memutar musik. Di daftar yang sama Spotify 16% dan Chrome 29%. Perlu dicatat, angka itu porsi dari total pemakaian, bukan 2% dari baterai. Ini hasil satu perangkat dan satu hari. Layar mati: tidak ada yang digambar. Adegan idle: 20 bingkai per detik selama 5 menit pertama, lalu dibekukan jadi gambar diam. Musik main: notch menyegarkan diri sekitar dua kali per detik dan lirik berjalan sekitar 30 bingkai per detik selama strip tampil. Aplikasi layar penuh: notch tersembunyi dan berhenti menggambar.
- *Cara memperbarui?* Pasang APK terbaru di atas yang lama.

Lisensi: [MIT](LICENSE).
