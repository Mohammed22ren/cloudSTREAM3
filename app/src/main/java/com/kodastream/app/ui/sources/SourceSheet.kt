package com.kodastream.app.ui.sources

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.engine.Engine
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity-scoped source state, shared by Home, Search and the navigation
 * shell's source button. Home's selection stays authoritative: HomeViewModel
 * reports its fallback here, and a choice made here asks it to switch.
 */
class SourceViewModel(application: Application) : AndroidViewModel(application) {
    private val _providers = MutableStateFlow<List<MainAPI>>(emptyList())
    val providers: StateFlow<List<MainAPI>> = _providers.asStateFlow()

    private val _home = MutableStateFlow(AppPrefs.selectedProvider(application))
    val home: StateFlow<String?> = _home.asStateFlow()

    /** null means every provider, the same meaning the Search chips had. */
    private val _search = MutableStateFlow<String?>(null)
    val search: StateFlow<String?> = _search.asStateFlow()

    init {
        viewModelScope.launch {
            PluginManager.revision.collect {
                val all = Engine.providers()
                _providers.value = all
                val search = _search.value
                if (search != null && all.none { it.name == search }) _search.value = null
            }
        }
    }

    fun reportHome(name: String?) {
        if (_home.value != name) _home.value = name
    }

    fun selectHome(name: String) {
        _home.value = name
        AppPrefs.setSelectedProvider(getApplication(), name)
    }

    fun selectSearch(name: String?) {
        _search.value = name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(
    providers: List<MainAPI>,
    selected: String?,
    showAllSources: Boolean,
    onSelect: (String?) -> Unit,
    onOpenExtensions: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.sources),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            if (providers.isEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenExtensions) {
                        Text(stringResource(R.string.open_extensions))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    if (showAllSources) {
                        item(key = "all") {
                            SourceRow(
                                name = stringResource(R.string.search_scope_all),
                                selected = selected == null,
                                onClick = { onSelect(null) },
                            )
                        }
                    }
                    items(providers, key = { it.name }) { api ->
                        SourceRow(
                            name = api.name,
                            selected = selected == api.name,
                            onClick = { onSelect(api.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null)
            }
        }
    }
}
