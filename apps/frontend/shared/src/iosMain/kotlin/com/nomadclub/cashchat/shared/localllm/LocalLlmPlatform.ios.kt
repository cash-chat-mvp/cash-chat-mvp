@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nomadclub.cashchat.shared.localllm

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.rewind

actual fun localModelsDir(): String {
    val base = (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)
        ?: NSTemporaryDirectory()
    return joinPath(base, "gemma-models")
}

actual fun totalRamBytes(): Long = NSProcessInfo.processInfo.physicalMemory.toLong()

actual fun availableStorageBytes(path: String): Long {
    val manager = NSFileManager.defaultManager
    val probePath = existingProbePath(path)
        ?: path.takeIf(::ensureDirectory)
        ?: parentPath(path)?.takeIf(::ensureDirectory)
        ?: return 0L
    val attributes = manager.attributesOfFileSystemForPath(probePath, null)
    val freeSize = attributes?.get(NSFileSystemFreeSize) as? NSNumber
    return freeSize?.longLongValue?.takeIf { it > 0L } ?: 0L
}

actual fun ensureDirectory(path: String): Boolean {
    return NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
    || NSFileManager.defaultManager.fileExistsAtPath(path)
}

actual fun fileExists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

actual fun fileSize(path: String): Long {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)
    val size = attributes?.get(NSFileSize) as? NSNumber
    return size?.longLongValue ?: 0L
}

actual fun readFileBytes(path: String): ByteArray? {
    val file = fopen(path, "rb") ?: return null
    try {
        if (fseek(file, 0, SEEK_END) != 0) return null
        val size = ftell(file)
        if (size < 0) return null
        rewind(file)

        val result = ByteArray(size.toInt())
        if (result.isNotEmpty()) {
            val read = result.usePinned {
                fread(it.addressOf(0), 1.convert(), result.size.convert(), file).toInt()
            }
            if (read != result.size) return null
        }
        return result
    } finally {
        fclose(file)
    }
}

actual fun writeFileBytes(path: String, bytes: ByteArray, append: Boolean, offset: Int, length: Int) {
    check(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "Invalid byte range." }
    parentPath(path)?.let(::ensureDirectory)
    val file = fopen(path, if (append) "ab" else "wb")
        ?: error("Unable to open $path for writing.")
    try {
        if (length > 0) {
            val written = bytes.usePinned {
                fwrite(it.addressOf(offset), 1.convert(), length.convert(), file).toInt()
            }
            check(written == length) { "Unable to write all bytes to $path." }
        }
    } finally {
        fclose(file)
    }
}

actual fun deleteFile(path: String): Boolean {
    val manager = NSFileManager.defaultManager
    return !manager.fileExistsAtPath(path) || manager.removeItemAtPath(path, null)
}

actual fun moveFile(fromPath: String, toPath: String): Boolean {
    val manager = NSFileManager.defaultManager
    parentPath(toPath)?.let(::ensureDirectory)
    if (manager.fileExistsAtPath(toPath)) {
        manager.removeItemAtPath(toPath, null)
    }
    return manager.moveItemAtPath(fromPath, toPath, null)
}

actual fun sha256(path: String): String? {
    val file = fopen(path, "rb") ?: return null
    try {
        val digest = Sha256Digest()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = buffer.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), buffer.size.convert(), file).toInt()
            }
            if (read <= 0) break
            digest.update(buffer, read)
        }
        return digest.digestHex()
    } finally {
        fclose(file)
    }
}

actual fun readTextFile(path: String): String? = readFileBytes(path)?.decodeToString()

actual fun writeTextFile(path: String, text: String) {
    writeFileBytes(path, text.encodeToByteArray(), append = false)
}

private fun existingProbePath(path: String): String? {
    var current: String? = path
    while (current != null) {
        if (NSFileManager.defaultManager.fileExistsAtPath(current)) {
            return current
        }
        current = parentPath(current)
    }
    return null
}
