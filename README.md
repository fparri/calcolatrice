# Calcolatrice

Calcolatrice scientifica per Android scritta in **Kotlin** con **Jetpack Compose** e **Material 3**.
L'APK viene compilato automaticamente da **GitHub Actions** a ogni push: non serve installare Android Studio.

## Funzionalità

- Operazioni di base, percentuale, parentesi "intelligenti" (un solo tasto apre e chiude)
- Funzioni scientifiche: sin, cos, tan e inverse, ln, log, eˣ, 10ˣ, √, x², xʸ, x!, π, e, 1/x
- Gradi e radianti, con valori esatti per gli angoli notevoli (sin 180° = 0, tan 90° = errore)
- Calcolo a 34 cifre di precisione con numeri decimali esatti (0,1 + 0,2 = 0,3)
- Anteprima del risultato mentre scrivi; dopo "=" puoi continuare dal risultato
- Cronologia persistente: tocca un calcolo per riusarne il risultato
- Pressione prolungata sul display: copia il risultato; pressione prolungata su ⌫: cancella tutto
- Separatori decimali e delle migliaia secondo la lingua del telefono (1.234,5 in italiano)
- Supporto per tastiere fisiche (tablet, Chromebook, tastiere Bluetooth)

## Standard Android adottati

| Area | Cosa usa l'app |
|---|---|
| UI | Jetpack Compose, Material 3, tasti con forma animata in stile Material 3 Expressive |
| Colori | Colori dinamici (Material You) da Android 12, tema chiaro/scuro automatico |
| Schermo | Edge-to-edge, gestione di notch e barre di sistema, gesto Indietro predittivo |
| Layout adattivo | Verticale con pannello scientifico a scomparsa; orizzontale e tablet con tutti i tasti |
| Icona | Icona adattiva con versione monocromatica per le icone a tema (Android 13+) |
| Lingue | Italiano e inglese, con scelta della lingua per singola app (Android 13+) |
| Accessibilità | Descrizioni per TalkBack su ogni tasto, risultato annunciato, testo scalabile |
| Architettura | ViewModel + StateFlow, stato salvato anche se Android chiude l'app in background |
| Build | AGP 9 con Kotlin integrato, version catalog, R8 per il rilascio, test unitari in CI |

Versioni principali: Android Gradle Plugin 9.3.3, Gradle 9.5.0, Kotlin 2.4.20, Compose BOM 2026.09.00,
`compileSdk`/`targetSdk` 37, `minSdk` 26 (Android 8.0 o successivo).

## 1. Caricare il progetto su GitHub

1. Crea un nuovo repository su GitHub (può essere privato).
2. Carica tutti i file di questa cartella, mantenendo la struttura delle sottocartelle.
   - **Da terminale** (consigliato):
     ```bash
     cd Calcolatrice
     git init
     git add .
     git commit -m "Prima versione"
     git branch -M main
     git remote add origin https://github.com/TUO-UTENTE/TUO-REPO.git
     git push -u origin main
     ```
   - **Dal sito di GitHub** ("Add file" → "Upload files"): trascina il *contenuto* della cartella.
     Attenzione alla cartella nascosta `.github` (contiene il workflow) e al file `.gitignore`:
     su Mac premi `Cmd + Shift + .` nel Finder per vederli. Se la cartella `.github` non viene caricata,
     crea il file da GitHub con "Add file" → "Create new file", come nome scrivi
     `.github/workflows/build.yml` e incolla il contenuto del file.

## 2. Scaricare l'APK

1. Apri il repository → tab **Actions** → clicca sull'ultima esecuzione di **Build APK**.
2. Quando la compilazione è finita (circa 5 minuti la prima volta), in fondo alla pagina,
   nella sezione **Artifacts**, scarica `calcolatrice-debug-…`.
3. Estrai lo zip e copia il file `.apk` sul telefono. Aprilo e consenti l'installazione da fonti sconosciute
   quando Android lo chiede.

La compilazione riparte da sola a ogni modifica caricata sul ramo `main`. Puoi avviarla anche a mano
da Actions → Build APK → **Run workflow**.

> **APK di debug e aggiornamenti:** ogni compilazione su GitHub usa una chiave di debug diversa, quindi per
> installare una nuova versione devi prima disinstallare quella vecchia. Per aggiornare senza disinstallare,
> configura la firma di release (punto 3).

## 3. (Facoltativo) Firma di release

Serve una volta sola e permette di installare gli aggiornamenti sopra la versione precedente.

1. **Crea il keystore** sul tuo computer (serve Java; `keytool` è incluso nel JDK):
   ```bash
   keytool -genkeypair -v -keystore calcolatrice.jks -alias calcolatrice \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
   Ti chiederà una password e alcuni dati (nome, città…). **Conserva il file e la password in un posto
   sicuro:** se li perdi, non potrai più pubblicare aggiornamenti della stessa app.

2. **Converti il keystore in testo** (Base64):
   - macOS / Linux: `base64 -i calcolatrice.jks | tr -d '\n' > keystore.txt`
   - Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("calcolatrice.jks")) > keystore.txt`

3. **Aggiungi i Secrets** su GitHub: repository → **Settings** → **Secrets and variables** → **Actions** →
   **New repository secret**. Creane quattro:

   | Nome | Valore |
   |---|---|
   | `SIGNING_KEYSTORE_BASE64` | il contenuto di `keystore.txt` |
   | `SIGNING_STORE_PASSWORD` | la password del keystore |
   | `SIGNING_KEY_ALIAS` | `calcolatrice` (l'alias usato nel comando) |
   | `SIGNING_KEY_PASSWORD` | la password della chiave (di solito la stessa) |

4. Da qui in poi ogni build produce anche l'artifact `calcolatrice-release-…` con l'APK firmato.

5. **Pubblicare una versione:** crea un tag che inizia con `v`, per esempio
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   Il workflow crea una **Release** su GitHub con l'APK allegato, scaricabile direttamente dal telefono.

Non caricare mai il file `.jks` o `keystore.txt` nel repository (il `.gitignore` esclude già i keystore).

## Personalizzare

- **Nome del pacchetto:** in `app/build.gradle.kts` cambia `applicationId` (per esempio `it.tuonome.calcolatrice`).
  Va deciso prima di distribuire l'app: dispositivi e Play Store la riconoscono da questo nome.
- **Nome dell'app:** `app/src/main/res/values-it/strings.xml` (italiano) e `values/strings.xml` (inglese).
- **Colori di riserva** (usati prima di Android 12): `app/src/main/java/io/github/calcolatrice/ui/theme/Theme.kt`.
- **Versioni delle librerie:** `gradle/libs.versions.toml`.

## Struttura del progetto

```
.github/workflows/build.yml        Compilazione automatica su GitHub Actions
gradle/libs.versions.toml          Versioni di plugin e librerie
app/build.gradle.kts               Configurazione dell'app e della firma
app/src/main/java/io/github/calcolatrice/
├── MainActivity.kt                Avvio, edge-to-edge
├── CalculatorViewModel.kt         Stato, cronologia, salvataggio
├── engine/                        Motore di calcolo (Kotlin puro, senza Android)
│   ├── Evaluator.kt               Parser ed esecuzione delle espressioni
│   ├── CalculatorEditor.kt        Regole di inserimento dei tasti
│   └── Formatting.kt              Formattazione di numeri e simboli
└── ui/                            Interfaccia Compose
    ├── CalculatorScreen.kt        Schermata, layout verticale/orizzontale, tastiera fisica
    ├── CalculatorButton.kt        Tasto animato
    ├── Keys.kt                    Definizione dei tasti
    ├── HistorySheet.kt            Cronologia
    └── theme/Theme.kt             Tema Material 3
app/src/test/                      Test unitari del motore di calcolo
```

## Aprire in Android Studio (facoltativo)

File → Open → seleziona la cartella `Calcolatrice`. Android Studio scarica da solo Gradle e l'SDK
necessari; puoi poi avviare l'app su un emulatore o su un telefono collegato via USB.
