package eu.kanade.novel.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

object NovelSettingsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var bionicReading by remember { mutableStateOf(false) }
        var authorNotes by remember { mutableStateOf(true) }
        var ttsSpeed by remember { mutableFloatStateOf(1.0f) }
        var ttsPitch by remember { mutableFloatStateOf(1.0f) }
        var useOnlineTranslation by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Novel Settings") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionHeader("Reader")
                ListItem(
                    headlineContent = { Text("Bionic Reading") },
                    supportingContent = { Text("Bold first letters of words") },
                    trailingContent = { Switch(checked = bionicReading, onCheckedChange = { bionicReading = it }) },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Show Author Notes") },
                    trailingContent = { Switch(checked = authorNotes, onCheckedChange = { authorNotes = it }) },
                )
                HorizontalDivider()

                SectionHeader("Text-to-Speech")
                ListItem(
                    headlineContent = { Text("TTS Speed: ${"%.1f".format(ttsSpeed)}x") },
                    supportingContent = {
                        Slider(value = ttsSpeed, onValueChange = { ttsSpeed = it }, valueRange = 0.5f..3.0f)
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("TTS Pitch: ${"%.1f".format(ttsPitch)}x") },
                    supportingContent = {
                        Slider(value = ttsPitch, onValueChange = { ttsPitch = it }, valueRange = 0.5f..2.0f)
                    },
                )
                HorizontalDivider()

                SectionHeader("Translation")
                ListItem(
                    headlineContent = { Text("Use Online Translation") },
                    supportingContent = { Text("Falls back to ML Kit when offline") },
                    trailingContent = { Switch(checked = useOnlineTranslation, onCheckedChange = { useOnlineTranslation = it }) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
