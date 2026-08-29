# 薪流 SalaryFlow Design System

## 0. Design Foundations

- 薪流 (SalaryFlow) is an original local-first product: it turns a monthly salary into a live,
  per-second flow so that time and money can be felt together.
- The visual language is cool mist + deep ink navy with ice-blue accents, with a
  monospaced ticker as the signature element; all tokens are defined in this document and implemented in
  `apps/web/src/styles.css` and `apps/web/src/mobile.css`.
- Scope decisions: ledger/accounting pages are intentionally out of scope; the product
  focuses on live salary, wishes, slacking-time, unexpected income/expenses, and owned
  assets.

## 1. Atmosphere & Identity

薪流 feels like a calm, focused workday companion. The surface is cool mist with a deep ink-navy dashboard card acting as the visual anchor. The signature is “money in motion”: a monospaced number grows in real time inside a dark, softly lit card, turning an abstract monthly salary into something tactile without becoming a gamified finance app.

## 2. Color

### Palette

| Role | Token | Value | Usage |
|------|-------|-------|-------|
| Canvas | `--cream` | `#eef2f7` | App background |
| Paper | `--paper` | `#fbfdff` | Cards, forms, list rows |
| Ink | `--ink` | `#161d2a` | Primary text |
| Dark surface | `--dark` | `#14213a` | Hero, active navigation, primary actions |
| Muted text | `--muted` | `#5d6f86` | Descriptions and metadata |
| Line | `--line` | `#d9e0ea` | Separators and outlines |
| Steel blue | `--green` | `#3e6ea8` | Secondary accent and links |
| Ice blue accent | `--lime` | `#b7d8f0` | Progress, active detail, success affordance |
| Coral accent | `--orange` | `#dd7e66` | Stop/destructive-but-playful action |
| Hero text | `--hero-text` | `#f7f4e8` | Text on dark surface |
| Hero secondary | `--hero-muted` | `#aebe99` | Rate and progress metadata |
| Error | `--error` | `#bd7962` | Invalid input boundary |

### Rules

- Use the warm canvas for the shell and the dark surface for the primary metric or a primary action.
- Accent colors carry meaning: lime means progress/confirmed, warm orange means stop/attention, sage means navigable/secondary.
- Avoid gradients except for the low-contrast radial atmosphere in the app background and the hero glow.
- Web and Android use local system font stacks; monetary values use the local monospace stack.

## 3. Typography

| Level | Size | Weight | Line Height | Usage |
|------|------|--------|-------------|-------|
| Page title | `34px` | 650 | 1.14 | Screen heading |
| Section title | `19px` | 650 | 1.3 | Group heading |
| Card title | `14px` | 600 | 1.4 | Card or row title |
| Body | `14px` | 400 | 1.6 | Supporting copy |
| Label | `12px` | 600 | 1.4 | Form labels and compact actions |
| Caption | `11px` | 500 | 1.5 | Metadata and eyebrow |
| Metric | `27px` | 500 | 1.1 | Secondary monetary values |
| Hero metric | `64px` | 500 | 1.05 | Today's earned amount |
| Timer | `45px` | 500 | 1.1 | Slacking timer |

- Primary: local system UI fonts with Chinese fallbacks.
- Numeric: local monospace fonts.
- Keep monetary and timer widths stable with tabular/monospaced figures so the live ticker never shifts surrounding layout.

## 4. Spacing & Layout

The base unit is 4px, with a compact 4/8/12/16/20/24/32/36/48 rhythm.

| Token | Value | Usage |
|------|-------|-------|
| `--space-1` | 4px | Icon and label separation |
| `--space-2` | 8px | Compact control groups |
| `--space-3` | 12px | List/card inner gaps |
| `--space-4` | 16px | Standard card and grid gaps |
| `--space-5` | 20px | Hero/detail breathing room |
| `--space-6` | 24px | Form/card padding |
| `--space-8` | 32px | Section separation |
| `--space-9` | 36px | Page-header/hero spacing |
| `--space-12` | 48px | Primary metric vertical rhythm |

- Desktop shell: fixed 220px sidebar, centered content capped at 1120px, 48px horizontal page inset.
- Mobile shell: one readable column, 15px page inset, bottom glass dock with three compact tabs and a swipe/tap expansion for secondary destinations.
- Breakpoints: 900px switches to mobile navigation; 560px switches converter cards to a single-column action layout; 390px tightens dock gutters.
- Bottom navigation respects `safe-area-inset-bottom` in the Android WebView.
- Runtime CSS consumes semantic tokens for shared roles; `--unit-*` aliases centralize source-specific one-off dimensions without changing the frozen rendering.

## 5. Components

### App Shell & Navigation

- **Structure**: fixed sidebar on desktop; fixed glass bottom dock and expandable drawer on mobile.
- **Variants**: desktop, mobile compact, mobile expanded.
- **Spacing**: 220px rail; 14px mobile dock side inset; 4px tab gap.
- **States**: default, active dark pill, hover/pressed tonal shift, focus-visible outline, expanded drawer, reduced-motion.
- **Accessibility**: semantic links/buttons, labels on icon-only controls, active destination is visually and semantically exposed.
- **Motion**: route content fades/slides in; drawer expands from the dock; reduced motion removes transforms.

### Hero Earnings Card

- **Structure**: label + live monospaced amount + rate + progress track + earned/worked/forecast metadata.
- **Variants**: before work, working, after work, invalid profile fallback.
- **Spacing**: 16px radius, 28px top/24px horizontal padding, 12px metadata separator.
- **States**: live ticker, progress at 0/partial/100, rate hint, focusable quick action.
- **Accessibility**: metric has a descriptive label; progress includes text equivalents; color is never the only signal.
- **Motion**: numeric text updates without layout movement; ambient glow is static; all non-essential motion honors reduced motion.

### Metric / Quick Cards

- **Structure**: label, icon badge, monospaced value, helper text, optional link.
- **Variants**: paper metric, lime-tinted metric, quick conversion card.
- **States**: default, hover/pressed lift, focus-visible, empty/zero value.
- **Accessibility**: cards with actions are links/buttons, not clickable generic containers.

### Input Card & Settings Form

- **Structure**: grouped labelled controls, inline preview, primary save action.
- **Variants**: converter, salary settings, living-cost extension.
- **States**: default, focus, invalid, saved, disabled, calculation warning.
- **Accessibility**: every input has a visible label, keyboard/native picker support, alert text for invalid work duration.

### List Card & Confirm Dialog

- **Structure**: avatar/emoji, title/meta, right-aligned result, action cluster; confirmation surface for destructive or irreversible actions.
- **Variants**: wish, slacking history, owned asset, unexpected-money record.
- **States**: default, purchased/settled, empty, delete confirmation, purchase confirmation, toast follow-up.
- **Accessibility**: destructive actions are labelled by item; dialog has explicit cancel and confirm actions.

### Timer Card

- **Structure**: fish orbit, state label, HH:mm:ss timer, earned amount, start/stop action, rate caption.
- **Variants**: idle, running, completed history.
- **States**: idle, active, stop confirmation, empty history, reduced-motion.
- **Motion**: running fish uses a restrained float; timer value is timestamp-derived; reduced motion removes float transform.

## 6. Motion & Interaction

| Token | Value | Usage |
|------|-------|-------|
| `--motion-micro` | 180ms ease | Press, hover, focus surface change |
| `--motion-standard` | 280ms ease | Dock/backdrop state |
| `--motion-emphasis` | 360ms cubic-bezier(.22,.8,.24,1) | Route entry, toast, drawer |
| `--motion-live` | 250ms refresh cadence | Live ticker/timer computation, not CSS animation |

- Interaction mechanisms: compact navigation drawer, stateful timer card, and confirmation flow.
- Spatial movement uses transform/opacity; color and shadow transitions use short easing.
- The live amount is recomputed from timestamps rather than incremented counters, so backgrounding, lock screen, and refresh remain correct.
- Android system “remove animations” / reduced-motion preference should disable the fish float and route/drawer transforms while preserving state feedback.

## 7. Depth & Surface

Strategy: mixed. Warm paper surfaces use a 1px warm line plus a restrained soft shadow. Dark surfaces use layered shadow and a radial glow. Mobile navigation uses translucent glass with blur, an inner top rim, and a lift shadow.

| Level | Recipe | Usage |
|------|--------|-------|
| Paper | `1px solid #e4e0d6` + `0 8px 25px rgba(36,41,32,.03)` | Cards and forms |
| Hero | `0 18px 50px rgba(32,43,32,.15)` + radial glow | Dashboard hero |
| Dock | `0 12px 34px rgba(41,49,39,.14)` + inset white rim | Mobile navigation |
| Dialog/toast | `0 18px 48px rgba(28,35,27,.24)` | Confirmations and follow-up |

## 8. Accessibility Constraints & Accepted Debt

### Constraints

- WCAG 2.2 AA intent: body text targets 4.5:1 contrast; large text and controls target 3:1 minimum.
- Every form field has a label; every icon-only button has an accessible name; focus-visible state remains visible in the WebView.
- Touch targets are at least 44dp for primary mobile actions and dock destinations.
- No essential meaning relies on color alone; status text accompanies progress, purchase, and warnings.
- Native time/number controls remain operable with Android TalkBack and keyboard navigation.
- Respect Android/system reduced-motion preference in the web layer.

### Accepted Debt

| Item | Location | Why accepted | Owner / Exit |
|------|----------|--------------|--------------|
| A few one-off geometry and effect values sit alongside the runtime tokens | `apps/web/src/**/*.css` | The runtime bridge centralizes the documented base tokens while preserving the exact rendering | Consolidate remaining one-off values only when it does not change the current rendering |
| Android shell is Capacitor WebView rather than a separate native layout | `apps/web/native/android` | Single-UI architecture keeps web/Android in parity | Add native haptics/share only when requested |
