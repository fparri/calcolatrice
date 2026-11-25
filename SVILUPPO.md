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
├── MainActivity.kt                Calcolatrice: display con cursore, menu, variabili, cronologia, salvataggio
├── EquationActivity.kt            Equazioni e sistemi: tabella dei coefficienti e risultati
├── StatisticsActivity.kt          Statistica: tabella dei dati e risultati
├── engine/                        Motore di calcolo (Kotlin puro, senza Android)
│   ├── Evaluator.kt               Tokenizer, parser ed esecuzione delle espressioni, variabili
│   ├── BigMath.kt                 exp, ln, sin/cos, atan, √ in BigDecimal a 50 cifre
│   ├── CalculatorEditor.kt        Regole di inserimento dei tasti e cursore
│   ├── CalculatorSession.kt       Stato della calcolatrice: cronologia, variabili, memoria, SOLVE
│   ├── Solver.kt                  SOLVE: Newton smorzato, poi ricerca di un cambio di segno
│   ├── Formatting.kt              Formati NORM/FIX/SCI/ENG, testo per lo schermo, apici, posizioni
│   ├── Equations.kt               Sistemi lineari, 2° e 3° grado, frazioni, testo dei risultati
│   ├── EquationSheet.kt           Stato della schermata equazioni (celle, selezione, risultato)
│   └── Statistics.kt              Statistica a una variabile, regressione, stato della tabella
└── ui/
    ├── KeyButton.kt               Tasto disegnato a mano e colori del tema
    ├── Keys.kt                    Definizione dei tasti e delle azioni
    ├── KeyPad.kt                  Costruzione di righe e griglie di tasti, tastiera fisica
    ├── Settings.kt                Formato dei numeri e variabili, condivisi tra le schermate
    ├── Dialogs.kt                 Dialoghi "salva in una variabile" e "formato dei numeri"
    └── ViewUtils.kt               Edge-to-edge, insets, sfondi e altre utilità condivise
app/src/main/res/                  Testi (it/en), colori, icone, tema di sistema
app/src/test/                      Test unitari del motore di calcolo
```

### Formato interno delle espressioni

L'editor costruisce un testo che il parser valuta a ogni tasto per l'anteprima:

- numeri con il punto decimale, eventualmente `1.5E-7`; operatori `+ - * / ^`; postfissi `!` e `%`
- `π`, `e`, `@` (risultato precedente, a piena precisione), `√` prefisso, funzioni `sin(` `asin(` `ln(` `log(` ecc.
- variabili `A B C D X Y M` (lettere maiuscole); manca la `E`, che indica l'esponente
- la moltiplicazione implicita è sempre scritta esplicitamente dall'editor (`2π` → `2*π`)

Il cursore (`CalculatorState.cursor`, null = in fondo) divide l'espressione in due: le regole
dell'editor lavorano sul testo prima del cursore e quello dopo resta invariato. Quando il cursore non
è in fondo l'espressione non contiene `@` (viene sostituito dal suo valore appena si sposta il cursore).
`DisplayFormatter.layout` restituisce, oltre al testo, la posizione a schermo di ogni carattere interno:
serve per disegnare il cursore e per spostarlo toccando il display.

Regole di precedenza da tenere presenti: `-2^2 = -4`, `2^3^2 = 2^9`, `√` si applica solo all'operando
che segue (`√9! = (√9)!`), e dopo `+`/`−` una percentuale semplice si riferisce al valore precedente
(`200+10% = 220`).

Tutti i calcoli usano `BigDecimal` a 34 cifre. Le funzioni di `BigMath` lavorano a 50 cifre e
arrotondano il risultato: π con la formula di Machin, eˣ dimezzando l'argomento (serie di Taylor e
quadrati successivi), ln con passi di Halley su eˣ, seno e coseno riducendo l'angolo a [−π/4, π/4],
arcotangente dimezzando l'angolo. Le potenze non intere sono `e^(y·ln x)`; `e^x` usa direttamente
l'esponenziale. Residui come sin(π) ≈ 3·10⁻³⁴ (π stesso ha 34 cifre) vengono azzerati.
I test in `PrecisionTest` confrontano i risultati con valori di riferimento calcolati a 40 cifre.

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
- **Terzo grado** (`CubicEquation`): il discriminante è calcolato esatto; se è zero le radici multiple
  si ottengono con formule esatte (così `(x−2)³` dà esattamente 2). Altrimenti una radice reale si trova
  per bisezione entro il limite di Cauchy e Newton, e le altre due dall'equazione di secondo grado che
  resta, rifinite con Newton sul polinomio originale.
- **Coefficienti**: ogni cella è un'espressione modificata con `CalculatorEditor`, quindi segue le stesse
  regole della calcolatrice. Le celle sono salvate in `SharedPreferences` (file `equations`).

### SOLVE

`NumericSolver` cerca uno zero dell'espressione nella variabile X: prima il metodo di Newton con
derivata numerica e passo smorzato (si dimezza finché |f| non diminuisce), poi, se non converge,
cerca un cambio di segno allontanandosi dal valore iniziale e lo restringe alternando bisezione e
secante. Un cambio di segno attraverso un polo (come 1/X) viene scartato controllando |f| alla fine.
Se un valore più "pulito" (intero, o con meno cifre) va bene almeno quanto, si usa quello.

### Statistica

`Statistics` calcola media e deviazioni standard in due passaggi (scarti dalla media, più preciso di
Σx² − n·x̄²) e i quartili come le calcolatrici: mediane delle due metà, esclusa la mediana se n è
dispari. `StatisticsSheet` tiene una tabella per modalità, con una riga vuota finale per aggiungere
dati (massimo 200 righe). I dati sono salvati nel file `statistics`.

### Dati salvati

| File `SharedPreferences` | Contenuto |
|---|---|
| `calculator` | calcolo in corso, cronologia, unità angolare, formato dei numeri, variabili, frazioni sì/no |
| `equations` | coefficienti di ogni modalità, modalità scelta, frazioni sì/no |
| `statistics` | dati di ogni modalità, modalità scelta |

### Scorciatoie dell'icona

`res/xml/shortcuts.xml` definisce le scorciatoie "Equazioni" e "Statistica". Aprono `MainActivity`
(l'unica schermata esportata) con un'azione, e `MainActivity` passa alla schermata richiesta.
**Se cambi `applicationId`, aggiorna anche `targetPackage` in `shortcuts.xml`.**

## Compilare in locale

```bash
./gradlew testDebugUnitTest   # test unitari
./gradlew lintRelease         # controllo statico (report in app/build/reports/)
./gradlew assembleDebug       # APK di debug
./gradlew assembleRelease     # APK di release (firmato con la chiave di debug se non c'è un keystore)
```

Oppure apri la cartella in Android Studio (File → Open).

## Compilazione automatica (GitHub Actions)

Il workflow **Build APK** parte:

- a ogni push sul branch `calcolatrice-lite`;
- a ogni tag che inizia con `v` (per esempio `v1.2.0`): in questo caso crea anche una Release con l'APK allegato;
- a mano, da Actions → Build APK → **Run workflow**.

Esegue i test e Android Lint (il report HTML resta tra gli artifact; gli errori fermano la build),
compila l'APK di release, verifica che sia firmato con il certificato atteso e lo carica come artifact (`calcolatrice-lite-<versione>-SIGNED`). Il `versionCode` è il numero dell'esecuzione;
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

- **Nome del pacchetto:** `applicationId` in `app/build.gradle.kts` (e `targetPackage` in `res/xml/shortcuts.xml`). Va deciso prima di distribuire l'app.
- **Nome dell'app:** `app/src/main/res/values-it/strings.xml` (italiano) e `values/strings.xml` (inglese).
- **Colori:** `app/src/main/res/values*/colors.xml` (le cartelle `-v31` contengono i colori dinamici di Android 12+).
