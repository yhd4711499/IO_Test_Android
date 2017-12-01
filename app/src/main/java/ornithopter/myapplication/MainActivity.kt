package ornithopter.myapplication

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    var ioTest: IOTest ? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton.setOnClickListener {
            if (ioTest == null) {
                start()
            } else {
                stop()
            }
        }
    }

    private fun stop() {
        startButton.text = "STOPPING"
        startButton.isEnabled = false
        ioTest?.stop()
        ioTest = null
    }

    private fun start() {
        startButton.text = "STOP"
        resultTextView.text = "Running..."
        Thread(Runnable {
            try {
                val methodString = bufferSizeByEditText.text
                val method = when (methodString[0]) {
                    'x',
                    'X',
                    '*' -> {
                        IOTest.Config.BY_MULTIPLY
                    }
                    '+' -> {
                        IOTest.Config.BY_ADD
                    }
                    else -> throw IllegalArgumentException("unsupported method: " + methodString[0])
                }
                val byAmount = methodString.substring(1).toInt()
                val ioTest = IOTest(
                        IOTest.Config(
                                fileSizeEditText.text.toString().toLong() * 1024 * 1024,
                                timesEditText.text.toString().toInt(),
                                bufferSizeFromEditText.text.toString().toInt(),
                                bufferSizeToEditText.text.toString().toInt(),
                                byAmount,
                                method
                        ),
                        this
                )

                this.ioTest = ioTest

                val result = ioTest.start()

                val print = ioTest.print(result)
                Log.i("RESULT", print)
                runOnUiThread {
                    resultTextView.text = print
                    startButton.text = "START"
                    startButton.isEnabled = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    resultTextView.text = e.message
                    startButton.text = "START"
                    startButton.isEnabled = true
                }
            } finally {
                this.ioTest = null
            }

        }).start()
    }

    companion object {

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}
