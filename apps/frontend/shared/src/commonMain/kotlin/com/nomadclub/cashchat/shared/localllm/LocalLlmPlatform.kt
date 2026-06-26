package com.nomadclub.cashchat.shared.localllm

expect fun localModelsDir(): String

expect fun totalRamBytes(): Long

expect fun availableStorageBytes(path: String): Long

expect fun ensureDirectory(path: String): Boolean

expect fun fileExists(path: String): Boolean

expect fun fileSize(path: String): Long

expect fun readFileBytes(path: String): ByteArray?

expect fun writeFileBytes(
    path: String,
    bytes: ByteArray,
    append: Boolean = false,
    offset: Int = 0,
    length: Int = bytes.size,
)

expect fun deleteFile(path: String): Boolean

expect fun moveFile(fromPath: String, toPath: String): Boolean

expect fun sha256(path: String): String?

expect fun readTextFile(path: String): String?

expect fun writeTextFile(path: String, text: String)

fun availableStorageBytes(): Long = availableStorageBytes(localModelsDir())

fun joinPath(base: String, child: String): String {
    val normalizedBase = base.trimEnd('/')
    val normalizedChild = child.trimStart('/')
    return "$normalizedBase/$normalizedChild"
}

fun parentPath(path: String): String? {
    val normalized = path.trimEnd('/')
    val index = normalized.lastIndexOf('/')
    return if (index <= 0) null else normalized.substring(0, index)
}
