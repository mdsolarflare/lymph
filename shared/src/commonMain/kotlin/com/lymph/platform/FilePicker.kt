package com.lymph.platform

/**
 * Platform capability for picking a file from user storage and reading its bytes.
 *
 * Only targets with a native file chooser implement this (web, for now — native
 * pickers are deferred). The UI hides import entry points where no picker is
 * available, so common code never has to special-case platforms.
 */
fun interface FilePicker {

    /**
     * Opens the platform file chooser. [onResult] receives the selected file's bytes,
     * or null if the user cancelled or reading failed.
     */
    fun pick(onResult: (ByteArray?) -> Unit)
}
