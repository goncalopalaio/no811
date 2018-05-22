package gplio.com.no811

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ArrayBlockingQueue


class MainActivity : AppCompatActivity() {
    private val backgroundRenderer: BackgroundRenderer = BackgroundRenderer()
    private lateinit var recognitionThread: RecognitionThread
    private lateinit var gestureDetector: GestureDetector
    private lateinit var onFrameAvailableListener: SurfaceTexture.OnFrameAvailableListener
    private val queuedSingleTaps: ArrayBlockingQueue<MotionEvent> = ArrayBlockingQueue(16)
    private val queuedBitmaps: ArrayBlockingQueue<Bitmap> = ArrayBlockingQueue(3)

    private val cameraTextureHandler = CameraTextureHandler()
    private var updateTexture = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gestureDetector = GestureDetector(
                this,
                object: GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent?): Boolean {
                        onSingleTap(e)
                        return true
                    }

                    override fun onDown(e: MotionEvent?): Boolean {
                        return true
                    }
                }
        )

        onFrameAvailableListener = SurfaceTexture.OnFrameAvailableListener {
            updateTexture = true
        }

        recognitionThread = RecognitionThread(assets)

        val queueBitmap = fun (bitmap:Bitmap) {
            recognitionThread.queueRecognition(bitmap)
        }

        val novRenderer = NovRenderer(this@MainActivity, cameraTextureHandler, queueBitmap, onFrameAvailableListener)
        surfaceView.apply {
            setOnTouchListener({
                _, e -> return@setOnTouchListener gestureDetector.onTouchEvent(e)
            })
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0) // Alpha used for plane blending.
            setRenderer(novRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    private fun startRecognition() {
        val handler = Handler();

        val onStatus = fun (text:String) {
            Log.d("TAG", "Initialized")
        }

        val onRecognition = fun (text:String, bitmap:Bitmap){
             handler.post({
                 textViewRecognition.text = text
                 imageView.setImageBitmap(bitmap)
             })
        }

        recognitionThread.start(onStatus, onRecognition)
        recognitionThread.queueLoadModel()
    }

    override fun onResume() {
        super.onResume()

        startRecognition()

        if (CameraPermissionHelper.hasCameraPermission(this)) {
            surfaceView.onResume()
        } else {
            CameraPermissionHelper.requestCameraPermission(this)
        }
    }

    override fun onPause() {
        super.onPause()

        surfaceView.onPause()
        cameraTextureHandler.destroy()
    }

    override fun onDestroy() {
        recognitionThread.stop()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permissions required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun onSingleTap(e: MotionEvent?) {
        queuedSingleTaps.offer(e)
    }
}
