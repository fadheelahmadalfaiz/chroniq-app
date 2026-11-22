# Product Requirements Document (PRD): Aplikasi Alarm Berbasis Tanggal (Date-Specific Alarm)

| Atribut | Detail |
| :--- | :--- |
| **Nama Produk (Sementara)** | ChronoDate Alarm |
| **Versi Dokumen** | 1.1 (MVP & Google Calendar Sync Specification) |
| **Target Platform** | Android (Prioritas Utama) & iOS |
| **Status** | Draft Siap Review Engineering & Desain |

---

## 1. Latar Belakang & Pernyataan Masalah

Aplikasi alarm bawaan pada sebagian besar sistem operasi hanya berfokus pada rutinitas harian atau mingguan (pengulangan Senin hingga Minggu). Pengguna kesulitan menyetel alarm untuk tanggal spesifik di masa depan tanpa harus mengingat untuk mengaktifkannya sehari sebelumnya.

Di sisi lain, aplikasi kalender seperti **Google Calendar** atau Apple Calendar sangat handal dalam penjadwalan acara berbasis tanggal, namun notifikasinya hanya berupa suara singkat (*ping*) yang mudah terlewat, tidak berbunyi terus-menerus, dan sering kali tertahan oleh mode *Silent* atau *Do Not Disturb* (DND).

**Solusi:** Aplikasi alarm khusus yang menggabungkan presisi penjadwalan berbasis tanggal (manual maupun **otomatis terhubung dengan Google Calendar**) dengan keandalan dering alarm layar penuh (*full-screen ringing*) yang dipastikan membangunkan pengguna.

---

## 2. Target Pengguna & Skenario Penggunaan

* **Pengguna Aktif Google Calendar:** Profesional dan mahasiswa yang rutin mencatat jadwal rapat, ujian, penerbangan, atau tenggat waktu di Google Calendar, tetapi sering melewatkan notifikasi kalender bawaan.
* **Pekerja Shift (Perawat, Staf Pabrik, Kru Penerbangan):** Memiliki jadwal kerja tidak beraturan yang sering diimpor/disinkronkan ke Google Calendar dan membutuhkan alarm keras otomatis berdasarkan jadwal tersebut.
* **Pelancong & Komuter:** Membutuhkan alarm keras pada tanggal keberangkatan penerbangan/kereta yang sudah tercatat di Google Calendar.
* **Pengguna Jadwal Berkala Non-Mingguan:** Rutinitas spesifik (misalnya minum obat tiap interval tertentu atau bayar tagihan bulanan) yang tercatat di Google Calendar.

---

## 3. Ruang Lingkup Fitur (MVP vs. Fase Lanjutan)

| Kategori | Fitur MVP (Versi 1.0 - 1.1) | Fase Lanjutan (Versi 2.0) |
| :--- | :--- | :--- |
| **Penjadwalan Tanggal** | Tanggal tunggal (*Single Date*), banyak tanggal acak (*Multi-Date Picker*), pengulangan tanggal, dan **Sinkronisasi Otomatis Google Calendar**. | Sinkronisasi dengan Apple Calendar / Outlook; pemrosesan bahasa alami (NLP) saat membuat alarm. |
| **Integrasi Google Calendar** | Otentikasi Google OAuth 2.0, pemilihan kalender yang ingin disinkronkan, filter kata kunci/tag acara, serta pengaturan *offset* alarm (misal: alarm berbunyi 30 menit sebelum event). | Sinkronisasi dua arah (*Two-Way Sync*: membuat alarm di aplikasi otomatis menambah event di Google Calendar). |
| **Tampilan Utama** | *List View* (diurutkan berdasarkan waktu terdekat + hitung mundur), *Calendar View*, dan indikator status sinkronisasi Google Calendar. | *Home Screen Widget* untuk melihat alarm tanggal & jadwal Google Calendar terdekat. |
| **Eksekusi Alarm** | Dering layar penuh (*Full-screen*), tombol *Dismiss* & *Snooze*, dukungan *Lock Screen*, kustomisasi nada/getar/volume. | Misi mematikan alarm (*Math puzzle*, *Barcode scan*) dan *Pre-alarm* (peringatan lembut). |
| **Manajemen & Data** | Penyimpanan lokal (*Offline-first*), arsip/hapus otomatis, skema pemetaan ID Event Google Calendar. | Sinkronisasi *cloud* antar perangkat berbasis akun ChronoDate. |

---

## 4. Persyaratan Fungsional & User Stories

### 4.1 Pembuatan & Pengeditan Alarm Tanggal (Manual)
* **Pemilihan Tanggal Fleksibel:** Pengguna dapat memilih satu tanggal spesifik atau mengetuk beberapa tanggal sekaligus (*multi-date selection*) dalam pemilih kalender interaktif.
* **Validasi Waktu:** Mencegah penyimpanan alarm pada tanggal/waktu yang sudah lewat secara otomatis.
* **Atribut Alarm:** Jam/menit, tanggal/daftar tanggal, label, nada dering, getar, volume *fade-in*, durasi *snooze*, dan *Post-Alarm Behavior* (Arsip, Off, Hapus).

### 4.2 Integrasi & Sinkronisasi Google Calendar (Fitur Baru)
* **Otentikasi Akun:** Login aman menggunakan Google OAuth 2.0. Pengguna dapat menghubungkan satu atau lebih akun Google.
* **Pemilihan Kalender (Multi-Calendar Support):** Pengguna dapat memilih kalender spesifik mana saja yang ingin diimpor (misal: Kalender Utama, Kalender Pekerjaan, Kalender Jadwal Shift).
* **Aturan Sinkronisasi Alarm (*Sync Rules*):**
  * **Waktu Pemicu (*Alarm Offset*):** Opsi untuk menyetel alarm berbunyi tepat saat acara dimulai, atau $N$ menit/jam sebelum acara (misal: 15 menit, 30 menit, 1 jam sebelum *event*).
  * **Filter Acara (*Event Filter*):** 
    * *Sync All Events:* Impor semua acara bertarget waktu.
    * *Keyword Filter:* Hanya buat alarm jika judul atau deskripsi acara mengandung kata kunci tertentu (contoh: `#alarm`, `[ALARM]`, `Shift`, `Ujian`).
    * *Ignore All-Day Events:* Pilihan untuk mengabaikan atau menyetel waktu khusus untuk acara *All-day* (sepanjang hari).
* **Latar Belakang & Sinkronisasi Berkala:** Aplikasi melakukan sinkronisasi otomatis di latar belakang secara berkala (misal: setiap 6 jam) atau sinkronisasi manual secara instan melalui gestur *pull-to-refresh*.
* **Penanganan Perubahan Acara di Google Calendar:**
  * Jika acara di Google Calendar diubah (waktu/tanggal bergeser), alarm yang terhubung di aplikasi otomatis diperbarui.
  * Jika acara di Google Calendar dihapus, alarm yang sesuai otomatis dinonaktifkan atau dihapus (sesuai preferensi pengguna).

### 4.3 Tampilan & Navigasi Utama
* **Tab Daftar (*Upcoming List*):** Alarm hasil sinkronisasi Google Calendar diberi lencana (*badge*) khusus bertuliskan ikon Google Calendar beserta nama kalendernya.
* **Tab Kalender (*Calendar View*):** Menampilkan titik penanda beda warna antara alarm manual dan alarm hasil sinkronisasi kalender.

### 4.4 Layar Eksekusi Alarm (Saat Berbunyi)
* **Prioritas Tinggi & Lock Screen:** Membangunkan layar terkunci dengan tampilan penuh. Menampilkan judul acara dari Google Calendar, lokasi acara (jika ada), dan waktu mulai acara.
* **Interaksi Anti-Salah Sentuh:** Tombol *Slide to Dismiss* dan tombol *Snooze* besar.

### 4.5 Ringkasan User Stories & Acceptance Criteria

| ID | User Story | Acceptance Criteria (Kriteria Penerimaan) |
| :--- | :--- | :--- |
| **US-01** | Sebagai pengguna, saya ingin menyetel alarm pada tanggal spesifik di masa depan agar tidak melewatkan jadwal penting. | Pengguna dapat memilih tanggal spesifik hingga 5 tahun ke depan dan menerima notifikasi sisa waktu saat alarm disimpan. |
| **US-02** | Sebagai pekerja shift, saya ingin memilih 5 tanggal acak dalam satu bulan untuk jam yang sama. | Entri alarm menyimpan banyak tanggal sekaligus dan otomatis mati setelah tanggal terakhir selesai berbunyi. |
| **US-05 (Baru)** | Sebagai pengguna Google Calendar, saya ingin mengoneksikan akun Google saya agar acara di kalender otomatis terbuat menjadi alarm keras. | Pengguna dapat login Google, memilih kalender yang disinkronkan, dan acara baru di Google Calendar otomatis muncul sebagai alarm dalam waktu kurang dari 1 menit setelah sinkronisasi. |
| **US-06 (Baru)** | Sebagai pengguna, saya ingin alarm menyesuaikan diri jika jadwal di Google Calendar saya berubah. | Ketika jadwal acara di Google Calendar digeser dari pukul 10:00 ke 14:00, waktu alarm terhubung otomatis berubah ke 14:00 (atau mengikuti *offset* yang ditentukan). |

---

## 5. Persyaratan Non-Fungsional (NFR)

* **Keandalan Dering:** Dering alarm 99,9% tepat waktu walau dihasilkan dari sinkronisasi kalender eksternal.
* **Offline Resilience:** Setelah acara Google Calendar berhasil disinkronkan dan tersimpan di database lokal, alarm tetap wajib berbunyi tepat waktu meskipun saat alarm harus berbunyi perangkat dalam keadaan *offline* tanpa internet.
* **Keamanan Data & Privasi:** Token akses OAuth Google disimpan terenkripsi di *Keystore* (Android) / *Keychain* (iOS). Data kalender hanya digunakan untuk penjadwalan alarm lokal dan tidak dijual/dikirim ke server pihak ketiga.

---

## 6. Desain Model Data (Skema Lokal Terperinci)

Skema database lokal diperbarui untuk menangani relasi dengan entitas Google Calendar:

```json
{
  "alarm": {
    "id": "uuid-v4",
    "source_type": "GOOGLE_CALENDAR", 
    "gcal_event_id": "gcal_evt_987654321",
    "gcal_calendar_id": "user@gmail.com",
    "label": "Rapat Klien - Proyek Alpha",
    "hour": 8,
    "minute": 30,
    "recurrence_type": "SINGLE_DATE",
    "is_enabled": true,
    "sound_uri": "content://media/internal/audio/media/12",
    "vibrate": true,
    "volume_level": 90,
    "offset_minutes_before": 30, 
    "post_alarm_action": "AUTO_ARCHIVE",
    "sync_status": "SYNCED",
    "last_synced_at": "2026-11-22T05:15:00Z"
  },
  "alarm_dates": [
    {
      "id": "uuid-date-1",
      "alarm_id": "uuid-v4",
      "target_date": "2026-12-10",
      "status": "SCHEDULED"
    }
  ]
}
```

---

## 7. Risiko Teknis & Mitigasi Platform

| Risiko | Dampak | Strategi Mitigasi |
| :--- | :--- | :--- |
| **Pembatasan *Exact Alarm* Android 12+** | Alarm tertunda karena *Doze Mode*. | Gunakan `AlarmManager.setAlarmClock()` dan minta izin `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`. |
| **Masa Kadaluarsa Token OAuth Google** | Sinkronisasi latar belakang gagal karena *refresh token* kedaluwarsa atau dicabut. | Tangani pembaharuan token secara otomatis (*auto refresh token*). Jika gagal, tampilkan notifikasi sistem *"Silakan hubungkan ulang akun Google Calendar Anda"*. |
| **Batas Kuota Google Calendar API (Rate Limiting)** | API *request* tertahan jika polling terlalu sering. | Gunakan *Incremental Sync* dengan `syncToken` dari Google Calendar API sehingga aplikasi hanya meminta data perubahan terbaru, bukan mengunduh ulang seluruh kalender. |
| **Perubahan Jadwal Secara Mendadak saat Offline** | Perubahan di web Google Calendar tidak masuk ke ponsel yang sedang mati internet. | Tampilkan indikator *"Terakhir disinkronkan X jam lalu"* pada alarm berbasis Google Calendar agar pengguna menyadari jika data belum diperbarui. |

---

## 8. Metrik Keberhasilan (KPI)

* **Alarm Reliability Rate:** $\ge 99,9\%$ keberhasilan pemicuan tepat waktu.
* **Google Sync Adoption Rate:** minimal $45\%$ dari total pengguna aktif menghubungkan minimal 1 akun Google Calendar.
* **Sync Accuracy Rate:** $\ge 98\%$ perubahan pada Google Calendar berhasil terefleksi di aplikasi dalam kurun waktu sinkronisasi yang ditentukan.

---

## 9. Roadmap Pengembangan (Estimasi 9 Minggu untuk MVP + GCal Sync)

1. **Minggu 1–2 (Desain UI/UX & Arsitektur):** Wireframe UI daftar alarm, integrasi *Calendar View*, dan alur *onboarding* login Google.
2. **Minggu 3–4 (Core Alarm Engine):** Implementasi `AlarmManager`/`AlarmKit`, pemilih tanggal jamak (*multi-date*), dan layar dering *Lock Screen*.
3. **Minggu 5–6 (Integrasi Google Calendar API):** Implementasi Google OAuth 2.0, pengambilan daftar kalender, logika *sync engine* (`syncToken`), serta *offset manager* alarm.
4. **Minggu 7 (Edge Cases & Background Sync):** Pengujian *Background WorkManager*, perubahan acara (edit/hapus), serta pembatalan token.
5. **Minggu 8 (QA & Testing Fisik):** Pengujian menyeluruh pada berbagai merek ponsel Android (uji *Doze Mode*, uji ganti zona waktu, uji *offline*).
6. **Minggu 9 (Beta & Release 1.1):** Peluncuran versi Beta publik dan rilis resmi di Play Store / App Store.