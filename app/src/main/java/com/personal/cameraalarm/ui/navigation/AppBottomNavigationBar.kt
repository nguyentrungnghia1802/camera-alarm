package com.personal.cameraalarm.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.personal.cameraalarm.R
import com.personal.cameraalarm.ui.AppScreen

@Composable
fun AppBottomNavigationBar(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentScreen == AppScreen.DASHBOARD,
            onClick = {
                if (currentScreen != AppScreen.DASHBOARD) {
                    onNavigate(AppScreen.DASHBOARD)
                }
            },
            icon = {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = stringResource(R.string.nav_dashboard)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_dashboard),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        NavigationBarItem(
            selected = currentScreen == AppScreen.RULES,
            onClick = {
                if (currentScreen != AppScreen.RULES) {
                    onNavigate(AppScreen.RULES)
                }
            },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Rule,
                    contentDescription = stringResource(R.string.nav_rules)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_rules),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        NavigationBarItem(
            selected = currentScreen == AppScreen.HISTORY,
            onClick = {
                if (currentScreen != AppScreen.HISTORY) {
                    onNavigate(AppScreen.HISTORY)
                }
            },
            icon = {
                Icon(
                    Icons.Default.History,
                    contentDescription = stringResource(R.string.nav_history)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_history),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        NavigationBarItem(
            selected = currentScreen == AppScreen.SETTINGS,
            onClick = {
                if (currentScreen != AppScreen.SETTINGS) {
                    onNavigate(AppScreen.SETTINGS)
                }
            },
            icon = {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = stringResource(R.string.nav_settings)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_settings),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}
