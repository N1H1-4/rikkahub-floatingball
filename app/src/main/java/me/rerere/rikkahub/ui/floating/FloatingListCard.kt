package me.rerere.rikkahub.ui.floating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

/** 悬浮窗里弹出的列表种类 */
enum class FloatingListMode { History, Assistants }

private val TimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

/**
 * 悬浮球旁边的浮层列表：历史会话 / 切换助手。
 *
 * 选中会话直接在悬浮面板里打开；选中助手会先把它设为当前助手，再开一个新的悬浮对话。
 */
@Composable
fun FloatingListCard(
    mode: FloatingListMode,
    onPickConversation: (Uuid) -> Unit,
    onPickAssistant: (Uuid) -> Unit,
    onDismiss: () -> Unit,
) {
    val settingsStore = koinInject<SettingsStore>()
    val conversationRepository = koinInject<ConversationRepository>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    when (mode) {
        FloatingListMode.History -> {
            val conversations by conversationRepository
                .getConversationsOfAssistant(settings.assistantId)
                .collectAsStateWithLifecycle(initialValue = emptyList())
            // LazyListScope 的 lambda 不是 @Composable，stringResource 要提到外面算好
            val emptyText = stringResource(R.string.floating_list_empty)
            ListSurface(title = stringResource(R.string.floating_list_history), onDismiss = onDismiss) {
                if (conversations.isEmpty()) {
                    emptyHint(emptyText)
                } else {
                    items(conversations, key = { it.id.toString() }) { conversation ->
                        ListRow(
                            title = conversation.title.ifBlank { stringResource(R.string.floating_list_new_chat) },
                            subtitle = TimeFormat.format(Date(conversation.updateAt.toEpochMilli())),
                            onClick = { onPickConversation(conversation.id) },
                        )
                    }
                }
            }
        }

        FloatingListMode.Assistants -> {
            val currentId = settings.assistantId
            ListSurface(title = stringResource(R.string.floating_list_assistants), onDismiss = onDismiss) {
                items(settings.assistants, key = { it.id.toString() }) { assistant ->
                    ListRow(
                        title = assistant.name.ifBlank { stringResource(R.string.floating_list_untitled) },
                        subtitle = if (assistant.id == currentId) stringResource(R.string.floating_list_current) else "",
                        selected = assistant.id == currentId,
                        onClick = { onPickAssistant(assistant.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListSurface(
    title: String,
    onDismiss: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 10.dp,
        modifier = Modifier
            .padding(6.dp)
            .width(300.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.floating_ball_close), modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                content()
            }
        }
    }
}

private fun LazyListScope.emptyHint(text: String) {
    item {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        )
    }
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
