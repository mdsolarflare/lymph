package com.lymph.platform

import kotlin.io.encoding.Base64
import kotlin.js.JsName

/**
 * Installs a small JS helper on `globalThis` that turns (filename, base64 payload)
 * into a browser file download via Blob + object URL + anchor click.
 *
 * The helper is defined as raw JS so it works identically on the JS IR and wasmJs
 * targets. Kotlin/Wasm forbids passing [ByteArray] across the interop boundary, so
 * callers base64-encode the payload first (see [downloadInBrowser]).
 */
private val downloadHelper: String = js(
    "(function () {" +
        "  if (!globalThis.__lymphDownload) {" +
        "    globalThis.__lymphDownload = function (name, b64) {" +
        "      var bin = atob(b64);" +
        "      var bytes = new Uint8Array(bin.length);" +
        "      for (var i = 0; i < bin.length; i++) { bytes[i] = bin.charCodeAt(i); }" +
        "      var blob = new Blob([bytes], { type: 'application/zip' });" +
        "      var url = URL.createObjectURL(blob);" +
        "      var a = document.createElement('a');" +
        "      a.href = url;" +
        "      a.download = name;" +
        "      document.body.appendChild(a);" +
        "      a.click();" +
        "      document.body.removeChild(a);" +
        "      setTimeout(function () { URL.revokeObjectURL(url); }, 1000);" +
        "    };" +
        "  }" +
        "  return 'installed';" +
        "})()"
)

/** JS-side download helper installed by [downloadHelper]. */
@JsName("__lymphDownload")
external fun lymphDownload(name: String, b64: String)

/**
 * Triggers a browser file download of [data] saved as [filename].
 *
 * Web targets only (JS IR + wasmJs). The payload is base64-encoded before crossing
 * the JS interop boundary because Kotlin/Wasm does not allow [ByteArray] parameters
 * on external functions.
 */
fun downloadInBrowser(filename: String, data: ByteArray) {
    check(downloadHelper == "installed") { "browser download helper failed to install" }
    lymphDownload(filename, Base64.encode(data))
}
