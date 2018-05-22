package gplio.com.no811

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Trace
import android.util.Log
import hugo.weaving.DebugLog
import org.tensorflow.Operation
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.Buffer
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.Comparator
import kotlin.collections.ArrayList

/**
 * Created by goncalo on 09-11-2017.
 */

class InceptionClassifier(assetManager: AssetManager) : TensorFlowClassifier(assetManager,
        modelFilename =  "tensorflow_inception_graph.pb",
        labelFilename =  "imagenet_comp_graph_label_strings.txt",
        inputSize = 224,
        imageMean = 117,
        imageStd = 1f,
        inputName = "input",
        outputName = "output"){

    // These are the settings for the original v1 Inception model. If you want to
    // use a model that's been produced from the TensorFlow for Poets codelab,
    // you'll need to set IMAGE_SIZE = 299, IMAGE_MEAN = 128, IMAGE_STD = 128,
    // INPUT_NAME = "Mul", and OUTPUT_NAME = "final_result".
    // You'll also need to update the MODEL_FILE and LABEL_FILE paths to point to
    // the ones you produced.
    //
    // To use v3 Inception model, strip the DecodeJpeg Op from your retrained
    // model first:
    //
    // python strip_unused.py \
    // --input_graph=<retrained-pb-file> \
    // --output_graph=<your-stripped-pb-file> \
    // --input_node_names="Mul" \
    // --output_node_names="final_result" \
    // --input_binary=true
}


open class TensorFlowClassifier(assetManager: AssetManager,
                           val modelFilename:String,
                           val labelFilename: String,
                           val inputSize: Int,
                           val imageMean: Int,
                           val imageStd: Float,
                           val inputName: String,
                           val outputName: String) {
    private val THRESHOLD = 0.005f

    private var labels: ArrayList<String>
    private var inferenceInterface: TensorFlowInferenceInterface

    private var outputOp: Operation?


    private var outputNames: Array<String>

    private var intValues: IntArray

    private var floatValues: FloatArray

    private var outputs: FloatArray

    init {
        labels = readFileLinesFromAssets(assetManager, labelFilename)
        inferenceInterface = TensorFlowInferenceInterface(assetManager, modelFilename)

        outputOp = inferenceInterface.graphOperation(outputName)
        val numberClasses = outputOp?.output<Float>(0)?.shape()?.size(1) ?: -1
        log("Number of classes $numberClasses labels: ${labels.size}")

        outputNames = arrayOf(outputName)
        intValues = IntArray(inputSize * inputSize)
        floatValues = FloatArray(inputSize * inputSize * 3)
        outputs = FloatArray(numberClasses.toInt())


        log("outputNames : ${outputNames.joinToString(separator = "|")}")
    }

    fun recognize(inputBitmap: Bitmap): ConcurrentLinkedQueue<Recognition> {
        Trace.beginSection("recognizeImage")
        val resized = getResizedBitmap(inputBitmap, inputSize, inputSize)
        val bitmap = resized.copy(resized.config, false)

        log("Resized bitmap: ${bitmap.width}, ${bitmap.height}")
        bitmap.getPixels(intValues, 0, bitmap.width, 0,0, bitmap.width, bitmap.height)
        for (i in 0 until intValues.size) {
            val v = intValues[i]
            floatValues[i * 3 + 0] =  ((v shr 16 and 0xFF) - imageMean) / imageStd
            floatValues[i * 3 + 1] = ((v shr 8 and 0xFF) - imageMean) / imageStd
            floatValues[i * 3 + 2] = ((v and 0xFF) - imageMean) / imageStd
        }

        inferenceInterface.feed(inputName, floatValues, 1L, inputSize.toLong(), inputSize.toLong(), 3L)
        inferenceInterface.run(outputNames, true)
        inferenceInterface.fetch(outputName, outputs)


        val pq = PriorityQueue<Recognition>(3, {
            rhs, lhs ->
            // @note bleh
            if (rhs.confidence < lhs.confidence) {
                return@PriorityQueue 1
            } else if (rhs.confidence > lhs.confidence) {
                return@PriorityQueue -1
            }
            return@PriorityQueue 0
        })

        outputs.forEachIndexed( {i, output ->
            if (output > THRESHOLD) {
                val label = if (labels.size > i) labels[i] else "unknown label"
                pq.add(Recognition("i:$i", label, output, RectF(0f,0f,0f,0f)))
            }
        })

        pq.forEach {
            log(it.toString())
        }
        log("-- end --")

        val n = minOf(pq.size, 3)
        val recognitions = ConcurrentLinkedQueue<Recognition>()
        for (i in 0 until n) {
            recognitions.add(pq.poll())
        }

        Trace.endSection()
        return recognitions
    }

    fun close() {
        inferenceInterface.close()
    }

    private fun log(message: String) {
        Log.d("TensorFlowClassifier", message)
    }

    fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)

        val resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false)
        bm.recycle()
        return resizedBitmap
    }

    data class Recognition(val id:String, val title: String, val confidence: Float, val location: RectF)
}