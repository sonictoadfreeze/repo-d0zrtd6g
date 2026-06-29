package com.inkvpn.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkvpn.app.core.Format
import com.inkvpn.app.core.ServerConfig
import com.inkvpn.app.core.Subscription
import com.inkvpn.app.ui.theme.InkBackground
import com.inkvpn.app.ui.theme.LocalAccent
import com.inkvpn.app.ui.theme.InkDanger
import com.inkvpn.app.ui.theme.InkPrimary
import com.inkvpn.app.ui.theme.InkSecondary
import com.inkvpn.app.ui.theme.InkSubtext
import com.inkvpn.app.ui.theme.InkSuccess
import com.inkvpn.app.ui.theme.InkSurface
import com.inkvpn.app.ui.theme.InkSurfaceVariant
import com.inkvpn.app.ui.theme.InkText
import com.inkvpn.app.vpn.VpnStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val title: String) { HOME("Главная"), SERVERS("Серверы"), SUBS("Подписка"), SETTINGS("Настройки") }

@Composable
fun InkVpnApp(vm: MainViewModel, activity: MainActivity) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(2200); showSplash = false }

    val subs by vm.subscriptions.collectAsState()
    val deepLink by activity.pendingDeepLink
    val status by vm.status.collectAsState()

    var autoConnectTried by remember { mutableStateOf(false) }
    LaunchedEffect(subs, showSplash) {
        if (showSplash || autoConnectTried || subs.isEmpty()) return@LaunchedEffect
        autoConnectTried = true
        if (vm.settings.value.autoConnect && status == VpnStatus.DISCONNECTED) {
            vm.selectedServer()?.let { activity.requestConnect(it) }
        }
    }

    var tab by remember { mutableStateOf(Tab.HOME) }
    var editTarget by remember { mutableStateOf<Subscription?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var prefillUrl by remember { mutableStateOf("") }

    LaunchedEffect(deepLink) {
        val dl = deepLink ?: return@LaunchedEffect
        val url = vm.handleDeepLink(dl)
        if (url != null) { prefillUrl = url; showAdd = true; tab = Tab.SUBS }
        activity.pendingDeepLink.value = null
    }

    Box(Modifier.fillMaxSize().background(InkBackground)) {
        if (showSplash) {
            SplashScreen()
        } else if (subs.isEmpty()) {
            OnboardingScreen(onAdd = { prefillUrl = ""; showAdd = true })
        } else {
            Scaffold(
                containerColor = InkBackground,
                bottomBar = { BottomNav(tab) { tab = it } }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally { fullWidth -> direction * fullWidth / 4 } + fadeIn(tween(250)))
                                .togetherWith(slideOutHorizontally { fullWidth -> -direction * fullWidth / 4 } + fadeOut(tween(200)))
                        },
                        label = "tab_transition"
                    ) { animatedTab ->
                        when (animatedTab) {
                            Tab.HOME -> HomeScreen(vm, activity)
                            Tab.SERVERS -> ServersScreen(vm, activity)
                            Tab.SUBS -> SubscriptionScreen(
                                vm,
                                onAdd = { prefillUrl = ""; showAdd = true },
                                onEdit = { editTarget = it }
                            )
                            Tab.SETTINGS -> SettingsScreen(vm, activity)
                        }
                    }
                }
            }
        }

        if (showAdd) {
            AddEditSubscriptionDialog(
                initialName = "",
                initialUrl = prefillUrl,
                loading = vm.loading,
                onDismiss = { showAdd = false },
                onSave = { name, url -> vm.addSubscription(name, url); showAdd = false }
            )
        }
        editTarget?.let { sub ->
            AddEditSubscriptionDialog(
                initialName = sub.name,
                initialUrl = sub.url,
                loading = vm.loading,
                onDismiss = { editTarget = null },
                onSave = { name, url -> vm.addSubscription(name, url, existingId = sub.id); editTarget = null }
            )
        }

        vm.message?.let { msg ->
            LaunchedEffect(msg) { delay(2500); vm.message = null }
            Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp)) {
                GlassCard {
                    Text(msg.text, color = if (msg.isError) InkDanger else InkSuccess, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    val scale by animateFloatAsState(targetValue = 1f, animationSpec = tween(700), label = "splash")
    Box(Modifier.fillMaxSize().background(InkBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            InkLogo(size = 110)
            Spacer(Modifier.height(20.dp))
            Text("InkVPN", color = Color.White, fontSize = (28 * scale).sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Secure. Fast. Invisible.", color = InkSubtext, fontSize = 14.sp)
        }
    }
}

@Composable
fun InkLogo(size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(InkPrimary, InkSecondary))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Power, contentDescription = "InkVPN", tint = Color.White, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    val items = listOf(
        Tab.HOME to Icons.Filled.Home,
        Tab.SERVERS to Icons.Filled.Dns,
        Tab.SUBS to Icons.Filled.Star,
        Tab.SETTINGS to Icons.Filled.Settings,
    )
    val selectedIndex = items.indexOfFirst { it.first == current }.coerceAtLeast(0)

    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
        ) {
            val itemWidth = maxWidth / items.size
            val pillOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "pill_offset"
            )
            Box(
                Modifier
                    .offset(x = pillOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                items.forEach { (t, icon) ->
                    val selected = t == current
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "icon_${t.name}"
                    )
                    val contentAlpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.5f,
                        animationSpec = tween(200),
                        label = "alpha_${t.name}"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelect(t) }
                    ) {
                        Icon(
                            icon,
                            contentDescription = t.title,
                            tint = Color.White,
                            modifier = Modifier
                                .scale(iconScale)
                                .graphicsLayer { alpha = contentAlpha }
                        )
                        AnimatedVisibility(visible = selected) {
                            Text(
                                t.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditSubscriptionDialog(
    initialName: String,
    initialUrl: String,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkSurfaceVariant,
        title = { Text("Подписка", color = InkText) },
        text = {
            Column {
                Text("Имя подписки", color = InkSubtext, fontSize = 12.sp)
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("URL подписки", color = InkSubtext, fontSize = 12.sp)
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    placeholder = { Text("inkvpn://add/https://...", color = InkSubtext) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, url) }, enabled = !loading && url.isNotBlank()) {
                Text(if (loading) "Загрузка..." else "Сохранить", color = InkPrimary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = InkSubtext) } }
    )
}

// ---------------- HOME ----------------

@Composable
fun HomeScreen(vm: MainViewModel, activity: MainActivity) {
    val status by vm.status.collectAsState()
    val server = vm.selectedServer()
    val info = vm.subscriptions.collectAsState().value.firstOrNull()?.info

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(info?.profileTitle ?: "InkVPN", color = InkText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Secure. Fast. Invisible.", color = InkSubtext, fontSize = 12.sp)
            }
            Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White)
        }

        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            PowerButton(status = status) {
                if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) vm.disconnect()
                else server?.let { activity.requestConnect(it) }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(server?.name ?: "Сервер не выбран", color = InkText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (server?.description != null) Text(server.description, color = InkSubtext, fontSize = 12.sp)
                    else if (server != null) Text("${server.displayProtocol} • •••••", color = InkSubtext, fontSize = 12.sp)
                }
                if (server != null) ProtocolBadge(server.displayProtocolFull)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Загрузка", info?.let { Format.bytes(it.download) } ?: "0 B", Modifier.weight(1f))
            StatCard("Отдача", info?.let { Format.bytes(it.upload) } ?: "0 B", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column {
            Text(label, color = InkSubtext, fontSize = 12.sp)
            Text(value, color = InkText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
fun PowerButton(status: VpnStatus, onClick: () -> Unit) {
    val connected = status == VpnStatus.CONNECTED
    val connecting = status == VpnStatus.CONNECTING
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val glowAlpha by animateFloatAsState(
        targetValue = if (connected) 0.25f else 0f,
        animationSpec = tween(600),
        label = "glow"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (connecting) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "connect_scale"
    )

    Box(contentAlignment = Alignment.Center) {
        if (connected) PulseRing(size = 200.dp, color = accent.accent)
        if (glowAlpha > 0f) {
            Box(
                Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(accent.accent.copy(alpha = glowAlpha))
            )
        }
        Box(
            Modifier
                .scale(scaleAnim.value * buttonScale)
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    if (connected) Brush.radialGradient(listOf(accent.accent, accent.secondary))
                    else Brush.radialGradient(listOf(InkSurfaceVariant, InkSurfaceVariant))
                )
                .clickable {
                    scope.launch {
                        scaleAnim.animateTo(0.88f, tween(80))
                        scaleAnim.animateTo(1.05f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        scaleAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                    }
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val iconRotation by animateFloatAsState(
                    targetValue = if (connecting) 360f else 0f,
                    animationSpec = tween(if (connecting) 1000 else 300),
                    label = "icon_rotate"
                )
                Icon(
                    Icons.Filled.Power,
                    contentDescription = "power",
                    tint = if (connected) Color.White else InkSubtext,
                    modifier = Modifier.size(56.dp).graphicsLayer { rotationZ = iconRotation }
                )
                Text(
                    when {
                        connected -> "Подключено"
                        connecting -> "Подключение..."
                        else -> "Нажмите"
                    },
                    color = if (connected) Color.White else InkSubtext, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ---------------- SERVERS ----------------

@Composable
fun ServersScreen(vm: MainViewModel, activity: MainActivity) {
    val subs by vm.subscriptions.collectAsState()
    val selectedId by vm.selectedServerId.collectAsState()
    val activeId by vm.activeServerId.collectAsState()
    val servers = subs.flatMap { it.servers }

    val pings by vm.pings.collectAsState()

    LaunchedEffect(servers) { vm.pingAllServers(servers) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Серверы", color = InkText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(servers) { s ->
                ServerRow(
                    server = s,
                    selected = s.id == (selectedId ?: servers.firstOrNull()?.id),
                    active = s.id == activeId,
                    pingMs = pings[s.id],
                    onClick = { vm.selectServer(s.id) },
                    onConnect = { vm.selectServer(s.id); activity.requestConnect(s) }
                )
            }
        }
    }
}

@Composable
fun ServerRow(server: ServerConfig, selected: Boolean, active: Boolean, pingMs: Int? = null, onClick: () -> Unit, onConnect: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(if (active) InkSuccess else if (selected) InkPrimary else InkSubtext.copy(alpha = 0.4f))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, color = InkText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("•••••:${server.port}", color = InkSubtext, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PingBadge(pingMs)
                    ProtocolBadge(if (server.supportedByCore) server.displayProtocolFull else "${server.displayProtocolFull}*")
                }
                Spacer(Modifier.height(4.dp))
                Text(if (active) "Активен" else "Подключить", color = if (active) InkSuccess else InkSecondary, fontSize = 11.sp, modifier = Modifier.clickable { onConnect() })
            }
        }
    }
}

// ---------------- SUBSCRIPTION ----------------

@Composable
fun SubscriptionScreen(vm: MainViewModel, onAdd: () -> Unit, onEdit: (Subscription) -> Unit) {
    val subs by vm.subscriptions.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Подписка", color = InkText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Row {
                Icon(Icons.Filled.Add, contentDescription = "add", tint = Color.White, modifier = Modifier.clickable { onAdd() })
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(subs) { sub ->
                GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { onEdit(sub) }) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(sub.name, color = InkText, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.Refresh, contentDescription = "refresh", tint = Color.White, modifier = Modifier.size(18.dp).clickable { vm.refresh(sub.id) })
                        }
                        Text("Серверов: ${sub.servers.size}", color = InkSubtext, fontSize = 12.sp)
                        val total = sub.info.total
                        if (total > 0) {
                            val used = sub.info.upload + sub.info.download
                            TrafficBar(used = used, total = total)
                            Text("${Format.bytes(used)} / ${Format.bytes(total)}", color = InkSubtext, fontSize = 11.sp)
                        }
                        if (sub.info.expire > 0) {
                            Text(com.inkvpn.app.core.DateUtils.expireLabel(sub.info.expire), color = InkSubtext, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrafficBar(used: Long, total: Long) {
    val pct = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val color = when {
        pct < 0.7f -> InkSecondary
        pct < 0.9f -> com.inkvpn.app.ui.theme.InkWarning
        else -> InkDanger
    }
    Box(Modifier.fillMaxWidth().height(8.dp).padding(vertical = 2.dp).clip(RoundedCornerShape(50)).background(InkSurfaceVariant)) {
        Box(Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(InkPrimary, color))))
    }
}

// ---------------- ONBOARDING ----------------

@Composable
fun OnboardingScreen(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(InkBackground).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        InkLogo(size = 90)
        Spacer(Modifier.height(24.dp))
        Text("Начни за 30 секунд", color = InkText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(20.dp))
        OnboardStep("1", "Получи ссылку на подписку")
        OnboardStep("2", "Вставь её в InkVPN")
        OnboardStep("3", "Нажми кнопку — и VPN готов")
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(InkPrimary, InkSecondary)))
                .clickable { onAdd() }.padding(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Text("Вставить ключ вручную", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun OnboardStep(num: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(InkPrimary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(num, color = InkPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Text(text, color = InkText, fontSize = 15.sp)
    }
}
