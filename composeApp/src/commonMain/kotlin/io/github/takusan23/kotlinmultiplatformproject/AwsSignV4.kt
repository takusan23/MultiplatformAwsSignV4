package io.github.takusan23.kotlinmultiplatformproject

import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPath
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256

/** ISO8601 */
@OptIn(FormatStringsInDatetimeFormats::class)
private val amzDateFormat = DateTimeComponents.Format {
    byUnicodePattern("""yyyyMMdd'T'HHmmss'Z'""")
}

/** yyyyMMdd */
@OptIn(FormatStringsInDatetimeFormats::class)
private val yearMonthDayDateFormat = DateTimeComponents.Format {
    byUnicodePattern("yyyyMMdd")
}

@OptIn(ExperimentalStdlibApi::class)
internal fun generateAwsSign(
    url: String,
    httpMethod: String = "GET",
    contentType: String? = null,
    region: String = "ap-northeast-1",
    service: String = "s3",
    amzDateString: String,
    yyyyMMddString: String,
    secretAccessKey: String,
    accessKey: String,
    requestHeader: HashMap<String, String> = hashMapOf(),
    payloadSha256: String = "".sha256().toHexString()
): String {
    val httpUrl = Url(urlString = url)

    // リクエストヘッダーになければ追加
    requestHeader.putIfAbsent("x-amz-date", amzDateString)
    requestHeader.putIfAbsent("host", httpUrl.host)
    if (contentType != null) {
        requestHeader.putIfAbsent("Content-Type", contentType)
    }
    requestHeader.putIfAbsent("x-amz-content-sha256", payloadSha256)

    // 1.正規リクエストを作成する
    // パス、クエリパラメータは URL エンコードする
    // リスト系はアルファベットでソート
    val canonicalUri = httpUrl.encodedPath.encodeURLPath().ifBlank { "/" }
    val canonicalQueryString = httpUrl.parameters
        .names()
        .map { it.encodeURLParameter() }
        .sortedBy { name -> name }
        .associateWith { name -> httpUrl.parameters[name]?.encodeURLParameter() }
        .toList()
        .joinToString(separator = "&") { (name, values) ->
            "$name=${values ?: ""}" // こっちはイコール
        }
    val canonicalHeaders = requestHeader
        .toList()
        .sortedBy { (name, _) -> name.lowercase() }
        .joinToString(separator = "\n") { (name, value) ->
            "${name.lowercase()}:${value.trim()}"
        } + "\n" // 末尾改行で終わる
    val signedHeaders = requestHeader
        .toList()
        .map { (name, _) -> name.lowercase() }
        .sorted()
        .joinToString(separator = ";")
    val hashedPayload = payloadSha256.lowercase()
    val canonicalRequest = httpMethod + "\n" + canonicalUri + "\n" + canonicalQueryString + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedPayload

    // 2.正規リクエストのハッシュを作成する。ペイロードと同じハッシュ関数
    val hashedCanonicalRequest = canonicalRequest.sha256().toHexString()

    // 3.署名文字列を作成する
    val algorithm = "AWS4-HMAC-SHA256"
    val requestDateTime = amzDateString
    val credentialScope = "$yyyyMMddString/$region/$service/aws4_request"
    val stringToSign = algorithm + "\n" + requestDateTime + "\n" + credentialScope + "\n" + hashedCanonicalRequest

    // 4.SigV4 の署名キーの取得
    val dateKey = "AWS4$secretAccessKey".toByteArray(Charsets.UTF_8).hmacSha256(message = yyyyMMddString)
    val dateRegionKey = dateKey.hmacSha256(message = region)
    val dateRegionServiceKey = dateRegionKey.hmacSha256(message = service)
    val signingKey = dateRegionServiceKey.hmacSha256(message = "aws4_request")

    // 5.署名を計算する
    val signature = signingKey.hmacSha256(message = stringToSign).toHexString().lowercase()

    // 6.リクエストヘッダーに署名を追加する
    val authorizationHeaderValue = algorithm + " " + "Credential=$accessKey/$credentialScope" + "," + "SignedHeaders=$signedHeaders" + "," + "Signature=$signature"
    return authorizationHeaderValue
}

/** ISO8601 の形式でフォーマットする */
internal fun Instant.formatAmzDateString(): String {
    return this.format(amzDateFormat)
}

/** yyyy/MM/dd の形式でフォーマットする */
internal fun Instant.formatYearMonthDayDateString(): String {
    return this.format(yearMonthDayDateFormat)
}

/** バイト配列から SHA-256 */
internal fun ByteArray.sha256(): ByteArray {
    return SHA256().digest(this)
}

/** Java の putIfAbsent 相当 */
private fun <K, V> HashMap<K, V>.putIfAbsent(key: K, value: V): V? {
    var v = this.get(key)
    if (v == null) {
        v = put(key, value)
    }
    return v
}

/** 文字列から SHA-256 */
private fun String.sha256(): ByteArray {
    return this.toByteArray(Charsets.UTF_8).sha256()
}

/**
 * HMAC-SHA256 を計算
 * this がキーです
 */
private fun ByteArray.hmacSha256(message: String): ByteArray {
    val secretKey = this
    return HmacSHA256(secretKey).doFinal(message.toByteArray(Charsets.UTF_8))
}
