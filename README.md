# Calcolatrice

Una calcolatrice scientifica per Android, leggera e senza pubblicità. Non chiede nessun permesso e
non si collega a Internet: i tuoi calcoli restano sul telefono.

Funziona su **Android 8.0 o successivo**, su telefoni, tablet e Chromebook.

## Installazione

1. Apri la pagina **Releases** di questo repository e scarica il file `calcolatrice-….apk`
   dell'ultima versione.
2. Apri il file sul telefono. Se Android lo chiede, consenti l'installazione di app da questa fonte.
3. Per aggiornare, scarica la nuova versione e installala sopra quella vecchia: la cronologia viene conservata.

## Cosa sa fare

- **Operazioni di base**, parentesi e percentuali, con il risultato che appare in anteprima mentre scrivi.
- **Funzioni scientifiche:** sin, cos, tan e le loro inverse, ln, log, eˣ, 10ˣ, √, x², xʸ, x!, 1/x, π ed e.
- **Gradi e radianti**, con valori esatti per gli angoli notevoli (sin 180° = 0; tan 90° non è definita).
- **Equazioni e sistemi**, come sulle calcolatrici scientifiche: sistemi lineari a 2 e 3 incognite ed
  equazioni di secondo grado, con risultati in frazione o in decimale.
- **Conti senza errori di arrotondamento** per le operazioni di base: 0,1 + 0,2 fa esattamente 0,3.
- **Cronologia** degli ultimi 100 calcoli, conservata anche quando chiudi l'app.
- **Numeri scritti come nella tua lingua** (1.234,5 in italiano, 1,234.5 in inglese).
- **Tema chiaro e scuro** automatici e, da Android 12, colori abbinati al tuo sfondo.
- **Italiano e inglese**. Da Android 13 puoi scegliere la lingua solo per questa app dalle impostazioni.
- **Accessibile con TalkBack:** ogni tasto ha una descrizione e il risultato viene letto ad alta voce.

## Come si usa

| Per… | Fai così |
|---|---|
| Vedere le funzioni scientifiche | In verticale tocca la freccia accanto alla prima riga di funzioni. Gira il telefono per averle tutte insieme al tastierino. |
| Usare le funzioni inverse (sin⁻¹, eˣ, x²…) | Tocca **INV**. |
| Passare da gradi a radianti | Tocca **DEG/RAD**, oppure la scritta in alto a sinistra. |
| Aprire e chiudere le parentesi | Il tasto **( )** capisce da solo se aprire o chiudere. Le parentesi lasciate aperte vengono chiuse al momento del calcolo. |
| Continuare dal risultato | Dopo **=** premi un operatore: il calcolo riparte dal risultato. Se digiti un numero, ne inizi uno nuovo. |
| Riusare un calcolo precedente | Tocca l'icona della cronologia in alto a destra, poi il calcolo che ti interessa. |
| Copiare il risultato | Tieni premuto sul display. |
| Cancellare tutto | Tocca **AC**, oppure tieni premuto ⌫. |

### Percentuali

Il tasto **%** funziona come sulle calcolatrici tascabili:

- `200 + 10%` = **220** (aggiunge il 10% di 200)
- `200 − 10%` = **180** (toglie il 10% di 200)
- `50 × 10%` = **5** (il 10% di 50)
- `10%` da solo = **0,1**

### Radice quadrata

**√** si applica al numero o alla parentesi che la segue: `√9 + 16` fa 3 + 16 = 19. Per la radice di
un'espressione usa le parentesi: `√(9 + 16)` = 5.

### Precisione

I risultati sono mostrati con un massimo di 15 cifre significative. Oltre questo limite, o per numeri
molto piccoli, si passa alla notazione scientifica (per esempio `1,5E20` significa 1,5 × 10²⁰).
La calcolatrice gestisce numeri fino a 10⁹⁹⁹⁹.

### Messaggi di errore

| Messaggio | Significato |
|---|---|
| Impossibile dividere per zero | Hai diviso per zero, per esempio `5 ÷ 0`. |
| Risultato non definito | L'operazione non ha un risultato reale, per esempio `√−4`, `ln 0` o `tan 90°`. |
| Numero troppo grande | Il risultato supera 10⁹⁹⁹⁹, per esempio `5000!`. |
| Espressione non valida | Manca qualcosa, per esempio un numero dopo un operatore. |

## Equazioni e sistemi

Tocca l'icona **x =** in alto per aprire la schermata delle equazioni.

1. Scegli il tipo di problema: **Sistema 2×2**, **Sistema 3×3** o **2° grado**. In alto vedi la forma
   dell'equazione (per esempio `ax + by = c`) e le lettere sopra la tabella ti dicono dove va ogni coefficiente.
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
| 2° grado con a = 0 | La soluzione dell'equazione di primo grado che resta. |

Per le equazioni di secondo grado viene mostrato anche il vertice della parabola, cioè il punto di
minimo (se a > 0) o di massimo (se a < 0).

**Frazioni o decimali:** il tasto **a/b** in alto a destra passa dai risultati in frazione (`x = −3/4`)
a quelli decimali (`x = −0,75`) e viceversa. Se un risultato non è una frazione semplice, per esempio √2,
resta in forma decimale.

Altre cose utili:

- **AC** cancella la casella selezionata; tenendolo premuto cancelli tutta la tabella.
- I coefficienti restano salvati anche se chiudi la schermata o l'app.
- Tieni premuto sui risultati per copiarli.
- Con una tastiera fisica: **Tab** passa alla casella successiva (**Maiusc + Tab** alla precedente) e **Invio** risolve.

## Tastiera fisica

Con una tastiera collegata (Bluetooth, tablet, Chromebook) puoi scrivere direttamente nella calcolatrice:

| Tasto | Funzione |
|---|---|
| `0`–`9`, `.` o `,` | cifre e virgola |
| `+` `-` `*` `/` `^` | operazioni (anche `x` per ×, `:` per ÷) |
| `%` `!` `(` `)` | percentuale, fattoriale, parentesi |
| `Invio` o `=` | risultato |
| `Backspace` | cancella l'ultimo carattere |
| `Esc` o `Canc` | cancella tutto |
| `s` `c` `t` | sin, cos, tan (con INV attivo: le inverse) |
| `l` `g` | ln, log (con INV attivo: eˣ, 10ˣ) |
| `r` `p` `e` | √, π, e |
| `Ctrl + C` | copia il risultato |

## Privacy

L'app non richiede permessi, non usa Internet e non raccoglie dati. La cronologia è salvata solo sul
telefono ed è inclusa nel backup di Android, se lo hai attivato.
