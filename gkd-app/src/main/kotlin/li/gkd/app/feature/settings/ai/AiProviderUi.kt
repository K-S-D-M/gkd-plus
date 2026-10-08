package li.gkd.app.feature.settings.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.util.TimeUtils.throttle

/** 分组小标题 + 圆角卡片：AI 设置相关页面统一用这个壳。 */
@Composable
fun AiSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            content = content,
        )
    }
}

@Composable
fun AiRowDivider(hasLeading: Boolean = true) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (hasLeading) 16.dp else 0.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 行首圆角图标底座。 */
@Composable
fun AiRowIcon(
    imageVector: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .padding(end = 14.dp)
            .size(34.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        GkIcon(
            imageVector = imageVector,
            modifier = Modifier.size(19.dp),
            tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 能力 / 状态小胶囊。 */
@Composable
fun AiTagChip(
    text: String,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (emphasized) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(
                if (emphasized) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun AiHint(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** 通用可点击行：标题 + 摘要 + 右侧操作区。 */
@Composable
fun AiBasicRow(
    title: String,
    summary: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (onClick != null) it.clickable(onClick = throttle(fn = onClick)) else it
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        startAction?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        endActions?.invoke()
    }
}

/** 右侧显示当前值的单选行，点击后由调用方弹出选择框。 */
@Composable
fun AiPickerRow(
    title: String,
    value: String,
    summary: String? = null,
    onClick: () -> Unit,
) {
    AiBasicRow(
        title = title,
        summary = summary,
        onClick = onClick,
        endActions = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
    )
}

/** 128000 -> 128K，1048576 -> 1M；0 表示未记录。 */
fun formatTokens(count: Int): String = when {
    count <= 0 -> "未知"
    count % 1_000_000 == 0 -> "${count / 1_000_000}M"
    count >= 1000 -> "${count / 1000}K"
    else -> count.toString()
}
