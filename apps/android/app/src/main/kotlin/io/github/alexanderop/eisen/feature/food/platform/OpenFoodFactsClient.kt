package io.github.alexanderop.eisen.feature.food.platform

import android.content.Context
import android.util.Log
import io.github.alexanderop.eisen.BuildConfig
import io.github.alexanderop.eisen.feature.food.PRODUCT_FIELDS
import io.github.alexanderop.eisen.feature.food.ProductAnswer
import io.github.alexanderop.eisen.feature.food.SEARCH_PAGE_SIZE
import io.github.alexanderop.eisen.feature.food.SearchAnswer
import io.github.alexanderop.eisen.feature.food.countryTag
import io.github.alexanderop.eisen.feature.food.productAnswer
import io.github.alexanderop.eisen.feature.food.searchAnswer
import io.github.alexanderop.eisen.feature.food.searchFields
import io.github.alexanderop.eisen.feature.food.searchLangs
import io.github.alexanderop.eisen.feature.food.searchQuery
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The requests to Open Food Facts: a product by its barcode, and a search. Plain `HttpURLConnection` off the
 * main thread, one request after the other; what the answer means is decided in `feature/food` (pure), and
 * whether a request may go at all (the rate limits) in `update`. Nothing but the barcode or the typed search
 * leaves the phone, and the User-Agent names the app as their terms ask, never a person.
 *
 * Debug builds answer from the app's `files/off/` when that folder exists (`product/<barcode>.json`,
 * `search/<query>.json`; a file that is missing is no network), so a journey needs neither the internet nor
 * a camera. Release builds never look there.
 */
object OpenFoodFactsClient {
    private const val BASE = "https://world.openfoodfacts.org"
    private const val SEARCH = "https://search.openfoodfacts.org/search"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    /** `AppName/Version (contact)`, the form Open Food Facts asks for, with the project's page as the contact. */
    val USER_AGENT = "Eisen/${BuildConfig.VERSION_NAME} (Android; https://github.com/alexanderop/eisen)"

    private val requests = Executors.newSingleThreadExecutor()

    /** Answers on a background thread. */
    fun product(context: Context, barcode: String, done: (ProductAnswer) -> Unit) {
        requests.execute {
            val (status, body) = local(context, "product", barcode) ?: get("$BASE/api/v2/product/$barcode?fields=$PRODUCT_FIELDS")
            done(productAnswer(barcode, status, body))
        }
    }

    /**
     * Answers on a background thread. Search-a-licious, the full-text search Open Food Facts documents for it: the
     * phone's language first, and only products sold in the phone's country (Germany for a phone set to Germany).
     */
    fun search(context: Context, query: String, done: (SearchAnswer) -> Unit) {
        requests.execute {
            val locale = Locale.getDefault()
            val lang = locale.language
            val country = runCatching { Locale.Builder().setRegion(locale.country).build().getDisplayCountry(Locale.ENGLISH) }.getOrNull()?.let(::countryTag)
            fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
            val url = "$SEARCH?q=${encode(searchQuery(query, country))}&langs=${encode(searchLangs(lang))}&page_size=$SEARCH_PAGE_SIZE&fields=${encode(searchFields(lang))}"
            val (status, body) = local(context, "search", query.trim().lowercase()) ?: get(url)
            done(searchAnswer(status, body, lang))
        }
    }

    /** The status and the body, 0 and null when there was no answer. */
    private fun get(url: String): Pair<Int, String?> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            val status = connection.responseCode
            val stream = if (status < 400) connection.inputStream else connection.errorStream
            status to stream?.use { it.readBytes().decodeToString() }
        } catch (error: IOException) {
            Log.i(TAG, "no answer from Open Food Facts: ${error.javaClass.simpleName}")
            0 to null
        } finally {
            connection?.disconnect()
        }
    }

    /** A debug build's answer from `files/off/<kind>/<key>.json`; null when the folder is not there (the network then). */
    private fun local(context: Context, kind: String, key: String): Pair<Int, String?>? {
        if (!BuildConfig.DEBUG) return null
        val folder = File(context.filesDir, "off")
        if (!folder.isDirectory) return null
        val file = File(folder, "$kind/$key.json")
        Log.i(TAG, "answered $kind $key from files/off (${if (file.isFile) "found" else "missing: no network"})")
        return if (file.isFile) 200 to file.readText() else 0 to null
    }

    private const val TAG = "EisenFood"
}
