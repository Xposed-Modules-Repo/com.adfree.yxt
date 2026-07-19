package com.adfree.yxt

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.autoClear(this)
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 ->
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) { SettingsScreen(this) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(activity: ComponentActivity) {
    val ctx = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val logText = remember(refreshKey) { Store.read(ctx) }
    val count = remember(refreshKey) { Store.count(ctx) }
    var policy by remember { mutableStateOf(Store.getPolicy(ctx)) }
    var hidden by remember { mutableStateOf(isIconHidden(activity)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("乐校通去广告", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "模块运行中 · 已记录 $count 条拦截",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 隐藏桌面图标
            Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "隐藏桌面图标",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = hidden, onCheckedChange = {
                    hidden = it
                    setIconHidden(activity, it)
                    Toast.makeText(
                        ctx,
                        if (it) "已隐藏,可从 LSPosed 打开本界面" else "已显示桌面图标",
                        Toast.LENGTH_SHORT
                    ).show()
                })
            }
            Text(
                "隐藏后仍可从 LSPosed 管理器 → 本模块 → 打开 进入本界面",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(14.dp))
            Text("自动清理日志", style = MaterialTheme.typography.titleMedium)
            val opts = listOf("off" to "关闭", "daily" to "每天", "weekly" to "每周")
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                opts.forEachIndexed { i, (k, label) ->
                    SegmentedButton(
                        selected = policy == k,
                        onClick = { policy = k; Store.setPolicy(ctx, k) },
                        shape = SegmentedButtonDefaults.itemShape(i, opts.size)
                    ) { Text(label) }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { Store.clear(ctx); refreshKey++ }, modifier = Modifier.weight(1f)) {
                    Text("清空")
                }
                Button(onClick = { export(ctx) }, modifier = Modifier.weight(1f)) { Text("导出txt") }
                Button(onClick = { refreshKey++ }, modifier = Modifier.weight(1f)) { Text("刷新") }
            }

            Spacer(Modifier.height(8.dp))
            Text("拦截记录", style = MaterialTheme.typography.titleMedium)
            Card(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 6.dp)
            ) {
                Text(
                    if (logText.isEmpty()) "(暂无记录)" else Store.legend() + logText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
            Text(
                "© 2026 github@zzdwymk · Powered by zzdwymk",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

private fun export(ctx: Context) {
    runCatching {
        val log = Store.read(ctx)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val out = File(ctx.getExternalFilesDir(null), "yxt_adfree_log_$ts.txt")
        out.writeText(Store.legend() + if (log.isEmpty()) "(暂无记录)" else log)
        Toast.makeText(ctx, "已导出:${out.absolutePath}", Toast.LENGTH_LONG).show()
    }.onFailure {
        Toast.makeText(ctx, "导出失败:${it.message}", Toast.LENGTH_SHORT).show()
    }
}

private const val LAUNCHER_ALIAS = "com.adfree.yxt.LauncherAlias"

private fun isIconHidden(a: ComponentActivity): Boolean = runCatching {
    a.packageManager.getComponentEnabledSetting(ComponentName(a, LAUNCHER_ALIAS)) ==
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}.getOrDefault(false)

private fun setIconHidden(a: ComponentActivity, hidden: Boolean) {
    runCatching {
        val state = if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        a.packageManager.setComponentEnabledSetting(
            ComponentName(a, LAUNCHER_ALIAS), state, PackageManager.DONT_KILL_APP
        )
    }
}
