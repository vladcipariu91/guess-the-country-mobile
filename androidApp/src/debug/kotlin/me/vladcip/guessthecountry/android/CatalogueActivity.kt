package me.vladcip.guessthecountry.android

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.vladcip.guessthecountry.designsystem.catalogue.CatalogueScreen

/** Debug-only second launcher entry that shows every design system token and component. */
class CatalogueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Every cover is dark, so the system bars always get light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { CatalogueScreen() }
    }
}
