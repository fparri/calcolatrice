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
├── EquationActivity.kt            Equazioni, sistemi e disequazioni: tabella dei coefficienti e risultati
├── FunctionActivity.kt            Funzioni e grafici: tabella dei valori, grafico, analisi
├── SheetActivity.kt               Base delle tre schermate con caselle: impostazione, layout, strumenti, copia, tastiera fisica
├── StatisticsActivity.kt          Statistica: tabella dei dati e risultati
├── engine/                        Motore di calcolo (Kotlin puro, senza Android)
│   ├── Evaluator.kt               Tokenizer, albero dell'espressione (parser) e valutazione a 34 cifre
│   ├── MathSupport.kt             Potenze, radici, fattoriale, trigonometria e logaritmi a 34 cifre
│   ├── BigMath.kt                 exp, ln, sin/cos, atan, √, erfc in BigDecimal a 50 cifre
│   ├── MoreMath.kt                Menu MATH: radice n-esima, log in base b, nCr, nPr, MCD, mcm, binomiale, normale
│   ├── ExactForms.kt              Frazioni e forme esatte dei risultati: p/q, (p/q)·√r, (p/q)·π
│   ├── Inequalities.kt            Disequazioni di 1° e 2° grado, insiemi di intervalli, sistemi, testo delle soluzioni
│   ├── FastEval.kt                Valutazione in double dello stesso albero, per i grafici
│   ├── FunctionAnalysis.kt        Derivata, integrale, zeri e tabella di f(X) a 34 cifre
│   ├── FunctionSheet.kt           Stato della schermata funzioni (caselle, vista, analisi)
│   ├── TextWindow.kt              Parte di un testo da mostrare su una riga troppo corta
│   ├── CalculatorEditor.kt        Regole di inserimento dei tasti e cursore
│   ├── CalculatorSession.kt       Stato della calcolatrice: cronologia, variabili, memoria, SOLVE
│   ├── Solver.kt                  SOLVE: Newton smorzato, poi ricerca di un cambio di segno
│   ├── Formatting.kt              Formati NORM/FIX/SCI/ENG, testo per lo schermo, apici, posizioni, testo dei risultati
│   ├── Equations.kt               Sistemi lineari, 2° e 3° grado
│   ├── EquationSheet.kt           Stato della schermata equazioni (celle, selezione, risultato)
│   └── Statistics.kt              Statistica a una variabile, regressione, stato della tabella
└── ui/
    ├── KeyButton.kt               Tasto disegnato a mano e colori del tema
    ├── Keys.kt                    Definizione dei tasti e delle azioni
    ├── KeyPad.kt                  Costruzione di righe e griglie di tasti, tastiera fisica
    ├── Settings.kt                Formato dei numeri e variabili, condivisi tra le schermate
    ├── Dialogs.kt                 Dialoghi "salva in una variabile", "formato dei numeri" e menu MATH
    ├── FitTextView.kt             Testo su una riga che si rimpicciolisce fino a una dimensione minima
    ├── GraphView.kt               Grafico disegnato a mano: assi, griglia, curva, trascinamento, zoom, lettura dei punti
    └── ViewUtils.kt               Edge-to-edge, insets, sfondi e altre utilità condivise
app/src/main/res/                  Testi (it/en), colori, icone, tema di sistema
app/src/test/                      Test unitari del motore di calcolo
```

### Formato interno delle espressioni

L'editor costruisce un testo che `ExpressionParser` trasforma in un albero (`Expr`), valutato a ogni tasto
per l'anteprima. Le regole (precedenze, percentuali, moltiplicazione implicita) stanno solo nel parser:
lo stesso albero si valuta a 34 cifre (`Evaluation`) o in double per i grafici (`FastEval`), e SOLVE,
integrali e tabelle lo analizzano una volta sola. Formato del testo:

- numeri con il punto decimale, eventualmente `1.5E-7`; operatori `+ - * / ^`; postfissi `!` e `%`
- `π`, `e`, `@` (risultato precedente, a piena precisione), `√` prefisso, funzioni `sin(` `asin(` `ln(` `log(` ecc.
- funzioni con più argomenti separati da `;`: `log(b;x)`, `root(n;x)`, `ncr(n;k)`, `npr(n;k)`, `gcd(a;b;…)`,
  `lcm(a;b;…)`, `binp(n;p;k)`, `binc(n;p;k)`, `normc(a;b)` e `normc(a;b;μ;σ)`, più `abs(`. Il numero di argomenti
  ammesso è in `Tokenizer.ARITY`; l'editor accetta `;` solo dentro una funzione che ne prevede più d'uno.
  A schermo i nomi interni diventano `nCr(`, `ⁿ√(`, `NormC(`…, e in italiano `MCD(` e `mcm(` (`localFunctionNames`).
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

### Menu MATH

- **Radice n-esima e log in base b**: `exp(ln x / n)` e `ln x / ln b` a 50 cifre; se il risultato è "tondo"
  (³√8, log₂ 8) si verifica elevando a potenza e si restituisce il valore esatto.
- **nCr e nPr**: prodotto esatto con `BigInteger`; prima si stima la grandezza (somma dei logaritmi dei fattori)
  per dare "Numero troppo grande" senza calcolare numeri enormi.
- **Binomiale**: coefficiente e potenze in `BigDecimal` a 50 cifre (niente limiti intermedi: C(10⁵, 5·10⁴) è enorme,
  ma la probabilità no); la cumulativa somma i termini con la ricorrenza P(i+1) = P(i)·(n−i)/(i+1)·p/(1−p). n ≤ 10⁶.
- **Normale**: Φ tramite `BigMath.erfc`, serie di erf per |x| < 3 e frazione continua (Lentz) nelle code,
  così anche probabilità come 7,6·10⁻²⁴ hanno tutte le cifre. Valori controllati con mpmath a 60 cifre.

### Forme esatte

`ExactForms` riconosce dal valore a 34 cifre: frazioni (frazioni continue, come prima), radicali (x² è una frazione
a/b → √(a·b)/b, poi si portano fuori i quadrati) e multipli di π (x/π è una frazione). La tolleranza è quella delle
frazioni (10⁻²⁰ relativa): un numero digitato con poche cifre non viene scambiato per un radicale. Restano fuori
le somme di radicali diversi e le forme (a + b√c)/d, che richiederebbero un algoritmo di relazioni intere (PSLQ).

### Disequazioni

`Inequalities.solve` prende le radici da `QuadraticEquation` e controlla il segno del polinomio in un punto interno
di ogni tratto; le radici stesse soddisfano solo ≥ e ≤. Il risultato è un insieme di intervalli disgiunti
(`SolutionSet`), e un sistema è l'intersezione dei due insiemi. `InequalityText` lo scrive in forma algebrica
(`x < −1 ∨ x > 3`, `x ≠ 2`) e come intervalli, all'italiana (`]−∞; −1[`) o all'inglese (`(−∞, −1)`).

### Funzioni e grafici

- **Grafico**: `FastEval` valuta in `double` lo stesso albero usato per il calcolo a 34 cifre
  (un test confronta i due su molte espressioni). Il grafico campiona un punto per pixel; una curva si spezza
  quando tra due punti vicini c'è un salto grande e il punto di mezzo non sta tra i due (asintoti).
- **Derivata**: differenze centrali con due passi di estrapolazione di Richardson (errore ~h⁶). Prima si confrontano
  derivata destra e sinistra: se sono diverse la funzione non è derivabile (|x| in 0) e si mostra un errore.
- **Integrale**: Gauss–Legendre a 20 punti adattivo; la stima deve stabilizzarsi sia per f sia per |f|
  (così ∫₋₁¹ 1/x non risulta 0). Se non basta si usa la quadratura tanh-sinh, che gestisce le singolarità agli estremi
  (√x, 1/√x, ln x in 0); se nemmeno questa converge il risultato è segnato come approssimato.
- **Zeri**: 1000 campioni in double per trovare i cambi di segno, poi raffinamento a 34 cifre
  (`NumericSolver.rootBetween`); i minimi di |f| vicini a zero si verificano con SOLVE (zeri doppi).
- L'analisi gira in un thread separato: il risultato si mostra solo se nel frattempo non è cambiato nulla.

### Display su una riga

Il display principale è un `FitTextView`: una riga sola, con il testo che si rimpicciolisce tra 72 e 30 sp
(48 e 24 in orizzontale). Se l'espressione non ci sta nemmeno alla dimensione minima, `TextWindow` sceglie
la parte da mostrare attorno al cursore (o alla fine), con "…" ai bordi; la finestra resta ferma finché
il cursore si sposta al suo interno. `windowShift` riporta i tocchi sul display alla posizione nel testo completo.

### Equazioni e sistemi

- **Sistemi lineari** (`LinearSystem`): eliminazione di Gauss con pivot parziale a 34 cifre, dopo aver
  normalizzato righe e colonne (coefficiente massimo 1 in ognuna): così la soglia di zero non dipende dalla
  scala dei dati e 10¹⁵x = 10¹⁵ con 10⁻¹⁵y = 2·10⁻¹⁵ si risolve. Un pivot o un termine noto residuo sotto 10⁻²⁵
  conta come zero: così `1/3`, che a 34 cifre non è un terzo esatto, non fa sembrare risolubile un sistema singolare.
  Funziona per n qualsiasi; l'interfaccia offre 2 e 3 incognite.
- **Secondo grado** (`QuadraticEquation`): Δ è calcolato esatto e poi ripulito dagli stessi residui;
  le radici reali usano la formula stabile (`q = −(b + sgn(b)·√Δ)/2`, `x₁ = q/a`, `x₂ = c/q`), che non perde
  cifre quando b² ≫ 4ac: per questo le radici non vengono mai azzerate perché piccole rispetto all'altra
  (x² − 10²⁶x + 1 ha radici 10²⁶ e 10⁻²⁶). Con `a = 0` si risolve l'equazione di primo grado.
- **Frazioni** (`Fractions`): il risultato decimale viene riconosciuto con le frazioni continue, con
  denominatore fino a 1 000 000 e numeratore fino a 12 cifre; altrimenti resta decimale. Non ci sono
  forme esatte con radicali (`(1+√5)/2`): servirebbe un motore simbolico.
- **Terzo grado** (`CubicEquation`): il discriminante è calcolato esatto; se è zero le radici multiple
  si ottengono con formule esatte (così `(x−2)³` dà esattamente 2). Altrimenti una radice reale si trova
  per bisezione entro il limite di Cauchy e Newton, e le altre due dall'equazione di secondo grado che
  resta, rifinite con Newton sul polinomio originale. Una radice diventa esattamente 0 solo se 0 è davvero
  una radice (termine noto trascurabile), e solo tante quante la sua molteplicità.
- **Coefficienti**: ogni cella è un'espressione modificata con `CalculatorEditor`, quindi segue le stesse
  regole della calcolatrice. Le celle sono salvate in `SharedPreferences` (file `equations`).

### SOLVE

`NumericSolver` cerca uno zero dell'espressione nella variabile X: prima il metodo di Newton con
derivata numerica e passo smorzato (si dimezza finché |f| non diminuisce), poi, se non converge,
cerca un cambio di segno allontanandosi dal valore iniziale a passi che raddoppiano (fino a circa 10⁴⁰)
e lo restringe alternando bisezione e secante. Un cambio di segno attraverso un polo (come 1/X)
viene scartato controllando |f| alla fine. Se un valore più "pulito" (intero, o con meno cifre) va bene
almeno quanto, si usa quello.

- Se la derivata numerica risulta zero perché f è enorme rispetto al passo (X − 10²⁵ vicino a 0),
  il passo cresce finché la differenza diventa visibile.
- Uno zero senza cambio di segno è accettato solo se |f| è al livello del rumore di arrotondamento:
  10⁻³¹ volte il più grande termine sommato nell'espressione (`Evaluation.magnitude`). Così `(X − 1)² + 10⁻³⁰`
  non ha soluzioni, mentre `(X − 1)²` e `sin(X) − 1` sì.

### Statistica

`Statistics` calcola media e deviazioni standard in due passaggi (scarti dalla media, più preciso di
Σx² − n·x̄²) e i quartili come le calcolatrici: mediane delle due metà, esclusa la mediana se n è
dispari. Tutto passa da `Statistics.weighted` (coppie valore–frequenza): i quartili si leggono con le
frequenze cumulate, senza ripetere i valori, quindi anche frequenze di miliardi restano veloci.
La moda è vuota se tutti i valori hanno la stessa frequenza. `StatisticsSheet` tiene una tabella per modalità, con una riga vuota finale per aggiungere
dati (massimo 200 righe). I dati sono salvati nel file `statistics`.

### Dati salvati

| File `SharedPreferences` | Contenuto |
|---|---|
| `calculator` | calcolo in corso, cronologia, unità angolare, formato dei numeri, variabili, frazioni sì/no |
| `equations` | coefficienti di ogni modalità, segni delle disequazioni, modalità scelta, frazioni sì/no |
| `statistics` | dati di ogni modalità, modalità scelta |
| `functions` | funzione, parametri di tabella e analisi, vista scelta, gradi o radianti |

### Scorciatoie dell'icona

`res/xml/shortcuts.xml` definisce le scorciatoie "Equazioni", "Statistica" e "Funzioni". Aprono `MainActivity`
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
