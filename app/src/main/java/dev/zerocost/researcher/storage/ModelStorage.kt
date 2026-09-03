package dev.zerocost.researcher.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.security.DigestOutputStream
import java.io.File
import java.security.MessageDigest

data class ImportedModel(
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
)

class ModelStorage(
    context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val modelDirectory = File(
        context.filesDir,
        "models",
    ).apply { mkdirs() }

    fun importGguf(
        uri: Uri,
        previousModelPath: String?,
    ): ImportedModel {
        val temp = File(
            modelDirectory,
            "import-${System.currentTimeMillis()}.partial",
        )
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) {
                    "Could not open the selected model."
                }

                temp.outputStream().buffered().use { rawOutput ->
                    DigestOutputStream(rawOutput, digest).use { output ->
                        input.copyTo(output, COPY_BUFFER_BYTES)
                    }
                }
            }

            validateGguf(temp)

            val sha256 = digest.digest()
                .joinToString("") { "%02x".format(it) }
            val finalFile = File(
                modelDirectory,
                "model-${sha256.take(FILE_HASH_CHARS)}.gguf",
            )

            if (finalFile.exists()) {
                temp.delete()
            } else {
                check(temp.renameTo(finalFile)) {
                    "Could not finalize the imported model."
                }
            }

            deletePreviousPrivateModel(
                previousModelPath = previousModelPath,
                keep = finalFile,
            )

            return ImportedModel(
                path = finalFile.absolutePath,
                sha256 = sha256,
                sizeBytes = finalFile.length(),
            )
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    private fun validateGguf(file: File) {
        require(file.length() >= GGUF_MAGIC.size) {
            "Selected file is too small to be a GGUF model."
        }

        val magic = ByteArray(GGUF_MAGIC.size)
        file.inputStream().use { input ->
            val read = input.read(magic)
            require(read == GGUF_MAGIC.size) {
                "Could not read the GGUF header."
            }
        }
        require(magic.contentEquals(GGUF_MAGIC)) {
            "Selected file does not have a GGUF header."
        }
    }

    private fun deletePreviousPrivateModel(
        previousModelPath: String?,
        keep: File,
    ) {
        if (previousModelPath.isNullOrBlank()) return

        val previous = runCatching {
            File(previousModelPath).canonicalFile
        }.getOrNull() ?: return
        val directory = runCatching {
            modelDirectory.canonicalFile
        }.getOrNull() ?: return

        if (
            previous != keep.canonicalFile &&
            previous.parentFile == directory &&
            previous.extension.equals("gguf", ignoreCase = true)
        ) {
            previous.delete()
        }
    }

    companion object {
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val FILE_HASH_CHARS = 16
        private val GGUF_MAGIC = byteArrayOf(
            'G'.code.toByte(),
            'G'.code.toByte(),
            'U'.code.toByte(),
            'F'.code.toByte(),
        )
    }
}
