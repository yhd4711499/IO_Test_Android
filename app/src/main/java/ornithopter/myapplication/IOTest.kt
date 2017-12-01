package ornithopter.myapplication

import android.content.Context
import java.io.*
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * @author haodongyuan on 17/11/28.
 * @since 6.5
 */
class IOTest(private val config: Config, private val context: Context) {
    data class Config(
            val fileSize: Long,
            val times: Int = 1,
            val bufferSizeFrom: Int,
            val bufferSizeTo: Int,
            val bufferSizeBy: Int,
            val bufferSizeByMethod: Int
    ) {
        var bufferSize: Int = 0

        companion object {
            const val BY_ADD = 1
            const val BY_MULTIPLY = 2
        }
    }

    abstract class TestCase(val name: String) {
        abstract fun setup(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int)

        abstract fun run(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int)

        abstract fun teardown()
    }

    data class Result(val name: String, val cost: Long, val bufferSize:Int, val fileSize: Long, val times: Int): Comparable<Result> {
        override fun compareTo(other: Result): Int {
            return (cost - other.cost).toInt()
        }
    }

    inner class InputStreamTestCase(@Transient val n: String, @Transient private val creator: (file: File, config: Config) -> InputStream) : TestCase(n) {
        private lateinit var inputStream: InputStream

        override fun setup(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int) {
            inputStream = creator.invoke(file, config)
        }

        override fun run(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int) {
            while (inputStream.read(buffer, offset, size) > 0) {

            }
        }

        override fun teardown() {
            inputStream.close()
        }
    }

    /**
     * @return cost in nano-sec
     */
    external fun prepareNative(filePath: String, bufferSize: Int): Long
    external fun startNative(param: Long): Long
    external fun teardownNative(param: Long): Long

    private var isCancelled: Boolean = false

    fun start(): List<Result> {
        return run(loadTestCases())
    }

    fun stop() {
        isCancelled = true
    }

    fun print(results: List<Result>): String {
        val sorted = results.sorted()

        val bench = sorted.first().cost

        val sb = StringBuilder()
        sb.append("name\tcost(ms)\tbufferSize(byte)\tfileSize(MB)\ttimes\tcompare")
        sorted.forEach {
            sb.append("\n")
            sb.append(it.name, "\t", Math.round(it.cost / 1e6), "\t", it.bufferSize, "\t", it.fileSize / (1024 * 1024), "\t", it.times, "\t", "x%.2f".format(it.cost.toDouble() / bench.toDouble()))
        }
        return sb.toString()
    }

    private fun run(testCases: List<TestCase>): List<Result> {
        val file = File.createTempFile("IOTEST", ".testfile")
        ensureFile(file, config)
        val results = mutableListOf<Result>()
        config.bufferSize = config.bufferSizeFrom
        while (!isCancelled && config.bufferSize <= config.bufferSizeTo) {
            val byteArray = ByteArray(config.bufferSize)
            testCases.forEach {
                results.add(runTestCase(it, file, byteArray, 0, byteArray.size))
            }

            when (config.bufferSizeByMethod) {
                Config.BY_ADD -> config.bufferSize += config.bufferSizeBy
                Config.BY_MULTIPLY -> config.bufferSize *= config.bufferSizeBy
            }
        }
        return results
    }

    private fun loadTestCases(): List<TestCase> {
        return arrayListOf(
                object : TestCase("RandomAccessFile") {
                    private lateinit var raf: RandomAccessFile

                    override fun setup(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int) {
                        raf = RandomAccessFile(file, "r")
                    }

                    override fun run(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int) {
                        while (raf.read(buffer, offset, size) > 0) {

                        }
                    }

                    override fun teardown() {
                        raf.close()
                    }
                },
                InputStreamTestCase("BufferedInputStream", { file, _ -> BufferedInputStream(FileInputStream(file)) }),
                InputStreamTestCase("FileInputStream", { file, _ -> FileInputStream(file) }),
                object : TestCase("ByteBuffer(Wrapped)") {
                    private lateinit var byteBuffer: ByteBuffer
                    private lateinit var raf: RandomAccessFile
                    private lateinit var channel: FileChannel
                    override fun setup(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int) {
                        byteBuffer = ByteBuffer.wrap(buffer)
                        raf = RandomAccessFile(file, "r")
                        channel = raf.channel
                    }

                    override fun run(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int) {

                        while (channel.read(byteBuffer) > 0) {
                            byteBuffer.clear()
                        }

                    }

                    override fun teardown() {
                        channel.close()
                        raf.close()
                    }
                },
                object : TestCase("MappedByteBuffer") {
                    private lateinit var mappedByteBuffer: MappedByteBuffer
                    private lateinit var raf: RandomAccessFile
                    private lateinit var channel: FileChannel

                    override fun setup(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int) {
                        raf = RandomAccessFile(file, "r")
                        channel = raf.channel
                        mappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                    }

                    override fun teardown() {
                        channel.close()
                        raf.close()
                    }

                    override fun run(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int) {
                        var remaining: Int
                        while (true) {
                            remaining = mappedByteBuffer.remaining()
                            if (remaining >= buffer.size) {
                                mappedByteBuffer.get(buffer)
                            } else if (remaining == 0) {
                                break
                            } else {
                                mappedByteBuffer.get(buffer, 0, remaining)
                            }
                        }
                    }
                },
                object : TestCase("fread") {
                    private var param: Long = 0L
                    override fun setup(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int) {
                        param = prepareNative(file.absolutePath, size)
                    }

                    override fun teardown() {
                        teardownNative(param)
                    }

                    override fun run(file: File, config: Config, buffer: ByteArray, offset: Int, size: Int) {
                        startNative(param)
                    }
                }
        )
    }

    private fun runTestCase(testCase: TestCase, file: File, byteArray: ByteArray, offset: Int, size: Int): Result {
        var totalCost = 0L
        var time = 0

        while (!isCancelled && time < config.times) {
            time++
            testCase.setup(file, config, byteArray, offset, size)
            val now = System.nanoTime()
            testCase.run(file, config, byteArray, offset, size)
            totalCost += (System.nanoTime() - now)
            testCase.teardown()
        }

        val cost = if (time == 0) 0 else totalCost / time
        return Result(testCase.name, cost, size, config.fileSize, config.times)
    }

    private fun ensureFile(file: File, config: Config) {
        val f = RandomAccessFile(file, "rw")
        f.setLength(config.fileSize)
        f.close()
    }
}