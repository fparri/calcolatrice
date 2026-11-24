# Calcolatrice Android Lite (Zero Dipendenze)

App calcolatrice ultra-leggera in **Kotlin puro** senza librerie esterne (niente Jetpack Compose, niente AndroidX, niente Material library).

## Caratteristiche
- **Zero dipendenze esterne:** usa solo le API native del sistema operativo Android.
- **APK ultra-compatto (~50–150 KB):** compilato in modalità Release con ottimizzatore R8 e rimozione metadati superflui.
- **UI moderna:** supporto **Dynamic Color** nativo (Android 12+), tema chiaro/scuro automatico, layout **Edge-to-Edge**, tasti circolari con effetto ripple e feedback aptico.
- **Precisione decimale:** calcoli basati su `BigDecimal`.

## Come ottenere l'APK
1. Carica i file su un repository GitHub (branch `main`).
2. Apri la scheda **Actions** e attendi il completamento di **Build Android Lite APK**.
3. Scarica l'artifact **Calcolatrice-Lite-APK**, estrai lo `.zip` e installa `app-release.apk`.
