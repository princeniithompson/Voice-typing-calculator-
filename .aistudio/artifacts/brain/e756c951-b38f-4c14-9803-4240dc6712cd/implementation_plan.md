# 📘 Final Revised Blueprint: Master-Detail Adaptive History & "Dry Paint" Aesthetic 🎨✨

This revised blueprint outlines the complete **History Architecture**, **Adaptive Layout Safeguards**, and **Google M3 Rounded Typography Rules** for **QuickCalc**.

---

## 🗂️ 1. Master-Detail History Architecture 📱

When you open **History**, you get a clean, minimal, chronological list of all saved calculation sessions.

### 📜 Step A: The Master History List View
Every session is listed as a clean, flat matte container showing:
* 🕒 **Timestamp:** e.g., `"Today, 10:28 AM"` (or `"10:28 AM"`).
* 🏷️ **Batch Badge:** `"1-in-1"`, `"2-in-1"`, `"3-in-1"`, `"4-in-1"`... with a flat M3 matte pill background.
* 💰 **Grand Total:** Big, clear, crisp numbers (e.g., **`GH₵ 91.00`**).

```
┌────────────────────────────────────────────────────────┐
│  🕒 10:28 AM   [ 3-in-1 Session ]           GH₵ 91.00  │
│  Rice, Chicken, Salad, Plantain...                     │
└────────────────────────────────────────────────────────┘
┌────────────────────────────────────────────────────────┐
│  🕒 09:15 AM   [ 1-in-1 Session ]           GH₵ 25.00  │
│  Banku + Fried Fish                                    │
└────────────────────────────────────────────────────────┘
```

---

### 🔍 Step B: Tapping a Session ➔ Full Swipeable Receipt Detail View
When you tap any session row in the list:
* **For a 1-in-1 Session:** Opens a clean 1-page batch receipt view with flat matte M3 fills.
* **For a 2-in-1, 3-in-1, or 4-in-1 Session:** Opens the full **Swipeable Horizontal Pager** where you can swipe left/right to view **Batch 1**, **Batch 2**, **Batch 3**...

```
┌────────────────────────────────────────────────────────┐
│ 🟢 BATCH 1 OF 3  [1-in-1 Matte Tag]   [Swipe Left 👈] │
│                                                        │
│               GH₵ 56.00                                │
│       (Big, Bold Rounded Numerals)                     │
│                                                        │
│  🥗 Rice • Chicken • Salad • Plantain                  │
│                                                        │
│  ● ◯ ◯  (Page Dots)          Session Total: GH₵ 91.00 │
└────────────────────────────────────────────────────────┘
```

---

## 📐 2. Adaptive Layout Safeguards & Android 17 Bubble Mode 🫧

To prevent any weird text clipping, awkward line breaks, or text morphing out of bounds (such as `"Today"` clipping to `"T"`):

1. **Android 17 Floating App Bubble & Multi-Window Safety:**
   * Uses `BoxWithConstraints` and fluid responsive paddings (`dp` / `sp`) so that even when opened inside a small Android 17 app bubble or split screen, every element scales proportionally.
2. **Text Clipping Prevention:**
   * All timestamps, text titles, and batch labels use `maxLines = 1`, `TextOverflow.Ellipsis`, and flex-weight bounds (`Modifier.weight(1f, fill = false)`).
   * Date text like `"Today, 10:28 AM"` will never wrap vertically or clip letters out of containers.

---

## ✏️ 3. Google "Dry Paint" Rounded Geometry & Typography 🎨

1. **Flat "Dry Paint" Matte Fills:** All cards, keypads, and batch tags use solid, flat, opaque M3 container colors (`surfaceContainer`, `primaryContainer`, `secondaryContainer`, `tertiaryContainer`, `surfaceVariant`). Zero glossy gradients or glowing overlays.
2. **Rounded Digit Geometry:** All numbers and text use Google Product Sans / Rounded font geometry (`CalculatorTokens.CalcTypeface`). Numbers like `7`, `4`, `1`, `8` feature soft, rounded corners and smooth terminal curves matching official Google Material 3 calculator design.

---

## 🗣️ 4. Cumulative Voice Sections & Echo Safeguard 🛡️

1. **First Section (Batch 1):** Say *"3 cups of rice, 1 chicken, salad, 3 plantains"*. Tap **`=`**. Total: **`GH₵ 56.00`**.
2. **Second Section (Batch 2):** Tap **`=`**. Live mic restarts. Previous `56.00 GH₵` is preserved. You say *"That was 56 cedis... plus 4 cups of rice"*.
3. **Echo Guard Filter:** Identifies `56` as the carried-over total and filters it out. Only evaluates `4 cups of rice` (`GH₵ 20.00`).
4. **Updated Grand Total:** `56.00 + 20.00 = ` **`GH₵ 76.00`**.
5. **Reset:** Tapping **`AC`** or **`Stop`** commits the multi-batch order (`2-in-1`) to History and clears the live screen to `GH₵ 0.00`.

---

## 🏗️ 5. Next Steps

1. **Room Database & Data Model:** Support multi-batch order serialization (`1-in-1`, `2-in-1`, `3-in-1`...) with timestamping.
2. **History List UI:** Master list of sessions with batch badges and grand totals.
3. **Swipeable Detail Modal:** Full-screen/modal swipeable carousel for inspecting individual batches with M3 matte container fills.
4. **Echo Safeguard & Voice Logic:** Running balance carryover, duplicate number suppression, and `AC`/`Stop` reset rules.

---
*Please review this complete blueprint! Whenever you're ready, click **Proceed** or let me know and I will begin building!* 🚀
