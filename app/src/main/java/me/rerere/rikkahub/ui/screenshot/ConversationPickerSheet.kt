package me.rerere.rikkahub.ui.screenshot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.compose.koinInject

/**
 * 选一个已有会话把截图发过去。
 *
 * 只列当前助手的会话，和聊天里"当前助手"的概念保持一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationPickerSheet(
    onDismiss: () -> Unit,
    onPick: (Conversation) -> Unit,
) {
    // 这个页面不在 RouteActivity 的组合里，没有 LocalSettings，直接读 SettingsStore
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val repository = koinInject<ConversationRepository>()
    val assistantId = settings.getCurrentAssistant().id

    val conversationsFlow = remember(assistantId) {
        repository.getConversationsOfAssistant(assistantId)
    }
    val conversations by conversationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column {
            Text(
                text = stringResource(R.string.screenshot_pick_conversation),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            if (conversations.isEmpty()) {
                Text(
                    text = stringResource(R.string.screenshot_pick_conversation_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(conversations, key = { it.id.toString() }) { conversation ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = conversation.title.ifBlank {
                                        stringResource(R.string.chat_page_new_chat)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.clickable { onPick(conversation) },
                        )
                    }
                }
            }
        }
    }
}
