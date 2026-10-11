package com.example.hotticketapplication.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.hotticketapplication.data.*
import com.example.hotticketapplication.R
import com.example.hotticketapplication.ui.theme.HotTicketApplicationTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

private val Tashkent = ZoneId.of("Asia/Tashkent")
private fun today() = LocalDate.now(Tashkent)
private fun money(value: Long) = NumberFormat.getIntegerInstance(Locale.US).format(value).replace(',', ' ')
private fun dateText(value: String): String = runCatching {
    LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault(value)
private fun timeText(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(Tashkent).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(value.substringAfter('T').take(5))
private fun openLink(context: Context, value: String) {
    val uri = Uri.parse(value)
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

private val stations = listOf(
    Triple("2900000", "Ташкент", "Toshkent"), Triple("2900001", "Ташкент Центральный", "Toshkent Markaziy"),
    Triple("2900002", "Ташкент Южный", "Toshkent Janubiy"), Triple("2900700", "Самарканд", "Samarqand"),
    Triple("2900800", "Бухара 1", "Buxoro 1"), Triple("2900930", "Навои", "Navoiy"),
    Triple("2900750", "Карши", "Qarshi"), Triple("2900796", "Шахрисабз", "Shahrisabz"),
    Triple("2900255", "Термез", "Termiz"), Triple("2900780", "Денау", "Denov"),
    Triple("2900680", "Андижан", "Andijon"), Triple("2900940", "Наманган", "Namangan"),
    Triple("2900346", "Фергана 1", "Farg‘ona 1"), Triple("2900967", "Фергана 2", "Farg‘ona 2"),
    Triple("2900920", "Маргилан", "Marg‘ilon"), Triple("2900880", "Коканд 1", "Qo‘qon 1"),
    Triple("2900720", "Джизак", "Jizzax"), Triple("2900850", "Гулистан", "Guliston"),
    Triple("2900790", "Ургенч", "Urganch"), Triple("2900172", "Хива", "Xiva"),
    Triple("2900970", "Нукус", "Nukus"), Triple("2900885", "Кунград", "Qo‘ng‘irot")
)
private val origins = listOf(
    Triple("TAS", "Ташкент", "Toshkent"), Triple("SKD", "Самарканд", "Samarqand"),
    Triple("BHK", "Бухара", "Buxoro"), Triple("FEG", "Фергана", "Farg‘ona"),
    Triple("NMA", "Наманган", "Namangan"), Triple("UGC", "Ургенч", "Urganch"),
    Triple("NCU", "Нукус", "Nukus"), Triple("TMJ", "Термез", "Termiz"),
    Triple("KSQ", "Карши", "Qarshi"), Triple("AZN", "Андижан", "Andijon"),
    Triple("NVI", "Навои", "Navoiy"), Triple("OQN", "Коканд", "Qo‘qon")
)

private class AppState(val repository: HotTicketRepository) : ViewModel() {
    var connected by mutableStateOf(repository.token.isNotEmpty())
    var demo by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")
    var profile by mutableStateOf(JSONObject())
    var access by mutableStateOf(JSONObject())
    var deals by mutableStateOf(emptyList<JSONObject>())
    var nextCursor by mutableStateOf("")
    var trainTrackings by mutableStateOf(emptyList<JSONObject>())
    var flightTrackings by mutableStateOf(emptyList<JSONObject>())
    var trainResult by mutableStateOf(JSONObject())
    var trainOrigin by mutableStateOf("2900000")
    var trainDestination by mutableStateOf("2900700")
    var trainDate by mutableStateOf(today().plusDays(1).toString())
    var language by mutableStateOf(repository.store.language)
    var theme by mutableStateOf(repository.store.theme)
    fun clearAccount() {
        profile = JSONObject(); access = JSONObject(); deals = emptyList(); nextCursor = ""
        trainTrackings = emptyList(); flightTrackings = emptyList(); trainResult = JSONObject()
    }
    fun t(ru: String, uz: String) = if (language == "uz") uz else ru
    suspend fun load() {
        if (demo) return
        val boot = repository.request("/api/v1/bootstrap", "POST", json())
        profile = boot.getJSONObject("profile"); access = boot.getJSONObject("pro")
        language = profile.text("languageCode", "ru"); repository.store.language = language
        loadDeals(); loadTrackings()
    }
    suspend fun loadDeals(query: String = "", append: Boolean = false, sort: String = "cheapest") {
        val result = repository.request("/api/v1/deals?limit=30&sort=$sort$query")
        deals = if (append) deals + result.rows("items") else result.rows("items")
        nextCursor = result.text("nextCursor")
    }
    suspend fun loadTrackings() {
        flightTrackings = repository.request("/api/v1/subscriptions").rows("items")
        trainTrackings = repository.request("/api/v1/train-trackings").rows("items")
        access = repository.request("/api/v1/pro")
    }
    fun startDemo() {
        demo = true; connected = false; error = ""
        profile = json("firstName" to "Hot Ticket", "defaultOriginCode" to "TAS", "languageCode" to language,
            "instantNotificationsEnabled" to true, "morningDigestEnabled" to false, "quietHoursEnabled" to true)
        access = json("plan" to "free", "trackingLimit" to 1)
        deals = listOf(
            Triple("IST", t("Стамбул", "Istanbul"), 1480000), Triple("DXB", t("Дубай", "Dubay"), 2160000),
            Triple("ALA", t("Алматы", "Olmaota"), 1120000)
        ).mapIndexed { index, item -> json("id" to index + 1, "originCode" to "TAS", "destinationCode" to item.first,
            "originName" to t("Ташкент", "Toshkent"), "destinationName" to item.second, "price" to item.third,
            "currencyCode" to "UZS", "departureDate" to today().plusDays(7L + index * 4).toString(),
            "airlineName" to listOf("Turkish Airlines", "flydubai", "Uzbekistan Airways")[index],
            "isDirect" to true, "hasBaggage" to (index != 2), "openUrl" to "https://www.aviasales.uz/") }
    }
    fun demoTrains(origin: String, destination: String, date: String) {
        val names = stations.associate { it.first to if (language == "uz") it.third else it.second }
        trainResult = json("bookingUrl" to "https://eticket.uzrailpass.uz/uz/home", "trains" to JSONArray(
            listOf("06:33", "07:30", "08:30", "20:32").mapIndexed { index, time ->
                json("number" to listOf("764Ф", "766Ф", "770Ф", "712Ф")[index],
                    "brand" to if (index == 3) "Sharq" else "Afrosiyob", "departureAt" to "${date}T${time}:00+05:00",
                    "arrivalAt" to "${date}T${time.take(2).toInt() + 2}:${time.takeLast(2)}:00+05:00",
                    "duration" to "02:00", "originName" to names[origin], "destinationName" to names[destination],
                    "wagons" to JSONArray(if (index == 0) emptyList<JSONObject>() else listOf(
                        json("type" to "seated", "name" to t("Сидячий", "O‘tiradigan"), "freeSeats" to (12 - index), "price" to 245000))))
            }))
    }
}

@Composable
fun HotTicketApp(initialRepository: HotTicketRepository) {
    val owner = LocalContext.current as ViewModelStoreOwner
    val s = remember(owner) {
        ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppState(initialRepository) as T
        })[AppState::class.java]
    }
    val repository = s.repository
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var flight by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var plans by rememberSaveable { mutableStateOf(false) }
    val dark = when (s.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    fun run(block: suspend () -> Unit) {
        if (s.busy) return
        scope.launch {
            s.busy = true; s.error = ""
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is ApiException && e.code == "unauthorized") { repository.clearSession(); s.connected = false; s.clearAccount() }
                s.error = e.message ?: s.t("Ошибка сети. Повторите попытку.", "Tarmoq xatosi. Qayta urinib ko‘ring.")
            } finally { s.busy = false }
        }
    }
    LaunchedEffect(s.connected) { if (s.connected) run { s.load() } }
    BackHandler(detail != null || plans) { detail = null; plans = false }
    HotTicketApplicationTheme(dark) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            if ((s.connected || s.demo) && detail == null && !plans) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val labels = listOf(s.t("Поиск", "Qidirish"), s.t("Скоро", "Tez orada"),
                    s.t("Слежение", "Kuzatuv"), s.t("Профиль", "Profil"))
                val icons = listOf(R.drawable.ic_hot_search, R.drawable.ic_hot_calendar, R.drawable.ic_hot_bell, R.drawable.ic_hot_user)
                labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index,
                    onClick = { tab = index }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    icon = { Icon(painterResource(icons[index]), contentDescription = null) }, label = { Text(label, fontSize = 11.sp) }) }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (s.error.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        if (s.connected) TextButton(enabled = !s.busy, onClick = { run { s.load() } }) { Text(s.t("Повторить", "Qayta")) }
                        TextButton(onClick = { s.error = "" }) { Text("×") }
                    }
                }
                if (!s.connected && !s.demo) {
                    LoginScreen(s, onRun = ::run, onDemo = { s.startDemo() })
                } else {
                    val scroll = rememberScrollState()
                    LaunchedEffect(tab, flight, plans, detail) { scroll.scrollTo(0) }
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Spacer(Modifier.height(2.dp))
                        if (s.demo) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(s.t("Демо • Пример данных, без реальных уведомлений", "Demo • Namuna ma’lumotlar, haqiqiy xabarlarsiz"), Modifier.fillMaxWidth().padding(12.dp), fontSize = 12.sp)
                        }
                        when {
                            detail != null -> TicketDetail(s, detail!!, ::run, onBack = { detail = null },
                                onTracked = { detail = null; tab = 2 })
                            plans -> PlansScreen(s, ::run, onBack = { plans = false })
                            tab == 3 -> ProfileScreen(s, ::run, onPlans = { plans = true }, onLogout = {
                                if (s.demo) { s.demo = false; s.deals = emptyList(); s.trainTrackings = emptyList(); s.flightTrackings = emptyList() }
                                else run {
                                    try { repository.request("/api/v1/android/logout", "POST", json()) }
                                    finally { repository.clearSession(); s.connected = false; s.clearAccount() }
                                }
                            })
                            tab == 2 -> WatchlistScreen(s, ::run, onSearch = { tab = 0 }, onOpen = { detail = it })
                            else -> {
                                Header(if (tab == 1) s.t("Ближайшие поездки", "Yaqin safarlar") else s.t("Найдите свой билет", "Chiptangizni toping"))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = !flight, onClick = { flight = false }, label = { Text(s.t("Поезда", "Poyezdlar")) })
                                    FilterChip(selected = flight, onClick = { flight = true }, label = { Text(s.t("Авиабилеты", "Aviachiptalar")) })
                                }
                                if (flight) FlightScreen(s, ::run, upcoming = tab == 1, onOpen = { ticket ->
                                    if (s.demo) detail = ticket else run {
                                        detail = repository.request("/api/v1/tickets/${ticket.optLong("id")}")
                                    }
                                }) else if (tab == 1) UpcomingTrainScreen(s, ::run, onTracked = { tab = 2 })
                                else TrainScreen(s, ::run, upcoming = false, onTracked = { tab = 2 })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("HOT TICKET", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp, letterSpacing = 2.sp)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
private fun EmptyState(title: String, description: String) {
    Panel { Text(title, fontWeight = FontWeight.Bold); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun LoginScreen(s: AppState, onRun: (suspend () -> Unit) -> Unit, onDemo: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = (context as LifecycleOwner).lifecycle
    var code by remember { mutableStateOf("") }
    var deviceToken by remember { mutableStateOf("") }
    var expiresAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(deviceToken) {
        if (deviceToken.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (deviceToken.isNotEmpty()) {
                delay(4000)
                if (System.currentTimeMillis() >= expiresAt) { deviceToken = ""; code = ""; s.error = s.t("Код истёк. Начните вход снова.", "Kod muddati tugadi. Qayta kiring."); break }
                try {
                    val result = s.repository.request("/api/android/poll", "POST", json("deviceToken" to deviceToken), anonymous = true)
                    if (result.text("status") == "approved") {
                        s.repository.setSession(result.getString("token")); deviceToken = ""; s.connected = true
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    s.error = e.message.orEmpty()
                    if (e is ApiException) { deviceToken = ""; code = ""; break }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(32.dp))
        Header(s.t("Ваш следующий путь", "Keyingi safaringiz"), s.t("Поезда и авиабилеты из Узбекистана", "O‘zbekistondan poyezd va aviachiptalar"))
        Panel {
            Text(s.t("Войти через Telegram", "Telegram orqali kirish"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(s.t("Ваш профиль и отслеживания будут доступны и здесь.", "Profilingiz va kuzatuvlaringiz shu yerda ham mavjud bo‘ladi."))
            if (code.isEmpty()) Button(enabled = !s.busy, modifier = Modifier.fillMaxWidth(), onClick = { onRun {
                s.repository.validateServer(s.repository.store.server)
                val result = s.repository.request("/api/android/login", "POST", json(), anonymous = true)
                code = result.getString("code"); expiresAt = java.time.Instant.parse(result.getString("expiresAt")).toEpochMilli()
                deviceToken = result.getString("deviceToken")
                openLink(context, "https://t.me/hot_ticket_buy_bot?startapp=android_$code")
            } }) { Text(s.t("Продолжить в Telegram", "Telegram’da davom etish")) }
            else {
                Text(code, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text(s.t("Сверьте код и подтвердите вход в Mini App, затем вернитесь сюда. Код действует 5 минут.", "Mini App’da kodni tekshirib kirishni tasdiqlang, keyin bu yerga qayting. Kod 5 daqiqa amal qiladi."))
                Button(onClick = { openLink(context, "https://t.me/hot_ticket_buy_bot?startapp=android_$code") }, modifier = Modifier.fillMaxWidth()) { Text(s.t("Открыть Telegram", "Telegram’ni ochish")) }
                TextButton(onClick = { code = ""; deviceToken = "" }) { Text(s.t("Отмена", "Bekor qilish")) }
            }
        }
        OutlinedButton(onClick = onDemo, enabled = !s.busy && code.isEmpty(), modifier = Modifier.fillMaxWidth()) { Text(s.t("Посмотреть демо", "Demoni ko‘rish")) }
        Text(s.t("Уведомления о билетах приходят в Telegram.", "Chiptalar haqida xabarlar Telegram’ga keladi."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun RoutePicker(label: String, value: String, items: List<Triple<String, String, String>>, language: String,
    onSelect: (String) -> Unit, onSearch: (suspend (String) -> List<Triple<String, String, String>>)? = null) {
    var show by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf(emptyList<Triple<String, String, String>>()) }
    var searchError by remember { mutableStateOf("") }
    val name = items.find { it.first == value }?.let { if (language == "uz") it.third else it.second } ?: value
    OutlinedButton(onClick = { show = true; query = ""; remote = emptyList() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name.ifEmpty { if (language == "uz") "Tanlang" else "Выберите" }, fontWeight = FontWeight.Bold)
        }
    }
    LaunchedEffect(query, show) {
        searchError = ""
        if (show && query.length >= 2 && onSearch != null) {
            delay(350)
            try { remote = onSearch(query) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { searchError = e.message.orEmpty() }
        } else remote = emptyList()
    }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text(label) }, text = {
        Column {
            OutlinedTextField(query, onValueChange = { query = it }, singleLine = true,
                label = { Text(if (language == "uz") "Shahar yoki bekat" else "Город или станция") })
            if (searchError.isNotEmpty()) Text(searchError, color = MaterialTheme.colorScheme.error)
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                (remote + items).distinctBy { it.first }.filter {
                    query.isEmpty() || it.second.contains(query, true) || it.third.contains(query, true) || it.first.contains(query, true)
                }.forEach { item -> TextButton(onClick = { onSelect(item.first); show = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (language == "uz") item.third else item.second, Modifier.fillMaxWidth())
                } }
            }
        }
    }, confirmButton = { TextButton(onClick = { show = false }) { Text(if (language == "uz") "Yopish" else "Закрыть") } })
}

@Composable
private fun DateField(label: String, value: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val initial = runCatching { LocalDate.parse(value) }.getOrDefault(today().plusDays(1))
        DatePickerDialog(context, { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day).toString()) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                datePicker.minDate = today().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.show()
    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Text("$label · ${dateText(value)}", Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun TrainScreen(s: AppState, onRun: (suspend () -> Unit) -> Unit, upcoming: Boolean, onTracked: () -> Unit) {
    val context = LocalContext.current
    val origin = s.trainOrigin
    val destination = s.trainDestination
    val date = s.trainDate
    var onlySeats by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    fun clearResults() { s.trainResult = JSONObject() }
    val stationSearch: (suspend (String) -> List<Triple<String, String, String>>)? = if (s.demo) null else { query ->
        s.repository.request("/api/v1/trains/stations?q=${Uri.encode(query)}").rows("items").map {
            Triple(it.text("code"), it.text("name"), it.text("nameUz", it.text("name")))
        }
    }
    Panel {
        RoutePicker(s.t("Откуда", "Qayerdan"), origin, stations, s.language, onSelect = { s.trainOrigin = it; clearResults() }, onSearch = stationSearch)
        TextButton(onClick = { val old = origin; s.trainOrigin = destination; s.trainDestination = old; clearResults() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(s.t("⇅ Поменять местами", "⇅ Almashtirish")) }
        RoutePicker(s.t("Куда", "Qayerga"), destination, stations, s.language, onSelect = { s.trainDestination = it; clearResults() }, onSearch = stationSearch)
        DateField(s.t("Дата поездки", "Safar sanasi"), date) { s.trainDate = it; clearResults() }
        if (upcoming) Text(s.t("Выберите ближайшую дату поездки", "Yaqin safar sanasini tanlang"), fontSize = 12.sp)
        Button(enabled = !s.busy, modifier = Modifier.fillMaxWidth(), onClick = { onRun {
            require(origin != destination) { s.t("Станции должны отличаться", "Bekatlar farq qilishi kerak") }
            if (s.demo) s.demoTrains(origin, destination, date)
            else s.trainResult = s.repository.request("/api/v1/trains/search", "POST", json("originCode" to origin, "destinationCode" to destination, "date" to date))
        } }) { Text(s.t("Найти поезда", "Poyezdlarni topish")) }
    }
    if (s.trainResult.has("trains")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.t("Расписание", "Jadval"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FilterChip(onlySeats, onClick = { onlySeats = !onlySeats }, label = { Text(s.t("Есть места", "Joylar bor")) })
        }
        val result = s.trainResult.rows("trains").filter { !onlySeats || it.rows("wagons").any { w -> w.optInt("freeSeats") > 0 } }
            .sortedBy { it.text("departureAt") }
        if (result.isEmpty()) EmptyState(s.t("Поезда не найдены", "Poyezdlar topilmadi"), s.t("Измените дату или фильтр наличия мест.", "Sanani yoki joylar filtrini o‘zgartiring."))
        result.forEach { train -> Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(train.text("brand"), fontWeight = FontWeight.Bold)
                Text("№ ${train.text("number")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${timeText(train.text("departureAt"))}  →  ${timeText(train.text("arrivalAt"))}", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("${train.text("originName")} → ${train.text("destinationName")} · ${train.text("duration")}", fontSize = 12.sp)
            val wagons = train.rows("wagons").filter { it.optInt("freeSeats") > 0 }
            if (wagons.isEmpty()) Text(s.t("Нет свободных мест", "Bo‘sh joylar yo‘q"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            wagons.forEach { wagon -> Text("${wagon.text("name")} · ${wagon.optInt("freeSeats")} ${s.t("мест", "joy")} · ${if (wagon.isNull("price")) s.t("Цена неизвестна", "Narx noma’lum") else money(wagon.optLong("price")) + " " + s.t("сум", "so‘m")}", fontSize = 13.sp) }
            if (train.optBoolean("kassasOnly")) Text(s.t("Продажа только в кассе", "Faqat kassada sotiladi"), color = MaterialTheme.colorScheme.secondary)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected = train }, enabled = !s.busy) { Text(s.t("Отслеживать", "Kuzatish")) }
                if (wagons.isNotEmpty()) Button(onClick = { openLink(context, s.trainResult.text("bookingUrl")) }) { Text(s.t("Купить", "Sotib olish")) }
            }
        } }
        OutlinedButton(onClick = { selected = json() }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text(s.t("Отслеживать весь маршрут", "Butun yo‘nalishni kuzatish")) }
        Text(s.t("Места могут измениться. Покупка на сайте Uzrailpass.", "Joylar o‘zgarishi mumkin. Xarid Uzrailpass saytida."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (selected != null) TrackingDialog(s, train = true, onDismiss = { selected = null }, onSave = { maxPrice, wagonTypes ->
        val chosen = selected!!
        onRun {
            val body = json("originCode" to origin, "destinationCode" to destination, "date" to date,
                "trainNumber" to chosen.text("number").ifEmpty { null }, "wagonTypes" to JSONArray(wagonTypes), "maxPrice" to maxPrice,
                "trainGroups" to JSONArray(), "departureWindows" to JSONArray())
            if (s.demo) s.trainTrackings = s.trainTrackings + json("id" to System.nanoTime(), "originName" to stations.find { it.first == origin }?.second,
                "destinationName" to stations.find { it.first == destination }?.second, "date" to date, "trainNumber" to chosen.text("number"), "isActive" to true,
                "status" to "waiting", "maxPrice" to maxPrice)
            else { s.repository.request("/api/v1/train-trackings", "POST", body); s.loadTrackings() }
            selected = null; onTracked()
        }
    })
}

@Composable
private fun UpcomingTrainScreen(s: AppState, onRun: (suspend () -> Unit) -> Unit, onTracked: () -> Unit) {
    val context = LocalContext.current
    var origin by rememberSaveable { mutableStateOf("2900000") }
    var day by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(emptyList<JSONObject>()) }
    var checked by remember { mutableIntStateOf(0) }
    var failures by remember { mutableStateOf(emptyList<String>()) }
    var onlySeats by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    val date = today().plusDays(day.toLong()).toString()
    val destinations = stations.filter { it.first != origin && it.first !in listOf("2900001", "2900002") }
    Panel {
        RoutePicker(s.t("Откуда", "Qayerdan"), origin, stations, s.language, onSelect = { origin = it; checked = 0; results = emptyList(); failures = emptyList() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(s.t("Сегодня", "Bugun"), s.t("Завтра", "Ertaga")).forEachIndexed { index, title ->
                FilterChip(day == index, onClick = { day = index; checked = 0; results = emptyList(); failures = emptyList() }, label = { Text(title) })
            }
        }
        Text(s.t("Проверяем ближайшие отправления по направлениям.", "Yo‘nalishlar bo‘yicha yaqin jo‘nashlarni tekshiramiz."), fontSize = 13.sp)
        Button(enabled = !s.busy && checked < destinations.size, onClick = { onRun {
            val batch = destinations.drop(checked).take(3)
            for (station in batch) {
                try {
                    val result = if (s.demo) {
                        val saved = s.trainResult
                        s.demoTrains(origin, station.first, date)
                        val demo = JSONObject(s.trainResult.toString()).put("route", json("originCode" to origin, "destinationCode" to station.first, "date" to date))
                        s.trainResult = saved
                        demo
                    } else s.repository.request("/api/v1/trains/search", "POST", json("originCode" to origin, "destinationCode" to station.first, "date" to date))
                    results = results + result
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (e is ApiException && e.code == "unauthorized") throw e
                    failures = failures + "${if (s.language == "uz") station.third else station.second}: ${e.message}"
                }
                checked += 1
            }
        } }, modifier = Modifier.fillMaxWidth()) { Text(if (checked == 0) s.t("Найти ближайшие поезда", "Yaqin poyezdlarni topish") else s.t("Проверить ещё направления", "Yana yo‘nalishlarni tekshirish")) }
        if (checked > 0) Text("${s.t("Проверено направлений", "Tekshirilgan yo‘nalishlar")}: $checked / ${destinations.size}", fontSize = 12.sp)
    }
    FilterChip(onlySeats, onClick = { onlySeats = !onlySeats }, label = { Text(s.t("Только с местами", "Faqat joylari bor")) })
    failures.forEach { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    if (failures.isNotEmpty()) OutlinedButton(enabled = !s.busy, onClick = { checked = 0; results = emptyList(); failures = emptyList() }) { Text(s.t("Повторить проверку", "Qayta tekshirish")) }
    var shown = 0
    results.forEach { result ->
        val trains = result.rows("trains").filter { train ->
            val future = runCatching { OffsetDateTime.parse(train.text("departureAt")).toInstant().isAfter(java.time.Instant.now()) }.getOrDefault(true)
            future && (!onlySeats || train.rows("wagons").any { it.optInt("freeSeats") > 0 })
        }.sortedBy { it.text("departureAt") }
        if (trains.isNotEmpty()) {
            shown += trains.size
            Panel {
                Text(trains.first().text("destinationName"), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                trains.forEach { train ->
                    val seats = train.rows("wagons").sumOf { it.optInt("freeSeats") }
                    Text("${timeText(train.text("departureAt"))} · ${train.text("brand")} · ${if (seats > 0) "$seats ${s.t("мест", "joy")}" else s.t("Нет мест", "Joylar yo‘q")}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !s.busy, onClick = { selected = json("route" to result.optJSONObject("route"), "number" to train.text("number")) }) { Text(s.t("Отслеживать", "Kuzatish")) }
                        if (seats > 0 && !train.optBoolean("kassasOnly")) TextButton(onClick = { openLink(context, result.text("bookingUrl")) }) { Text(s.t("Купить", "Sotib olish")) }
                    }
                }
            }
        }
    }
    if (checked > 0 && shown == 0 && !s.busy) EmptyState(s.t("Ближайших поездов нет", "Yaqin poyezdlar yo‘q"), s.t("Попробуйте завтра или проверьте другие направления.", "Ertaga yoki boshqa yo‘nalishlarni tekshiring."))
    if (selected != null) TrackingDialog(s, true, onDismiss = { selected = null }, onSave = { amount, wagons -> onRun {
        val route = selected!!.getJSONObject("route")
        val body = JSONObject(route.toString()).put("trainNumber", selected!!.text("number")).put("maxPrice", amount ?: JSONObject.NULL).put("wagonTypes", JSONArray(wagons))
        if (s.demo) s.trainTrackings = s.trainTrackings + JSONObject(body.toString()).put("id", System.nanoTime()).put("isActive", true).put("status", "waiting")
        else { s.repository.request("/api/v1/train-trackings", "POST", body); s.loadTrackings() }
        selected = null; onTracked()
    } })
}

@Composable
private fun TrackingDialog(s: AppState, train: Boolean, onDismiss: () -> Unit, initialPrice: String = "", onSave: (Long?, List<String>) -> Unit) {
    var price by rememberSaveable { mutableStateOf(initialPrice) }
    var wagon by rememberSaveable { mutableStateOf("") }
    var validation by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!s.busy) onDismiss() }, title = { Text(s.t("Условия отслеживания", "Kuzatuv shartlari")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.t("Уведомим в Telegram, когда появится подходящий билет.", "Mos chipta paydo bo‘lsa, Telegram’da xabar beramiz."))
            OutlinedTextField(price, onValueChange = { price = it.filter(Char::isDigit); validation = "" },
                label = { Text(s.t("Максимальная цена, сум", "Eng yuqori narx, so‘m")) }, singleLine = true)
            Text(s.t("Пустое поле — любая цена", "Bo‘sh maydon — istalgan narx"), fontSize = 12.sp)
            if (train) {
                listOf("" to s.t("Любой вагон", "Istalgan vagon"), "seated" to s.t("Сидячий", "O‘tiradigan"),
                    "business" to s.t("Бизнес", "Biznes"), "coupe" to s.t("Купе", "Kupe"), "platzkart" to s.t("Плацкарт", "Platskart"), "sv" to "СВ / SV").forEach { item ->
                    Row(Modifier.fillMaxWidth().clickable { wagon = item.first }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(wagon == item.first, onClick = { wagon = item.first }); Text(item.second)
                    }
                }
            }
            if (validation.isNotEmpty()) Text(validation, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !s.busy, onClick = {
        val amount = price.toLongOrNull()
        if (price.isNotEmpty() && (amount == null || amount <= 0)) validation = s.t("Введите положительную цену", "Musbat narx kiriting")
        else onSave(amount, if (wagon.isEmpty()) emptyList() else listOf(wagon))
    }) { Text(s.t("Сохранить", "Saqlash")) } }, dismissButton = {
        TextButton(enabled = !s.busy, onClick = onDismiss) { Text(s.t("Отмена", "Bekor qilish")) }
    })
}

@Composable
private fun FlightScreen(s: AppState, onRun: (suspend () -> Unit) -> Unit, upcoming: Boolean, onOpen: (JSONObject) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var direct by rememberSaveable { mutableStateOf(false) }
    var baggage by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(today().toString()) }
    var destinationCode by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<JSONObject>()) }
    var createTracking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(query, destinationCode) {
        if (query.length < 2 || destinationCode.isNotEmpty()) { suggestions = emptyList(); return@LaunchedEffect }
        delay(350)
        try {
            suggestions = if (s.demo) s.deals.filter { it.text("destinationName").contains(query, true) || it.text("destinationCode").contains(query, true) }
                .map { json("code" to it.text("destinationCode"), "name" to it.text("destinationName")) }
            else s.repository.request("/api/v1/destinations?q=${Uri.encode(query)}&limit=8").rows("items")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { suggestions = emptyList(); s.error = e.message.orEmpty() }
    }
    OutlinedTextField(query, onValueChange = { query = it; destinationCode = ""; s.nextCursor = "" }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(s.t("Куда хотите поехать?", "Qayerga bormoqchisiz?")) })
    suggestions.forEach { suggestion -> TextButton(onClick = {
        destinationCode = suggestion.text("code"); query = suggestion.text("name"); suggestions = emptyList()
    }) { Text("${suggestion.text("name")} · ${suggestion.text("code")}") } }
    if (upcoming) DateField(s.t("Ближайшие вылеты от", "Yaqin parvozlar sanasi"), date) { date = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(direct, onClick = { direct = !direct; s.nextCursor = "" }, label = { Text(s.t("Без пересадок", "To‘g‘ridan-to‘g‘ri")) })
        FilterChip(baggage, onClick = { baggage = !baggage; s.nextCursor = "" }, label = { Text(s.t("С багажом", "Bagaj bilan")) })
    }
    val apiQuery = "&direct=${if (direct) 1 else 0}&baggage=${if (baggage) 1 else 0}" +
        (if (destinationCode.isNotEmpty()) "&destination=${Uri.encode(destinationCode)}" else "") +
        (if (upcoming) "&date_from=$date&date_to=${LocalDate.parse(date).plusDays(7)}" else "")
    val sort = if (upcoming) "departing_soon" else "cheapest"
    Button(onClick = { onRun { if (!s.demo) s.loadDeals(apiQuery, sort = sort) } }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text(s.t("Найти авиабилеты", "Aviachiptalarni topish")) }
    OutlinedButton(enabled = !s.busy, onClick = { createTracking = true }, modifier = Modifier.fillMaxWidth()) { Text(s.t("Создать отслеживание маршрута", "Yo‘nalish kuzatuvini yaratish")) }
    if (createTracking) FlightTrackingForm(s, onRun, onDismiss = { createTracking = false })
    val items = s.deals.filter { item -> (!direct || item.optBoolean("isDirect")) && (!baggage || item.optBoolean("hasBaggage")) &&
        (query.isBlank() || item.text("destinationName").contains(query, true) || item.text("destinationCode").contains(query, true)) &&
        (!upcoming || item.text("departureDate") in date..LocalDate.parse(date).plusDays(7).toString()) }
    if (items.isEmpty()) EmptyState(s.t("Пока нет предложений", "Hozircha takliflar yo‘q"), s.t("Попробуйте другие условия поиска.", "Boshqa qidiruv shartlarini sinab ko‘ring."))
    items.sortedBy { if (upcoming) it.text("departureDate") else it.optLong("price").toString().padStart(15, '0') }.forEach { ticket ->
        Surface(onClick = { onOpen(ticket) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${ticket.text("originName", ticket.text("originCode"))} → ${ticket.text("destinationName")}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${money(ticket.optLong("price"))} ${ticket.text("currencyCode", "UZS")}", fontSize = 25.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${dateText(ticket.text("departureDate"))} · ${ticket.text("airlineName")}", fontSize = 13.sp)
                Text((if (ticket.optBoolean("isDirect")) s.t("Прямой рейс", "To‘g‘ridan-to‘g‘ri") else s.t("С пересадкой", "Transfer bilan")) + " · " +
                    (if (ticket.optBoolean("hasBaggage")) s.t("С багажом", "Bagaj bilan") else s.t("Без багажа", "Bagajsiz")), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
    if (s.nextCursor.isNotEmpty() && !s.demo) OutlinedButton(enabled = !s.busy, onClick = { onRun { s.loadDeals("$apiQuery&cursor=${Uri.encode(s.nextCursor)}", append = true, sort = sort) } }, modifier = Modifier.fillMaxWidth()) { Text(s.t("Показать ещё", "Yana ko‘rsatish")) }
}

@Composable
private fun FlightTrackingForm(s: AppState, onRun: (suspend () -> Unit) -> Unit, onDismiss: () -> Unit) {
    var destination by rememberSaveable { mutableStateOf("IST") }
    var from by rememberSaveable { mutableStateOf(today().plusDays(1).toString()) }
    var to by rememberSaveable { mutableStateOf(today().plusDays(30).toString()) }
    var price by rememberSaveable { mutableStateOf("") }
    var direct by rememberSaveable { mutableStateOf(false) }
    var round by rememberSaveable { mutableStateOf(false) }
    var baggage by rememberSaveable { mutableStateOf(false) }
    var validation by remember { mutableStateOf("") }
    val options = listOf(Triple("", s.t("Куда угодно", "Istalgan joyga"), s.t("Куда угодно", "Istalgan joyga"))) +
        s.deals.map { Triple(it.text("destinationCode"), it.text("destinationName"), it.text("destinationName")) }.distinctBy { it.first }
    AlertDialog(onDismissRequest = { if (!s.busy) onDismiss() }, title = { Text(s.t("Отслеживать авиабилеты", "Aviachiptalarni kuzatish")) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${s.t("Из", "Qayerdan")}: ${s.profile.text("defaultOriginCode", "TAS")}")
            RoutePicker(s.t("Куда", "Qayerga"), destination, options, s.language, onSelect = { destination = it },
                onSearch = if (s.demo) null else { q -> s.repository.request("/api/v1/destinations?q=${Uri.encode(q)}&limit=20").rows("items").map { Triple(it.text("code"), it.text("name"), it.text("name")) } })
            DateField(s.t("От", "Boshlanish"), from) { from = it }
            DateField(s.t("До", "Tugash"), to) { to = it }
            OutlinedTextField(price, onValueChange = { price = it.filter(Char::isDigit) }, label = { Text(s.t("Максимальная цена, сум", "Eng yuqori narx, so‘m")) }, singleLine = true)
            listOf(s.t("Без пересадок", "To‘g‘ridan-to‘g‘ri") to direct, s.t("Туда и обратно", "Borish va qaytish") to round,
                s.t("С багажом", "Bagaj bilan") to baggage).forEachIndexed { index, (label, checked) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, onCheckedChange = { value -> when (index) { 0 -> direct = value; 1 -> round = value; else -> baggage = value } }); Text(label)
                }
            }
            if (validation.isNotEmpty()) Text(validation, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !s.busy, onClick = {
        if (from > to || (price.isNotEmpty() && (price.toLongOrNull() ?: 0) <= 0)) {
            validation = s.t("Проверьте период поездки и цену", "Safar davri va narxni tekshiring")
        } else onRun {
            val body = json("destinationCode" to destination.ifEmpty { null }, "departureDateFrom" to from, "departureDateTo" to to,
                "maxPrice" to price.toLongOrNull(), "directOnly" to direct, "roundTripOnly" to round, "baggageRequired" to baggage, "tripClass" to s.profile.text("preferredTripClass", "economy"))
            if (s.demo) s.flightTrackings = s.flightTrackings + JSONObject(body.toString()).put("id", System.nanoTime()).put("originCode", s.profile.text("defaultOriginCode", "TAS")).put("isActive", true)
            else { s.repository.request("/api/v1/subscriptions", "POST", body); s.loadTrackings() }
            onDismiss()
        }
    }) { Text(s.t("Сохранить", "Saqlash")) } }, dismissButton = { TextButton(enabled = !s.busy, onClick = onDismiss) { Text(s.t("Отмена", "Bekor qilish")) } })
}

@Composable
private fun TicketDetail(s: AppState, ticket: JSONObject, onRun: (suspend () -> Unit) -> Unit, onBack: () -> Unit, onTracked: () -> Unit) {
    val context = LocalContext.current
    var tracking by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(emptyList<JSONObject>()) }
    TextButton(onClick = onBack) { Text(s.t("← Назад", "← Orqaga")) }
    Header("${ticket.text("originName", ticket.text("originCode"))} → ${ticket.text("destinationName", ticket.text("destinationCode"))}")
    Panel {
        Text("${money(ticket.optLong("price"))} ${ticket.text("currencyCode", "UZS")}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("${s.t("Вылет", "Jo‘nash")}: ${dateText(ticket.text("departureDate"))}")
        if (ticket.text("returnDate").isNotEmpty()) Text("${s.t("Обратно", "Qaytish")}: ${dateText(ticket.text("returnDate"))}")
        Text(ticket.text("airlineName"))
        Text(if (ticket.optBoolean("isDirect")) s.t("Прямой рейс", "To‘g‘ridan-to‘g‘ri parvoz") else s.t("Рейс с пересадкой", "Transferli parvoz"))
        Text(if (ticket.optBoolean("hasBaggage")) s.t("Багаж включён", "Bagaj kiritilgan") else s.t("Без багажа", "Bagajsiz"))
        ticket.optJSONObject("dealScore")?.let { score ->
            val level = when (score.text("level")) {
                "very_good" -> s.t("Отличная цена", "Ajoyib narx")
                "good" -> s.t("Хорошая цена", "Yaxshi narx")
                else -> s.t("Оценка цены", "Narx bahosi")
            }
            Text("$level · ${score.optInt("percentile")}%", color = MaterialTheme.colorScheme.secondary)
        }
        Button(onClick = { openLink(context, ticket.text("openUrl")) }, modifier = Modifier.fillMaxWidth()) { Text(s.t("Купить на Aviasales", "Aviasales’da sotib olish")) }
        OutlinedButton(enabled = !s.busy, onClick = { tracking = true }, modifier = Modifier.fillMaxWidth()) { Text(s.t("Отслеживать цену", "Narxni kuzatish")) }
    }
    OutlinedButton(enabled = !s.busy && !s.demo, onClick = { onRun {
        history = s.repository.request("/api/v1/routes/${ticket.text("originCode")}/${ticket.text("destinationCode")}/history?days=30").rows("items")
    } }, modifier = Modifier.fillMaxWidth()) { Text(s.t("История цен · 30 дней (Pro)", "Narxlar tarixi · 30 kun (Pro)")) }
    if (history.isNotEmpty()) Panel { history.forEach { point ->
        Text("${dateText(point.text("day"))} · ${money(point.optLong("minPrice"))} UZS")
    } }
    if (tracking) TrackingDialog(s, train = false, onDismiss = { tracking = false }, onSave = { amount, _ -> onRun {
        val body = json("originCode" to ticket.text("originCode"), "destinationCode" to ticket.text("destinationCode"),
            "currencyCode" to ticket.text("currencyCode", "UZS"), "departureDateFrom" to ticket.text("departureDate"),
            "departureDateTo" to ticket.text("departureDate"), "maxPrice" to amount, "directOnly" to false,
            "roundTripOnly" to ticket.text("returnDate").isNotEmpty(), "baggageRequired" to false, "tripClass" to ticket.text("tripClass", "economy"))
        if (s.demo) s.flightTrackings = s.flightTrackings + json("id" to System.nanoTime(), "originCode" to ticket.text("originCode"),
            "destinationCode" to ticket.text("destinationCode"), "isActive" to true, "departureDateFrom" to ticket.text("departureDate"), "maxPrice" to amount, "currentTicket" to ticket)
        else { s.repository.request("/api/v1/subscriptions", "POST", body); s.loadTrackings() }
        tracking = false; onTracked()
    } })
}

@Composable
private fun WatchlistScreen(s: AppState, onRun: (suspend () -> Unit) -> Unit, onSearch: () -> Unit, onOpen: (JSONObject) -> Unit) {
    var editing by remember { mutableStateOf<Pair<Boolean, JSONObject>?>(null) }
    Header(s.t("Мои отслеживания", "Kuzatuvlarim"), s.t("Уведомления приходят в Telegram", "Xabarlar Telegram’ga keladi"))
    val count = (s.trainTrackings + s.flightTrackings).count { it.optBoolean("isActive") }
    Text("$count / ${s.access.optInt("trackingLimit", 1)} · ${s.access.text("plan", "Free").uppercase()}", color = MaterialTheme.colorScheme.primary)
    OutlinedButton(onClick = { onRun { s.loadTrackings() } }, enabled = !s.busy && !s.demo, modifier = Modifier.fillMaxWidth()) { Text(s.t("Обновить", "Yangilash")) }
    if (s.trainTrackings.isEmpty() && s.flightTrackings.isEmpty()) {
        EmptyState(s.t("Начните с маршрута", "Yo‘nalishdan boshlang"), s.t("Найдите билет и добавьте отслеживание цены или свободных мест.", "Chipta topib, narx yoki bo‘sh joylarni kuzatishni qo‘shing."))
        Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text(s.t("Найти билет", "Chipta topish")) }
    }
    listOf(true to s.trainTrackings, false to s.flightTrackings).forEach { (train, items) ->
        items.forEach { item -> Panel {
            Text(if (train) s.t("ПОЕЗД", "POYEZD") else s.t("АВИАБИЛЕТ", "AVIACHIPTA"), color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
            Text("${item.text("originName", item.text("originCode"))} → ${item.text("destinationName", item.text("destinationCode"))}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(dateText(item.text(if (train) "date" else "departureDateFrom")))
            if (!item.isNull("maxPrice")) Text("${s.t("До", "Gacha")} ${money(item.optLong("maxPrice"))} UZS")
            Text(if (!item.optBoolean("isActive")) s.t("Отключено", "O‘chirilgan") else when (item.text("status")) {
                "expired" -> s.t("Поездка завершена", "Safar yakunlandi")
                "matched" -> s.t("Есть подходящие места", "Mos joylar bor")
                "source_error" -> s.t("Источник временно недоступен", "Manba vaqtincha ishlamayapti")
                else -> s.t("Отслеживаем билеты", "Chiptalar kuzatilmoqda")
            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.optJSONObject("currentTicket")?.let { current -> TextButton(onClick = { onOpen(current) }) { Text(s.t("Посмотреть билет", "Chiptani ko‘rish")) } }
            val active = item.optBoolean("isActive")
            TextButton(enabled = !s.busy, onClick = { editing = train to item }) { Text(s.t("Изменить условия", "Shartlarni o‘zgartirish")) }
            OutlinedButton(enabled = !s.busy, onClick = { onRun {
                if (s.demo) {
                    val changed = items.map { row -> if (row.optLong("id") == item.optLong("id")) JSONObject(row.toString()).put("isActive", !active) else row }
                    if (train) s.trainTrackings = changed else s.flightTrackings = changed
                } else {
                    val path = "/api/v1/${if (train) "train-trackings" else "subscriptions"}/${item.optLong("id")}" + if (active) "" else "/activate"
                    s.repository.request(path, if (active) "DELETE" else "POST", if (active) null else json()); s.loadTrackings()
                }
            } }) { Text(if (active) s.t("Отключить", "O‘chirish") else s.t("Возобновить", "Davom ettirish")) }
        } }
    }
    editing?.let { (train, item) -> TrackingDialog(s, train, onDismiss = { editing = null },
        initialPrice = item.text("maxPrice"), onSave = { price, wagons -> onRun {
            if (s.demo) {
                val items = if (train) s.trainTrackings else s.flightTrackings
                val changed = items.map { row -> if (row.optLong("id") == item.optLong("id")) JSONObject(row.toString()).put("maxPrice", price ?: JSONObject.NULL) else row }
                if (train) s.trainTrackings = changed else s.flightTrackings = changed
            } else {
                val body = if (train) json("originCode" to item.text("originCode"), "destinationCode" to item.text("destinationCode"), "date" to item.text("date"),
                    "trainNumber" to item.text("trainNumber").ifEmpty { null }, "wagonTypes" to JSONArray(wagons), "maxPrice" to price,
                    "trainGroups" to (item.optJSONArray("trainGroups") ?: JSONArray()), "departureWindows" to (item.optJSONArray("departureWindows") ?: JSONArray()))
                    else json("maxPrice" to price)
                s.repository.request("/api/v1/${if (train) "train-trackings" else "subscriptions"}/${item.optLong("id")}", "PATCH", body)
                s.loadTrackings()
            }
            editing = null
        } }) }
}

@Composable
private fun ProfileScreen(s: AppState, onRun: (suspend () -> Unit) -> Unit, onPlans: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    Header(s.t("Профиль", "Profil"), s.profile.text("firstName", "Hot Ticket"))
    Panel {
        Text("${s.t("Тариф", "Tarif")}: ${s.access.text("plan", "Free").uppercase()}", fontWeight = FontWeight.Bold)
        Text("${s.t("Лимит отслеживаний", "Kuzatuvlar limiti")}: ${s.access.optInt("trackingLimit", 1)}")
        s.access.text("accessEndsAt").takeIf { it.isNotEmpty() }?.let { Text("${s.t("До", "Gacha")} ${dateText(it)}") }
        Button(onClick = onPlans, modifier = Modifier.fillMaxWidth()) { Text(s.t("Lite и Pro", "Lite va Pro")) }
    }
    Panel {
        Text(s.t("Настройки", "Sozlamalar"), fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ru" to "Русский", "uz" to "O‘zbekcha").forEach { (code, label) -> FilterChip(s.language == code,
                enabled = !s.busy, onClick = { onRun {
                    if (!s.demo) s.profile = s.repository.request("/api/v1/me", "PATCH", json("languageCode" to code))
                    s.language = code; s.repository.store.language = code
                    if (!s.demo) s.loadDeals()
                } }, label = { Text(label) }) }
        }
        RoutePicker(s.t("Город вылета", "Jo‘nash shahri"), s.profile.text("defaultOriginCode", "TAS"), origins, s.language,
            onSelect = { origin -> onRun {
                if (s.demo) s.profile = JSONObject(s.profile.toString()).put("defaultOriginCode", origin)
                else { s.profile = s.repository.request("/api/v1/me", "PATCH", json("defaultOriginCode" to origin)); s.loadDeals() }
            } })
        Text(s.t("Класс перелёта", "Parvoz klassi"), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("economy" to s.t("Эконом", "Ekonom"), "business" to s.t("Бизнес", "Biznes")).forEach { (value, label) ->
                FilterChip(s.profile.text("preferredTripClass", "economy") == value, enabled = !s.busy, onClick = { onRun {
                    if (s.demo) s.profile = JSONObject(s.profile.toString()).put("preferredTripClass", value)
                    else s.profile = s.repository.request("/api/v1/me", "PATCH", json("preferredTripClass" to value))
                } }, label = { Text(label) })
            }
        }
        listOf("instantNotificationsEnabled" to s.t("Мгновенные уведомления", "Tezkor xabarlar"),
            "morningDigestEnabled" to s.t("Утренний дайджест", "Ertalabki dayjest"),
            "quietHoursEnabled" to s.t("Тихие часы", "Sokin soatlar")).forEach { (key, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                Switch(s.profile.optBoolean(key), enabled = !s.busy, onCheckedChange = { enabled -> onRun {
                    if (s.demo) s.profile = JSONObject(s.profile.toString()).put(key, enabled)
                    else s.profile = s.repository.request("/api/v1/me", "PATCH", json(key to enabled))
                } })
            }
        }
        if (s.profile.optBoolean("quietHoursEnabled")) {
            listOf("quietStartMinute" to s.t("Начало", "Boshlanish"), "quietEndMinute" to s.t("Конец", "Tugash")).forEach { (key, label) ->
                val minute = s.profile.optInt(key, if (key == "quietStartMinute") 1380 else 480)
                TextButton(enabled = !s.busy, onClick = {
                    TimePickerDialog(context, { _, hour, minutes -> onRun {
                        if (s.demo) s.profile = JSONObject(s.profile.toString()).put(key, hour * 60 + minutes)
                        else s.profile = s.repository.request("/api/v1/me", "PATCH", json(key to hour * 60 + minutes))
                    } }, minute / 60, minute % 60, true).show()
                }) { Text("$label · ${"%02d:%02d".format(minute / 60, minute % 60)}") }
            }
            Text(s.t("Часовой пояс: Ташкент", "Vaqt mintaqasi: Toshkent"), fontSize = 12.sp)
        }
        Text(s.t("Тема приложения", "Ilova mavzusi"), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("auto" to s.t("Авто", "Avto"), "light" to s.t("Светлая", "Yorug‘"), "dark" to s.t("Тёмная", "Qorong‘i")).forEach { (value, label) ->
                FilterChip(s.theme == value, onClick = { s.theme = value; s.repository.store.theme = value }, label = { Text(label) })
            }
        }
    }
    s.profile.optJSONObject("trackedSavings")?.let { savings -> Panel {
        Text(s.t("Сэкономлено на билетах", "Chiptalarda tejalgan"), fontWeight = FontWeight.Bold)
        Text("${money(savings.optLong("amount"))} ${savings.text("currency", "UZS")}", color = MaterialTheme.colorScheme.primary)
    } }
    if (s.profile.text("referralShareUrl").isNotEmpty()) OutlinedButton(onClick = {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, s.profile.text("referralShareUrl")), s.t("Пригласить друга", "Do‘stni taklif qilish")))
    }, modifier = Modifier.fillMaxWidth()) { Text(s.t("Пригласить друга", "Do‘stni taklif qilish")) }
    OutlinedButton(enabled = !s.busy, onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text(s.t("Выйти", "Chiqish")) }
}

@Composable
private fun PlansScreen(s: AppState, onRun: (suspend () -> Unit) -> Unit, onBack: () -> Unit) {
    var items by remember { mutableStateOf(emptyList<JSONObject>()) }
    var sent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!s.demo) onRun { items = s.repository.request("/api/v1/plans").rows("plans") }
    }
    TextButton(onClick = onBack) { Text(s.t("← Назад", "← Orqaga")) }
    Header(s.t("Больше возможностей", "Ko‘proq imkoniyatlar"), s.t("Free · Lite · Pro", "Free · Lite · Pro"))
    if (s.demo) EmptyState(s.t("Тарифы в демо", "Demo tariflari"), s.t("Войдите, чтобы увидеть актуальные цены и оформить заявку.", "Amaldagi narxlarni ko‘rish va ariza yuborish uchun kiring."))
    items.forEach { plan -> Panel {
        Text(if (plan.text("tier") == "lite") "Lite" else "Pro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${money(plan.optLong("priceUzs"))} ${s.t("сум", "so‘m")} · ${plan.optInt("durationDays")} ${s.t("дней", "kun")}")
        Text(if (plan.text("tier") == "lite") s.t("Больше мгновенных уведомлений", "Ko‘proq tezkor xabarlar") else s.t("Больше отслеживаний и история цен", "Ko‘proq kuzatuvlar va narxlar tarixi"))
        Button(enabled = !s.busy && !sent && plan.optBoolean("purchasable", true), onClick = { onRun {
            s.access = s.repository.request("/api/v1/pro/applications", "POST", json("planCode" to plan.text("code"))); sent = true
        } }, modifier = Modifier.fillMaxWidth()) { Text(s.t("Оставить заявку", "Ariza yuborish")) }
    } }
    if (sent) Text(s.t("Заявка отправлена. Продолжите оформление в Telegram.", "Ariza yuborildi. Telegram’da davom eting."), color = MaterialTheme.colorScheme.primary)
    if (!s.demo && s.access.optJSONObject("application") != null) ProConversation(s, onRun)
    val context = LocalContext.current
    OutlinedButton(onClick = { openLink(context, "https://t.me/hot_ticket_buy_bot") }, modifier = Modifier.fillMaxWidth()) { Text(s.t("Открыть Telegram", "Telegram’ni ochish")) }
}

@Composable
private fun ProConversation(s: AppState, onRun: (suspend () -> Unit) -> Unit) {
    val context = LocalContext.current
    val id = s.access.optJSONObject("application")?.optLong("id") ?: return
    var text by rememberSaveable(id) { mutableStateOf("") }
    var attachment by remember(id) { mutableStateOf<JSONObject?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onRun {
            attachment = withContext(Dispatchers.IO) {
                val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
                var name = "attachment"
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) name = cursor.getString(0)
                }
                val output = java.io.ByteArrayOutputStream()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer); if (read < 0) break
                        require(output.size() + read <= 10 * 1024 * 1024) { s.t("Файл должен быть не больше 10 МБ", "Fayl 10 MB dan oshmasligi kerak") }
                        output.write(buffer, 0, read)
                    }
                } ?: throw IllegalArgumentException(s.t("Не удалось открыть файл", "Faylni ochib bo‘lmadi"))
                json("name" to name, "mimeType" to type, "base64" to android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP))
            }
        }
    }
    Panel {
        Text(s.t("Чат по заявке", "Ariza bo‘yicha chat"), fontWeight = FontWeight.Bold)
        val status = when (s.access.optJSONObject("application")?.text("status")) {
            "waiting_payment" -> s.t("Ожидается оплата", "To‘lov kutilmoqda")
            "approved" -> s.t("Одобрено", "Tasdiqlangan")
            "closed" -> s.t("Заявка закрыта", "Ariza yopilgan")
            else -> s.t("В обработке", "Ko‘rib chiqilmoqda")
        }
        Text("№ $id · $status", fontSize = 12.sp)
        OutlinedButton(enabled = !s.busy, onClick = { onRun {
            s.access = s.repository.request("/api/v1/pro")
            s.repository.request("/api/v1/pro/applications/$id/read", "POST", json())
        } }) { Text(s.t("Обновить сообщения", "Xabarlarni yangilash")) }
        s.access.rows("messages").forEach { message ->
            Surface(shape = RoundedCornerShape(12.dp), color = if (message.text("sender") == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (message.text("sender") == "user") s.t("Вы", "Siz") else s.t("Поддержка", "Yordam"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (message.text("text").isNotEmpty()) Text(message.text("text"))
                    if (message.text("attachmentName").isNotEmpty()) Text("📎 ${message.text("attachmentName")}", fontSize = 12.sp)
                }
            }
        }
        if (s.access.optBoolean("canReply", true)) {
            OutlinedTextField(text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text(s.t("Сообщение", "Xabar")) }, minLines = 2)
            if (attachment != null) Text("📎 ${attachment!!.text("name")}", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !s.busy, onClick = { launcher.launch("*/*") }) { Text(s.t("Прикрепить чек", "Chekni biriktirish")) }
                Button(enabled = !s.busy && (text.isNotBlank() || attachment != null), onClick = { onRun {
                    s.repository.request("/api/v1/pro/applications/$id/messages", "POST", json("text" to text.trim().ifEmpty { null }, "attachment" to attachment))
                    text = ""; attachment = null; s.access = s.repository.request("/api/v1/pro")
                } }) { Text(s.t("Отправить", "Yuborish")) }
            }
        }
    }
}
