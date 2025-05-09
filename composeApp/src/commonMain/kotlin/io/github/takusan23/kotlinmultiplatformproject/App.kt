package io.github.takusan23.kotlinmultiplatformproject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import multiplatformawssignv4.composeapp.generated.resources.Res
import multiplatformawssignv4.composeapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

// TODO 各自変更してください！！！
const val bucketName = ""
const val region = "ap-northeast-1"
const val accessKey = ""
const val secretAccessKey = ""

/** Kotlin Multiplatform Compose */
@Composable
@Preview
fun App() {
    MaterialTheme {

        // バケットの中身を取得する REST API を叩く
        val responseXml = remember { mutableStateOf("") }
        LaunchedEffect(key1 = Unit) {
            val now = Clock.System.now()
            val url = "https://s3.$region.amazonaws.com/$bucketName/?list-type=2"
            val amzDateString = now.formatAmzDateString()
            val yyyyMMddString = now.formatYearMonthDayDateString()

            // 署名を作成
            val requestHeader = hashMapOf(
                "x-amz-date" to amzDateString,
                "host" to "s3.$region.amazonaws.com"
            )
            val signature = generateAwsSign(
                url = url,
                httpMethod = "GET",
                contentType = null,
                region = region,
                service = "s3",
                amzDateString = amzDateString,
                yyyyMMddString = yyyyMMddString,
                secretAccessKey = secretAccessKey,
                accessKey = accessKey,
                requestHeader = requestHeader
            )

            // レスポンス xml を取得
            val httpClient = HttpClient()
            val response = httpClient.get {
                url(url)
                headers {
                    // 署名をリクエストヘッダーにつける
                    requestHeader.forEach { (name, value) ->
                        this[name] = value
                    }
                    this["Authorization"] = signature
                }
            }
            responseXml.value = response.bodyAsText()
        }


        var showContent by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { showContent = !showContent }) {
                Text("Click me!")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }

            Text(text = responseXml.value)
        }
    }
}