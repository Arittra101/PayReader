# Android Application Architecture Specification

> A reusable, product-agnostic high-level technical specification for building maintainable Android applications.
> Hand this document to any team — it defines the stack, packaging, layering, and conventions to follow on any new product.

---

## 1. Tech Stack (Mandatory)

| Layer | Choice |
|---|---|
| Language | Kotlin (latest stable) |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM, feature-modular packaging |
| Dependency Injection | Koin (`koin-android`, `koin-androidx-compose`, `koin-compose-viewmodel`, `koin-compose-viewmodel-navigation`) |
| Networking | Ktor 3 Client (OkHttp engine) |
| Local Storage | AndroidX DataStore (Preferences) |
| Navigation | **Navigation 3** — `androidx.navigation3:navigation3-runtime` + `androidx.navigation3:navigation3-ui` + `androidx.lifecycle:lifecycle-viewmodel-navigation3`, with type-safe `@Serializable` `NavKey` destinations |
| Paging | Paging 3 |
| Image Loading | Coil 3 |
| Serialization | `kotlinx.serialization` (JSON) |
| Build Config | Version catalog (`libs.versions.toml`) + `BuildConfig` (or `BuildKonfig`) for env-driven constants |
| Logging | Timber wrapped behind an internal `AppLogger` |
| Architecture pattern | Single Activity + Compose destinations |
| Min SDK | 24 (or per product requirement) |
| JVM Target | 11+ |

**Rationale**: every entry above is non-negotiable for a maintainable Android codebase — they collectively give type safety end-to-end, testability, and zero per-call boilerplate.

**Why Navigation 3 and not Navigation 2**: in Navigation 3 the back stack is a plain observable list that the app owns, not a black box inside a `NavController`. Destinations are real Kotlin objects, not route strings. This removes the string-building, the argument encoding, and the "who owns the NavController" problem that Navigation 2 forced on every feature.

| Navigation 2 | Navigation 3 |
|---|---|
| `NavController` | `NavBackStack` — a list you own |
| `NavHost { composable<T> { } }` | `NavDisplay { entryProvider { entry<T> { } } }` |
| `navController.navigate(route)` | `backStack.add(key)` |
| `navController.popBackStack()` | `backStack.removeLastOrNull()` |
| Route strings / `@Serializable` routes | `@Serializable` keys implementing `NavKey` |
| Nested graphs | Plain list slices + `SceneStrategy` |

Minimum versions: `navigation3-runtime` / `navigation3-ui` 1.1.x (stable), `lifecycle-viewmodel-navigation3` matching your Lifecycle version. Add `androidx.compose.material3.adaptive:adaptive-navigation3` only if the product needs list-detail / multi-pane layouts.

---

## 2. Top-Level Project Layout

```
<root>/
├── app/                              # The single Android application module
│   ├── build.gradle.kts
│   └── src/main/kotlin/<package>/
├── gradle/libs.versions.toml         # Version catalog — the ONLY place versions live
├── settings.gradle.kts
└── local.properties                  # Secrets — NEVER committed
```

Rule: **one app module** unless there is a measured build-time reason to split. Premature modularization is the most common cause of architecture drift.

---

## 3. Package Architecture (Inside `app/src/main/kotlin/<root-package>`)

```
<root-package>/
├── App.kt                            # Root Composable (Scaffold + NavDisplay + global overlays)
├── MainApplication.kt                # Application — initializes DI, logging, monitoring
├── MainActivity.kt                   # Single activity — hosts App()
├── NetworkStatus.kt + NetworkMonitor # Connectivity state exposed as Flow<Boolean>
│
├── core/                             # Feature-agnostic building blocks (shared by all features)
│   ├── di/                           # KoinInitializer, networkModule, storageModule, sessionModule, appModules()
│   ├── data/
│   │   ├── network/                  # HttpClientFactory, safeApiCall, no-auth markers
│   │   ├── storage/                  # LocalStorage interface + DataStore impl
│   │   ├── session/                  # Session interface + Impl
│   │   └── dto/                      # Cross-cutting DTOs (auth tokens, refresh request, base envelope)
│   ├── domain/                       # Result<D,E>, Error, DataError, BaseResponse
│   ├── presentation/                 # Shared Composables + base screens
│   │   └── components/               # PrimaryButton, EmptyView, BottomSheets, PullToRefresh, …
│   ├── feature_components/           # Cross-feature widgets that own logic (file pickers, uploaders…)
│   ├── helper/                       # Compose helpers (no nav-JSON helpers needed — keys are @Serializable)
│   └── utils/                        # AppLogger, DateTimeFormat, AppUtils
│
├── navigation/                       # Single navigation setup for the whole app
│   ├── Route.kt                      # Sealed interface : NavKey; @Serializable destinations
│   ├── AppNavDisplay.kt              # The only NavDisplay — wires every Route → Screen
│   ├── Navigator.kt                  # Façade over NavBackStack (guards, helpers)
│   ├── TopLevelDestination.kt        # Bottom-bar entries
│   ├── NavigationAction.kt           # Side-effect actions (global logout, deep-link target, …)
│   └── LogoutEventManager.kt         # Global app-wide events as SharedFlow
│
├── theme/                            # MaterialTheme, color scheme, typography
├── bottombar/                        # AppBottomBar Composable
│
└── feature/                          # One package per business feature — see §4
    ├── <feature-a>/
    ├── <feature-b>/
    └── …
```

Rules:
- The **only** packages allowed to depend on `feature/*` are `App.kt`, `navigation/`, and DI aggregation.
- `core/` may NOT import anything from `feature/`. Ever.
- `feature/<x>/` may NOT import anything from `feature/<y>/`. Cross-feature state lives in `core/data/session`.

---

## 4. Feature Module Convention (Clean Architecture)

Every feature package follows the same internal shape — no exceptions:

```
feature/<name>/
├── di/         <Name>Module.kt        # Koin module: ApiService, Repository, ViewModel
├── data/
│   ├── api_service/                   # Ktor service interface + Impl
│   ├── dto/                           # @Serializable DTOs
│   ├── mapper/                        # DTO ⇄ Domain mappers (extension functions)
│   ├── paging/                        # PagingSource(s) if list is paginated
│   └── repository/                    # Repository implementation
├── domain/
│   ├── model/                         # Plain domain models (no annotations)
│   └── repository/                    # Repository interface
└── presentation/
    ├── <Name>ScreenRoot.kt            # Stateful wrapper — obtains ViewModel, side-effects, navigation
    ├── <Name>Screen.kt                # Stateless Composable — pure state → UI
    ├── <Name>ViewModel.kt             # Exposes StateFlow<UiState> + onEvent(event)
    └── components/                    # Screen-local widgets
```

### Why the `ScreenRoot` / `Screen` split

| Layer | Responsibility |
|---|---|
| `ScreenRoot` | Obtains `koinViewModel()`, collects state, performs side-effects (navigate via `Navigator`, show snackbar, request permissions). |
| `Screen` | Pure stateless Composable. Inputs: `state`, callbacks. No `viewModel`, no `Navigator`, no back stack, no Koin. |

Result: `Screen` is preview-friendly, screenshot-testable, and reusable. Never inline a ViewModel inside a `Screen`.

### Data flow inside a feature

```
ApiService  →  Repository  →  ViewModel (StateFlow<UiState>)  →  ScreenRoot  →  Screen
   ↑              ↑                                                                  │
   └─── DTOs ──── Mappers → Domain Models ────────────────────────────────────────────┘
                                                            events bubble back up via onEvent()
```

---

## 5. Dependency Injection (Koin)

### 5.1 Initialization

```kotlin
// core/di/KoinInitializer.kt
fun initKoin(config: KoinAppDeclaration? = null) = startKoin {
    config?.invoke(this)
    modules(appModules())
}
```

```kotlin
// MainApplication.kt
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeAndroidContext(this)

        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) Timber.plant(Timber.DebugTree())
        else              AppLogger.disableLogging()

        initKoin { androidContext(this@MainApplication) }
        NetworkMonitor.start(this)
    }
}
```

### 5.2 Module aggregation

```kotlin
// core/di/Modules.kt
fun appModules() = listOf(
    networkModule, storageModule, sessionModule,
    // one per feature, in the order they are needed
    authModule, /* featureAModule, featureBModule, … */
)
```

### 5.3 Per-feature module shape (mandatory)

```kotlin
val <featureName>Module = module {
    singleOf(::<Name>ApiServiceImpl).bind<<Name>ApiService>()
    singleOf(::<Name>RepositoryImpl).bind<<Name>Repository>()
    viewModelOf(::<Name>ViewModel)
}
```

### 5.4 Consuming in Composables

```kotlin
val viewModel: <Name>ViewModel = koinViewModel()
```

With Navigation 3, ViewModel scoping is handled by the entry decorators on `NavDisplay`
(`rememberViewModelStoreNavEntryDecorator()`), not by passing a `ViewModelStoreOwner` around.
Each `NavEntry` gets its own `ViewModelStore`, so a ViewModel is created when the key is pushed
and cleared when the key is popped. Nothing extra is needed inside a `ScreenRoot`.

Passing the route key itself into the ViewModel:

```kotlin
entry<Route.Details> { key ->
    val viewModel: DetailsViewModel = koinViewModel { parametersOf(key.id) }
    DetailsScreenRoot(viewModel = viewModel, navigator = navigator)
}
```

If two screens genuinely must share one ViewModel instance, do **not** try to scope it to a
back-stack entry. Either put the shared state on `Session` (§9.2), or declare the ViewModel as a
`single` and inject it into both — the choice is explicit, not a navigation side effect.

---

## 6. Networking Layer

### 6.1 Single `HttpClient`

Build **one** Ktor `HttpClient` per process inside `core/data/network/HttpClientFactory.kt`. No feature module may create its own client.

Install the following plugins, in this order:

| Plugin | Configuration |
|---|---|
| `ContentNegotiation` | `json(Json { ignoreUnknownKeys = true })` |
| `HttpTimeout` | 20 s request + 20 s socket |
| `Logging` | Routes through `AppLogger`; `LogLevel.ALL` in debug, `NONE` in release |
| Custom `DynamicTokenPlugin` | On every request, read fresh token from `AuthTokenProvider` and set `Authorization: Bearer …` — skip if request is marked no-auth |
| `Auth { bearer { … } }` | `loadTokens` reads from storage; `refreshTokens` calls `POST auth/refresh`; on failure, clear tokens and emit `NavigationAction.NavigateToLogIn` via `LogoutEventManager` |
| `defaultRequest` | `url(baseUrl)` + `contentType(Json)` |

### 6.2 Public-endpoint marker

```kotlin
fun HttpRequestBuilder.markAsNoAuth()           // login / register / forgot-password
fun HttpRequestBuilder.isMarkedAsNoAuth(): Boolean
```

### 6.3 Safe-call wrapper (mandatory at every API call)

```kotlin
suspend inline fun <reified T> safeApiCall(execute: () -> HttpResponse): Result<T, DataError.Remote> {
    val response = try { execute() }
    catch (e: SocketTimeoutException)     { return Result.Error(DataError.Remote.REQUEST_TIMEOUT) }
    catch (e: UnresolvedAddressException) { return Result.Error(DataError.Remote.NO_INTERNET) }
    catch (e: Exception) {
        coroutineContext.ensureActive()
        return Result.Error(DataError.Remote.UNKNOWN)
    }
    return responseToResult(response)
}
```

Mapping table:

| HTTP / Exception | Result |
|---|---|
| 2xx + decoded body | `Result.Success(body)` |
| 2xx + decode failure | `DataError.Remote.SERIALIZATION` |
| 408 / `SocketTimeoutException` | `DataError.Remote.REQUEST_TIMEOUT` |
| 429 | `DataError.Remote.TOO_MANY_REQUESTS` |
| 5xx | `DataError.Remote.SERVER` |
| `UnresolvedAddressException` | `DataError.Remote.NO_INTERNET` |
| Other 4xx | Decode `ApiErrorResponse`, return `DataError.RemoteMessage(message, statusCode)` |

**Rule**: repositories never throw. They return `Result<DomainModel, DataError>`. ViewModels never see Ktor types.

---

## 7. Domain Result Type

```kotlin
sealed interface Result<out D, out E : Error> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Error<out E : Error>(val error: E) : Result<Nothing, E>
}

inline fun <T, E : Error, R> Result<T, E>.map(f: (T) -> R): Result<R, E>
inline fun <T, E : Error>    Result<T, E>.onSuccess(action: () -> Unit): Result<T, E>
inline fun <T, E : Error>    Result<T, E>.onError(action: () -> Unit): Result<T, E>
fun        <T, E : Error>    Result<T, E>.toData(): T?
fun        <E : Error>       Result<*, E>.asUnit(): Result<Unit, E>
```

Use this — and only this — for all repository return types. Do not use Kotlin's stdlib `Result` (it cannot encode typed domain errors).

---

## 8. Navigation 3 — **Centralized Routing (Mandatory)**

All navigation in the app is **centralized**. There is exactly one place where destination keys are declared, one place where they are wired to screens, one façade that screens use to navigate, and one place where top-level (bottom-bar) destinations are listed. **No feature is allowed to define its own keys, own the back stack, or expose any navigation API of its own.**

### 8.0 The four canonical files (single source of truth)

| File | Role | Allowed contents |
|---|---|---|
| `navigation/Route.kt` | **Every** destination in the app | A `sealed interface Route : NavKey` with `@Serializable` `data object` / `data class` entries — one per screen |
| `navigation/AppNavDisplay.kt` | The **only** `NavDisplay` in the app | `NavDisplay(backStack, onBack, entryDecorators, entryProvider { entry<Route.X> { … } })` for every `Route` entry |
| `navigation/Navigator.kt` | The **only** navigation API screens see | A `class Navigator(backStack, session, onAuthRequired)` exposing `navigateTo / navigateBack / navigateToTopLevel / navigateToRoute / navigateTo(action)` |
| `navigation/TopLevelDestination.kt` | The **only** list of bottom-bar entries | An `enum class TopLevelDestination(val route: Route, val icon: String, val label: String)` |

Supporting files in the same package:

- `navigation/NavigationAction.kt` — sealed type for global side-effect actions (e.g. `NavigateToLogInScreen`).
- `navigation/LogoutEventManager.kt` — global `SharedFlow<NavigationAction>` that any layer (e.g. the Ktor auth refresher) can emit to; collected once, in `App.kt`.

### 8.1 Type-safe keys

```kotlin
@Serializable
sealed interface Route : NavKey {
    @Serializable data object Splash : Route
    @Serializable data object Home   : Route
    @Serializable data object Login  : Route
    @Serializable data class Details(val id: String) : Route
    @Serializable data class ListScreen(val filter: String, val advanced: Filter? = null) : Route
    // …every destination in the app lives here, nowhere else
}
```

Rules for `Route.kt`:

- Every screen the user can reach is an entry here. No exceptions.
- Every entry implements `NavKey` and is `@Serializable`. This is what makes `rememberNavBackStack` survive process death.
- Arguments may be any `@Serializable` type. **Navigation 2's JSON-string workaround is gone** — do not hand-encode objects into `String` arguments any more, and delete `fromNavJson<T>()` helpers if you are migrating.
- Keep argument objects small and stable. They are saved to `Bundle` on process death, so do not put bitmaps, large lists, or whole API responses in a key — pass an id and reload.
- Routes are never built as strings (`"home/$id"` is forbidden — and is not even possible any more).
- A feature must not declare its own `Route`-like sealed types or its own `NavKey` implementations.

`AppNavDisplay.kt` consumes them generically:

```kotlin
@Composable
fun AppNavDisplay(
    backStack: NavBackStack<NavKey>,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { navigator.navigateBack() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(), // per-entry rememberSaveable state
            rememberViewModelStoreNavEntryDecorator(),      // per-entry ViewModel scoping
        ),
        entryProvider = entryProvider(
            fallback = { NavEntry(it) { FullScreenError("Unknown destination") } }
        ) {
            entry<Route.Splash> { SplashScreenRoot(navigator) }
            entry<Route.Home>   { HomeScreenRoot(navigator) }
            entry<Route.Login>  { LoginScreenRoot(navigator) }

            entry<Route.Details> { key ->
                DetailsScreenRoot(id = key.id, navigator = navigator)
            }
        },
    )
}
```

Every `Route` entry has exactly one `entry<…>` in `AppNavDisplay.kt`. If a key exists without wiring, or wiring exists without a key, it is a bug. The `fallback` exists only to fail visibly in release — never to hide a missing wiring.

**Both decorators are mandatory.** Without `rememberSaveableStateHolderNavEntryDecorator()`, `rememberSaveable` inside a screen silently loses its value. Without `rememberViewModelStoreNavEntryDecorator()`, every ViewModel is scoped to the Activity and is never cleared on pop.

### 8.2 `Navigator` façade (the **only** navigation API screens see)

Screens depend on `Navigator`, **never** on the back stack itself. `Navigator` is created once at the root via `rememberNavigator(backStack) { msg -> … }` and passed down to every `ScreenRoot`.

```kotlin
class Navigator(
    private val backStack: NavBackStack<NavKey>,
    val session: Session,
    private val onAuthRequired: (String) -> Unit = {},
) {
    val currentRoute: NavKey? get() = backStack.lastOrNull()

    fun navigateTo(route: Route, clearBackStack: Boolean = false)
    fun navigateToTopLevel(destination: TopLevelDestination)   // bottom-bar tab switch
    fun navigateToRoute(route: Route)                          // simple push
    fun navigateBack()
    fun navigateTo(action: NavigationAction)                   // global side-effects (logout, …)
}

@Composable
fun rememberNavigator(
    backStack: NavBackStack<NavKey>,
    onAuthRequired: (String) -> Unit = {},
): Navigator { … }
```

Reference implementation of the list operations — this is the whole of navigation in Nav3:

```kotlin
fun navigateToRoute(route: Route) {
    if (backStack.lastOrNull() == route) return   // replaces launchSingleTop
    backStack.add(route)
}

fun navigateBack() {
    if (backStack.size > 1) backStack.removeLastOrNull()
}

fun navigateTo(route: Route, clearBackStack: Boolean = false) {
    if (!guard(route)) return
    if (clearBackStack) backStack.clear()         // replaces popUpTo(inclusive = true)
    backStack.add(route)
}
```

Rules:

- All back-stack changes happen on the main thread. If you navigate from a coroutine, switch to `Dispatchers.Main` first.
- Never let the back stack become empty. `navigateBack()` guards with `size > 1`; a full `clear()` must always be followed by an `add()` in the same call.

Forbidden patterns (any of these is a code-review block):

- `@Composable fun MyScreen(backStack: NavBackStack, …)` — screens never accept the back stack.
- `backStack.add(...)` / `backStack.removeLastOrNull()` inside any feature package.
- A feature exposing its own `NavigateXxx` helper, sealed `Destination`, `NavKey`, or any custom navigation API.
- Importing `androidx.navigation3.*` anywhere outside the `navigation/` package.
- Any use of `androidx.navigation.*` (Navigation 2) anywhere in the app.

### 8.3 Auth-guarded routes

Maintain a single map of protected routes. The `Navigator` consults it before every navigate and either proceeds or triggers `onAuthRequired(message)`:

```kotlin
private val protectedRoutes: Map<KClass<out Route>, String> = mapOf(
    Route.Profile::class       to "Please login to access your profile",
    Route.MyList::class        to "Please login to view your list",
    // …
)

private fun guard(route: Route): Boolean {
    val message = protectedRoutes[route::class] ?: return true
    if (session.isUserLoggedIn.value) return true
    onAuthRequired(message)
    return false
}
```

`onAuthRequired` is wired from `App.kt` to show a `LoginPromptBottomSheet`.

### 8.4 Bottom bar (single source of truth for tabs)

`TopLevelDestination.kt` is the **only** place where bottom-bar entries are declared. Adding a new tab means adding one enum entry — nothing else changes.

```kotlin
enum class TopLevelDestination(
    val route: Route,
    val icon: String,
    val label: String,
) {
    HOME      (route = Route.Home,      icon = "files/ic_home.svg",       label = "Home"),
    SECONDARY (route = Route.Secondary, icon = "files/ic_secondary.svg",  label = "Secondary"),
    TERTIARY  (route = Route.Tertiary,  icon = "files/ic_tertiary.svg",   label = "Tertiary"),
    PROFILE   (route = Route.Profile,   icon = "files/ic_profile.svg",    label = "Profile");
}
```

Because the back stack is a list, visibility is a direct comparison — no route-string parsing:

```kotlin
val currentKey = backStack.lastOrNull()
val shouldShowBottomBar = TopLevelDestination.entries.any { it.route == currentKey }
```

Tab switching goes through `navigator.navigateToTopLevel(destination)`, which pops back to the
start destination and pushes the tab:

```kotlin
fun navigateToTopLevel(destination: TopLevelDestination) {
    val route = destination.route
    if (backStack.lastOrNull() == route) return
    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)  // back to start destination
    if (route != Route.Home) backStack.add(route)
}
```

Never touch the back stack from the bottom bar itself.

**Per-tab back stacks**: Nav3 has no `saveState = true`. If the product needs each tab to keep its
own history, hold a `Map<TopLevelDestination, NavBackStack<NavKey>>` in `Navigator` and pass the
active one to `NavDisplay`. Do this only if the product genuinely requires it — the single-stack
model above is the default.

### 8.5 Global app events (logout, force-update, …)

```kotlin
object LogoutEventManager {
    private val _events = MutableSharedFlow<NavigationAction>(...)
    val events: SharedFlow<NavigationAction> = _events
    suspend fun sendLogoutEvent(action: NavigationAction) = _events.emit(action)
}
```

The auth refresh handler and any repository can emit; `App.kt` collects and routes through `navigator.navigateTo(action)`. No callback chains.

---

## 9. Session & Local Storage

### 9.1 `LocalStorage` (the only key-value API in the app)

```kotlin
interface LocalStorage {
    suspend fun setString(key: String, value: String); suspend fun getString(key: String): String?
    suspend fun setInt(key: String, value: Int);       suspend fun getInt(key: String): Int?
    suspend fun setBoolean(key: String, value: Boolean); suspend fun getBoolean(key: String): Boolean?
    suspend fun setLong(key: String, value: Long);      suspend fun getLong(key: String): Long?
    suspend fun setFloat(key: String, value: Float);    suspend fun getFloat(key: String): Float?

    fun getFlowBoolean(key: String): Flow<Boolean>
    fun getFlowInt(key: String): Flow<Int>

    suspend fun remove(key: String)
    suspend fun clear()
    fun getAll(): Flow<Map<String, Any>>
}
```

Implementation: `DataStoreLocalStorage` backed by AndroidX DataStore Preferences. **No feature may import DataStore directly.**

### 9.2 `Session` — single owner of cross-feature reactive state

```kotlin
interface Session {
    val isUserLoggedIn: StateFlow<Boolean>
    suspend fun setUserLoggedIn(value: Boolean)

    // Cross-feature triggers (e.g. "a new item was submitted — refresh the list screen")
    val refreshListTrigger: SharedFlow<Unit>
    suspend fun triggerListRefresh()

    val userFullName: Flow<String?>
    suspend fun setUserProfile(fullName: String?, …)

    suspend fun resetUserPref()
}
```

Rule: if Feature A needs to react to something that happens in Feature B, that "something" goes on `Session`. Features do not import each other, and results are never passed back through the back stack.

---

## 10. Paging 3 (Mandatory For All Paginated Lists)

```kotlin
class XPagingSource(private val api: XApiService, …) : PagingSource<Int, XItem>() { … }

override suspend fun getStream(…): Flow<PagingData<XItem>> =
    Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false, prefetchDistance = 10),
        pagingSourceFactory = { XPagingSource(api, …) }
    ).flow
```

In Compose: `viewModel.flow.collectAsLazyPagingItems()`. Never hand-roll pagination.

---

## 11. UI Architecture

### 11.1 Root Composable shape

```kotlin
@Composable
fun App(currentLogoutEvent: NavigationAction? = null, onLogoutHandler: () -> Unit = {}) {
    val backStack = rememberNavBackStack(Route.Splash)
    var authMessage by remember { mutableStateOf<String?>(null) }
    val navigator = rememberNavigator(backStack) { msg -> authMessage = msg }

    authMessage?.let { LoginPromptBottomSheet(message = it, onDismiss = { authMessage = null }, …) }

    val currentKey = backStack.lastOrNull()
    val shouldShowBottomBar = TopLevelDestination.entries.any { it.route == currentKey }

    val isOnline by NetworkStatus.isAvailable.debounce(2000L).collectAsState(initial = true)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = { if (shouldShowBottomBar) AppBottomBar(...) }
        ) { padding ->
            AppNavDisplay(backStack, navigator, Modifier.padding(padding))
        }
        AnimatedVisibility(visible = !isOnline) { NoInternetScreen() }
    }
}
```

Note: system back is handled by `NavDisplay`'s `onBack`. Do not add a separate `BackHandler` at the root.

### 11.2 Required reusable components (live in `core/presentation/components`)

`PrimaryButton`, `SecondaryButton`, `KeyValueRow`, `EmptyView`, `FullScreenError`, `FullScreenLoader`, `PaginationLoader`, `PaginationError`, `ExitConfirmationDialog`, `MaterialDropdown`, `ApiResponseBottomSheet`, `ErrorMsgBottomSheet`, `ConfirmationBottomSheet`, `LoginPromptBottomSheet`, `SectionHeader`, `PullToRefreshIndicator`, `PullToRefreshBox`, `ReadMoreText`, `PillTag`, `ScrollableTabs`, `FilePicker`, `rememberPdfPicker`.

Every feature must reuse these. Inventing a new button style inside a feature is a code-review block.

---

## 12. Build Variants & Configuration

### 12.1 Product flavors

| Flavor | App ID | Suffix | App name |
|---|---|---|---|
| `prod` | `<base.applicationId>` | — | Production name |
| `qa`   | `<base.applicationId>.qa` | `-qa` | QA name |

Add `dev` / `staging` only if the product genuinely needs them.

### 12.2 Build types

| Type | Minify | Shrink resources | Signing |
|---|---|---|---|
| `debug` | off | off | debug keystore |
| `release` | on (ProGuard / R8) | on | release keystore from `local.properties` |

R8 note: navigation keys are resolved through `kotlinx.serialization`, so keep the serializer rules that the Kotlin serialization plugin ships. Do not add reflection-based keep rules for `Route`.

### 12.3 APK output naming

```
<app>-<flavor>-<buildType>-<versionName>-<versionCode>.apk
```

### 12.4 Versioning scheme

```kotlin
val major  = 0   // breaking changes
val minor  = 1   // new features
val hotfix = 0   // bug fixes
fun generateVersionCode() = major * 100_000 + minor * 1_000 + hotfix
fun generateVersionName() = "$major.$minor.$hotfix"
```

### 12.5 Environment-driven constants

Load secrets from `local.properties`, expose as compile-time constants. Either:

- `android.buildFeatures.buildConfig = true` + `buildConfigField(...)`, or
- `buildkonfig` plugin (recommended if you ever need to share with another platform)

Detect flavor at config time:

```kotlin
val isQa = gradle.startParameter.taskNames.any { it.contains("qa", ignoreCase = true) }
        || (project.findProperty("env") as? String) == "qa"
val baseURL = if (isQa) localProperties.getProperty("qa.base.url")
              else      localProperties.getProperty("base.url")
require(baseURL.isNotEmpty()) { "Base URL missing in local.properties" }
buildConfigField("String", "BASE_URL", "\"$baseURL\"")
```

### 12.6 `local.properties` template

```
sdk.dir=/Users/.../sdk
base.url=https://api.example.com/
qa.base.url=https://qa.api.example.com/
google.maps.api.key=...
store.file=/path/to/keystore.jks
store.password=...
key.alias=...
key.password=...
```

`local.properties` is **never** committed. `local.properties.example` (with empty values) is.

### 12.7 Version catalog (`gradle/libs.versions.toml`)

All library and plugin versions live here. Feature modules use `libs.<alias>`. No hard-coded version strings anywhere in any `build.gradle.kts`.

Navigation entries:

```toml
[versions]
navigation3 = "1.1.7"
lifecycle   = "2.10.0"

[libraries]
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui      = { module = "androidx.navigation3:navigation3-ui",      version.ref = "navigation3" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
```

Check the current versions before starting a new project; Navigation 3 still ships frequent releases.

---

## 13. Logging

```kotlin
object AppLogger {
    var isEnabled = true; private set
    fun disableLogging() { isEnabled = false }
    fun d(message: String) { if (isEnabled) Timber.d(message) }
    fun e(message: String, t: Throwable? = null) { if (isEnabled) Timber.e(t, message) }
}
```

Rules:
- Debug builds: `Timber.plant(Timber.DebugTree())`.
- Release builds: `AppLogger.disableLogging()`.
- Ktor `Logging` plugin routes through `AppLogger.d` — production binaries print nothing.
- **Never** log token values. Log only their presence (`"access token present: ${!token.isNullOrBlank()}"`).
- **Never** log full request/response bodies for endpoints handling PII in production.
- **Never** log a whole `Route` key — keys can carry user data. Log the class name only.

---

## 14. Mandatory Conventions

1. **Centralized routing — non-negotiable.** All navigation lives in exactly four files inside the `navigation/` package:
   - `Route.kt` — every destination, `sealed interface Route : NavKey`, all entries `@Serializable`.
   - `AppNavDisplay.kt` — every `Route` ↔ `Screen` wiring (the only `NavDisplay` in the app).
   - `Navigator.kt` — the only navigation API screens see.
   - `TopLevelDestination.kt` — the only list of bottom-bar entries.

   Forbidden everywhere outside `navigation/`:
   - Importing `androidx.navigation3.*`.
   - Importing `androidx.navigation.*` (Navigation 2) — forbidden everywhere, including `navigation/`.
   - Holding or accepting a `NavBackStack`.
   - Declaring sealed types named `Destination`, `Screen`, `Route`, `Nav*`, or any other `NavKey`.
   - Calling `backStack.add(...)` / `backStack.removeLastOrNull()` directly.
   - Building routes as strings (`"home/$id"`).

2. **One feature = one package = one Koin module.** No splitting a feature's data / domain / presentation across packages.
3. **Screens depend on `Navigator`, never on the back stack.** New guards (auth, deep-link, A/B) are added once, in `Navigator`.
4. **`Result<D, E>` end-to-end.** Repositories never throw. ViewModels never see network types.
5. **One `HttpClient` per process.** Token attachment, 401-refresh, and timeouts are configured once.
6. **`Session` owns cross-feature reactive state.** Features must not import each other, and screen results are never passed back through the back stack.
7. **`ScreenRoot` / `Screen` split.** `Screen` takes only `state` + callbacks.
8. **Secrets in `local.properties`, exposed via `BuildConfig` / `BuildKonfig`.** Never in VCS.
9. **DataStore behind a `LocalStorage` interface.** Features never call DataStore directly.
10. **Single Activity.** One `MainActivity` hosting the root Composable. Everything else is a `NavKey`.
11. **Version catalog is the only place versions live.**
12. **Reuse shared components.** Inventing a new button/dialog style inside a feature is a review block.
13. **Both entry decorators are always present on `NavDisplay`** — `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`.
14. **Code must be readable by developers of every skill level.** Write clear, easy-to-read code — prefer simple, self-explanatory names and straightforward control flow over cleverness, so that junior and senior developers alike can understand it at a glance.

---

## 15. Definition of Done (Per Feature)

A feature is "done" only when **all** of the following are true:

- [ ] Package follows the `feature/<name>/{di,data,domain,presentation}` shape.
- [ ] One Koin module added to `appModules()`.
- [ ] Repository interface in `domain`, implementation in `data`.
- [ ] All API calls go through `safeApiCall` and return `Result<DomainModel, DataError>`.
- [ ] DTOs are `@Serializable`; mappers (`DTO → Domain`) live in `data/mapper`.
- [ ] Screens are split into `ScreenRoot` (stateful) + `Screen` (stateless).
- [ ] ViewModel exposes `StateFlow<UiState>` + an `onEvent(event)` entry point.
- [ ] **All navigation goes through `Navigator`.** New screens are added to `Route` (sealed, `@Serializable`, `NavKey`) and wired with `entry<Route.X>` in `AppNavDisplay.kt`. No feature defines its own keys, holds the back stack, or imports `androidx.navigation3.*`.
- [ ] Key arguments are small and `@Serializable` (ids, not objects). No JSON-string arguments.
- [ ] Auth-protected routes are added to the guard map in `Navigator`.
- [ ] If the screen is a top-level destination, it is added to `TopLevelDestination.kt`.
- [ ] Rotation and process-death tested: screen state survives (`rememberSaveable` works) and the ViewModel is cleared when the screen is popped.
- [ ] Lists with > 1 page of data use Paging 3.
- [ ] No `Log.d`, no `println`, no `try/catch` around HTTP outside `safeApiCall`.
- [ ] No hardcoded strings, colors, dimens — all in `theme/` or string resources.
- [ ] No new dependencies without an entry in `libs.versions.toml`.
- [ ] Reuses shared components from `core/presentation/components`.

---

## 16. New-Project Bootstrap Checklist

1. Create app module; set `applicationId`, `namespace`, `minSdk`, `targetSdk`.
2. Add the version catalog and copy this stack's dependencies (Compose, Koin, Ktor, DataStore, Paging, Coil, kotlinx-serialization, Timber, `navigation3-runtime`, `navigation3-ui`, `lifecycle-viewmodel-navigation3`).
3. Create the package skeleton from §3 — empty packages are fine.
4. Drop in: `Result`, `Error`, `DataError`, `safeApiCall`, `HttpClientFactory`, `LocalStorage` + `DataStoreLocalStorage`, `Session` + impl, `AppLogger`, `Navigator`, `Route` (with `Splash` + `Home` + `Login`), `AppNavDisplay`, `TopLevelDestination`, `LogoutEventManager`, `AppBottomBar`, `App.kt`, `MainApplication`, `MainActivity`.
5. Wire `appModules()` with `networkModule`, `storageModule`, `sessionModule`.
6. Set up `qa` + `prod` flavors, `debug` + `release` types, `local.properties` template, version catalog, BuildConfig / BuildKonfig.
7. Create the first `feature/<name>/` following §4 exactly.
8. Add it to `appModules()` and add its `entry<Route.X>` to `AppNavDisplay`.
9. Ship.

Everything else — every product, every screen, every API — fits into this exact mold.

---

## Appendix A. Migrating an Existing Nav2 App to Nav3

| Step | Nav2 | Nav3 |
|---|---|---|
| 1 | `sealed interface Route` with `@Serializable` objects | Same file, but `Route : NavKey` — keep the entries |
| 2 | `rememberNavController()` | `rememberNavBackStack(Route.Splash)` |
| 3 | `NavHost(navController, startDestination)` | `NavDisplay(backStack, onBack, entryDecorators, entryProvider)` |
| 4 | `composable<Route.X> { backStackEntry -> val args = backStackEntry.toRoute<Route.X>() … }` | `entry<Route.X> { key -> … }` — `key` is already typed, no `toRoute()` |
| 5 | `navController.navigate(route)` | `backStack.add(route)` inside `Navigator` |
| 6 | `popUpTo(inclusive = true)` | `backStack.clear()` then `add()` |
| 7 | `launchSingleTop = true` | `if (backStack.lastOrNull() == route) return` |
| 8 | `popBackStack()` | `backStack.removeLastOrNull()` |
| 9 | JSON-string args + `fromNavJson<T>()` | `@Serializable` argument types directly — delete the helper |
| 10 | Bottom bar by route-string matching | `TopLevelDestination.entries.any { it.route == backStack.lastOrNull() }` |
| 11 | `koinViewModel(viewModelStoreOwner = backStackEntry)` | `rememberViewModelStoreNavEntryDecorator()` on `NavDisplay` — plain `koinViewModel()` in the screen |

Because `Navigator` is the only navigation API the features see, steps 5–8 change in exactly one file. Feature code should not need edits beyond removing any leftover `NavController` parameters.

Things Nav2 had that Nav3 does not give you for free — decide these consciously before migrating:

- **`saveState` on tab switching.** See §8.4 for the per-tab back stack option.
- **Deep links.** Nav3 has no built-in deep-link matching. Parse the incoming `Intent` in `MainActivity`, map it to a `Route`, and build the starting back stack yourself.
- **Nested graphs.** There are none. A "graph" is now just a slice of the list, and shared state goes on `Session`.