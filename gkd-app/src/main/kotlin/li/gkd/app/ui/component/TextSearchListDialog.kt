package li.gkd.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import li.gkd.app.util.TimeUtils.throttle

/**
 * 可搜索的列表选择弹窗：选中项用主色文字标记，不显示对勾。
 * [searchPlaceholder] 为空时退化为普通列表。
 */
@Composable
fun TextSearchListDialog(
    onDismiss: () -> Unit,
    textList: List<Pair<String, () -> Unit>>,
    title: String? = null,
    selectedText: String? = null,
    searchPlaceholder: String? = null,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(textList, query) {
        if (query.isEmpty()) textList
        else textList.filter { it.first.contains(query, ignoreCase = true) }
    }
    GkDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (searchPlaceholder != null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text(searchPlaceholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                filtered.forEach { (text, onClick) ->
                    val selected = text == selectedText
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = throttle {
                                onClick()
                                onDismiss()
                            })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
