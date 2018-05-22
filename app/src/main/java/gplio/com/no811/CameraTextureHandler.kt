package gplio.com.no811

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log

/**
 * Created by goncalo on 09-11-2017.
 */
class CameraTextureHandler {
    var oesTextureHandle = 0
    var transformationMatrix = FloatArray(16)
    private var camera: Camera? = null
    var surfaceTexture: SurfaceTexture? = null
    private val ratios = FloatArray(2)
    private val orientationMatrix = FloatArray(16)

    init {
        Matrix.setIdentityM(transformationMatrix, 0)
    }

    fun init(width:Int, height: Int, onFrameAvailableListener: SurfaceTexture.OnFrameAvailableListener) {
        // Initialize OES texture
        val mTextureHandles = IntArray(1)
        GLES20.glGenTextures(1, mTextureHandles, 0)
        oesTextureHandle = mTextureHandles[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureHandles[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 1)

        // Initialize surface texture
        if (surfaceTexture != null){
            surfaceTexture?.release()
        }

        surfaceTexture = SurfaceTexture(oesTextureHandle)
        surfaceTexture?.setOnFrameAvailableListener(onFrameAvailableListener)

        // Initialize Camera
        camera?.stopPreview()
        camera?.release()


        val info = Camera.CameraInfo()

        // Try to find a front-facing camera (e.g. for videoconferencing).
        val numCameras = Camera.getNumberOfCameras()
        for (i in 0 until numCameras) {
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                camera = Camera.open(i)
                break
            }
        }

        try {
            camera?.setPreviewTexture(surfaceTexture)
        } catch (e: Exception) {
            Log.d("CameraTextureHandler", "Exception: ${e.localizedMessage}")
        }

        val params = camera?.parameters
        val supportedPreviews = params?.supportedPreviewSizes

        supportedPreviews?.forEach { Log.d("Previews", "Preview: ${it.width} ${it.height}" ) }

        val lastPreview = (supportedPreviews?.size ?: 1) - 1

        val cameraWidth = supportedPreviews?.get(lastPreview)?.width ?: 0
        val cameraHeight = supportedPreviews?.get(lastPreview)?.height ?: 0

        params?.setPreviewSize(cameraWidth, cameraHeight)

        Matrix.setRotateM(orientationMatrix, 0, 90.0f, 0f, 0f, 1f);
        ratios[1] = cameraWidth*1.0f/height;
        ratios[0] = cameraWidth*1.0f/width;

        camera?.parameters = params
        camera?.startPreview()
        Log.d("CameraTextureHandler", "Starting Camera Preview $cameraWidth $cameraHeight")
    }

    fun update() {
        surfaceTexture?.updateTexImage()
        //surfaceTexture?.getTransformMatrix(transformationMatrix)
    }

    fun destroy() {
        surfaceTexture?.release();
        camera?.apply {
            stopPreview()
            setPreviewCallback(null)
            release()
        }
        camera = null
    }



}