package com.inkvpn.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkvpn.app.core.AppSettings
import com.inkvpn.app.core.DnsChoice
import com.inkvpn.app.core.IpStrategy
import com.inkvpn.app.core.LangChoice
import com.inkvpn.app.core.PerAppMode
import com.inkvpn.app.core.ThemeChoice
import com.inkvpn.app.core.TunnelMode
import com.inkvpn.app.ui.theme.InkBackground
import com.inkvpn.app.ui.theme.InkSubtext
import com.inkvpn.app.ui.theme.InkSurfaceVariant
import com.inkvpn.app.ui.theme.InkText
import com.inkvpn.app.ui.theme.LocalAccent

private enum class SettingsPage { ROOT, CONNECTION, TUNNEL, PERAPP, THEME, LANGUAGE }

@Composable
fun SettingsScreen(vm: MainViewModel, activity: MainActivity) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    val settings by vm.settings.collectAsState()

    BackHandler(enabled = page != SettingsPage.ROOT) { page = SettingsPage.ROOT }

    when (page) {
        SettingsPage.ROOT -> RootSettings(vm, activity, settings) { page = it }
        SettingsPage.CONNECTION -> ConnectionSettings(vm, settings) { page = SettingsPage.ROOT }
        SettingsPage.TUNNEL -> TunnelSettings(vm, settings) { page = SettingsPage.ROOT }
        SettingsPage.PERAPP -> PerAppSettings(vm, settings) { page = SettingsPage.ROOT }
        SettingsPage.THEME -> ThemeSettings(vm, settings) { page = SettingsPage.ROOT }
        SettingsPage.LANGUAGE -> LanguageSettings(vm, settings) { page = SettingsPage.ROOT }
    }
}

// ---------------- ROOT ----------------

@Composable
private fun RootSettings(
    vm: MainViewModel,
    activity: MainActivity,
    s: AppSettings,
    navigate: (SettingsPage) -> Unit,
) {
    val hwid = remember { com.inkvpn.app.core.Hwid.generate(activity) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Настройки", color = InkText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))

        SectionLabel("ПРОКСИ ПО ПРИЛОЖЕНИЯМ")
        SettingsCard {
            NavRow(Icons.Filled.Apps, Color(0xFF7E57C2), "Прокси по приложениям", s.perAppMode.label) { navigate(SettingsPage.PERAPP) }
        }

        SectionLabel("ОФОРМЛЕНИЕ")
        SettingsCard {
            NavRow(Icons.Filled.Palette, Color(0xFF26A69A), "Тема", s.theme.label) { navigate(SettingsPage.THEME) }
            RowDivider()
            NavRow(Icons.Filled.Language, Color(0xFF66BB6A), "Язык", s.language.label) { navigate(SettingsPage.LANGUAGE) }
        }

        SectionLabel("СОЕДИНЕНИЕ")
        SettingsCard {
            NavRow(Icons.Filled.Bolt, Color(0xFFFFB300), "Соединение", "Настроить") { navigate(SettingsPage.CONNECTION) }
        }

        SectionLabel("ТУННЕЛЬ")
        SettingsCard {
            NavRow(Icons.Filled.Layers, Color(0xFF9CCC65), "Туннель", "Настроить") { navigate(SettingsPage.TUNNEL) }
        }

        SectionLabel("УСТРОЙСТВО")
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Text("Идентификатор устройства (HWID)", color = InkText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(com.inkvpn.app.core.Hwid.shortId(hwid), color = LocalAccent.current.secondary, fontSize = 13.sp)
                Text("Генерируется из аппаратных характеристик устройства", color = InkSubtext, fontSize = 11.sp)
            }
            RowDivider()
            Column(Modifier.padding(16.dp)) {
                Text("О приложении", color = InkText, fontWeight = FontWeight.SemiBold)
                Text("InkVPN 1.0.0 • ядро sing-box", color = InkSubtext, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- CONNECTION ----------------

@Composable
private fun ConnectionSettings(vm: MainViewModel, s: AppSettings, onBack: () -> Unit) {
    SubScreen("Соединение", onBack) {
        SettingsCard {
            ToggleRow(Icons.Filled.Bolt, Color(0xFFFFB300), "Автоподключение", "Подключаться при запуске приложения", s.autoConnect) { v -> vm.updateSettings { it.copy(autoConnect = v) } }
            RowDivider()
            ToggleRow(Icons.Filled.PowerSettingsNew, Color(0xFFFFA726), "Автоподключение при загрузке устройства", "Подключаться после включения устройства", s.connectOnBoot) { v -> vm.updateSettings { it.copy(connectOnBoot = v) } }
            RowDivider()
            ToggleRow(Icons.Filled.Shield, Color(0xFFEF5350), "Kill Switch", "Блокировать интернет без туннеля", s.killSwitch) { v -> vm.updateSettings { it.copy(killSwitch = v) } }
            RowDivider()
            ToggleRow(Icons.AutoMirrored.Filled.AltRoute, Color(0xFF66BB6A), "Разрешить LAN подключения", "Исключить локальную сеть из туннеля", s.allowLan) { v -> vm.updateSettings { it.copy(allowLan = v) } }
            RowDivider()
            ToggleRow(Icons.Filled.Wifi, Color(0xFF42A5F5), "Доступ через хотспот", "Привязать SOCKS5/HTTP прокси к 0.0.0.0 для устройств в той же сети", s.hotspot) { v -> vm.updateSettings { it.copy(hotspot = v) } }
            RowDivider()
            ToggleRow(Icons.AutoMirrored.Filled.AltRoute, Color(0xFFFF7043), "LAN через прокси", "Направить локальный трафик через прокси", s.lanThroughProxy) { v -> vm.updateSettings { it.copy(lanThroughProxy = v) } }
        }
    }
}

// ---------------- TUNNEL ----------------

@Composable
private fun TunnelSettings(vm: MainViewModel, s: AppSettings, onBack: () -> Unit) {
    SubScreen("Туннель", onBack) {
        SettingsCard {
            DropdownRow(Icons.Filled.Layers, Color(0xFF26A69A), "Режим туннеля", s.tunnelMode.label, TunnelMode.entries.map { it.label }) { idx -> vm.updateSettings { it.copy(tunnelMode = TunnelMode.entries[idx]) } }
            RowDivider()
            ToggleRow(Icons.AutoMirrored.Filled.AltRoute, Color(0xFF42A5F5), "Профили маршрутизации", "Включить расширенные настройки маршрутизации", s.routingProfiles) { v -> vm.updateSettings { it.copy(routingProfiles = v) } }
            RowDivider()
            ToggleRow(Icons.Filled.Speed, Color(0xFF26C6DA), "Скорость в уведомлении", "Показывать скорость загрузки/отдачи в уведомлении", s.speedInNotification) { v -> vm.updateSettings { it.copy(speedInNotification = v) } }
            RowDivider()
            ToggleRow(Icons.Filled.Bolt, Color(0xFFFFB300), "Держать устройство активным", "Удерживать wakelock во время работы. Нужно на Xiaomi/HyperOS.", s.wakelock) { v -> vm.updateSettings { it.copy(wakelock = v) } }
            RowDivider()
            ToggleRow(Icons.Filled.Tune, Color(0xFF9CCC65), "Фрагментирование", "Обход DPI через фрагментацию пакетов", s.fragment) { v -> vm.updateSettings { it.copy(fragment = v) } }
            RowDivider()
            ToggleRow(Icons.Filled.Cached, Color(0xFF7E57C2), "Мультиплексирование (Mux)", "Объединение соединений в один канал", s.mux) { v -> vm.updateSettings { it.copy(mux = v) } }
            RowDivider()
            DropdownRow(Icons.Filled.Dns, Color(0xFF26A69A), "Тип IP", s.ipStrategy.label, IpStrategy.entries.map { it.label }) { idx -> vm.updateSettings { it.copy(ipStrategy = IpStrategy.entries[idx]) } }
            RowDivider()
            DropdownRow(Icons.Filled.Dns, Color(0xFF5C6BC0), "DNS", s.dns.label, DnsChoice.entries.map { it.label }) { idx -> vm.updateSettings { it.copy(dns = DnsChoice.entries[idx]) } }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                RowHeader(Icons.Filled.Memory, Color(0xFF26A69A), "Память", "Предел ${if (s.unlimitedMemory) "снят" else "${s.memoryLimitMb} MB"}")
                Spacer(Modifier.height(12.dp))
                MemorySelector(s.memoryLimitMb, s.unlimitedMemory) { mb -> vm.updateSettings { it.copy(memoryLimitMb = mb) } }
            }
            RowDivider()
            ToggleRow(Icons.Filled.Memory, Color(0xFF66BB6A), "Снять ограничение", "Для мощных устройств. Больше памяти — стабильнее при высокой нагрузке.", s.unlimitedMemory) { v -> vm.updateSettings { it.copy(unlimitedMemory = v) } }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard {
            ToggleRow(Icons.Filled.VisibilityOff, Color(0xFF7E57C2), "Скрыть значок (proxy-only)", "Запускать локальный HTTP/SOCKS5 прокси без туннеля. Android не покажет значок VPN.", s.proxyOnly) { v -> vm.updateSettings { it.copy(proxyOnly = v) } }
            RowDivider()
            ToggleRow(Icons.Filled.Lock, Color(0xFFEF5350), "SOCKS5 авторизация", "Защищает локальный прокси от приложений, сканирующих порты", s.socks5Auth) { v ->
                vm.updateSettings {
                    if (v && it.socks5User.isBlank())
                        it.copy(socks5Auth = true, socks5User = "inkv_" + randomToken(6), socks5Pass = randomToken(12))
                    else it.copy(socks5Auth = v)
                }
            }
            if (s.socks5Auth) {
                RowDivider()
                Column(Modifier.padding(16.dp)) {
                    Text("Логин: ${s.socks5User}", color = InkSubtext, fontSize = 13.sp)
                    Text("Пароль: ${s.socks5Pass}", color = InkSubtext, fontSize = 13.sp)
                    Text("Локальный порт: 127.0.0.1:${s.localPort}", color = InkSubtext, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- PER-APP ----------------

@Composable
private fun PerAppSettings(vm: MainViewModel, s: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .filter { it.packageName != context.packageName }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .sortedBy { it.second.lowercase() }
    }
    val selected = remember(s.perAppPackages) { s.perAppPackages.toMutableSet() }

    SubScreenScaffold("Прокси по приложениям", onBack) {
        item {
            SettingsCard {
                DropdownRow(Icons.Filled.Apps, Color(0xFF7E57C2), "Режим", s.perAppMode.label, PerAppMode.entries.map { it.label }) { idx -> vm.updateSettings { it.copy(perAppMode = PerAppMode.entries[idx]) } }
            }
            Spacer(Modifier.height(12.dp))
            if (s.perAppMode == PerAppMode.OFF) {
                Text("Включите режим, чтобы выбрать приложения.", color = InkSubtext, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            }
        }
        if (s.perAppMode != PerAppMode.OFF) {
            items(apps) { (pkg, label) ->
                val checked = selected.contains(pkg)
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    SettingsCard {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(label, color = InkText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(pkg, color = InkSubtext, fontSize = 10.sp)
                            }
                            Switch(checked = checked, onCheckedChange = { on ->
                                val next = s.perAppPackages.toMutableList()
                                if (on) { if (!next.contains(pkg)) next.add(pkg) } else next.remove(pkg)
                                vm.updateSettings { it.copy(perAppPackages = next) }
                            }, colors = switchColors())
                        }
                    }
                }
            }
        }
    }
}

// ---------------- THEME / LANGUAGE ----------------

@Composable
private fun ThemeSettings(vm: MainViewModel, s: AppSettings, onBack: () -> Unit) {
    SubScreen("Тема", onBack) {
        SettingsCard {
            ThemeChoice.entries.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                SelectRow(t.label, selected = s.theme == t) { vm.updateSettings { it.copy(theme = t) } }
            }
        }
    }
}

@Composable
private fun LanguageSettings(vm: MainViewModel, s: AppSettings, onBack: () -> Unit) {
    SubScreen("Язык", onBack) {
        SettingsCard {
            LangChoice.entries.forEachIndexed { i, l ->
                if (i > 0) RowDivider()
                SelectRow(l.label, selected = s.language == l) { vm.updateSettings { it.copy(language = l) } }
            }
        }
    }
}

// ---------------- reusable pieces ----------------

@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(InkBackground)) {
        SettingsTopBar(title, onBack)
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SubScreenScaffold(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(InkBackground)) {
        SettingsTopBar(title, onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            content()
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "назад", tint = InkText, modifier = Modifier.size(24.dp).clickable { onBack() })
            Spacer(Modifier.width(12.dp))
            Text(title, color = InkText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Text("Готово", color = LocalAccent.current.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onBack() })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = InkSubtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Column { content() }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun ToggleRow(icon: ImageVector, tint: Color, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = InkText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = InkSubtext, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = switchColors())
    }
}

@Composable
private fun RowHeader(icon: ImageVector, tint: Color, title: String, subtitle: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = InkText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = InkSubtext, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, tint: Color, title: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(12.dp))
        Text(title, color = InkText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = InkSubtext, fontSize = 13.sp)
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = InkSubtext)
    }
}

@Composable
private fun DropdownRow(icon: ImageVector, tint: Color, title: String, value: String, options: List<String>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint)
            Spacer(Modifier.width(12.dp))
            Text(title, color = InkText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(value, color = LocalAccent.current.accent, fontSize = 13.sp)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = InkSubtext)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}

@Composable
private fun SelectRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = InkText, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (selected) Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(LocalAccent.current.accent))
    }
}

@Composable
private fun MemorySelector(current: Int, disabled: Boolean, onSelect: (Int) -> Unit) {
    val options = listOf(40, 60, 80, 100)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { mb ->
            val active = !disabled && mb == current
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (active) LocalAccent.current.accent else InkSurfaceVariant)
                    .clickable(enabled = !disabled) { onSelect(mb) },
                contentAlignment = Alignment.Center
            ) {
                Text("$mb MB", color = if (active) Color.Black else if (disabled) InkSubtext else InkText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = LocalAccent.current.primary,
    uncheckedThumbColor = InkSubtext,
    uncheckedTrackColor = InkSurfaceVariant,
)

private fun randomToken(len: Int): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..len).map { chars.random() }.joinToString("")
}
