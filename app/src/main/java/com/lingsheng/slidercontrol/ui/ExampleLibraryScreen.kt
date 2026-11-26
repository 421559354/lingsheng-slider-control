package com.lingsheng.slidercontrol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingsheng.slidercontrol.examples.LoadedExample

@Composable
fun ExampleLibraryScreen(
    examples: List<LoadedExample>,
    categories: List<String>,
    query: String,
    selectedCategory: String?,
    connected: Boolean,
    actionMessage: String,
    onSearch: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onSelect: (LoadedExample) -> Unit,
    onBack: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp).padding(bottom = 82.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(12.dp)) { Text("返回") }
                Spacer(Modifier.weight(1f))
                Text("案例 / 配方", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${examples.size} 项", color = MaterialTheme.colorScheme.secondary)
            }
            OutlinedTextField(
                value = query,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索案例名称、用途或来源") },
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelect(null) },
                    label = { Text("全部") },
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onCategorySelect(if (selectedCategory == category) null else category) },
                        label = { Text(category) },
                    )
                }
            }
            if (actionMessage.isNotBlank()) {
                Text(actionMessage, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(examples, key = { it.entry.id }) { example ->
                    val runnable = example.parseResult.recipe != null
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(example) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(example.entry.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Surface(
                                    color = if (runnable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        when {
                                            !runnable -> "说明"
                                            example.parseResult.notes.isNotEmpty() -> "手动启动"
                                            else -> "可写入"
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (runnable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(example.entry.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${example.entry.category} · ${example.entry.sourcePath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (examples.isEmpty()) item { Text("没有找到匹配的案例。", modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.secondary) }
            }
        }
        Button(
            onClick = onStop,
            enabled = connected,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("■   停止", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}
