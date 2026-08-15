import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

internal object DexStringTable {
    fun stringsFromApk(apk: File): Set<String> {
        return stringsFromZip(apk, dexEntry = { name ->
            name == "classes.dex" || name.matches(Regex("""classes\d+\.dex"""))
        })
    }

    fun stringsFromAab(aab: File): Set<String> {
        return stringsFromZip(aab, dexEntry = { name ->
            name.endsWith(".dex") && "/dex/" in name
        })
    }

    fun dexFilesFromApk(apk: File): List<ByteArray> {
        return dexFilesFromZip(apk) { name ->
            name == "classes.dex" || name.matches(Regex("""classes\d+\.dex"""))
        }
    }

    fun dexFilesFromAab(aab: File): List<ByteArray> {
        return dexFilesFromZip(aab) { name ->
            name.endsWith(".dex") && "/dex/" in name
        }
    }

    fun stringsFromDex(bytes: ByteArray): Set<String> {
        require(bytes.size >= 0x70) { "DEX too small" }
        val magic = bytes.decodeToString(0, 4)
        require(magic == "dex\n") { "Not a DEX file" }
        val endian = bytes.readInt(0x28)
        require(endian == 0x12345678) { "Unsupported DEX endian $endian" }
        val stringIdsSize = bytes.readInt(0x38)
        val stringIdsOff = bytes.readInt(0x3C)
        val strings = LinkedHashSet<String>(stringIdsSize)
        repeat(stringIdsSize) { index ->
            val dataOff = bytes.readInt(stringIdsOff + index * 4)
            strings += bytes.readMutf8(dataOff)
        }
        return strings
    }

    private fun stringsFromZip(archive: File, dexEntry: (String) -> Boolean): Set<String> {
        return dexFilesFromZip(archive, dexEntry).flatMap { stringsFromDex(it) }.toSet()
    }

    private fun dexFilesFromZip(archive: File, dexEntry: (String) -> Boolean): List<ByteArray> {
        check(archive.isFile) { "Archive is missing at ${archive.path}" }
        ZipFile(archive).use { zip ->
            val dexEntries = zip.entries().asSequence().filter { dexEntry(it.name) }.toList()
            check(dexEntries.isNotEmpty()) {
                "${archive.name} contains no DEX files"
            }
            return dexEntries.map { entry ->
                zip.getInputStream(entry).use { it.readBytes() }
            }
        }
    }

    private fun ByteArray.readInt(offset: Int): Int {
        return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun ByteArray.readMutf8(offset: Int): String {
        var cursor = offset
        // Skip uleb128 utf16_size
        while (cursor < size && (this[cursor].toInt() and 0x80) != 0) {
            cursor++
        }
        cursor++
        val buffer = ByteArrayOutputStream()
        while (cursor < size && this[cursor] != 0.toByte()) {
            buffer.write(this[cursor].toInt() and 0xff)
            cursor++
        }
        return buffer.toString("UTF-8")
    }
}
