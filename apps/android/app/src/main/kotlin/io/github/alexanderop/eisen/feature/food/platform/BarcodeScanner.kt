package io.github.alexanderop.eisen.feature.food.platform

import android.app.Activity
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.ScanFailure

/**
 * Google's code scanner: Play services shows the camera and hands back only the barcode, so the app needs no
 * camera permission. Product barcodes only. When the scanner is missing, fails or is cancelled the lifter
 * types the barcode instead, which `update` offers.
 */
object BarcodeScanner {
    private val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .enableAutoZoom()
        .build()

    fun scan(activity: Activity, dispatch: (Msg) -> Unit) {
        val task = try {
            GmsBarcodeScanning.getClient(activity, options).startScan()
        } catch (error: RuntimeException) {
            Log.w(TAG, "scanner could not start", error)
            dispatch(Msg.Food.ScanFailed(ScanFailure.UNAVAILABLE))
            return
        }
        task.addOnSuccessListener { barcode ->
            val code = barcode.rawValue?.filter(Char::isDigit).orEmpty()
            Log.i(TAG, "scanned ${barcode.format} ${code.length} digits")
            dispatch(if (code.isEmpty()) Msg.Food.ScanFailed(ScanFailure.FAILED) else Msg.Food.Scanned(code))
        }
            .addOnCanceledListener {
                Log.i(TAG, "scan cancelled")
                dispatch(Msg.Food.ScanFailed(ScanFailure.CANCELLED))
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "scan failed", error)
                val why = when ((error as? MlKitException)?.errorCode) {
                    MlKitException.CODE_SCANNER_CANCELLED -> ScanFailure.CANCELLED
                    MlKitException.CODE_SCANNER_UNAVAILABLE -> ScanFailure.UNAVAILABLE
                    else -> ScanFailure.FAILED
                }
                dispatch(Msg.Food.ScanFailed(why))
            }
    }

    private const val TAG = "EisenFood"
}
