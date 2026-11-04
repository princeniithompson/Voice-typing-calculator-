# Noise-Resilient Restaurant Voice POS (Akwaaba VoicePOS)

A high-performance, noise-resilient Android Point of Sale (POS) system built for bustling restaurant and food counter environments in Ghana. Combines continuous audio listening with noise gating, custom Ghanaian food inventory management (GHS pricing), instant on-device phonetic fuzzy matching, and low-latency Gemini AI fallback parsing for unstructured multi-item speech orders.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were confirmed during initial clarification and form the foundation of the architecture:

- **Confirmed Decision 1 (Microphone & Audio Behavior)**: Continuous listening mode with real-time noise gating, visual decibel/frequency waveform feedback, and an instant pause/resume toggle for busy service shifts.
- **Confirmed Decision 2 (Speech & Item Parsing)**: Hybrid 2-tier pipeline featuring instant (<10ms) on-device phonetic fuzzy matching (Levenshtein + phonetic Soundex/Metaphone specialized for Ghanaian dish names like Banku, Okro, Plantain, Waakye) with low-latency Gemini AI parsing (`gemini-3.5-flash-lite` / `gemini-3.8-flash`) for complex conversational orders.
- **Confirmed Decision 3 (Checkout & Order Fulfillment)**: Quick Dine-In vs. Takeout switch, live itemized digital receipt card with subtotal/tax/grand total in Ghanaian Cedis (GHS), quick manual quantity adjustments, and printable/shareable digital order tickets with order numbering.

---

## 1. Overview & Core Concept

In loud open-kitchen restaurants and chop bars, staff hands are often busy handling food, ladling stew, or packing takeout. Standard voice assistants disconnect after a two-second pause and fail on local dishes and noisy kitchen chatter.

**Akwaaba VoicePOS** solves this by providing:
1. **Hands-Free Continuous Voice Ordering**: A persistent listening engine that monitors speech, strips ambient clatter using Android hardware audio effects (Acoustic Echo Canceler, Noise Suppressor, Automatic Gain Control), and detects spoken food orders in continuous speech.
2. **Preloaded Custom Ghanaian Menu & Inventory (GHS)**: Preloaded with standard favorites (Banku, Okro scoop, Fried Fish, Plantain, Chicken, Goat Meat, Goat Feet, Salmon, Tuna, Rice, Sausage, Spaghetti, Rice Balls) with a complete Room-backed inventory editor.
3. **Robust Dual-Engine Parsing**:
   - *Tier 1 (Instant On-Device)*: Tokenized phonetic distance matching that understands local accents, pluralizations, units (e.g., "cups", "scoops", "pieces"), and number words ("two", "three", "half").
   - *Tier 2 (Low-Latency Gemini Fallback)*: Resolves complex composite phrasing (e.g., *"give me two plates of banku with three fried fish and one okro soup for takeout"*).
4. **Live POS Order Counter & Digital Receipt**: Live running subtotal, item breakdowns, quantity increment/decrement touch targets (>= 48dp), dine-in/takeout toggle, and order ticket completion.

---

## 2. User Experience & Visual Design

### Aesthetic Direction & Mood
- **Theme**: "Chop Bar Pro Dark / Warm Amber" — High-contrast, tactile, industrial-grade POS theme. Built for glare resistance under bright kitchen overhead lights and low-light evening counters.
- **Color Palette**:
  - Primary: `Warm Gold / Amber (#F59E0B)` — energetic, appetizing, and legible.
  - Background: `Deep Obsidian (#0F172A)` and `Card Surface (#1E293B)`.
  - Accent / Positive: `Emerald Green (#10B981)` for Grand Total and Confirm actions.
  - Live Audio Pulse: `Vibrant Cyan / Amber Glow` indicating mic activity and noise gate levels.
- **Typography & Numbers**: Bold tabular numerals for GHS currency amounts to prevent layout shifting during real-time speech tallying.

### Key Screens & User Flows

```
┌─────────────────────────────────────────────────────────────────┐
│                      AKWAABA VOICE POS                          │
│  [ ● REC: LISTENING ]   [ Noise Gate: OK ]   [ Pause / Resume ] │
├────────────────────────────────┬────────────────────────────────┤
│       CURRENT LIVE RECEIPT     │      SPEECH & INVENTORY FEED   │
│  Order #1042  •  Takeout 🥡    │                                │
│ ────────────────────────────── │  Live Transcript:              │
│  • 2x Banku            10 GHS  │  "two banku and one fried fish │
│  • 1x Fried Fish       15 GHS  │   with okro"                   │
│  • 1x Okro (1 scoop)    2 GHS  │  Matched:                      │
│ ────────────────────────────── │  [Banku x2] [Fish x1] [Okro x1]│
│  TOTAL:                27 GHS  │                                │
│                                │  QUICK MENU TILES (TOUCH/TAP): │
│  [ Clear ]  [ Charge / Done ]  │  [Banku 5GHS] [Chicken 15GHS]  │
│                                │  [Plantain 2GHS] [Rice 6GHS]   │
└────────────────────────────────┴────────────────────────────────┘
```

1. **POS Main Ordering Screen**:
   - Split/Adaptive layout: On portrait phones, the live receipt floats at the bottom with sliding drawer; on tablets/wide screens, a dual-pane layout displays the order ticket on the left and voice visualizer / quick menu tiles on the right.
   - **Continuous Listening Bar**: Shows live audio amplitude wave, recognized speech transcript banner with matched badge chips, and status badge (`LISTENING`, `PARSING`, `PAUSED`).
   - **Live Order Receipt**: Real-time item rows with `+` / `-` buttons, swipe-to-delete, and bold GHS grand total.
   - **Order Type Toggle**: Quick chips for `Dine-In` vs `Takeout`.
2. **Inventory & Menu Management Screen**:
   - List of all inventory items, unit prices, unit descriptors (e.g., "1 piece", "1 scoop", "1 cup"), and phonetic alias keywords (e.g., "bangu", "bankoo", "planten").
   - Add/Edit Item dialog with price updates and delete confirmation.
   - Reset to default Ghanaian menu button.
3. **Order History & Receipt Detail Screen**:
   - List of completed transactions, timestamps, totals, and reprintable receipt summary.

---

## 3. Key Product Decisions & Trade-Offs

### 1. Dual-Tier Hybrid Speech Parsing
- **Tier 1 (Instant On-Device Regex + Phonetic Levenshtein)**:
  - *Why*: In a busy restaurant, zero latency is paramount. When a cashier says *"two banku"*, the item must appear on the ticket within 50ms without waiting for a cloud round-trip or failing if the internet drops.
  - *Phonetic Engine*: Standardizes input tokens (e.g., converts "two", "to", "too" $\rightarrow 2$, "pieces of", "cups of"), computes normalized Levenshtein similarity against menu items and phonetic aliases (e.g., *Banku*, *Okro*, *Plantain*).
- **Tier 2 (Low-Latency Gemini AI Logic Fallback)**:
  - *Why*: Unstructured multi-sentence orders (e.g. *"Actually cancel one plantain and add two scoops of okro instead"*) are difficult for regex. Gemini 3.5 Flash / 3.8 Flash parses conversational intents into structured JSON (`[{"itemName": "Okro", "quantity": 2, "action": "ADD"}, {"itemName": "Plantain", "quantity": 1, "action": "REMOVE"}]`).

### 2. Audio Processing & Noise Resilience
- Uses Android `SpeechRecognizer` in continuous loop mode configured with `EXTRA_PARTIAL_RESULTS` to display words as they are spoken.
- Provides an integrated Audio Decibel Monitor with an adjustable **Noise Gate Threshold**: ignores background kitchen clatter below the threshold and processes voice bursts.
- Hardware acoustic filters: Requests `RECORD_AUDIO` and applies platform `NoiseSuppressor` / `AutomaticGainControl` where supported by the hardware audio session.

### 3. Local Persistence via Room
- Uses Room Database with reactive Kotlin `Flow` for:
  - `MenuItemEntity`: id, name, unitPrice, unitLabel, category, aliases, isAvailable.
  - `OrderEntity`: id, orderNumber, timestamp, orderType (DineIn/Takeout), totalAmount, status.
  - `OrderItemEntity`: id, orderId, itemId, itemName, unitPrice, quantity, subtotal.
- Prepopulates database on first launch with the 13 required Ghanaian baseline dishes and GHS pricing.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Jetpack Compose UI                            │
│  ┌───────────────────────┐  ┌───────────────────┐  ┌────────────────┐  │
│  │ Main POS & Live Order │  │ Inventory Manager │  │ Order History  │  │
│  │ - Mic Wave Visualizer │  │ - Add/Edit Dishes │  │ - Past Tickets │  │
│  │ - Running Receipt GHS │  │ - Pricing Config  │  │ - Reprint/Share│  │
│  └───────────┬───────────┘  └─────────┬─────────┘  └───────┬────────┘  │
└──────────────┼────────────────────────┼────────────────────┼───────────┘
               │                        │                    │
┌──────────────▼────────────────────────▼────────────────────▼───────────┐
│                               ViewModel                                │
│       OrderSessionState (IDLE, LISTENING, PROCESSING, CONFIRMING)      │
└──────────────┬────────────────────────┬────────────────────┬───────────┘
               │                        │                    │
┌──────────────▼────────┐   ┌───────────▼────────────┐   ┌───▼───────────┐
│ Speech & Audio Engine │   │   Hybrid Parser Engine │   │  Room DB Repo │
│ - SpeechRecognizer    │──▶│ - On-Device Phonetic   │──▶│ - Menu Items  │
│ - Continuous Loop     │   │   Fuzzy Matcher        │   │ - Orders      │
│ - Noise Gate & Level  │   │ - Gemini AI Fallback   │   │ - Order Items │
└───────────────────────┘   └────────────────────────┘   └───────────────┘
```

### Data Models & Entities
1. **MenuItem**:
   - `id: Long` (Primary Key)
   - `name: String` (e.g. "Banku", "Okro (1 scoop)")
   - `price: Double` (GHS, e.g. 5.0, 2.0, 15.0)
   - `unit: String` (e.g. "portion", "scoop", "piece", "cup")
   - `aliases: String` (comma-separated phonetic variations)
2. **OrderItem**:
   - `menuItemId: Long`, `name: String`, `unitPrice: Double`, `quantity: Int`, `subtotal: Double`
3. **OrderReceipt**:
   - `orderNumber: Int`, `orderType: OrderType (DineIn, Takeout)`, `items: List<OrderItem>`, `subtotal: Double`, `grandTotal: Double`, `timestamp: Long`

---

## Implementation Stages

1. **Slice 1: Separate the Two Calculators (Completed & Verified)**:
   - Created `/manual/ManualState.kt` and `/manual/ManualKeypadHandler.kt` for independent manual calculation state.
   - Created `/voice/VoiceState.kt` for independent voice transcript, detected items, and total.
   - Refactored `PosViewModel.kt` to guarantee zero state leakage: keypad buttons modify `ManualState` only; voice bar and transcription modify `VoiceState` only.
   - Implemented deliberate `+` bridge button on Voice card to append voice total to manual expression.
   - Resolved blank/black screen launch issue caused by class constructor collision (`QuickCalcApp` vs `QuickCalcScreen`).
   - Verified with unit tests (`CalculatorSeparationTest`) and app compilation.

2. **Slice 2: Gemini Live Transcription (Completed & Verified)**:
   - Relocated Voice Card `+` bridge button from the top row to the bottom row beside the transcript line, keeping Row 1 clean, symmetric, and uncluttered.
   - Built `VoiceAudioCapture.kt` in `/voice` capturing 16kHz 16-bit PCM mono audio with real-time RMS decibel monitoring and integration with `AudioNoiseGate`.
   - Built `GeminiLiveSession.kt` in `/voice` implementing bidirectional WebSocket streaming using `models/gemini-3.5-transcribe-live`.
   - Built `VoiceOrderParser.kt` in `/voice` supporting real-time parsing of Ghanaian dishes, quantities, aliases, and spoken arithmetic.
   - Built `VoiceTranscriptionManager.kt` coordinating audio capture, live transcription session, and parser.
   - Wired `VoiceTranscriptionManager` into `PosViewModel` and connected all voice bar actions (hold, lock, pause, resume) to start/stop listening.
   - Added unit test suite `VoiceSlice2Test.kt` verifying dual calculator state isolation, food order parsing, and spoken math conversion. All 18 tests passing.

3. **Slice 3: Voice Order Logic & Ghanaian Food Catalog Matching (Next Slice)**:
   - Match spoken food orders against custom menu items with GHS pricing and dynamic aliases.
   - Real-time catalog price updates reflected in voice total computation.

4. **Slice 4: UI Polish & Ergonomics**:
   - Final verification of keypad spacing, card padding, mic bar gestures (hold, swipe-to-lock, pause), and dynamic color fidelity across all screens.

