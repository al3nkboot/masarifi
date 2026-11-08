package com.midroid.masarifi.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether the home extended FAB shows its label; lists collapse it once scrolled away from the top. */
val LocalFabExpanded = staticCompositionLocalOf<MutableState<Boolean>> { mutableStateOf(true) }

@Composable
fun rememberFabAwareListState(): LazyListState {
    val state = rememberLazyListState()
    val fab = LocalFabExpanded.current
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 24 }
            .collect { fab.value = it }
    }
    return state
}
