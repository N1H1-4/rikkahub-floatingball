package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Robot01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.floating.DESKTOP_PET_MAX_SCALE
import me.rerere.rikkahub.ui.floating.DESKTOP_PET_MIN_OPACITY
import me.rerere.rikkahub.ui.floating.DESKTOP_PET_MIN_SCALE
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/**
 * 桌面宠物设置：开关、形象、大小、透明度、贴边与自动隐藏。
 *
 * 所有项都是即时生效的：桌宠窗口订阅同一份设置，改完不用重启。
 */
@Composable
fun SettingDesktopPetPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    // 从系统授权页回来时刷新
    LifecycleResumeEffect(Unit) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        onPauseOrDispose {}
    }

    fun update(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_preferences_desktop_pet)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup {
                    switchItem(
                        checked = displaySetting.enableDesktopPet,
                        onCheckedChange = {
                            if (it && !canDrawOverlays) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        "package:${context.packageName}".toUri()
                                    )
                                )
                            }
                            update(displaySetting.copy(enableDesktopPet = it))
                        },
                        headlineContent = { Text(stringResource(R.string.setting_desktop_pet_enable)) },
                        supportingContent = { Text(stringResource(R.string.setting_desktop_pet_enable_desc)) },
                    )
                    if (displaySetting.enableDesktopPet && !canDrawOverlays) {
                        item(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        "package:${context.packageName}".toUri()
                                    )
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification_overlay_permission)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_preferences_notification_overlay_permission_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                        )
                    }
                }
            }

            if (!displaySetting.enableDesktopPet) return@LazyColumn

            item {
                CardGroup(title = { Text(stringResource(R.string.setting_desktop_pet_group_appearance)) }) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_desktop_pet_avatar)) },
                        supportingContent = { Text(stringResource(R.string.setting_desktop_pet_avatar_desc)) },
                        trailingContent = {
                            // 和助手头像共用同一个选择器：相册裁剪 / Emoji / 图片 URL / 重置
                            UIAvatar(
                                name = "pet",
                                value = displaySetting.desktopPetAvatar,
                                onUpdate = { avatar ->
                                    update(displaySetting.copy(desktopPetAvatar = avatar))
                                },
                                modifier = Modifier.size(64.dp),
                            )
                        },
                    )
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_desktop_pet_size)) },
                        trailingContent = { CardGroupValue("${(displaySetting.desktopPetSize * 100).roundToInt()}%") },
                    ) {
                        Slider(
                            value = displaySetting.desktopPetSize,
                            onValueChange = { update(displaySetting.copy(desktopPetSize = it)) },
                            valueRange = DESKTOP_PET_MIN_SCALE..DESKTOP_PET_MAX_SCALE,
                            steps = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_desktop_pet_opacity)) },
                        trailingContent = { CardGroupValue("${(displaySetting.desktopPetOpacity * 100).roundToInt()}%") },
                    ) {
                        Slider(
                            value = displaySetting.desktopPetOpacity,
                            onValueChange = { update(displaySetting.copy(desktopPetOpacity = it)) },
                            valueRange = DESKTOP_PET_MIN_OPACITY..1f,
                            steps = 11,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                CardGroup(title = { Text(stringResource(R.string.setting_desktop_pet_group_docking)) }) {
                    switchItem(
                        checked = displaySetting.desktopPetDockToEdge,
                        onCheckedChange = {
                            update(displaySetting.copy(desktopPetDockToEdge = it))
                        },
                        headlineContent = { Text(stringResource(R.string.setting_desktop_pet_dock)) },
                        supportingContent = { Text(stringResource(R.string.setting_desktop_pet_dock_desc)) },
                    )
                    switchItem(
                        checked = displaySetting.desktopPetAutoHide,
                        enabled = displaySetting.desktopPetDockToEdge,
                        onCheckedChange = {
                            update(displaySetting.copy(desktopPetAutoHide = it))
                        },
                        headlineContent = { Text(stringResource(R.string.setting_desktop_pet_auto_hide)) },
                        supportingContent = { Text(stringResource(R.string.setting_desktop_pet_auto_hide_desc)) },
                    )
                }
            }
        }
    }
}
