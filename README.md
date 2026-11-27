# Calcolatrice

Una calcolatrice scientifica per Android, leggera e senza pubblicità, pensata per le scuole superiori:
funzioni, variabili, equazioni, disequazioni e sistemi, grafici, derivate e integrali, statistica e probabilità.
Non chiede nessun permesso e non si collega a Internet: i tuoi calcoli restano sul telefono.

Funziona su **Android 8.0 o successivo**, su telefoni, tablet e Chromebook.

## Installazione

1. Apri la pagina **Releases** di questo repository e scarica il file `calcolatrice-….apk`
   dell'ultima versione.
2. Apri il file sul telefono. Se Android lo chiede, consenti l'installazione di app da questa fonte.
3. Per aggiornare, scarica la nuova versione e installala sopra quella vecchia: cronologia, variabili
   e dati vengono conservati.

## Cosa sa fare

- **Operazioni di base**, parentesi e percentuali, con il risultato che appare in anteprima mentre scrivi.
- **Funzioni scientifiche:** sin, cos, tan e le loro inverse, ln, log, eˣ, 10ˣ, √, x², xʸ, x!, 1/x, π ed e.
- **Menu MATH:** valore assoluto, radice n-esima, logaritmo in qualunque base, combinazioni e disposizioni,
  MCD e mcm, distribuzioni binomiale e normale.
- **Precisione a 34 cifre** in tutti i calcoli, funzioni scientifiche comprese: anche dopo molti passaggi
  il risultato mostrato è esatto (per esempio (√2)² fa proprio 2).
- **Variabili** A, B, C, D, X e Y, per riusare i risultati nei problemi a più passaggi.
- **Notazione scientifica** con il tasto **×10ⁿ**: 6,02×10²³ si scrive `6,02` **×10ⁿ** `23`.
- **Risultati in forma esatta o in decimale:** frazioni, radici e multipli di π (√8 = `2√2`, sin 60° = `√3/2`),
  con le frazioni scritte su due livelli, e formato dei numeri a scelta: normale, decimali fissi o notazione scientifica.
- **Display su una riga:** le cifre si rimpiccioliscono man mano che l'espressione si allunga.
- **Cursore:** tocca l'espressione per correggerla in qualunque punto.
- **Equazioni e sistemi:** sistemi lineari a 2 e 3 incognite, equazioni di secondo e terzo grado.
- **Disequazioni:** di primo e secondo grado, fratte, e sistemi fino a tre disequazioni, con le soluzioni
  scritte come a scuola.
- **Funzioni e grafici:** una o due funzioni insieme, tabella dei valori, grafico da esplorare con le dita,
  derivata in un punto, integrale definito, zeri e punti d'intersezione in un intervallo, **limiti** (da
  destra, da sinistra, a ±∞) e lo **studio di
  funzione** (dominio, simmetrie, segno, crescenza, massimi e minimi, concavità e flessi, asintoti).
- **Statistica:** media, moda, deviazione standard, quartili, dati con frequenze e retta di regressione.
- **Gradi e radianti**, con valori esatti per gli angoli notevoli (sin 180° = 0; tan 90° non è definita).
- **Tutto resta salvato:** il calcolo in corso, la cronologia degli ultimi 100 calcoli, le variabili, i dati.
- **Numeri scritti come nella tua lingua** (1.234,5 in italiano, 1,234.5 in inglese) ed esponenti
  in apice (3², 10⁻³).
- **Tema chiaro e scuro** automatici, con tre scelte di colori: **corallo**, **azzurro** e, da Android 12,
  **dal sistema** (abbinati allo sfondo del telefono). L'icona dell'app segue il colore scelto (con i colori
  del sistema è grigio grafite).
- **Tablet e pieghevoli:** sugli schermi larghi la cronologia resta sempre visibile a fianco.
- **Italiano e inglese**. Da Android 13 puoi scegliere la lingua solo per questa app dalle impostazioni.
- **Accessibile con TalkBack:** ogni tasto ha una descrizione e i risultati vengono letti a parole
  ("3 quarti", "x al quadrato", "6,02 per 10 alla 23").

## Come si usa

| Per… | Fai così |
|---|---|
| Vedere le funzioni scientifiche | In verticale tocca la freccia accanto alla prima riga di funzioni. Gira il telefono per averle tutte insieme al tastierino. |
| Usare le funzioni inverse (sin⁻¹, cos⁻¹, tan⁻¹, eˣ, 10ˣ) | Tocca **INV**. |
| Passare da gradi a radianti | Tocca **DEG** (o **RAD**) in alto a sinistra. |
| Aprire e chiudere le parentesi | Il tasto **( )** capisce da solo se aprire o chiudere. Le parentesi lasciate aperte vengono chiuse al momento del calcolo. |
| Correggere l'espressione | Tocca il punto da correggere: compare il cursore. ⌫ cancella il carattere prima del cursore e i tasti scrivono lì. Se l'espressione è più lunga dello schermo, i **…** indicano la parte nascosta: toccali per spostarti. |
| Continuare dal risultato | Dopo **=** premi un operatore: il calcolo riparte dal risultato. Se digiti un numero, ne inizi uno nuovo. |
| Vedere il risultato in forma esatta | Tocca **Frazioni** in alto (compare il segno di spunta): 1 ÷ 3 = `1/3`, √8 = `2√2`, sin⁻¹(1) in radianti = `π/2`. Toccalo di nuovo per tornare ai decimali. |
| Scrivere un numero in notazione scientifica | Scrivi la mantissa, tocca **×10ⁿ** e poi l'esponente (il segno meno con **−**): `1,6` **×10ⁿ** `−19`. |
| Usare le altre funzioni (nCr, MCD, log in base b…) | Tocca **MATH** e scegli la funzione. Se ha più valori, separali con il tasto **;**: `nCr(10; 3)`. |
| Cambiare il formato dei numeri | Tocca **⋮ → Formato dei numeri** (vedi [Formato dei numeri](#formato-dei-numeri)). |
| Cambiare i colori | Tocca **⋮ → Colori**: corallo, azzurro o dal sistema. Cambia anche l'icona dell'app: su alcuni telefoni sparisce dalla Home e va riaggiunta dall'elenco delle app. |
| Riusare un calcolo precedente | Tocca l'icona della cronologia in alto a destra, poi il calcolo che ti interessa. |
| Aprire equazioni, funzioni, statistica e il resto | Tocca **⋮** in alto a destra. Oppure tieni premuta l'icona dell'app sulla schermata Home. |
| Copiare il risultato | Tieni premuto sul display. |
| Cancellare tutto | Tocca **AC**, oppure tieni premuto ⌫. |

### Percentuali

Il tasto **%** funziona come sulle calcolatrici tascabili:

- `200 + 10%` = **220** (aggiunge il 10% di 200)
- `200 − 10%` = **180** (toglie il 10% di 200)
- `50 × 10%` = **5** (il 10% di 50)
- `10%` da solo = **0,1**

### Funzioni del menu MATH

Tocca **MATH** (nel pannello delle funzioni scientifiche) e scegli una funzione: viene scritta nel calcolo.
Quando servono più valori, separali con il tasto **;**.

| Funzione | Esempio | Risultato |
|---|---|---|
| Valore assoluto | `abs(3 − 10)` | 7 |
| Radice n-esima | `ⁿ√(3; 8)`, `ⁿ√(3; −27)` | 2, −3 |
| Logaritmo in base b | `log(2; 8)` | 3 |
| Combinazioni semplici | `nCr(10; 3)` | 120 |
| Disposizioni semplici | `nPr(10; 3)` | 720 |
| Massimo comune divisore | `MCD(12; 18; 27)` | 3 |
| Minimo comune multiplo | `mcm(4; 6; 10)` | 60 |
| Binomiale: P(X = k) | `BinomP(10; 0,5; 5)` (n prove, probabilità p, k successi) | 0,24609375 |
| Binomiale: P(X ≤ k) | `BinomC(20; 0,3; 7)` | 0,77227179741816 |
| Normale: P(a ≤ X ≤ b) | `NormC(170; 180; 175; 10)` (a, b, media, deviazione standard) | 0,382924922548026 |

Con due soli valori `NormC(a; b)` usa la normale standardizzata (media 0, deviazione standard 1).
Per una coda usa un numero molto grande: P(X ≥ 180) = `NormC(180; 1E99; 175; 10)`.

### Forma esatta

Con **Frazioni** attivo i risultati compaiono, quando è possibile, in forma esatta: frazioni (`11/12`),
radici (`2√2`, `√6/3`) e multipli di π (`3π/4`). La forma esatta si riconosce dal valore calcolato con
34 cifre: per questo funziona anche dopo calcoli lunghi. Restano decimali le somme di radici diverse
(√2 + √3) e i numeri come (1 + √5)/2.

Sullo schermo le frazioni si scrivono come sul quaderno, con il numeratore sopra la linea di frazione e il
denominatore sotto (anche `√3/2`, `3π/4` e le parti immaginarie). Quando le copi diventano testo su una riga:
`3/4`.

### Scrivere le frazioni

Anche mentre scrivi le frazioni stanno su due livelli. Ci sono due modi, da scegliere in
**⋮ → Scrittura delle frazioni**:

| Modo | Come si scrive ¾ + 1 |
|---|---|
| **Tasto ▫/▫** (predefinito) | **▫/▫**, `3`, **▶**, `4`, **▶**, `+ 1`. Il tasto ÷ resta la divisione su una riga; % si sposta nel pannello scientifico e x! nel menu MATH. |
| **Tasto ÷, come sulle Casio** | `3` **÷** `4`, **▶**, `+ 1`. Il ÷ prende come numeratore quello che hai appena scritto (un numero, una parentesi, una potenza…) e porta il cursore nel denominatore. |

- Quando c'è una frazione, accanto all'anteprima compaiono le frecce **◀ ▶** per spostarsi: da numeratore
  a denominatore e fuori dalla frazione. Una parte ancora vuota si vede come un riquadro.
- Le frazioni si possono mettere una dentro l'altra e contenere qualunque espressione.
- **⌫** subito dopo una frazione entra nel denominatore; all'inizio del denominatore torna al numeratore;
  una frazione vuota sparisce.
- Nella cronologia e quando copi, le frazioni si scrivono su una riga: `3/4`, `(1+2)/5`.

### Radice quadrata

**√** si applica al numero o alla parentesi che la segue: `√9 + 16` fa 3 + 16 = 19. Per la radice di
un'espressione usa le parentesi: `√(9 + 16)` = 5.

## Variabili

Il tasto **VAR** si trova nell'ultima riga delle funzioni scientifiche.

- **VAR** mostra le variabili A, B, C, D, X e Y con i loro valori. Tocca una variabile per inserirla
  nel calcolo (per esempio `2 × A`); tocca **STO** accanto a una variabile per salvarci il risultato.
  Se c'è un calcolo non ancora fatto, STO lo calcola prima.
- Le variabili non ancora usate valgono 0, e restano salvate anche chiudendo l'app.
- Equazioni, funzioni e statistica possono salvare i loro risultati nelle stesse variabili.

Non c'è la variabile E: sulla tastiera fisica la E maiuscola scrive ×10ⁿ.

Per trovare la X che rende zero un'espressione usa **Funzioni e grafici**: scrivi l'espressione come
f(x) e leggi gli zeri nella vista **Analisi**, che li mostra anche sul grafico.

## Formato dei numeri

Tocca **⋮ → Formato dei numeri** per scegliere come vengono mostrati i risultati:

| Formato | Esempio con 1234,5678 | Quando serve |
|---|---|---|
| Normale (**NORM**) | 1.234,5678 | Uso di tutti i giorni: fino a 15 cifre. |
| Decimali fissi (**FIX**) | FIX 2: 1.234,57 | Soldi, misure: sempre lo stesso numero di decimali. |
| Notazione scientifica (**SCI**) | SCI 3: 1,23×10³ | Numeri molto grandi o molto piccoli, con le cifre che ti servono. |

Il formato cambia solo come vedi il risultato: i calcoli continuano a usare tutte le cifre.
Vale anche per le schermate di equazioni e statistica.

### Precisione

Tutti i calcoli, funzioni scientifiche comprese, sono fatti con 34 cifre significative; a schermo ne
vedi al massimo 15. Oltre questo limite, o per numeri molto piccoli, si passa alla notazione
scientifica (per esempio `1,5×10²⁰`). La calcolatrice gestisce numeri fino a 10⁹⁹⁹⁹. Copiando un
risultato in notazione scientifica si ottiene la forma con la E (`1,5E20`), che le altre app capiscono.

### Messaggi di errore

| Messaggio | Significato |
|---|---|
| Impossibile dividere per zero | Hai diviso per zero, per esempio `5 ÷ 0`. |
| Risultato non definito | L'operazione non ha un risultato reale, per esempio `√−4`, `ln 0` o `tan 90°`. |
| Numero troppo grande | Il risultato supera 10⁹⁹⁹⁹, per esempio `5000!`. |
| Espressione non valida | Manca qualcosa, per esempio un numero dopo un operatore. |

## Equazioni e sistemi

Tocca **⋮ → Equazioni e sistemi**, oppure tieni premuta l'icona dell'app e scegli **Equazioni**.

1. Scegli il tipo di problema: sistema **2×2** o **3×3**, equazione di **2° grado** o di **3° grado**,
   oppure **Diseq.** per le disequazioni (vedi sotto).
   In alto vedi la forma dell'equazione (per esempio `ax + by = c`) e le lettere sopra la tabella
   ti dicono dove va ogni coefficiente.
2. Scrivi i coefficienti. Tocca una casella per selezionarla, oppure passa alla precedente o alla
   successiva con **◀** e **▶**. Una casella vuota vale 0. Puoi scrivere anche frazioni ed espressioni,
   come `1 ÷ 3`, `−√2` o `2π`.
3. Premi **=** per risolvere.

| Problema | Cosa ottieni |
|---|---|
| Sistema 2×2 o 3×3 | I valori di x, y (e z), oppure "Nessuna soluzione" o "Infinite soluzioni". |
| 2° grado, due soluzioni reali | x₁ e x₂, dalla più grande alla più piccola. |
| 2° grado, Δ = 0 | La soluzione doppia. |
| 2° grado, Δ < 0 | Le due soluzioni complesse, nella forma `a + bi` e `a − bi`. |
| 3° grado | Tre soluzioni reali (ripetute se sono doppie o triple), oppure una reale e due complesse. |
| a = 0 | L'equazione di grado inferiore che resta. |

Per le equazioni di secondo grado viene mostrato anche il vertice della parabola, cioè il punto di
minimo (se a > 0) o di massimo (se a < 0).

### Disequazioni

Con **Diseq.** risolvi disequazioni del tipo ax² + bx + c > 0 (con a = 0 sono di primo grado).

- Tocca il **segno** a destra della riga per cambiarlo: >, ≥, <, ≤.
- La **seconda e la terza riga sono facoltative** (finché sono vuote restano in disparte): se le compili,
  ottieni le soluzioni comuni a tutte le disequazioni scritte (sistema di due o tre).
- Le soluzioni compaiono come a scuola, per esempio `x < −1 ∨ x > 3`, `−√2 < x < √2` o `x ≠ 2`,
  e sotto come intervalli: `]−∞; −1[ ∪ ]3; +∞[`.

| Disequazione | Soluzioni |
|---|---|
| x² − 2x − 3 > 0 | x < −1 ∨ x > 3 |
| x² − 4x + 4 > 0 | x ≠ 2 |
| x² + 1 > 0 | ∀x ∈ ℝ |
| x² + 1 < 0 | Nessuna soluzione |
| sistema: x² − 2x − 3 ≥ 0 e x − 5 ≤ 0 | x ≤ −1 ∨ 3 ≤ x ≤ 5 |
| sistema: x > 0, x − 5 < 0, x² − 4 ≥ 0 | 2 ≤ x < 5 |

### Disequazioni fratte

Con **Fratta** risolvi N(x) / D(x) > 0 (o ≥, <, ≤), con numeratore e denominatore di primo o secondo grado.
Nella riga **N(x)** scrivi i coefficienti del numeratore, in **D(x)** quelli del denominatore (sotto x², x e 1).

- Il segno della frazione è il prodotto dei segni di numeratore e denominatore.
- Gli zeri del denominatore non sono mai soluzioni, anche con ≥ o ≤: sopra le soluzioni compare la
  **condizione di esistenza** (per esempio `x ≠ −2`).

| Disequazione | Soluzioni |
|---|---|
| (x − 1)/(x + 2) ≥ 0 | x < −2 ∨ x ≥ 1 |
| (x + 3)/(x² − 4) < 0 | x < −3 ∨ −2 < x < 2 |
| (x² − 1)/(x − 1) > 0 | −1 < x < 1 ∨ x > 1 |
| 1/(x² + 1) > 0 | ∀x ∈ ℝ |

**Forma esatta o decimali:** il chip **Frazioni** in alto a destra passa dai risultati in forma esatta
(`x = −3/4`, `x = √2`, `x = −1/2 + (√3/2)i`) a quelli decimali (`x = −0,75`) e viceversa.

Altre cose utili:

- **Tocca i risultati** per salvarne uno in una variabile (A, B, X…) e usarlo poi nella calcolatrice.
- **AC** cancella la casella selezionata; tenendolo premuto cancelli tutta la tabella.
- I coefficienti restano salvati anche se chiudi la schermata o l'app.
- Tieni premuto sui risultati per copiarli.

## Funzioni e grafici

Tocca **⋮ → Funzioni e grafici**, oppure tieni premuta l'icona dell'app e scegli **Funzioni**.

In alto scrivi la funzione `f(x) =` usando il tasto **X**. Se vuoi confrontarla con un'altra, scrivila
nella casella `g(x) =` (è facoltativa). Sotto scegli una delle tre viste:

| Vista | Cosa ottieni |
|---|---|
| **Tabella** | I valori di f(x) (e di g(x)) a partire da x = **da**, con il **passo** scelto (per esempio da −5 con passo 0,5). Dove la funzione non è definita compare "non def.". |
| **Grafico** | Il grafico di f, e quello di g tratteggiato, con i punti d'incontro cerchiati. **Trascina** per spostarti, **avvicina due dita** per ingrandire, **doppio tocco** per tornare alla vista iniziale, **tocca** un punto per leggerne le coordinate. |
| **Analisi** | Scrivi x₀, a e b, poi premi **=**: ottieni f(x₀), la derivata f′(x₀), l'integrale da a a b, gli zeri di f tra a e b e, se c'è g, i punti d'intersezione dei due grafici tra a e b. |

- **RAD/DEG** in alto sceglie radianti o gradi; per i grafici delle funzioni goniometriche si usano di solito i radianti.
- **Frazioni** in alto mostra i risultati in forma esatta: ∫₀¹ (x² + x + 3) dx = `23/6`, zeri come `√2`.
- Con g puoi risolvere graficamente equazioni e disequazioni: per x² = x + 2 scrivi f = x², g = x + 2
  e leggi le intersezioni (−1; 1) e (2; 4).
- **◀** e **▶** passano da una casella all'altra; **AC** cancella la casella, tenendolo premuto ricominci da capo.
- La derivata è calcolata numericamente: dove la funzione ha uno spigolo (come |x| in 0) compare "non derivabile".
- Gli integrali funzionano anche con estremi difficili, come ∫₀¹ 1/√x dx = 2. Se l'integrale non esiste
  (come ∫₀¹ 1/x dx) il valore compare con **≈** e un avviso.
- Tocca i risultati dell'analisi per salvarne uno in una variabile.

### Limiti

Nella vista **Analisi**, sotto x₀, a e b, il chip **Limite** sceglie il verso: un tocco passa da `x → x₀`
(entrambi i lati) a `x → x₀⁻` (da sinistra), `x → x₀⁺` (da destra), `x → +∞` e `x → −∞`. Premendo **=**
ottieni anche il limite di f.

| Esempio | Risultato |
|---|---|
| sin(x)/x per x → 0 | ≈ 1 |
| (sin x − x)/x³ per x → 0 | ≈ −1/6 |
| 1/x per x → 0 | da sinistra −∞, da destra +∞: il limite non esiste |
| ln x per x → 0⁺ | −∞ (da verificare: cresce lentamente) |
| sin(1/x) per x → 0 | non esiste, la funzione oscilla |

Il limite è calcolato numericamente (valori di f sempre più vicini a x₀, a 34 cifre, con estrapolazione):
per questo c'è sempre **≈**, e **da verificare** quando la convergenza è troppo lenta per esserne sicuri.
Serve per controllare un limite fatto a mano, non al posto del procedimento.

### Studio di funzione

Nella vista **Studio** scegli l'intervallo (da −10 a 10 se non lo cambi) e premi **=**. In qualche secondo
ottieni, nell'ordine del quaderno: dominio, simmetrie (pari o dispari), intersezioni con gli assi, segno,
dove la funzione cresce e decresce con massimi e minimi, concavità e flessi, asintoti. Nel **Grafico** i
punti trovati vengono segnati: massimi e minimi pieni, flessi vuoti, asintoti tratteggiati.

Lo studio è numerico, quindi ogni parte dice quanto è affidabile:

| Segno | Significa | Parti |
|---|---|---|
| nessuno | calcolato con certezza, a 34 cifre | simmetrie, intersezioni, segno, crescenza, massimi e minimi |
| **≈** | trovato numericamente: preciso, ma non garantito | dominio (quando ha dei confini), concavità e flessi, asintoti verticali |
| **da verificare** (in rosso) | stimato con valori di x molto grandi: confermalo con i limiti | asintoti orizzontali e obliqui |

- Lo studio guarda solo l'intervallo scelto: −∞ e +∞ nei risultati indicano i suoi estremi.
- I valori "belli" escono in forma esatta, come nel resto dell'app (`x = √3`, `(1; −2)`).
- Le simmetrie si possono decidere solo con un intervallo simmetrico rispetto a 0.

## Statistica

Tocca **⋮ → Statistica**, oppure tieni premuta l'icona dell'app e scegli **Statistica**.

1. Scegli **1 variabile** (una colonna di dati), **Frequenze** (ogni valore x con la sua frequenza f)
   oppure **Regressione** (coppie di valori x e y).
2. Scrivi i dati nella tabella: c'è sempre una riga vuota in fondo per aggiungerne. Passa da una
   casella all'altra con **◀** e **▶**, o toccandola. **DEL** elimina la riga selezionata.
3. Premi **=** per vedere i risultati. Tocca una casella (o ◀ ▶) per tornare ai dati.

| Modalità | Risultati |
|---|---|
| 1 variabile e Frequenze | numero di dati n, media x̄, somma Σx, somma dei quadrati Σx², deviazione standard della popolazione σx e del campione sx, minimo, primo quartile Q₁, mediana, terzo quartile Q₃, massimo, moda Mo |
| Regressione | retta y = a + bx, coefficiente di correlazione r, medie x̄ e ȳ, deviazioni standard σx e σy |

I quartili sono calcolati come sulle calcolatrici scientifiche: mediane della metà inferiore e di
quella superiore dei dati. Con le frequenze ogni valore conta tante volte quanto la sua frequenza
(che deve essere un numero intero). La moda può avere più valori (`Mo = 3; 5`); se tutti i valori
compaiono lo stesso numero di volte non c'è moda (`Mo = —`). Come per le equazioni, tocca i risultati per salvarne uno in una variabile.
I dati restano salvati anche se chiudi l'app.

## Tastiera fisica

Con una tastiera collegata (Bluetooth, tablet, Chromebook) puoi scrivere direttamente nella calcolatrice:

| Tasto | Funzione |
|---|---|
| `0`–`9`, `.` o `,` | cifre e virgola |
| `;` | separatore tra i valori delle funzioni (`nCr(10;3)`) |
| `+` `-` `*` `/` `^` | operazioni (anche `x` per ×, `:` per ÷) |
| `%` `!` `(` `)` | percentuale, fattoriale, parentesi |
| `Invio` o `=` | risultato |
| `Backspace` | cancella il carattere prima del cursore |
| `Esc` o `Canc` | cancella tutto |
| `←` `→`, `Home` `Fine` | spostano il cursore |
| `A` `B` `C` `D` `X` `Y` `M` (maiuscole) | variabili |
| `s` `c` `t` | sin, cos, tan (con INV attivo: le inverse) |
| `l` `g` | ln, log (con INV attivo: eˣ, 10ˣ) |
| `r` `p` `e` | √, π, e |
| `Ctrl + C` | copia il risultato |

Nelle schermate di equazioni, funzioni e statistica: **Tab** passa alla casella successiva (**Maiusc + Tab**
alla precedente) e **Invio** calcola. Nella schermata delle funzioni `x` scrive la variabile X
(per moltiplicare usa `*`).

## Privacy

L'app non richiede permessi, non usa Internet e non raccoglie dati. Calcoli, variabili e dati sono
salvati solo sul telefono e sono inclusi nel backup di Android, se lo hai attivato.
