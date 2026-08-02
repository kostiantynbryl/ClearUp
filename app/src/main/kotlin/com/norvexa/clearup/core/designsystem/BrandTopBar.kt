package com.norvexa.clearup.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandTopBar(title: String? = null) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title ?: "ClearUp",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (title == null) {
                    Text(
                        text = "by NORVEXA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    )
}
