# Note di sviluppo

Informazioni per chi mantiene il progetto. Il [README](README.md) è rivolto a chi usa l'app.

## Tecnologie

- Kotlin, senza librerie: solo le API di sistema di Android (niente Jetpack Compose, AndroidX o Material Components).
  L'unica dipendenza è la libreria standard di Kotlin, ridotta da R8 alle poche funzioni usate.
- Android Gradle Plugin 9.3.3 con Kotlin integrato, Gradle 9.5.0, JDK 17+ (la CI usa il 21).
- `compileSdk`/`targetSdk` 37, `minSdk` 26 (Android 8.0). Le versioni sono in `gradle/libs.versions.toml`.

## Struttura

```
.github/workflows/build.yml        Test, compilazione, firma e Release su GitHub Actions
gradle/libs.versions.toml          Versioni di plugin e librerie
app/build.gradle.kts               Configurazione dell'app e della firma
app/src/main/java/io/github/calcolatrice/
├── MainActivity.kt                Calcolatrice: layout, cronologia, salvataggio, tastiera fisica
├── EquationActivity.kt            Equazioni e sistemi: tabella dei coefficienti e risultati
├── engine/                        Motore di calcolo (Kotlin puro, senza Android)
│   ├── Evaluator.kt               Tokenizer, parser ed esecuzione delle espressioni
│   ├── CalculatorEditor.kt        Regole di inserimento dei tasti
│   ├── CalculatorSession.kt       Stato della calcolatrice e cronologia
│   ├── Formatting.kt              Formattazione di numeri e simboli
│   ├── Equations.kt               Sistemi lineari, 2° grado, frazioni, testo dei risultati
│   └── EquationSheet.kt           Stato della schermata equazioni (celle, selezione, risultato)
└── ui/
    ├── KeyButton.kt               Tasto disegnato a mano e colori del tema
    ├── Keys.kt                    Definizione dei tasti e delle azioni
    ├── KeyPad.kt                  Costruzione di righe e griglie di tasti, tastiera fisica
    └── ViewUtils.kt               Edge-to-edge, insets, sfondi e altre utilità condivise
app/src/main/res/                  Testi (it/en), colori, icone, tema di sistema
app/src/test/                      Test unitari del motore di calcolo
```

### Formato interno delle espressioni

L'editor costruisce un testo che il parser valuta a ogni tasto per l'anteprima:

- numeri con il punto decimale, eventualmente `1.5E-7`; operatori `+ - * / ^`; postfissi `!` e `%`
- `π`, `e`, `@` (risultato precedente, a piena precisione), `√` prefisso, funzioni `sin(` `asin(` `ln(` `log(` ecc.
- la moltiplicazione implicita è sempre scritta esplicitamente dall'editor (`2π` → `2*π`)

Regole di precedenza da tenere presenti: `-2^2 = -4`, `2^3^2 = 2^9`, `√` si applica solo all'operando
che segue (`√9! = (√9)!`), e dopo `+`/`−` una percentuale semplice si riferisce al valore precedente
(`200+10% = 220`).

Le operazioni di base, le potenze intere e `√` usano `BigDecimal` a 34 cifre; trigonometria, logaritmi
e potenze non intere passano per `double` e sono arrotondati a 15 cifre, la stessa precisione mostrata a schermo.

### Equazioni e sistemi

- **Sistemi lineari** (`LinearSystem`): eliminazione di Gauss con pivot parziale a 34 cifre. Un pivot o
  un termine noto residuo sotto 10⁻²⁵ volte la scala dei dati conta come zero: così `1/3`, che a 34 cifre
  non è un terzo esatto, non fa sembrare risolubile un sistema singolare. Funziona per n qualsiasi;
  l'interfaccia offre 2 e 3 incognite.
- **Secondo grado** (`QuadraticEquation`): Δ è calcolato esatto e poi ripulito dagli stessi residui;
  le radici reali usano la formula stabile (`q = −(b + sgn(b)·√Δ)/2`, `x₁ = q/a`, `x₂ = c/q`), che non perde
  cifre quando b² ≫ 4ac. Con `a = 0` si risolve l'equazione di primo grado.
- **Frazioni** (`Fractions`): il risultato decimale viene riconosciuto con le frazioni continue, con
  denominatore fino a 1 000 000 e numeratore fino a 12 cifre; altrimenti resta decimale. Non ci sono
  forme esatte con radicali (`(1+√5)/2`): servirebbe un motore simbolico.
- **Coefficienti**: ogni cella è un'espressione modificata con `CalculatorEditor`, quindi segue le stesse
  regole della calcolatrice. Le celle sono salvate in `SharedPreferences` (file `equations`).

## Compilare in locale

```bash
./gradlew testDebugUnitTest   # test unitari
./gradlew assembleDebug       # APK di debug
./gradlew assembleRelease     # APK di release (firmato con la chiave di debug se non c'è un keystore)
```

Oppure apri la cartella in Android Studio (File → Open).

## Compilazione automatica (GitHub Actions)

Il workflow **Build APK** parte:

- a ogni push sul branch `calcolatrice-lite`;
- a ogni tag che inizia con `v` (per esempio `v1.2.0`): in questo caso crea anche una Release con l'APK allegato;
- a mano, da Actions → Build APK → **Run workflow**.

Esegue i test, compila l'APK di release, verifica che sia firmato con il certificato atteso e lo carica
come artifact (`calcolatrice-lite-<versione>-SIGNED`). Il `versionCode` è il numero dell'esecuzione;
il `versionName` è il nome del tag, oppure `1.0.<numero esecuzione>`.

La firma usa **solo la chiave permanente**: se i Secrets non sono configurati, il workflow si ferma
con un errore invece di produrre un APK firmato con una chiave diversa.

### Configurare la chiave di firma

Serve una volta sola.

1. Crea il keystore (formato PKCS12, con questo alias):
   ```bash
   keytool -genkeypair -v -keystore calcolatrice-lite.p12 -storetype PKCS12 \
     -alias calcolatrice-lite -keyalg RSA -keysize 4096 -validity 10000
   ```
   In PKCS12 la password della chiave coincide con quella del keystore.
   **Conserva il file e la password in un posto sicuro:** senza, non potrai più pubblicare aggiornamenti
   installabili sopra le versioni esistenti.

2. Convertilo in Base64:
   - macOS / Linux: `base64 -i calcolatrice-lite.p12 | tr -d '\n' > keystore.txt`
   - Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("calcolatrice-lite.p12")) > keystore.txt`

3. In GitHub → **Settings** → **Secrets and variables** → **Actions** crea due secret:

   | Nome | Valore |
   |---|---|
   | `LITE_KEYSTORE_BASE64` | il contenuto di `keystore.txt` |
   | `LITE_KEYSTORE_PASSWORD` | la password del keystore |

Non caricare mai il keystore o `keystore.txt` nel repository: il `.gitignore` esclude già `*.p12`, `*.jks`,
`*.keystore` e `keystore.txt`.

### Pubblicare una versione

```bash
git tag v1.2.0
git push origin v1.2.0
```

## Personalizzare

- **Nome del pacchetto:** `applicationId` in `app/build.gradle.kts`. Va deciso prima di distribuire l'app.
- **Nome dell'app:** `app/src/main/res/values-it/strings.xml` (italiano) e `values/strings.xml` (inglese).
- **Colori:** `app/src/main/res/values*/colors.xml` (le cartelle `-v31` contengono i colori dinamici di Android 12+).
