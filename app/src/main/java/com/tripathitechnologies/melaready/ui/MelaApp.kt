package com.tripathitechnologies.melaready.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tripathitechnologies.melaready.AppState
import com.tripathitechnologies.melaready.domain.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Paper = Color(0xFFF7F6F0)
private val Ink = Color(0xFF153D35)
private val Teal = Color(0xFF226557)
private val Muted = Color(0xFF5C6D64)
private val Line = Color(0xFFE0E5DB)
private val Mint = Color(0xFFEAF0E6)
private val Saffron = Color(0xFFF0B767)
private val LocalHindi = compositionLocalOf { false }
@Composable private fun t(en: String, hi: String) = if (LocalHindi.current) hi else en

@Composable
fun MelaApp(
    model: AppState,
    geo: GeoFix?,
    locating: Boolean,
    locationMessage: String?,
    microphoneGranted: Boolean,
    requestLocation: () -> Unit,
    clearLocation: () -> Unit,
    requestMicrophone: () -> Unit,
    pickProfilePhoto: (ValueCallback<Array<Uri>>) -> Unit
) {
    val w = model.workspace
    var page by rememberSaveable { mutableStateOf("Home") }
    var dialog by rememberSaveable { mutableStateOf("") }
    val mainTabs = listOf("Home", "Team", "Practice", "Map", "Help")
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Groups, Icons.Rounded.School, Icons.Rounded.Map, Icons.Rounded.HealthAndSafety)

    CompositionLocalProvider(LocalHindi provides w.hindi) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Teal, onPrimary = Color.White, secondary = Ink,
                background = Paper, onBackground = Ink, surface = Color.White, onSurface = Ink,
                surfaceVariant = Mint, onSurfaceVariant = Muted, outline = Line
            )
        ) {
            // First launch gateway screen check
            if (!w.gatewayCompleted) {
                GatewayScreen(w, model, onSignIn = { page = "Live" }, onContinueOffline = { page = "Home" })
                return@MaterialTheme
            }

            BackHandler(page != "Home") { page = "Home" }
            Scaffold(
                containerColor = Paper,
                topBar = {
                    Row(
                        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (page !in mainTabs) IconButton(onClick = { page = "Home" }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Back", "वापस")) }
                        else Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ink), contentAlignment = Alignment.Center) { Text("M", color = Paper, fontWeight = FontWeight.Black, fontSize = 24.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("MelaReady", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text(t("PREPARE · PRACTISE · COORDINATE", "तैयारी · अभ्यास · समन्वय"), fontSize = 8.sp, letterSpacing = 0.6.sp, color = Muted)
                        }
                        TextButton(onClick = { model.language() }) { Text(if (w.hindi) "EN" else "हिंदी", color = Ink) }
                        IconButton(onClick = { dialog = "Settings" }) { Icon(Icons.Rounded.Tune, t("Settings", "सेटिंग"), tint = Ink) }
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                        mainTabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = page == tab,
                                onClick = { page = tab },
                                icon = { Icon(icons[index], null) },
                                label = { Text(t(tab, listOf("होम", "टीम", "अभ्यास", "नक्शा", "मदद")[index])) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Ink, selectedTextColor = Ink,
                                    indicatorColor = Mint, unselectedIconColor = Muted, unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
            ) { insets ->
                Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (model.storageError) SurfaceCard {
                            Text(t("Local storage needs attention", "स्थानीय स्टोरेज में समस्या"), fontWeight = FontWeight.Bold)
                            Body(t("Changes were not saved. Existing data has not been overwritten. Reopen the app, or clear the workspace in Settings if you want to start over.", "बदलाव सेव नहीं हुए। पुराना डेटा बदला नहीं गया। ऐप दोबारा खोलें, या नई शुरुआत के लिए सेटिंग में डेटा हटाएँ।"))
                        }
                        when (page) {
                            "Home" -> Home(w, { dialog = "Event" }, { model.sample() }, { page = it })
                            "Team" -> Team(w, { dialog = "Member" }, { id -> dialog = "Remove:$id" }, { page = "Live" })
                            "Practice" -> LearningHub(w, model)
                            "Map" -> VenueMap(geo, locating, locationMessage, requestLocation, clearLocation)
                            "Help" -> HelpNow(geo, locating, locationMessage, requestLocation, clearLocation)
                            "Checklist" -> Checklist(w, model)
                            "Log" -> DrillLog(w, { dialog = "Incident" }, { model.advanceIncident(it) })
                            "Live" -> SharedTeam(w, model, geo, locating, locationMessage, microphoneGranted, requestLocation, requestMicrophone, pickProfilePhoto, onBackOffline = { page = "Home" })
                        }
                    }
                }
            }
            when {
                dialog == "Event" -> EntryDialog(
                    t("Create your event", "अपना कार्यक्रम बनाएँ"),
                    listOf(t("Event name", "कार्यक्रम का नाम"), t("Venue / district", "स्थान / जिला"), t("Expected attendance", "अनुमानित संख्या")),
                    true,
                    t("Attendance is a planning estimate, not a live crowd count.", "यह योजना का अनुमान है, लाइव भीड़ की गिनती नहीं।"),
                    { dialog = "" }
                ) { a, b, c -> model.createEvent(a, b, c) }

                dialog == "Member" -> EntryDialog(
                    t("Add a local member", "स्थानीय सदस्य जोड़ें"),
                    listOf(t("Display name", "दिखने वाला नाम"), t("Role", "भूमिका"), t("Assigned zone", "निर्धारित क्षेत्र")),
                    false,
                    t("Add people with their consent. This creates a local roster entry; it does not invite or verify an account.", "सहमति से नाम जोड़ें। यह केवल स्थानीय सूची है; इससे खाता या आमंत्रण नहीं बनता।"),
                    { dialog = "" }
                ) { a, b, c -> model.member(a, b, c) }

                dialog == "Incident" -> EntryDialog(
                    t("Add a drill note", "अभ्यास का नोट जोड़ें"),
                    listOf(t("What was observed? (5–240 characters)", "क्या देखा? (5–240 अक्षर)"), t("Zone", "क्षेत्र")),
                    false,
                    t("For practice only. Nothing is transmitted to responders.", "केवल अभ्यास के लिए। कोई रिपोर्ट सहायता दल तक नहीं भेजी जाती।"),
                    { dialog = "" }
                ) { a, b, _ -> model.incident(a, b) }

                dialog == "Settings" -> AlertDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text(t("Your workspace", "आपका कार्यस्थल")) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Body(t("MelaReady 0.4 · Connected-team prototype", "MelaReady 0.4 · कनेक्टेड-टीम प्रोटोटाइप"))
                            Body(t("Event, roster and practice progress are encrypted on this device. A location stays in memory unless you explicitly share its snapshot with a connected team.", "कार्यक्रम, सूची और अभ्यास इस डिवाइस पर एन्क्रिप्टेड हैं। लोकेशन मेमोरी में रहती है; कनेक्टेड टीम से स्नैपशॉट केवल आपके स्पष्ट शेयर करने पर जाता है।"))
                            Body(t("Help Now opens official sources and prepares a message; it never dispatches help. Shared-team server tools are bundled with the Android Studio project.", "Help Now आधिकारिक स्रोत खोलता और संदेश तैयार करता है; यह सहायता नहीं भेजता। साझा टीम सर्वर के टूल Android Studio प्रोजेक्ट में दिए हैं।"))
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = "" }) { Text(t("Done", "ठीक है")) } },
                    dismissButton = { TextButton(onClick = { dialog = "Clear" }) { Text(t("Clear local data", "स्थानीय डेटा हटाएँ")) } }
                )

                dialog == "Clear" -> ConfirmDialog(
                    t("Clear this workspace?", "यह डेटा हटाएँ?"),
                    t("Event, roster, checklist and drill history on this device will be deleted.", "इस डिवाइस का कार्यक्रम, सूची, तैयारी और अभ्यास इतिहास हट जाएगा।"),
                    { dialog = "" }
                ) {
                    if (model.clear()) { clearLocation(); page = "Home"; dialog = "" }
                }

                dialog.startsWith("Remove:") -> ConfirmDialog(
                    t("Remove member?", "सदस्य हटाएँ?"),
                    t("Only this local roster entry will be removed.", "केवल इस स्थानीय सूची से नाम हटेगा।"),
                    { dialog = "" }
                ) { if (model.removeMember(dialog.substringAfter(':'))) dialog = "" }
            }
        }
    }
}

@Composable
private fun GatewayScreen(
    w: Workspace,
    model: AppState,
    onSignIn: () -> Unit,
    onContinueOffline: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { model.language() }) {
                    Text(if (w.hindi) "EN" else "हिंदी", color = Ink, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 500.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Ink),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = Paper, fontWeight = FontWeight.Black, fontSize = 44.sp)
                }

                Text("MelaReady", fontWeight = FontWeight.Black, fontSize = 36.sp, color = Ink)

                Text(
                    t("PREPARE · PRACTISE · COORDINATE", "तैयारी · अभ्यास · समन्वय"),
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = Muted
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    t(
                        "Sign in to coordinate with your joined team, or continue offline for training and preparation.",
                        "अपनी जुड़ी टीम के साथ समन्वय के लिए साइन इन करें, या प्रशिक्षण और तैयारी के लिए ऑफलाइन जारी रखें।"
                    ),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        model.completeGateway()
                        onSignIn()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
                ) {
                    Icon(Icons.Rounded.AccountCircle, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        t("Sign in / Create account", "साइन इन / खाता बनाएँ"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        model.completeGateway()
                        onContinueOffline()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        t("Continue offline", "ऑफलाइन जारी रखें"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Ink
                    )
                }

                Text(
                    t(
                        "No permissions are requested on this screen.",
                        "इस स्क्रीन पर कोई अनुमति नहीं माँगी जाती।"
                    ),
                    fontSize = 11.sp,
                    color = Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun Home(w: Workspace, create: () -> Unit, sample: () -> Unit, go: (String) -> Unit) {
    Eyebrow(t("YOUR EVENT COMPANION", "आपका कार्यक्रम साथी"))
    Heading(t("A calmer start.\nA prepared team.", "शांत शुरुआत।\nतैयार टीम।"))

    // PROMINENT LIVE TEAM ENTRY CARD (Requirement #2)
    SurfaceCard(Teal) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.CloudSync, null, tint = Paper)
            }
            Column(Modifier.weight(1f)) {
                Text(t("Live Team", "लाइव टीम"), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Paper)
                Text(
                    t("Sign in · Tasks · Reports · Push-to-Talk", "साइन इन · कार्य · रिपोर्ट · पुश-टू-टॉक"),
                    fontSize = 12.sp,
                    color = Color(0xFFD6E4D8)
                )
            }
            Button(
                onClick = { go("Live") },
                colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)
            ) {
                Text(t("Open", "खोलें"))
            }
        }
    }

    if (w.event == null) {
        SurfaceCard(Ink) {
            Tag(t("START HERE", "यहाँ से शुरू करें"), Saffron, Ink)
            Text(t("Bring your people\nand plan together.", "लोगों और तैयारी\nको साथ लाएँ।"), fontSize = 26.sp, lineHeight = 33.sp, color = Paper, fontWeight = FontWeight.SemiBold)
            Text(t("Set up a venue, assign roles and practise before the day.", "स्थान तय करें, भूमिका बाँटें और कार्यक्रम से पहले अभ्यास करें।"), color = Color(0xFFD6E4D8), lineHeight = 23.sp)
            Button(onClick = create, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)) { Text(t("Create an event", "कार्यक्रम बनाएँ")); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) }
        }
        OutlinedButton(onClick = sample, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(t("Explore a fictional sample", "काल्पनिक नमूना देखें")) }
        Body(t("A local prototype for preparation and practice. No live response services are connected.", "तैयारी और अभ्यास का स्थानीय प्रोटोटाइप। लाइव सहायता सेवाएँ नहीं जुड़ी हैं।"))
    } else {
        SurfaceCard(Ink) {
            Tag(t(if (w.event.sample) "FICTIONAL SAMPLE" else "LOCAL EVENT", if (w.event.sample) "काल्पनिक नमूना" else "स्थानीय कार्यक्रम"), Saffron, Ink)
            Text(w.event.name, fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, color = Paper)
            Text(w.event.venue, color = Color(0xFFD6E4D8), lineHeight = 22.sp)
            HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat(w.members.size.toString(), t("local members", "स्थानीय सदस्य"), Modifier.weight(1f))
                Stat(String.format(Locale.US, "%,d", w.event.attendance), t("expected visitors", "अनुमानित आगंतुक"), Modifier.weight(1f))
            }
        }
        SectionLabel(t("Your next step", "अगला कदम"))
        ActionCard("01", t("Practise before the day", "कार्यक्रम से पहले अभ्यास"), t("A 3-decision communication drill", "संचार के 3 निर्णयों का अभ्यास"), Icons.Rounded.PlayCircle) { go("Practice") }
        ActionCard("02", t("Check the essentials", "ज़रूरी तैयारी जाँचें"), t("${w.checks.size}/4 planning items marked", "तैयारी के ${w.checks.size}/4 बिंदु चिह्नित"), Icons.Rounded.Checklist) { go("Checklist") }
        ActionCard("03", t("Open the drill log", "अभ्यास लॉग खोलें"), t("${w.incidents.size} notes · stored on this device", "${w.incidents.size} नोट · इसी डिवाइस पर"), Icons.Rounded.FactCheck) { go("Log") }
    }
}

@Composable
private fun Team(w: Workspace, add: () -> Unit, remove: (String) -> Unit, live: () -> Unit) {
    Eyebrow(t("PEOPLE & ROLES", "लोग और भूमिकाएँ")); Heading(t("Everyone has\na part to play.", "हर सदस्य की\nअपनी भूमिका।"))
    Body(t("Keep a device-local roster, or connect the bundled demo server for real joined accounts and shared follow-up.", "डिवाइस पर स्थानीय सूची रखें, या जुड़े खाते और साझा फॉलो-अप के लिए दिया गया डेमो सर्वर कनेक्ट करें।"))

    // ONE COMBINED LIVE TEAM CARD WITH STATUS CHIP (Requirement #3)
    SurfaceCard(Ink) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Tag(t("CONNECTED SYSTEM", "कनेक्टेड सिस्टम"), Saffron, Ink)
            Tag(t("READY", "तैयार"), Mint, Teal)
        }
        Text(t("Open Live Team", "लाइव टीम खोलें"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Paper)
        Text(
            t("Accounts · Invites · Tasks · Reports · Location · Voice", "खाते · आमंत्रण · कार्य · रिपोर्ट · लोकेशन · वॉइस"),
            color = Color(0xFFD6E4D8),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = live,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)
        ) {
            Icon(Icons.Rounded.CloudSync, null)
            Spacer(Modifier.width(8.dp))
            Text(t("Open Live Team", "लाइव टीम खोलें"), fontWeight = FontWeight.Bold)
        }
    }

    if (w.event == null) {
        Empty(t("Create an event from Home to add your local team.", "स्थानीय टीम जोड़ने के लिए होम से कार्यक्रम बनाएँ।"))
        return
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(t("${w.members.size} local members", "${w.members.size} स्थानीय सदस्य"), Modifier.weight(1f))
        TextButton(onClick = add) { Icon(Icons.Rounded.Add, null); Text(t("Add", "जोड़ें")) }
    }

    if (w.members.isEmpty()) {
        Empty(t("Start with the team lead, then add each role and zone.", "पहले टीम लीड, फिर अन्य भूमिकाएँ और क्षेत्र जोड़ें।"))
    }

    w.members.forEach { m ->
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
                    Text(m.name.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(m.name, fontWeight = FontWeight.SemiBold)
                    Body(m.role)
                    Text(m.zone, color = Teal, fontSize = 12.sp)
                }
                IconButton(onClick = { remove(m.id) }) {
                    Icon(Icons.Rounded.Close, t("Remove member", "सदस्य हटाएँ"), tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private sealed interface ConnectionState {
    object NotConnected : ConnectionState
    object Checking : ConnectionState
    object Connected : ConnectionState
    data class ConnectionFailed(val message: String) : ConnectionState
}

@Composable
private fun SharedTeam(
    w: Workspace,
    model: AppState,
    geo: GeoFix?,
    locating: Boolean,
    locationMessage: String?,
    microphoneGranted: Boolean,
    requestLocation: () -> Unit,
    requestMicrophone: () -> Unit,
    pickProfilePhoto: (ValueCallback<Array<Uri>>) -> Unit,
    onBackOffline: () -> Unit
) {
    var serverInput by rememberSaveable { mutableStateOf(w.serverUrl) }
    var connState by remember { mutableStateOf<ConnectionState>(ConnectionState.NotConnected) }
    var expandedSettings by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    val currentMicPermission = rememberUpdatedState(microphoneGranted)

    val runCheck = {
        connState = ConnectionState.Checking
        model.setServerUrl(serverInput)
        Thread {
            try {
                val target = serverInput.trimEnd('/') + "/api/health"
                val conn = java.net.URL(target).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                if (code == 200) {
                    connState = ConnectionState.Connected
                } else {
                    connState = ConnectionState.ConnectionFailed("HTTP $code from server")
                }
            } catch (_: Exception) {
                connState = ConnectionState.ConnectionFailed(
                    "Team server could not be reached. Confirm that the backend is running and both devices are connected to the same trusted Wi-Fi."
                )
            }
        }.start()
    }

    LaunchedEffect(Unit) {
        if (connState is ConnectionState.NotConnected) {
            runCheck()
        }
    }

    Eyebrow(t("CONNECTED WORKSPACE · V4", "कनेक्टेड कार्यस्थल · V4"))
    Heading(t("One team.\nVisible follow-up.", "एक टीम।\nस्पष्ट फॉलो-अप।"))

    // STATUS BADGE CHIP
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val (chipText, chipBg, chipFg) = when (connState) {
            is ConnectionState.NotConnected -> Triple(t("Not connected", "कनेक्ट नहीं"), Color(0xFFE0E5DB), Ink)
            is ConnectionState.Checking -> Triple(t("Checking...", "जाँच जारी..."), Color(0xFFFFE7D2), Color(0xFF914A17))
            is ConnectionState.Connected -> Triple(t("Connected", "कनेक्टेड"), Mint, Teal)
            is ConnectionState.ConnectionFailed -> Triple(t("Connection failed", "कनेक्शन विफल"), Color(0xFFFFE0DB), Color(0xFFA12F20))
        }
        Tag(chipText, chipBg, chipFg)
    }

    // EXPANDABLE SETTINGS SECTION (Requirement #4)
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                t("Advanced connection settings", "उन्नत कनेक्शन सेटिंग"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            IconButton(onClick = { expandedSettings = !expandedSettings }) {
                Icon(if (expandedSettings) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
        }

        if (expandedSettings) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = serverInput,
                    onValueChange = { serverInput = it.take(200) },
                    label = { Text(t("Server URL", "सर्वर URL")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { runCheck() }, modifier = Modifier.weight(1f)) {
                        Text(t("Connect", "कनेक्ट करें"))
                    }
                    OutlinedButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Rounded.HelpOutline, null)
                    }
                }
            }
        }
    }

    // RENDER STATES
    when (val state = connState) {
        is ConnectionState.Checking -> {
            SurfaceCard {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = Teal)
                Text(
                    t("Checking backend server health...", "बैकएंड सर्वर की स्थिति जाँची जा रही है..."),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is ConnectionState.ConnectionFailed -> {
            SurfaceCard {
                Tag(t("CONNECTION ERROR", "कनेक्शन त्रुटि"), Color(0xFFFFE0DB), Color(0xFFA12F20))
                Text(state.message, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { runCheck() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text(t("Retry", "पुनः प्रयास करें"))
                    }
                    OutlinedButton(onClick = { expandedSettings = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Edit server address", "सर्वर पता बदलें"))
                    }
                    OutlinedButton(onClick = onBackOffline, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Continue offline", "ऑफलाइन जारी रखें"))
                    }
                }
            }
        }

        is ConnectionState.Connected -> {
            // LOCATION & MIC BAR FOR CONNECTED WORKSPACE
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (geo == null) {
                    OutlinedButton(onClick = requestLocation, enabled = !locating, modifier = Modifier.weight(1f)) {
                        Text(if (locating) t("Locating...", "लोकेशन...") else t("Get Location", "लोकेशन लें"))
                    }
                } else {
                    Button(
                        onClick = {
                            web?.evaluateJavascript("shareNativeLocation(${geo.latitude},${geo.longitude},${geo.accuracy ?: -1f},${geo.capturedAt / 1000})", null)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(t("Share Snapshot (5m)", "स्नैपशॉट शेयर (5मिश्र)"))
                    }
                }

                OutlinedButton(
                    onClick = requestMicrophone,
                    enabled = !microphoneGranted
                ) {
                    Icon(if (microphoneGranted) Icons.Rounded.Mic else Icons.Rounded.MicOff, null)
                }
            }

            if (locationMessage != null) Body(locationMessage)

            // WEBVIEW CONTAINER (Requirement #13)
            Surface(
                Modifier.fillMaxWidth().height(640.dp),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {}
                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: PermissionRequest) {
                                    if (currentMicPermission.value && request.resources.all { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }) {
                                        request.grant(request.resources)
                                    } else {
                                        requestMicrophone()
                                        request.deny()
                                    }
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    callback: ValueCallback<Array<Uri>>,
                                    params: FileChooserParams?
                                ): Boolean {
                                    pickProfilePhoto(callback)
                                    return true
                                }
                            }
                            web = this
                            loadUrl(serverInput.trimEnd('/') + "/")
                        }
                    },
                    update = { view ->
                        web = view
                        if (view.url?.startsWith(serverInput.trimEnd('/')) != true) {
                            view.loadUrl(serverInput.trimEnd('/') + "/")
                        }
                    }
                )
            }
        }

        else -> {}
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(t("Backend Setup Instructions", "बैकएंड सेटअप निर्देश")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Body(t("Start server on laptop:", "लैपटॉप पर सर्वर चालू करें:"))
                    Text("python server/server.py --host 0.0.0.0 --port 8080", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Body(t("Emulator address:", "एमुलेटर पता:"))
                    Text("http://10.0.2.2:8080", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Body(t("Physical phone address:", "भौतिक फोन पता:"))
                    Text("http://<your-laptop-lan-ip>:8080", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text(t("OK", "ठीक है")) } }
        )
    }
}

private data class Question(val en: String, val hi: String, val choices: List<Pair<String, String>>, val whyEn: String, val whyHi: String)
private val questions = listOf(
    Question("You hear an unverified message: ‘Gate B is closed.’ What do you do first?", "एक अपुष्ट संदेश मिला: ‘गेट B बंद है।’ पहला कदम?",
        listOf("Forward it to everyone" to "सभी को आगे भेजें", "Ask the designated lead to verify it" to "निर्धारित लीड से सत्यापन माँगें", "Announce a new route yourself" to "खुद नया रास्ता घोषित करें"), "Verify the source before circulating an operational update.", "काम से जुड़ा संदेश फैलाने से पहले स्रोत की पुष्टि करें।"),
    Question("You are the team lead. How should the observation be recorded for this drill?", "आप टीम लीड हैं। इस अभ्यास में सूचना कैसे दर्ज करेंगे?",
        listOf("Mark it resolved immediately" to "तुरंत हल हुआ लिख दें", "Leave it only in a group chat" to "केवल ग्रुप चैट में छोड़ दें", "Log the zone and assign a verification task" to "क्षेत्र दर्ज करके सत्यापन का काम सौंपें"), "A named task and recorded zone make the follow-up clear.", "निर्धारित काम और दर्ज क्षेत्र से आगे की जाँच स्पष्ट रहती है।"),
    Question("The assigned person sends an update. What completes the communication loop?", "निर्धारित व्यक्ति अपडेट भेजता है। संचार का चक्र कैसे पूरा होगा?",
        listOf("Delete the original note" to "मूल नोट हटा दें", "Lead confirms the update and records the outcome" to "लीड अपडेट की पुष्टि करके परिणाम दर्ज करे", "Assume everyone has seen it" to "मान लें कि सभी ने देख लिया"), "Confirmation and an outcome distinguish a sent message from a completed task.", "पुष्टि और परिणाम से पता चलता है कि काम पूरा हुआ या केवल संदेश भेजा गया।")
)

@Composable private fun LearningHub(w: Workspace, model: AppState) {
    var topicId by rememberSaveable { mutableStateOf<String?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var showClassic by rememberSaveable { mutableStateOf(false) }
    if (showClassic) {
        TextButton(onClick = { showClassic = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null); Text(t("Learning hub", "लर्निंग हब")) }
        DecisionPractice(w, model); return
    }
    val topic = topicId?.let { LearningBank.topic(it) }
    if (topic == null) {
        Eyebrow(t("18 ORIGINAL QUESTIONS · 6 TOPICS", "18 मौलिक प्रश्न · 6 विषय"))
        Heading(t("Practise the\ndecisions that matter.", "ज़रूरी निर्णयों\nका अभ्यास करें।"))
        Body(t("Short bilingual practice for volunteers. This is not emergency-response certification.", "स्वयंसेवकों के लिए छोटा द्विभाषी अभ्यास। यह आपात-प्रतिक्रिया प्रमाणपत्र नहीं है।"))
        LearningBank.topics.forEach { item ->
            ActionCard((w.learning[item.id] ?: 0).let { if(it>0) "$it/3" else "03" }, t(item.titleEn,item.titleHi), t(item.subtitleEn,item.subtitleHi), Icons.Rounded.School) {
                topicId=item.id; index=0; score=0; selected=-1
            }
        }
        TextButton(onClick = { showClassic = true }, modifier=Modifier.fillMaxWidth()) { Text(t("Open original 3-decision drill", "मूल 3-निर्णय अभ्यास खोलें")) }
        return
    }
    TextButton(onClick = { topicId=null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,null); Text(t("All topics", "सभी विषय")) }
    Eyebrow(t(topic.titleEn, topic.titleHi).uppercase())
    Heading(t("Question ${index+1} of 3", "प्रश्न ${index+1} / 3"))
    LinearProgressIndicator(progress={index/3f},modifier=Modifier.fillMaxWidth(),color=Teal,trackColor=Mint)
    val q=topic.questions[index]
    SurfaceCard {
        Text(t(q.promptEn,q.promptHi),fontSize=21.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold)
        q.options.forEachIndexed { i, option ->
            OutlinedButton(onClick={ if(selected<0){selected=i;if(i==q.correct)score++} },enabled=selected<0,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(t(option.first,option.second)) }
        }
        if(selected>=0){
            Tag(if(selected==q.correct)t("CORRECT","सही") else t("LEARN AND RETRY","सीखें और दोहराएँ"),if(selected==q.correct)Mint else Color(0xFFFFE7D2),if(selected==q.correct)Teal else Color(0xFF914A17))
            Body(t(q.whyEn,q.whyHi))
            Button(onClick={
                if(index==2){model.saveLearning(topic.id,score);topicId=null;index=0;score=0}else{index++;selected=-1}
            },modifier=Modifier.fillMaxWidth()) { Text(if(index==2)t("Finish · score $score/3","पूरा करें · स्कोर $score/3") else t("Next question","अगला प्रश्न")) }
        }
    }
}

@Composable private fun DecisionPractice(w: Workspace, model: AppState) {
    var feedback by rememberSaveable { mutableStateOf(false) }
    Eyebrow(t("LEARN IT. REHEARSE IT.", "सीखें। अभ्यास करें।")); Heading(t("Ready starts\nwith practice.", "तैयारी शुरू होती है\nअभ्यास से।"))
    Body(t("Draft communication exercise · not a certification. Review with a qualified trainer before real event use.", "संचार अभ्यास का प्रारूप · प्रमाणपत्र नहीं। वास्तविक कार्यक्रम से पहले योग्य प्रशिक्षक से समीक्षा कराएँ।"))
    if (w.event == null) { Empty(t("Create an event or open the fictional sample first.", "पहले कार्यक्रम बनाएँ या काल्पनिक नमूना खोलें।")); return }
    SurfaceCard {
        Tag(t("SCENARIO 01 · TWO ROLES", "अभ्यास 01 · दो भूमिकाएँ"))
        Text(t("An unverified gate update", "गेट से जुड़ी अपुष्ट सूचना"), fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
        Body(t("Volunteer → team lead → confirmation", "स्वयंसेवक → टीम लीड → पुष्टि"))
        LinearProgressIndicator(progress = { w.answers.size.coerceAtMost(3) / 3f }, modifier = Modifier.fillMaxWidth(), color = Teal, trackColor = Mint)
        if (Training.complete(w.answers)) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Teal, modifier = Modifier.size(40.dp))
            Text(t("Practice complete", "अभ्यास पूरा"), fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Body(t("Three decisions rehearsed. Now discuss who verifies updates at your actual venue.", "तीन निर्णयों का अभ्यास हुआ। अब तय करें कि आपके स्थान पर अपडेट कौन सत्यापित करेगा।"))
            Primary(t("Practise again", "दोबारा अभ्यास")) { if (model.restartTraining()) feedback = false }
        } else {
            val index = w.answers.size.coerceIn(0, 2); val q = questions[index]
            Eyebrow(t("DECISION ${index + 1} OF 3", "निर्णय ${index + 1} / 3"))
            Text(t(q.en, q.hi), fontSize = 20.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium)
            q.choices.forEachIndexed { choice, label ->
                OutlinedButton(onClick = { feedback = !model.answer(choice) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(16.dp)) { Text(t(label.first, label.second), modifier = Modifier.fillMaxWidth(), lineHeight = 23.sp) }
            }
            if (feedback) Surface(Modifier.fillMaxWidth(), color = Mint, shape = RoundedCornerShape(14.dp)) { Text(t(q.whyEn, q.whyHi), Modifier.padding(16.dp), color = Ink, lineHeight = 23.sp) }
        }
    }
}

@Composable private fun VenueMap(geo: GeoFix?, locating: Boolean, error: String?, request: () -> Unit, clear: () -> Unit) {
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(geo) { while (true) { clock = SystemClock.elapsedRealtimeNanos(); delay(10_000) } }
    Eyebrow(t("KNOW THE PLACE", "स्थान को समझें")); Heading(t("A shared sense\nof place.", "स्थान की\nसाझा समझ।"))
    SurfaceCard {
        Tag(t("FICTIONAL VENUE SKETCH", "काल्पनिक स्थान का खाका"))
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(20.dp)).background(Mint)) {
            Canvas(Modifier.fillMaxSize()) {
                val path = Path().apply { moveTo(size.width * .05f, size.height * .8f); cubicTo(size.width * .3f, size.height * .7f, size.width * .65f, size.height * .85f, size.width * .9f, size.height * .05f) }
                drawPath(path, Paper, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 42.dp.toPx(), cap = StrokeCap.Round))
                drawRoundRect(Color(0xFFC8D7BE), Offset(size.width * .07f, size.height * .12f), Size(size.width * .32f, size.height * .28f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f))
                drawRoundRect(Color(0xFFD8DCC8), Offset(size.width * .63f, size.height * .59f), Size(size.width * .3f, size.height * .27f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f))
                drawCircle(Teal, 10.dp.toPx(), Offset(size.width * .46f, size.height * .63f))
            }
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MapLabel(t("ZONE A", "क्षेत्र A")); MapLabel(t("HELP DESK", "सहायता केंद्र")) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MapLabel(t("GATE A", "गेट A")); MapLabel(t("CONTROL", "नियंत्रण")) }
            }
        }
        Body(t("Illustration only. It is not your current location, a navigation map or an evacuation route.", "केवल चित्रण। यह आपकी वर्तमान लोकेशन, नेविगेशन नक्शा या निकासी मार्ग नहीं है।"))
    }
    SurfaceCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.MyLocation, null, tint = Teal); Text(t("My location snapshot", "मेरी लोकेशन का स्नैपशॉट"), fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
        Body(t("One request, on your tap. No sharing, background tracking or saved location history.", "आपके बटन दबाने पर एक अनुरोध। शेयरिंग, बैकग्राउंड ट्रैकिंग या सेव किया गया इतिहास नहीं।"))
        if (geo != null) {
            Text(String.format(Locale.US, "%.5f, %.5f", geo.latitude, geo.longitude), fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = Ink)
            val age = ((clock - geo.elapsedNanos) / 1_000_000_000).coerceAtLeast(0)
            Body(t("Accuracy: ${geo.accuracy?.toInt()?.let { "±$it m" } ?: "unknown"} · ${age}s old", "सटीकता: ${geo.accuracy?.toInt()?.let { "±$it मी" } ?: "अज्ञात"} · ${age} सेकंड पुरानी"))
            TextButton(onClick = clear) { Text(t("Clear snapshot", "स्नैपशॉट हटाएँ")) }
        }
        if (error != null) Body(error)
        Button(onClick = request, enabled = !locating, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (locating) t("Requesting location…", "लोकेशन माँगी जा रही है…") else t("Get my location", "मेरी लोकेशन लें")) }
    }
}

@Composable private fun HelpNow(geo: GeoFix?, locating: Boolean, error: String?, request:()->Unit, clear:()->Unit) {
    val context=LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(1) }
    var hazard by rememberSaveable { mutableStateOf("Flood") }
    var phase by rememberSaveable { mutableStateOf("Happening now") }
    var region by rememberSaveable { mutableStateOf("") }
    var people by rememberSaveable { mutableStateOf("") }
    var situation by rememberSaveable { mutableStateOf("") }
    var landmark by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(false) }
    fun open(uri:String){ try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(uri)))}catch(_:Exception){Toast.makeText(context,"No compatible app",Toast.LENGTH_SHORT).show()} }
    Eyebrow(t("OFFLINE-FIRST HELP CARD", "ऑफलाइन-फर्स्ट सहायता कार्ड"))
    Heading(t("Help Now", "अभी सहायता"))
    Body(t("A calm three-step card for floods, severe weather and crowded gatherings. It points to official help; it is not an emergency service.", "बाढ़, गंभीर मौसम और भीड़ वाले कार्यक्रमों के लिए शांत तीन-चरण कार्ड। यह आधिकारिक सहायता दिखाता है; स्वयं आपात सेवा नहीं है।"))
    Button(onClick={open("tel:112")},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFB9472F)),modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Icon(Icons.Rounded.Call,null);Spacer(Modifier.width(8.dp));Text(t("Call 112","112 पर कॉल करें"))}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..3).forEach{n->FilterChip(selected=step==n,onClick={step=n},label={Text(n.toString())},modifier=Modifier.weight(1f))}}
    when(step){
        1 -> SurfaceCard {
            SectionLabel(t("Where are you?", "आप कहाँ हैं?"))
            Body(t("Choose the situation and add your state/district. GPS stays in memory until you clear or close the app.", "स्थिति चुनें और राज्य/जिला लिखें। GPS क्लियर करने या ऐप बंद होने तक मेमोरी में रहता है।"))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Flood","Cyclone","Crowd").forEach{h->FilterChip(selected=hazard==h,onClick={hazard=h},label={Text(t(h,when(h){"Flood"->"बाढ़";"Cyclone"->"चक्रवात";else->"भीड़"}))},modifier=Modifier.weight(1f))}}
            OutlinedTextField(region,{region=it.take(100)},label={Text(t("State and district","राज्य और जिला"))},placeholder={Text(t("Example: Haryana, Ambala","उदाहरण: हरियाणा, अंबाला"))},modifier=Modifier.fillMaxWidth())
            if(geo!=null){
                Text(String.format(Locale.US,"%.5f, %.5f",geo.latitude,geo.longitude),fontFamily=FontFamily.Monospace,fontSize=18.sp)
                Body(t("Accuracy about ${geo.accuracy?.toInt()?.let{"$it m"}?:"unknown"}. Add a landmark if the reading is rough.","सटीकता लगभग ${geo.accuracy?.toInt()?.let{"$it मी"}?:"अज्ञात"}। रीडिंग अस्पष्ट हो तो पहचान-चिह्न लिखें।"))
                TextButton(onClick=clear){Text(t("Clear location","लोकेशन हटाएँ"))}
            } else Button(onClick=request,enabled=!locating,modifier=Modifier.fillMaxWidth()){Text(if(locating)t("Finding location…","लोकेशन खोज रहे हैं…") else t("Get my location","मेरी लोकेशन लें"))}
            if(error!=null)Body(error)
            Primary(t("Next · official help","अगला · आधिकारिक सहायता")){step=2}
        }
        2 -> {
            SurfaceCard {
                SectionLabel(t("Official help", "आधिकारिक सहायता"))
                Body(t("112 is India's integrated emergency number. Availability and other numbers can vary; verify district information on official sites.", "112 भारत का एकीकृत आपात नंबर है। उपलब्धता और अन्य नंबर बदल सकते हैं; जिला जानकारी आधिकारिक साइट पर जाँचें।"))
                OutlinedButton(onClick={open("tel:112")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.Call,null);Spacer(Modifier.width(8.dp));Text("112 · Police / Fire / Health")}
                OutlinedButton(onClick={open("https://sachet.ndma.gov.in/")},modifier=Modifier.fillMaxWidth()){Text(t("Open NDMA SACHET alerts","NDMA SACHET अलर्ट खोलें"))}
                OutlinedButton(onClick={open("https://112.gov.in/")},modifier=Modifier.fillMaxWidth()){Text(t("Open official 112 India site","आधिकारिक 112 India साइट खोलें"))}
                Body(t("MelaReady is not connected to government dispatch. Opening the dialer does not confirm that a call connected.", "MelaReady सरकारी डिस्पैच से जुड़ा नहीं है। डायलर खुलना कॉल जुड़ने की पुष्टि नहीं है।"))
            }
            SurfaceCard {
                SectionLabel(t("What to do", "क्या करें"))
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Prepare","Warning").forEach{p->FilterChip(selected=phase==p,onClick={phase=p},label={Text(t(p,if(p=="Prepare")"तैयारी" else "चेतावनी"))},modifier=Modifier.weight(1f))}}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Happening now","After").forEach{p->FilterChip(selected=phase==p,onClick={phase=p},label={Text(t(p,if(p=="After")"बाद में" else "अभी"))},modifier=Modifier.weight(1f))}}
                }
                val guidance=when(hazard to phase){
                    "Flood" to "Prepare" -> t("Identify higher ground, keep medicines and power ready, and agree on a family meeting point.","ऊँचा स्थान पहचानें, दवा और पावर तैयार रखें, परिवार का मिलन स्थान तय करें।")
                    "Flood" to "Happening now" -> t("Follow local authority instructions. Avoid moving water and unknown flooded roads.","स्थानीय प्राधिकरण के निर्देश मानें। बहते पानी और अनजान जलभराव वाले रास्तों से बचें।")
                    "Cyclone" to "Happening now" -> t("Stay in a strong indoor area away from windows and continue following official updates.","मजबूत अंदरूनी स्थान में खिड़कियों से दूर रहें और आधिकारिक अपडेट देखते रहें।")
                    "Crowd" to "Happening now" -> t("Do not push. Follow trained staff directions and move toward a less dense edge when possible.","धक्का न दें। प्रशिक्षित स्टाफ के निर्देश मानें और संभव हो तो कम भीड़ वाले किनारे जाएँ।")
                    else -> t("Follow current instructions from your district authority and trusted on-ground staff.","अपने जिला प्राधिकरण और विश्वसनीय स्थल-स्टाफ के वर्तमान निर्देश मानें।")
                }
                Body(guidance)
                Tag(t("GENERAL GUIDANCE · VERIFY LOCALLY","सामान्य मार्गदर्शन · स्थानीय पुष्टि करें"),Color(0xFFFFE7D2),Color(0xFF914A17))
            }
            Primary(t("Next · build message","अगला · संदेश बनाएँ")){step=3}
        }
        else -> {
            SurfaceCard {
                SectionLabel(t("Build one clear message", "एक स्पष्ट संदेश बनाएँ"))
                Body(t("Nothing is sent automatically. You choose the recipient and press send.", "कुछ अपने-आप नहीं भेजा जाता। प्राप्तकर्ता चुनकर आप स्वयं भेजते हैं।"))
                OutlinedTextField(people,{people=it.filter(Char::isDigit).take(3)},label={Text(t("People needing help","सहायता चाहिए लोगों की संख्या"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(situation,{situation=it.take(120)},label={Text(t("What is happening?","क्या हो रहा है?"))},modifier=Modifier.fillMaxWidth())
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(priority,{priority=it});Text(t("Priority need: child, elderly, pregnant, mobility or medicine","प्राथमिक जरूरत: बच्चा, बुजुर्ग, गर्भवती, चलने या दवा की जरूरत"),fontSize=13.sp,lineHeight=18.sp)}
                OutlinedTextField(landmark,{landmark=it.take(100)},label={Text(t("Nearest landmark","पास का पहचान-चिह्न"))},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(phone,{phone=it.filter{c->c.isDigit()||c=='+'}.take(13)},label={Text(t("Call-back number","वापस कॉल का नंबर"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(note,{note=it.take(160)},label={Text(t("Anything else (optional)","अन्य जानकारी (वैकल्पिक)"))},modifier=Modifier.fillMaxWidth())
                val details=listOf(people.isNotBlank(),situation.isNotBlank(),landmark.isNotBlank(),phone.length>=10,geo!=null).count{it}
                LinearProgressIndicator(progress={details/5f},modifier=Modifier.fillMaxWidth(),color=Teal,trackColor=Mint)
                Body(t("$details of 5 key details. Location can be replaced by a precise landmark.","5 में से $details जरूरी जानकारी। लोकेशन के बदले स्पष्ट पहचान-चिह्न दिया जा सकता है।"))
                val loc=geo?.let{String.format(Locale.US,"%.5f, %.5f (about %s m) https://maps.google.com/?q=%.5f,%.5f",it.latitude,it.longitude,it.accuracy?.toInt()?.toString()?:"?",it.latitude,it.longitude)}?:"not available"
                val msg="HELP NEEDED ($hazard)\nArea: ${region.ifBlank{"?"}}\nPeople: ${people.ifBlank{"?"}}\nSituation: ${situation.ifBlank{"?"}}\nPriority need: ${if(priority)"yes" else "none stated"}\nLandmark: ${landmark.ifBlank{"?"}}\nLocation: $loc\nCall back: ${phone.ifBlank{"?"}}"+(if(note.isNotBlank())"\nNote: $note" else "")
                Text(msg,Modifier.fillMaxWidth().background(Mint,RoundedCornerShape(14.dp)).padding(14.dp),fontFamily=FontFamily.Monospace,fontSize=12.sp,lineHeight=18.sp)
                Button(onClick={open("sms:?body=${Uri.encode(msg)}")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.Sms,null);Spacer(Modifier.width(8.dp));Text(t("Open messaging app","मैसेजिंग ऐप खोलें"))}
                OutlinedButton(onClick={ try { val send=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,msg) }; context.startActivity(Intent.createChooser(send,null)) } catch(_:Exception) { Toast.makeText(context,"Sharing unavailable",Toast.LENGTH_SHORT).show() } },modifier=Modifier.fillMaxWidth()){Text(t("Share message","संदेश साझा करें"))}
            }
            Body(t("Use the recipient's official instructions. A prepared message is not proof that help was requested, received or assigned.","प्राप्तकर्ता के आधिकारिक निर्देश अपनाएँ। तैयार संदेश सहायता माँगे, प्राप्त या सौंपे जाने का प्रमाण नहीं है।"))
        }
    }
}

@Composable private fun Checklist(w: Workspace, model: AppState) {
    Eyebrow(t("BEFORE THE DAY", "कार्यक्रम से पहले")); Heading(t("The essentials,\nin one place.", "ज़रूरी तैयारी,\nएक ही जगह।"))
    Body(t("Personal planning checklist. Ticking an item does not verify a permit or grant approval.", "आपकी तैयारी की सूची। चिह्न लगाने से परमिट सत्यापित या अनुमति प्राप्त नहीं होती।"))
    val items = listOf("Confirm the venue plan with your lead" to "लीड के साथ स्थान की योजना तय करें", "Assign zones and communication roles" to "क्षेत्र और संचार की भूमिकाएँ बाँटें", "Review the venue's official response plan" to "स्थान की आधिकारिक सहायता योजना देखें", "Verify local requirements with the authority" to "स्थानीय प्राधिकरण से आवश्यकताएँ सत्यापित करें")
    SurfaceCard {
        items.forEachIndexed { index, item -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = index in w.checks, onCheckedChange = { model.check(index) }); Text(t(item.first, item.second), Modifier.weight(1f), lineHeight = 23.sp) } }
    }
    SectionLabel(t("Official information", "आधिकारिक जानकारी"))
    ExternalLink("NDMA SACHET", "https://sachet.ndma.gov.in/", t("Open the official alert portal", "आधिकारिक अलर्ट पोर्टल खोलें"))
    ExternalLink(t("Haryana Police", "हरियाणा पुलिस"), "https://haryanapolice.gov.in/", t("Check official local information", "आधिकारिक स्थानीय जानकारी देखें"))
    Body(t("External websites; no live alert feed or automatic permit lookup is integrated.", "बाहरी वेबसाइटें; लाइव अलर्ट या अपने-आप परमिट खोजने की सुविधा नहीं जुड़ी है।"))
}

@Composable private fun DrillLog(w: Workspace, add: () -> Unit, advance: (String) -> Unit) {
    Eyebrow(t("NOTICE → CONFIRM → FOLLOW UP", "देखें → पुष्टि करें → आगे बढ़ें")); Heading(t("Close the loop.", "हर सूचना का\nफॉलो-अप।"))
    Body(t("Local practice log. Status changes simulate a workflow and do not contact a team or emergency service.", "स्थानीय अभ्यास लॉग। स्थिति बदलना केवल अभ्यास है; इससे टीम या आपात सेवा से संपर्क नहीं होता।"))
    if (w.event == null) { Empty(t("Create an event to start a drill log.", "अभ्यास लॉग के लिए कार्यक्रम बनाएँ।")); return }
    Primary(t("Add a drill note", "अभ्यास नोट जोड़ें"), add)
    if (w.incidents.isEmpty()) Empty(t("No notes yet. Capture an observation during your rehearsal.", "अभी कोई नोट नहीं। अभ्यास के दौरान अवलोकन दर्ज करें।"))
    w.incidents.reversed().forEach { i -> SurfaceCard {
        Tag(t(i.status.name.replace('_', ' '), when(i.status) { IncidentStatus.SAVED -> "सेव हुआ"; IncidentStatus.ACKNOWLEDGED -> "प्राप्ति की पुष्टि"; IncidentStatus.ASSIGNED -> "काम सौंपा"; IncidentStatus.RESOLVED -> "हल हुआ" }))
        Text(i.note, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
        Body(i.zone + " · " + SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(i.created)))
        if (i.status != IncidentStatus.RESOLVED) OutlinedButton(onClick = { advance(i.id) }, modifier = Modifier.fillMaxWidth()) {
            Text(when(i.status) { IncidentStatus.SAVED -> t("Simulate acknowledgement", "प्राप्ति का अभ्यास"); IncidentStatus.ACKNOWLEDGED -> t("Simulate assignment", "काम सौंपने का अभ्यास"); else -> t("Simulate resolution", "समाधान का अभ्यास") })
        }
    } }
}

@Composable private fun SurfaceCard(color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = color, border = if (color == Color.White) androidx.compose.foundation.BorderStroke(1.dp, Line) else null) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable private fun Eyebrow(text: String) { Text(text, color = Teal, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.5.sp, lineHeight = 17.sp) }
@Composable private fun Heading(text: String) { Text(text, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, color = Ink) }
@Composable private fun Body(text: String) { Text(text, color = Muted, fontSize = 14.sp, lineHeight = 22.sp) }
@Composable private fun SectionLabel(text: String, modifier: Modifier = Modifier) { Text(text, modifier, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
@Composable private fun Tag(text: String, background: Color = Mint, foreground: Color = Teal) { Text(text, Modifier.background(background, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp), color = foreground, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp) }
@Composable private fun MapLabel(text: String) { Text(text, Modifier.background(Color.White.copy(alpha = .9f), RoundedCornerShape(8.dp)).padding(8.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 9.sp) }
@Composable private fun Stat(value: String, label: String, modifier: Modifier) { Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(value, color = Paper, fontSize = 27.sp, fontWeight = FontWeight.SemiBold); Text(label, color = Color(0xFFD6E4D8), fontSize = 12.sp, lineHeight = 18.sp) } }
@Composable private fun Empty(text: String) { SurfaceCard { Icon(Icons.Rounded.WbSunny, null, tint = Teal); Body(text) } }
@Composable private fun Primary(label: String, action: () -> Unit) { Button(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text(label) } }
@Composable private fun ActionCard(number: String, title: String, subtitle: String, icon: ImageVector, action: () -> Unit) {
    Surface(onClick = action, shape = RoundedCornerShape(24.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { Icon(icon, null, tint = Teal, modifier = Modifier.size(23.dp)); Text(number, color = Muted, fontSize = 9.sp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Body(subtitle) }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Teal, modifier = Modifier.size(18.dp))
        }
    }
}
@Composable private fun ExternalLink(title: String, url: String, subtitle: String) {
    val context = LocalContext.current
    ActionCard("↗", title, subtitle, Icons.AutoMirrored.Rounded.OpenInNew) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show() }
    }
}
@Composable private fun EntryDialog(title: String, labels: List<String>, numberLast: Boolean, note: String, dismiss: () -> Unit, save: (String, String, String) -> Boolean) {
    var a by rememberSaveable { mutableStateOf("") }; var b by rememberSaveable { mutableStateOf("") }; var c by rememberSaveable { mutableStateOf("") }; var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Body(note)
            OutlinedTextField(a, { a = it.take(240); invalid = false }, label = { Text(labels[0]) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            OutlinedTextField(b, { b = it.take(120); invalid = false }, label = { Text(labels[1]) }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            if (labels.size == 3) OutlinedTextField(c, { c = it.take(60); invalid = false }, label = { Text(labels[2]) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = if (numberLast) KeyboardType.Number else KeyboardType.Text), maxLines = 2)
            if (invalid) Text(t("Check the fields and try again. Event count must be 1–1,000,000. Storage errors appear on the main screen.", "जानकारी जाँचकर दोबारा कोशिश करें। संख्या 1–10,00,000 हो। स्टोरेज की समस्या मुख्य स्क्रीन पर दिखेगी।"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = { if (save(a, b, c)) dismiss() else invalid = true }) { Text(t("Save", "सेव करें")) } }, dismissButton = { TextButton(onClick = dismiss) { Text(t("Cancel", "रद्द करें")) } })
}
@Composable private fun ConfirmDialog(title: String, message: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Body(message) }, confirmButton = { TextButton(onClick = confirm) { Text(t("Delete", "हटाएँ")) } }, dismissButton = { TextButton(onClick = dismiss) { Text(t("Cancel", "रद्द करें")) } })
}
