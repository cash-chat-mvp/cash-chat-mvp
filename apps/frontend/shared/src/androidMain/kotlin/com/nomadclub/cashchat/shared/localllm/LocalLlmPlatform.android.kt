package com.nomadclub.cashchat.shared.localllm

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

private const val FILE_BUFFER_SIZE = 64 * 1024

actual fun localModelsDir(): String = joinPath(AndroidLocalLlmContext.filesDirPath(), "gemma-models")

actual fun totalRamBytes(): Long = AndroidLocalLlmContext.totalRamBytes()

actual fun availableStorageBytes(path: String): Long {
    val probe = existingProbeFile(path)
        ?: path.takeIf(::ensureDirectory)?.let(::File)
        ?: return 0L
    return probe.usableSpace.takeIf { it > 0L } ?: 0L
}

actual fun ensureDirectory(path: String): Boolean {
    val file = File(path)
    return file.isDirectory || file.mkdirs()
}

actual fun fileExists(path: String): Boolean = File(path).exists()

actual fun fileSize(path: String): Long = File(path).takeIf(File::exists)?.length() ?: 0L

actual fun readFileBytes(path: String): ByteArray? {
    val file = File(path)
    return if (file.exists()) file.readBytes() else null
}

actual fun writeFileBytes(path: String, bytes: ByteArray, append: Boolean) {
    val file = File(path)
    file.parentFile?.mkdirs()
    FileOutputStream(file, append).use { output ->
        output.write(bytes)
    }
}

actual fun deleteFile(path: String): Boolean {
    val file = File(path)
    return !file.exists() || file.delete()
}

actual fun moveFile(fromPath: String, toPath: String): Boolean {
    val source = File(fromPath)
    if (!source.exists()) return false

    val destination = File(toPath)
    destination.parentFile?.mkdirs()
    if (destination.exists()) {
        destination.delete()
    }

    if (source.renameTo(destination)) {
        return true
    }

    source.copyTo(destination, overwrite = true)
    return source.delete()
}

actual fun sha256(path: String): String? {
    val file = File(path)
    if (!file.exists()) return null

    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(FILE_BUFFER_SIZE)
    FileInputStream(file).use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }

    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

actual fun readTextFile(path: String): String? {
    val file = File(path)
    return if (file.exists()) file.readText() else null
}

actual fun writeTextFile(path: String, text: String) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeText(text)
}

private fun existingProbeFile(path: String): File? {
    var current: File? = File(path)
    while (current != null) {
        if (current.exists()) {
            return current
        }
        current = current.parentFile
    }
    return null
}
