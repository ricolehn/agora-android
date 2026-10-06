package org.agora.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/** One action of the "+" button. */
data class FabAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The "+" button of a tab, native Material 3: with several actions the M3 FAB menu (the button turns into a close
 * button and the actions unfold above it as pills). A single action uses the same menu container and toggle button
 * without items, so the button looks and sits exactly the same, it just runs the action instead of unfolding.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgoraFabMenu(actions: List<FabAction>, contentDescription: String, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    if (actions.size == 1) {
        FloatingActionButtonMenu(
            expanded = false,
            modifier = modifier,
            button = {
                ToggleFloatingActionButton(
                    checked = false,
                    onCheckedChange = { actions.first().onClick() },
                    modifier = Modifier.semantics { this.contentDescription = contentDescription }
                ) { Icon(Icons.Filled.Add, contentDescription = null) }
            }
        ) {}
        return
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = !expanded },
                modifier = Modifier.semantics {
                    this.contentDescription = contentDescription
                    stateDescription = if (expanded) "expanded" else "collapsed"
                }
            ) {
                val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add } }
                Icon(rememberVectorPainter(icon), contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
            }
        }
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = { expanded = false; action.onClick() },
                icon = { Icon(action.icon, contentDescription = null) },
                text = { Text(action.label) }
            )
        }
    }
}
