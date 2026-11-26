package io.github.alexanderop.eisen.feature.food.platform

import android.app.Activity
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
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
 *
 * Play services downloads the scanner as a module. It does that by itself only for an app from the Play
 * Store; this app is installed from a file, so it asks for the module before the first scan.
 */
object BarcodeScanner {
    private val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .enableAutoZoom()
        .build()

    fun scan(activity: Activity, dispatch: (Msg) -> Unit) {
        // Nothing the scanner does may take the app down: the lifter can always type the barcode.
        try {
            val scanner = GmsBarcodeScanning.getClient(activity, options)
            ModuleInstall.getClient(activity).areModulesAvailable(scanner)
                .addOnSuccessListener { answer ->
                    guarded(dispatch) { if (answer.areModulesAvailable()) start(activity, dispatch) else fetch(activity, dispatch) }
                }
                // Play services could not say: the scanner itself gives the better answer.
                .addOnFailureListener { guarded(dispatch) { start(activity, dispatch) } }
        } catch (error: RuntimeException) {
            Log.w(TAG, "scanner could not be asked for", error)
            dispatch(Msg.Food.ScanFailed(ScanFailure.UNAVAILABLE))
        }
    }

    private inline fun guarded(dispatch: (Msg) -> Unit, block: () -> Unit) {
        try {
            block()
        } catch (error: RuntimeException) {
            Log.w(TAG, "scanner failed", error)
            dispatch(Msg.Food.ScanFailed(ScanFailure.UNAVAILABLE))
        }
    }

    /** Asks Play services to download the scanner now. The lifter types this barcode; the next Scan finds it there. */
    private fun fetch(activity: Activity, dispatch: (Msg) -> Unit) {
        val request = ModuleInstallRequest.newBuilder().addApi(GmsBarcodeScanning.getClient(activity, options)).build()
        ModuleInstall.getClient(activity).installModules(request)
            .addOnSuccessListener { Log.i(TAG, "scanner module asked for, already there: ${it.areModulesAlreadyInstalled()}") }
            .addOnFailureListener { Log.w(TAG, "scanner module could not be asked for", it) }
        dispatch(Msg.Food.ScanFailed(ScanFailure.INSTALLING))
    }

    private fun start(activity: Activity, dispatch: (Msg) -> Unit) {
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
                if (why == ScanFailure.UNAVAILABLE) guarded(dispatch) { fetch(activity, dispatch) } else dispatch(Msg.Food.ScanFailed(why))
            }
    }

    private const val TAG = "EisenFood"
}
