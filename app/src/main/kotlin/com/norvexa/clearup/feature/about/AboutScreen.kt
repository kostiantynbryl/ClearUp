package com.norvexa.clearup.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.norvexa.clearup.BuildConfig

@Composable
fun AboutScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("ClearUp", style = MaterialTheme.typography.displaySmall)
            Text("by NORVEXA", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Версия ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Назначение", style = MaterialTheme.typography.titleLarge)
                    Text("Прозрачная очистка накопителя Android без рекламы, облачной обработки и фиктивного RAM Booster.")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Приватность", style = MaterialTheme.typography.titleLarge)
                    Text("Список файлов, сведения о приложениях и результаты анализа остаются на устройстве. ClearUp не требует аккаунта и не содержит рекламных SDK.")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Безопасность", style = MaterialTheme.typography.titleLarge)
                    Text("Пользовательские файлы удаляются только после ручного выбора и системного подтверждения Android. На Android 11+ используется системная корзина MediaStore.")
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
