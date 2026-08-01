package org.taskhub.platform

/**
 * Minimal QR Code encoder (Version 1, 21×21, alphanumeric, level M).
 *
 * Encodes up to 16 alphanumeric characters into a boolean matrix
 * where true = black module, false = white module.
 */
object QrEncoder {

    private const val VERSION = 1
    private const val SIZE = 21  // Version 1 → 21×21 modules

    // Alphanumeric character set
    private const val ALPHA = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    /** Encode an alphanumeric string into a SIZE×SIZE boolean matrix. */
    fun encode(text: String): Array<BooleanArray> {
        val data = text.uppercase()
        val matrix = Array(SIZE) { BooleanArray(SIZE) }

        // 1. Place finder patterns
        placeFinderPatterns(matrix)

        // 2. Place timing patterns
        placeTimingPatterns(matrix)

        // 3. Encode data into bit stream
        val dataBits = encodeAlphanumeric(data)
        val totalDataBits = 128  // Version 1-M: 128 data bits (16 bytes)
        val padded = padBits(dataBits, totalDataBits)

        // 4. Generate error correction codewords (10 bytes for version 1-M)
        val allCodewords = padded + generateEcBytes(padded, 16, 10)

        // 5. Convert to bit stream
        val bitStream = mutableListOf<Boolean>()
        for (cw in allCodewords) {
            for (i in 7 downTo 0) {
                bitStream.add((cw.toInt() and (1 shl i)) != 0)
            }
        }

        // 6. Place data modules (zigzag pattern)
        placeDataModules(matrix, bitStream)

        // 7. Apply best mask
        applyBestMask(matrix)

        // 8. Place format info
        placeFormatInfo(matrix, 0, 0)  // mask 0, EC level M

        return matrix
    }

    // ── Finder patterns (7×7 in corners) ──

    private fun placeFinderPatterns(matrix: Array<BooleanArray>) {
        // Top-left
        drawFinder(0, 0, matrix)
        // Top-right
        drawFinder(0, SIZE - 7, matrix)
        // Bottom-left
        drawFinder(SIZE - 7, 0, matrix)
    }

    private fun drawFinder(r: Int, c: Int, matrix: Array<BooleanArray>) {
        for (i in 0 until 7) {
            for (j in 0 until 7) {
                val outer = i == 0 || i == 6 || j == 0 || j == 6
                val inner = i in 2..4 && j in 2..4
                matrix[r + i][c + j] = outer || inner
            }
        }
    }

    // ── Timing patterns (dotted lines between finders) ──

    private fun placeTimingPatterns(matrix: Array<BooleanArray>) {
        // Horizontal (row 6)
        for (j in 8 until SIZE - 8) {
            matrix[6][j] = j % 2 == 0
        }
        // Vertical (col 6)
        for (i in 8 until SIZE - 8) {
            matrix[i][6] = i % 2 == 0
        }
    }

    // ── Alphanumeric encoding ──

    private fun encodeAlphanumeric(text: String): MutableList<Boolean> {
        val bits = mutableListOf<Boolean>()

        // Mode indicator: 0010 (alphanumeric)
        bits.addAll(listOf(false, false, true, false))

        // Character count indicator (9 bits for version 1-9)
        val count = text.length
        for (i in 8 downTo 0) bits.add((count and (1 shl i)) != 0)

        // Encode pairs
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length) {
                val v1 = ALPHA.indexOf(text[i])
                val v2 = ALPHA.indexOf(text[i + 1])
                val value = v1 * 45 + v2
                for (b in 10 downTo 0) bits.add((value and (1 shl b)) != 0)
                i += 2
            } else {
                val v = ALPHA.indexOf(text[i])
                for (b in 5 downTo 0) bits.add((v and (1 shl b)) != 0)
                i += 1
            }
        }

        // Terminator (minimum 4 zeros)
        val termLen = minOf(4, 128 - bits.size)
        repeat(termLen) { bits.add(false) }

        // Pad to multiple of 8
        while (bits.size % 8 != 0) bits.add(false)

        return bits
    }

    private fun padBits(bits: MutableList<Boolean>, totalBits: Int): List<Byte> {
        // Pad with alternating 0xEC and 0x11 bytes
        val padBytes = listOf(0xEC.toByte(), 0x11.toByte())
        var pi = 0
        while (bits.size < totalBits) {
            val pad = padBytes[pi % 2]
            for (b in 7 downTo 0) bits.add((pad.toInt() and (1 shl b)) != 0)
            pi++
        }

        // Convert to bytes
        val bytes = mutableListOf<Byte>()
        for (i in bits.indices step 8) {
            var byte = 0
            for (b in 0 until 8) {
                if (bits[i + b]) byte = byte or (1 shl (7 - b))
            }
            bytes.add(byte.toByte())
        }
        return bytes
    }

    // ── Reed-Solomon error correction ──

    private fun generateEcBytes(data: List<Byte>, dataBytes: Int, ecBytes: Int): List<Byte> {
        val generator = generateGeneratorPolynomial(ecBytes)
        val msg = IntArray(dataBytes + ecBytes) { i ->
            if (i < dataBytes) data.getOrElse(i) { 0 }.toInt() and 0xFF else 0
        }

        for (i in 0 until dataBytes) {
            val factor = msg[i]
            if (factor != 0) {
                for (j in 0 until ecBytes) {
                    msg[i + j + 1] = msg[i + j + 1] xor galoisMultiply(
                        generator[ecBytes - 1 - j], factor
                    )
                }
            }
        }

        return (dataBytes until dataBytes + ecBytes).map { msg[it].toByte() }
    }

    private fun generateGeneratorPolynomial(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val factor = intArrayOf(1, galoisExp(i))
            poly = multiplyPolynomials(poly, factor)
        }
        return poly.reversedArray()
    }

    private fun multiplyPolynomials(a: IntArray, b: IntArray): IntArray {
        val result = IntArray(a.size + b.size - 1)
        for (i in a.indices) {
            for (j in b.indices) {
                result[i + j] = result[i + j] xor galoisMultiply(a[i], b[j])
            }
        }
        return result
    }

    // GF(256) arithmetic
    private val EXP_TABLE = IntArray(256)
    private val LOG_TABLE = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP_TABLE[i] = x
            LOG_TABLE[x] = i
            x = x shl 1
            if (x >= 256) x = x xor 0x11D
            x = x and 0xFF
        }
        EXP_TABLE[255] = EXP_TABLE[0]
    }

    private fun galoisExp(power: Int): Int = EXP_TABLE[power % 255]
    private fun galoisLog(x: Int): Int = if (x == 0) -1 else LOG_TABLE[x]
    private fun galoisMultiply(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        val sum = galoisLog(a) + galoisLog(b)
        return galoisExp(sum % 255)
    }

    // ── Module placement (zigzag) ──

    private fun placeDataModules(matrix: Array<BooleanArray>, bits: List<Boolean>) {
        var bitIdx = 0
        var col = SIZE - 1
        var upward = true

        while (col > 0) {
            if (col == 6) col-- // Skip vertical timing pattern column

            if (upward) {
                for (row in SIZE - 1 downTo 0) {
                    if (bitIdx < bits.size && isDataModule(row, col, matrix)) {
                        matrix[row][col] = bits[bitIdx]
                        bitIdx++
                    }
                    if (bitIdx < bits.size && isDataModule(row, col - 1, matrix)) {
                        matrix[row][col - 1] = bits[bitIdx]
                        bitIdx++
                    }
                }
            } else {
                for (row in 0 until SIZE) {
                    if (bitIdx < bits.size && isDataModule(row, col, matrix)) {
                        matrix[row][col] = bits[bitIdx]
                        bitIdx++
                    }
                    if (bitIdx < bits.size && isDataModule(row, col - 1, matrix)) {
                        matrix[row][col - 1] = bits[bitIdx]
                        bitIdx++
                    }
                }
            }

            upward = !upward
            col -= 2
            if (col == 6) col-- // Skip vertical timing pattern column
        }
    }

    private fun isDataModule(r: Int, c: Int, matrix: Array<BooleanArray>): Boolean {
        // Skip finder patterns and timing patterns
        if (r == 6 || c == 6) return false
        // Top-left finder + separators
        if (r < 9 && c < 9) return false
        // Top-right finder + separators
        if (r < 9 && c >= SIZE - 8) return false
        // Bottom-left finder + separators
        if (r >= SIZE - 8 && c < 9) return false
        return true
    }

    // ── Masking ──

    private val MASK_PATTERNS: List<(Int, Int) -> Boolean> = listOf(
        { i, j -> (i + j) % 2 == 0 },
        { _, j -> j % 2 == 0 },
        { i, _ -> i % 3 == 0 },
        { i, j -> (i + j) % 3 == 0 },
        { i, j -> (i / 2 + j / 3) % 2 == 0 },
        { i, j -> (i * j) % 2 + (i * j) % 3 == 0 },
        { i, j -> ((i * j) % 2 + (i * j) % 3) % 2 == 0 },
        { i, j -> ((i + j) % 2 + (i * j) % 3) % 2 == 0 }
    )

    private fun applyBestMask(matrix: Array<BooleanArray>) {
        val dataCopy = Array(SIZE) { r -> BooleanArray(SIZE) { c -> matrix[r][c] } }

        var bestMask = 0
        var bestScore = Int.MAX_VALUE
        val bestMatrix = Array(SIZE) { BooleanArray(SIZE) }

        for (maskIdx in MASK_PATTERNS.indices) {
            val maskFn = MASK_PATTERNS[maskIdx]
            val testMatrix = Array(SIZE) { r -> BooleanArray(SIZE) { c -> dataCopy[r][c] } }

            for (r in 0 until SIZE) {
                for (c in 0 until SIZE) {
                    if (isDataModule(r, c, dataCopy)) {
                        testMatrix[r][c] = dataCopy[r][c] xor maskFn(r, c)
                    }
                }
            }

            val score = evaluateMask(testMatrix)
            if (score < bestScore) {
                bestScore = score
                bestMask = maskIdx
                for (r in 0 until SIZE) {
                    for (c in 0 until SIZE) {
                        bestMatrix[r][c] = testMatrix[r][c]
                    }
                }
            }
        }

        // Apply best mask
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                matrix[r][c] = bestMatrix[r][c]
            }
        }
    }

    private fun evaluateMask(matrix: Array<BooleanArray>): Int {
        var score = 0

        // Penalty 1: consecutive modules in rows/columns
        for (r in 0 until SIZE) {
            var run = 0
            var last = false
            for (c in 0 until SIZE) {
                if (matrix[r][c] == last) run++ else { if (run >= 5) score += run - 2; run = 1; last = matrix[r][c] }
            }
            if (run >= 5) score += run - 2
        }
        for (c in 0 until SIZE) {
            var run = 0
            var last = false
            for (r in 0 until SIZE) {
                if (matrix[r][c] == last) run++ else { if (run >= 5) score += run - 2; run = 1; last = matrix[r][c] }
            }
            if (run >= 5) score += run - 2
        }

        // Penalty 2: 2×2 blocks
        for (r in 0 until SIZE - 1) {
            for (c in 0 until SIZE - 1) {
                if (matrix[r][c] == matrix[r][c + 1] &&
                    matrix[r][c] == matrix[r + 1][c] &&
                    matrix[r][c] == matrix[r + 1][c + 1]
                ) score += 3
            }
        }

        return score
    }

    // ── Format info (5 bits EC level + 3 bits mask, BCH encoded) ──

    private fun placeFormatInfo(matrix: Array<BooleanArray>, ecLevel: Int, mask: Int) {
        val formatBits = (ecLevel shl 3) or mask
        var encoded = formatBits
        // BCH(15,5) encoding
        for (i in 0 until 10) {
            if (encoded and (1 shl (14 - i)) != 0) {
                encoded = encoded xor (0x537 shl (4 - i))
            }
        }
        val final = ((formatBits shl 10) or (encoded and 0x3FF)) xor 0x5412

        // Place in matrix
        val coords = listOf(
            // Around top-left finder
            8 to 0, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5, 8 to 7, 8 to 8,
            7 to 8, 5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8,
            // Bottom-left + top-right
            // (simplified placement for the two copies)
        )

        for (i in 0 until 15) {
            val bit = (final shr i) and 1 != 0
            when (i) {
                in 0..7 -> matrix[SIZE - 1 - i][8] = bit
                in 8..14 -> matrix[8][SIZE - 15 + i] = bit
            }
        }
        // Dark module
        matrix[SIZE - 8][8] = true
    }
}