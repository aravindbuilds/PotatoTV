package app.potatotv.ui.mylist

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.potatotv.ServiceLocator
import app.potatotv.ui.components.EmptyState
import app.potatotv.ui.components.PosterCard
import app.potatotv.ui.components.PotatoButton
import app.potatotv.ui.theme.PotatoText
import app.potatotv.ui.theme.PotatoTextDim
import kotlinx.coroutines.launch

@Composable
fun MyListScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myList by ServiceLocator.prefs.myList
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 88.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("My List", color = PotatoText, style = MaterialTheme.typography.displayMedium)
                Text(
                    if (myList.isEmpty()) {
                        "Titles you save will show up here."
                    } else {
                        "${myList.size} saved title${if (myList.size == 1) "" else "s"}"
                    },
                    color = PotatoTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            PotatoButton(
                label = "Live TV",
                icon = Icons.Filled.LiveTv,
                primary = false
            ) { nav.navigate("iptv") }
        }

        Spacer(Modifier.height(20.dp))

        if (myList.isEmpty()) {
            EmptyState(
                title = "Your list is empty",
                message = "Open a title and press “My List”. Hold OK on a poster here to remove it.",
                actionLabel = "Browse the catalogue"
            ) { nav.navigate("home") }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(158.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(myList, key = { it.id }) { item ->
                    PosterCard(
                        item = item,
                        onClick = { nav.navigate("title/${item.type}/${item.id}") },
                        onLongClick = {
                            scope.launch {
                                ServiceLocator.prefs.toggleMyList(item)
                                Toast.makeText(context, "Removed from My List", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}
