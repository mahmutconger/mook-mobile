# WalkTalk live-translation demo

An interactive, in-app showcase of WalkTalk's live translation, embedded in Mook and
ending in a CTA that hands the user to WalkTalk through Mook's existing SSO flow.

Two participants share one transcript, each with their own language. Whatever the
active participant types is translated into the other one's language through a real
DeepL call and posted as a bubble whose **hero line is the translation**. The second
participant is simulated locally — there is no networking beyond the translation proxy,
no accounts, and nothing written to Firestore.

---

## Where it lives

| Layer | Path |
|---|---|
| Contract | `shared/…/domain/translation/Translator.kt`, `DemoLanguages.kt` |
| Transport | `shared/…/data/translation/FirebaseFunctionsTranslator.kt`, `CallableErrorMapping.kt` |
| Test/preview double | `shared/…/data/translation/FakeTranslator.kt` |
| Logic (no platform imports) | `shared/…/feature/walktalkdemo/LiveTranslationEngine.kt`, `ChatDemoUiState.kt`, `DemoSeed.kt` |
| UI | `shared/…/feature/walktalkdemo/WalkTalkDemoScreen.kt`, `ChatBubble.kt` |
| Entry point | `shared/…/ui/components/WalkTalkDemoPromoCard.kt` (top of the Chats tab) |
| CTA URL | `shared/…/util/WalkTalkDeepLink.kt` → `buildWalkTalkSsoEntryUrl()` |
| Backend | `functions/index.js` → `translateText` |
| Route | `NavRoutes.WalkTalkDemo` (`walktalk_demo`, deep link `mook://walktalk-demo`) |

---

## Backend setup

The DeepL key is **never** in the client. It lives in Secret Manager and is read only
inside the Cloud Function.

```bash
# 1. Store the key (free-tier keys end in ":fx"; the function picks the host from that).
firebase functions:secrets:set DEEPL_AUTH_KEY

# 2. Deploy just this function.
firebase deploy --only functions:translateText
```

Nothing else needs configuring: the client calls the function by name through the
Firebase SDK, so there is no base URL, no endpoint constant and no build-config value
to keep in sync. Requires Node 20 (global `fetch`), already pinned in
`functions/package.json`.

### Proxy contract

```
callable: translateText            (Firebase auth token attached automatically)
request:  { "text": "...", "source": "TR" | null, "target": "EN-US" }
response: { "translatedText": "...", "detectedSource": "TR" | null }
```

The function validates length (≤ 500 chars) and the target against an allow-list
mirroring `Languages.ALL`, rate-limits per uid, and **never logs message text** — only
status codes and error class names.

Failure codes map to user-facing states in `CallableErrorMapping.kt`:

| Callable code | `TranslationError` | What the user sees |
|---|---|---|
| `unavailable`, `aborted`, `data-loss`, `cancelled` | `NETWORK` | "No connection…" + retry |
| `deadline-exceeded` | `TIMEOUT` | "Took too long…" + retry |
| `resource-exhausted` | `RATE_LIMITED` | "Wait a few seconds…" + retry |
| `invalid-argument`, `out-of-range`, `failed-precondition` | `INVALID_INPUT` | Length/language message |
| `unauthenticated`, `permission-denied` | `UNAUTHENTICATED` | "Sign in again…" |
| anything else | `SERVER` | "Unavailable right now…" + retry |

### Swapping DeepL for a raw HTTPS proxy

`Translator` is the only thing the app depends on. To move to a plain
`POST {MOOK_TRANSLATE_ENDPOINT}` with Ktor, write a second implementation and change
one line in `di/AppModule.kt`:

```kotlin
single<Translator> { FirebaseFunctionsTranslator() }   // → KtorTranslator(baseUrl = …)
```

The request/response shape above is already the HTTP shape, so nothing else moves.

---

## Running it

- Android: `./gradlew :androidApp:assembleDebug`, open the **Chats** tab, tap the
  "Chat without the language barrier" card. Or `adb shell am start -a android.intent.action.VIEW -d "mook://walktalk-demo"`.
- iOS: run from Xcode as usual; same entry point.
- Tests: `./gradlew :shared:testAndroidHostTest` and `./gradlew :shared:iosSimulatorArm64Test`.
  All of the demo's tests run in `commonTest` with no network.

## Fake vs. live translator

| Context | Implementation | How |
|---|---|---|
| App | `FirebaseFunctionsTranslator` | Bound in `di/AppModule.kt` |
| Compose previews | `FakeTranslator` | Already used by the `@Preview`s in `WalkTalkDemoScreen.kt` |
| Unit tests | `ManualTranslator` / `ScriptedTranslator` | Local to `LiveTranslationEngineTest` |
| Manual offline demo | `FakeTranslator` | Temporarily change the `single<Translator>` line in `AppModule.kt` |

`FakeTranslator(latencyMillis = 600)` is the useful one for showing the loading states;
`FakeTranslator(failWith = TranslationError.NETWORK)` for the failure states.

---

## Design decisions worth knowing

**One request per keystroke burst, and the newest always wins.** Typing feeds a 400 ms
debounce that produces a preview of what the other side will read. Two independent
guards keep a stale answer off screen: the previous job is cancelled, *and* every
response carries a generation (for the preview) or an attempt number (for a bubble) that
is checked before it is applied. Cancellation alone is not sufficient — a request already
inside the provider call can still return — which is why both exist and why
`LiveTranslationEngineTest` uses a non-cancellable translator double to prove it.

**Send reuses the preview.** If the debounced preview already resolved the exact text
for the exact language pair, the bubble opens already translated: one keystroke burst
costs one DeepL call, not two.

**History is immutable.** Each bubble records the source/target it was sent with.
Changing a participant's language affects the next message, never the transcript.

**No blank bubbles.** `BubbleTranslation` has exactly three states — in flight, done,
failed — so there is no code path that renders an empty hero line. The in-flight state
keeps the same typography as the resolved one, so nothing jumps when it lands.

**Nothing user-authored is logged.** Not the input, not the translation, not the token,
not the email. `TranslationException` carries only an enum name, deliberately without a
`cause`, so it cannot smuggle text into a crash report.

**Strings.** English lives in `composeResources/values/strings.xml`, Turkish in
`values-tr/`, matching the rest of the app rather than hard-coding Turkish. Keys are
prefixed `wtdemo_`. `generate_strings.py` is additive and skips existing keys, so it
will not clobber them.

---

## Open items

1. **WalkTalk entry path.** `buildWalkTalkSsoEntryUrl()` opens
   `https://walktalkk.com/chat?source=mook_translate_demo&flow=sso`. `/chat` is used
   because it is the one path already proven to be registered in WalkTalk's App Links /
   `apple-app-site-association` — an unregistered path opens Safari instead of the app on
   iOS. Confirm with the WalkTalk team whether a dedicated landing path (`/sso`) exists;
   if so, change the single constant `WALKTALK_SSO_ENTRY_URL` in `WalkTalkDeepLink.kt`.
2. **Android package visibility.** The store fallback needs a `<queries>` entry for
   `com.istaps.walktalk2` in the Android manifest (already required by the existing match
   flow — worth confirming it is there).
3. **Arabic.** The brief lists `AR`; `Languages.ALL` does not contain it. Adding it to the
   demo alone would fork the catalogue, so it was left out. Add it to `Languages` first if
   WalkTalk supports it.
4. **Rate limiting** in `translateText` is in-memory, so it is per warm instance. Good
   enough to stop a runaway client; move it to Firestore if the demo is ever opened to
   unauthenticated callers.
