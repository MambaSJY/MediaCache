package com.example.mediacache

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mediacache.ui.theme.TestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    main()
                }
            }
        }
    }
}

@Composable
fun main(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cacheEngine = App.cacheEngine(context)
    val uri =
        Uri.parse(cacheEngine.getProxyUrl(URL))
    Row(
        modifier = Modifier
            .fillMaxWidth() // Fill the maximum width available
            .padding(16.dp) // Add padding around the Row
            .height(IntrinsicSize.Min) // Set the height to wrap the content
                // , // Set background color and corner shape
    ) {
        VideoPlayer(uri)
    }
}

private val URL = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
