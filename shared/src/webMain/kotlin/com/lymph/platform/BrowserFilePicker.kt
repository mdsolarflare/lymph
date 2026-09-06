package com.lymph.platform

import kotlin.io.encoding.Base64
import kotlin.js.JsName

/**
 * Installs a small JS helper on `globalThis` that opens the browser's native file
 * chooser and hands the selected file back as base64.
 *
 * The helper keeps one persistent hidden `<input type="file">` (reused across picks,
 * with its value reset first so re-selecting the same file still fires 'change') and
 * reads it via FileReader. Base64 is used because Kotlin/Wasm forbids passing
 * [ByteArray] across the interop boundary — the same constraint as [BrowserDownload].
 * The raw JS keeps behavior identical on both web targets (JS IR + wasmJs).
 */
private val pickFileHelper: String = js(
    "(function () {" +
        "  if (!globalThis.__lymphPickFile) {" +
        "    globalThis.__lymphPickFile = function (cb) {" +
        "      var input;" +
        "      if (document.getElementById('__lymph_file_input')) {" +
        "        input = document.getElementById('__lymph_file_input');" +
        "      } else {" +
        "        input = document.createElement('input');" +
        "        input.type = 'file';" +
        "        input.accept = '.zip,application/zip,application/x-zip-compressed';" +
        "        input.id = '__lymph_file_input';" +
        "        input.style.display = 'none';" +
        "        document.body.appendChild(input);" +
        "      }" +
        "      input.value = '';" +
        "      input.onchange = function () {" +
        "        var f = input.files && input.files[0];" +
        "        if (!f) { cb(null); return; }" +
        "        var reader = new FileReader();" +
        "        reader.onload = function () {" +
        "          var s = String(reader.result);" +
        "          cb(s.substring(s.indexOf(',') + 1));" +
        "        };" +
        "        reader.onerror = function () { cb(null); };" +
        "        reader.readAsDataURL(f);" +
        "      };" +
        "      input.click();" +
        "    };" +
        "  }" +
        "  return 'installed';" +
    "})()"
)

/** JS-side file-picker helper installed by [pickFileHelper]. */
@JsName("__lymphPickFile")
external fun lymphPickFile(onResult: (String?) -> Unit)

/**
 * Opens the browser's native file chooser for a ZIP archive.
 * [onResult] receives the selected file's bytes, or null if the user cancelled.
 * Web targets only (JS IR + wasmJs).
 */
fun pickZipFileInBrowser(onResult: (ByteArray?) -> Unit) {
    check(pickFileHelper == "installed") { "browser file picker helper failed to install" }
    lymphPickFile { b64 -> onResult(if (b64 == null) null else Base64.decode(b64)) }
}

/** [FilePicker] backed by the browser's native file chooser. */
class BrowserFilePicker : FilePicker {
    override fun pick(onResult: (ByteArray?) -> Unit) = pickZipFileInBrowser(onResult)
}
