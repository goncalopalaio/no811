package gplio.com.no811

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log

/**
 * Created by goncalo on 10-11-2017.
 */
class RecognitionThread(val assetManager: AssetManager) {
    private var initialized = false;
    private lateinit var recognitionThread: HandlerThread
    private lateinit var recognitionHandler: Handler
    private lateinit var classifier: InceptionClassifier
    private @Volatile var inQueue = 0
    private enum class TaskType {
        INITIALIZE,
        RECOGNIZE
    }

    fun start(onStatus: (String) -> Unit, onRecognition: (String, Bitmap) -> Unit) {

        recognitionThread = HandlerThread("RecognitionThread", Process.THREAD_PRIORITY_BACKGROUND)
        recognitionThread.start();
        recognitionHandler = Handler(recognitionThread.looper, Handler.Callback {

            when (it.what) {
                TaskType.INITIALIZE.ordinal -> loadClassifier(onStatus)
                TaskType.RECOGNIZE.ordinal -> infer(it.obj as Bitmap, onRecognition)
                else -> {
                    log("Wrong task type ${it.what}")
                }
            }
            inQueue--
            return@Callback false
        });

        initialized = true
        inQueue = 0
    }

    private fun loadClassifier(onStatus: (String) -> Unit) {
        classifier = InceptionClassifier(assetManager)
        onStatus("SUCCESS")
    }

    private fun infer(bitmap: Bitmap, onRecognition: (String, Bitmap) -> Unit) {
        val copy = bitmap.copy(bitmap.getConfig(), true)

        val recognitions = classifier.recognize(bitmap)
        var text = ""
        recognitions.forEach {
            text += "${it.title} ${"%.2f".format(it.confidence)} \n"
        }


        onRecognition(text, copy)
    }

    fun stop() {
        initialized = false
        recognitionThread.quit()
    }

    fun queueLoadModel() {
        log("Queuing load model")
        queue(TaskType.INITIALIZE.ordinal, null)
    }

    fun queueRecognition(bitmap: Bitmap) {
        if(!initialized) {
            log( "Ignoring bitmap since thread is not initialized yet")
            return
        }
        log("Queuing bitmap")
        queue(TaskType.RECOGNIZE.ordinal,bitmap)
    }

    private fun queue(what: Int, bitmap: Bitmap?) {
        if (inQueue > MAX_IN_QUEUE) {
            log("Throttling : $inQueue max: $MAX_IN_QUEUE")
            return
        }
        inQueue++
        recognitionHandler.obtainMessage(what, bitmap).sendToTarget()
    }

    private companion object {
        val TAG = "RecognitionThread"
        val MAX_IN_QUEUE = 10
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }
}