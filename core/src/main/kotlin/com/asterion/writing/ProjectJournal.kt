package com.asterion.writing

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/** Small local control-plane journal. Novel/chapter text is stored in Nexus instead. */
data class JournalRecord(val sequence: Long, val type: String, val payload: String)
data class JournalRead(val records: List<JournalRecord>, val truncatedTail: Boolean, val lastValidOffset: Long)

class ProjectJournal(private val file: File) {
    companion object {
        private const val MAGIC = 0x41575231 // AWR1
        private const val MAX_ENTRY_BYTES = 1024 * 1024
    }

    @Synchronized fun read(): JournalRead {
        if (!file.exists()) return JournalRead(emptyList(), false, 0)
        val entries = mutableListOf<JournalRecord>()
        RandomAccessFile(file, "r").use { raf ->
            var lastValid = 0L
            while (raf.filePointer < raf.length()) {
                if (raf.length() - raf.filePointer < 4 + 8 + 4 + 32) return JournalRead(entries, true, lastValid)
                if (raf.readInt() != MAGIC) throw IOException("Corrupted journal magic at $lastValid")
                val seq = raf.readLong()
                val length = raf.readInt()
                if (length !in 1..MAX_ENTRY_BYTES) throw IOException("Invalid journal length at $lastValid")
                val expected = ByteArray(32)
                raf.readFully(expected)
                if (raf.length() - raf.filePointer < length) return JournalRead(entries, true, lastValid)
                val data = ByteArray(length)
                raf.readFully(data)
                if (!MessageDigestEqual(expected, hashBytes(data))) throw IOException("Journal checksum mismatch at $lastValid")
                if (seq != entries.size.toLong() + 1) throw IOException("Journal sequence gap at $lastValid")
                val decoded = String(data, StandardCharsets.UTF_8)
                val delimiter = decoded.indexOf('\n')
                if (delimiter <= 0) throw IOException("Invalid record encoding at $lastValid")
                entries += JournalRecord(seq, decoded.substring(0, delimiter), decoded.substring(delimiter + 1))
                lastValid = raf.filePointer
            }
            return JournalRead(entries, false, lastValid)
        }
    }

    @Synchronized fun append(type: String, payload: String): JournalRecord {
        require(type.matches(Regex("[a-z][a-z0-9._-]{0,63}"))) { "Invalid event type" }
        val snapshot = read()
        require(!snapshot.truncatedTail) { "Incomplete final record: explicit recoverTruncatedTail() required" }
        val data = "$type\n$payload".toByteArray(StandardCharsets.UTF_8)
        require(data.size in 1..MAX_ENTRY_BYTES) { "Metadata event exceeds limit" }
        val sequence = snapshot.records.size.toLong() + 1
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.writeInt(MAGIC)
            raf.writeLong(sequence)
            raf.writeInt(data.size)
            raf.write(hashBytes(data))
            raf.write(data)
            raf.fd.sync()
        }
        return JournalRecord(sequence, type, payload)
    }

    /** Back up original before discarding only the incomplete final record; checksum failures are never silently repaired. */
    @Synchronized fun recoverTruncatedTail(): Long {
        val state = read()
        if (!state.truncatedTail) return 0L
        val bytesDropped = file.length() - state.lastValidOffset
        file.copyTo(File(file.path + ".truncated.bak"), overwrite = true)
        RandomAccessFile(file, "rw").use { raf -> raf.setLength(state.lastValidOffset); raf.fd.sync() }
        return bytesDropped
    }

    private fun hashBytes(bytes: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun MessageDigestEqual(a: ByteArray, b: ByteArray): Boolean = java.security.MessageDigest.isEqual(a, b)
}
