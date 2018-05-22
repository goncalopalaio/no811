package gplio.com.no811

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.*
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.graphics.Bitmap
import android.opengl.GLES20


/**
 * Created by goncalo on 09-11-2017.
 */

class NovRenderer(private val context: Context,
                  private val cameraTextureHandler: CameraTextureHandler,
                  private val queueBitmap: (Bitmap) -> Unit, private val onFrameAvailableListener: SurfaceTexture.OnFrameAvailableListener): GLSurfaceView.Renderer {
    var tick = 0.0f
    val tickIncrement = 0.1f


    private var surfaceWidth = 0
    private var surfaceHeight = 0

    val renderObjects = ArrayList<SimpleShape>()

    val viewMatrix = FloatArray(16)
    val projectionMatrix = FloatArray(16)
    val viewProjectionMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.5f, 0.5f , 5f, 1.0f)

        //renderObjects.add(SimpleShape(SimpleShapeType.CUBE))
        renderObjects.add(SimpleShape(SimpleShapeType.QUAD))
        renderObjects.add(SimpleShape(SimpleShapeType.QUAD))
        renderObjects.add(SimpleShape(SimpleShapeType.QUAD))

        //renderObjects.add(SimpleShape(SimpleShapeType.TRIANGLE))
        //renderObjects.add(SimpleShape(SimpleShapeType.CUBE))

    }


    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0,0, width, height)

        renderObjects.forEach { it.init(context, width, height, cameraTextureHandler, onFrameAvailableListener, "trigrid.png", DEFAULT_VERTEX_SHADER, DEFAULT_FRAGMENT_SHADER) }

        var aspectRatio = 1f
        var left = -1f
        var right = 1f
        var bottom = -1f
        var top = 1f
        val near = -1f
        val far = 114f

        if (width > height) {
            aspectRatio = width / height.toFloat()
            left = -aspectRatio
            right = aspectRatio
        } else {
            aspectRatio = height / width.toFloat()
            bottom = -aspectRatio
            top = aspectRatio
        }

        Matrix.orthoM(projectionMatrix, 0, left, right, bottom, top, near, far)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setLookAtM(viewMatrix, 0,1f,0.5f,1f,0f, 0.5f, 0f, 0f,1f ,0f)

        Matrix.multiplyMM(viewProjectionMatrix, 0,
                projectionMatrix, 0, viewMatrix,0)
    }

    override fun onDrawFrame(gl: GL10?) {
        val startTime = System.currentTimeMillis()
        tick += tickIncrement

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)


        cameraTextureHandler.update()

        var x = 0.0f
        renderObjects.forEach {
            x+=x + 0.4f
            it.update(tick * x, viewProjectionMatrix, cameraTextureHandler.oesTextureHandle)
            it.draw(tick)
        }

        val bitmap = saveTexture(surfaceWidth,surfaceHeight)
        //var bitmap: Bitmap? = null
        queueBitmap(bitmap)
    }

    fun saveTexture(width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocate(width * height * 4)
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun log(message: String) {
        Log.d("Renderer", message);
    }

    companion object {
        val DEFAULT_VERTEX_SHADER = """
            uniform mat4 mvp;
            uniform float time;
            attribute vec4 position;
            attribute vec2 uv;
            varying float vTime;
            varying vec2 vUv;
            void main() {
                vTime = time;
                vUv = uv;
                gl_Position = mvp * position;
            }
            """
        val DEFAULT_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform vec4 color;
            varying float vTime;
            varying vec2 vUv;


            uniform samplerExternalOES sTexture;

            uniform sampler2D texture;
            void main() {
            	//vec2 position = abs((gl_FragCoord.xy / vec2(100.0,100.0)) - vec2(0.5));
                //gl_FragColor = color;
                gl_FragColor = texture2D(sTexture, vUv);
            }
            """
    }
}

enum class SimpleShapeType {
    TRIANGLE, CUBE, QUAD
}

class SimpleShape(shapeType: SimpleShapeType = SimpleShapeType.TRIANGLE) {
    var vertexBuffer: FloatBuffer? = null
    var uvsBuffer: FloatBuffer? = null
    var vertexStride = 0
    var vertexCount = 0
    var oesTextureHandle = 0

    var runtimeProgram = 0
    var runtimeAttributePositionLoc = 0
    var runtimeAttributeUVsLoc = 0
    var runtimeUniformColorLoc = 0
    var runtimeUniformMVPLoc = 0
    var runtimeUniformTimeLoc = 0
    var runtimeUniformTextureLoc = 0
    var runtimeUniformTexture2Loc = 0
    val textures = IntArray(1)

    val mvpMatrix: FloatArray = FloatArray(16)

    init {
        var coordinates: FloatArray? = null
        var uvs: FloatArray? = null
        if (shapeType == SimpleShapeType.TRIANGLE) {
            coordinates = triangleCoordinates

        }
        else if(shapeType == SimpleShapeType.QUAD){
            coordinates = quadCoordinates
            uvs = quadUvs
        } else {
            coordinates = cubeCoordinates
        }
        vertexStride = COORDS_PER_VERTEX * BYTES_PER_FLOAT
        vertexCount = coordinates.size / COORDS_PER_VERTEX

        var bb: ByteBuffer = ByteBuffer.allocateDirect(coordinates.size * BYTES_PER_FLOAT)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer?.put(coordinates)
        vertexBuffer?.position(0)

        if (uvs != null) {
            bb = ByteBuffer.allocateDirect(uvs.size * BYTES_PER_FLOAT)
            bb.order(ByteOrder.nativeOrder())
            uvsBuffer= bb.asFloatBuffer()
            uvsBuffer?.put(uvs)
            uvsBuffer?.position(0)
        }
    }

    fun init(context: Context, width:Int, height:Int, cameraTextureHandler: CameraTextureHandler, onFrameAvailableListener: SurfaceTexture.OnFrameAvailableListener, textureName: String, vertexShaderCode: String, fragmentShaderCode: String) {
        runtimeProgram = ShaderUtil.createGLShaderProgram(vertexShaderCode, fragmentShaderCode)

        GLES20.glUseProgram(runtimeProgram)

        runtimeAttributePositionLoc = GLES20.glGetAttribLocation(runtimeProgram, "position")
        runtimeAttributeUVsLoc = GLES20.glGetAttribLocation(runtimeProgram, "uv")
        ShaderUtil.checkGLError("Init", "Configuring attributes")

        runtimeUniformColorLoc = GLES20.glGetUniformLocation(runtimeProgram, "color");
        runtimeUniformMVPLoc = GLES20.glGetUniformLocation(runtimeProgram, "mvp");
        runtimeUniformTimeLoc = GLES20.glGetUniformLocation(runtimeProgram, "time");
        runtimeUniformTextureLoc = GLES20.glGetUniformLocation(runtimeProgram, "texture");
        runtimeUniformTexture2Loc = GLES20.glGetUniformLocation(runtimeProgram, "texture2");
        ShaderUtil.checkGLError("Init", "Configuring uniforms")

        // Read the texture.
        val textureBitmap = BitmapFactory.decodeStream(
                context.getAssets().open(textureName))

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGenTextures(textures.size, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        cameraTextureHandler.init(width, height, onFrameAvailableListener)

        ShaderUtil.checkGLError("Init", "Texture loading")

    }

    fun update(tick:Float, viewProjectionMatrix: FloatArray, oesTextureHandle: Int) {
        updateModelMatrix(viewProjectionMatrix, 0f,0f,0f, 270f)
        this.oesTextureHandle = oesTextureHandle
    }

    fun draw(tick:Float) {
        GLES20.glUseProgram(runtimeProgram)

        GLES20.glEnableVertexAttribArray(runtimeAttributePositionLoc)

        GLES20.glVertexAttribPointer(
                runtimeAttributePositionLoc, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false,
                vertexStride, vertexBuffer)

        if (uvsBuffer != null) {
            GLES20.glEnableVertexAttribArray(runtimeAttributeUVsLoc)
            GLES20.glVertexAttribPointer(
                    runtimeAttributeUVsLoc, UVS_PER_VERTEX, GLES20.GL_FLOAT,
                    false, 0, uvsBuffer);
        }

        GLES20.glUniform4fv(runtimeUniformColorLoc, 1, COLOR_RED, 0)

        GLES20.glUniformMatrix4fv(runtimeUniformMVPLoc, 1, false, mvpMatrix, 0)

        GLES20.glUniform1f(runtimeUniformTimeLoc, tick);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glUniform1i(runtimeUniformTextureLoc, 0)

        //GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        //GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureHandle);
        //GLES20.glUniform1i(runtimeUniformTexture2Loc, 1)


        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(runtimeAttributePositionLoc)
        if (uvsBuffer != null) {
            GLES20.glDisableVertexAttribArray(runtimeAttributeUVsLoc)
        }

        GLES20.glUseProgram(0)
    }

    private fun updateModelMatrix(viewProjection:FloatArray, x:Float, y:Float, z:Float, angle:Float) {
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.translateM(mvpMatrix,0, mvpMatrix, 0, x, y, z)
        val scale = 3.2f;
        Matrix.scaleM(mvpMatrix,0,mvpMatrix,0,scale, scale,scale);
        Matrix.rotateM(mvpMatrix, 0, angle, 0.0f, 0.0f, 1.0f);
        Matrix.multiplyMM(mvpMatrix, 0, viewProjection, 0, mvpMatrix, 0)
    }

    companion object {
        val COORDS_PER_VERTEX = 3
        val UVS_PER_VERTEX = 2
        val BYTES_PER_FLOAT = 4
        val COLOR_RED = floatArrayOf( 1.0f, 0.01f, 0.01f, 1f )
        val COLOR_GREEN = floatArrayOf( 0.01f, 1.0f, 0.01f, 1f )
        val COLOR_BLUE = floatArrayOf( 0.01f, 0.01f, 1.0f, 1f )

        // @note this val is not needed after we upload it
        val triangleCoordinates by lazy {
            floatArrayOf(   0.0f,  0f, 0.5f,
                    -0.5f,0f,  -0.5f,
                    0.5f,0f, -0.5f)
        }

        val cubeCoordinates by lazy {
            floatArrayOf(
                    -1.0f,-1.0f,-1.0f,
                    -1.0f,-1.0f, 1.0f,
                    -1.0f, 1.0f, 1.0f,
                    1.0f, 1.0f,-1.0f,
                    -1.0f,-1.0f,-1.0f,
                    -1.0f, 1.0f,-1.0f,
                    1.0f,-1.0f, 1.0f,
                    -1.0f,-1.0f,-1.0f,
                    1.0f,-1.0f,-1.0f,
                    1.0f, 1.0f,-1.0f,
                    1.0f,-1.0f,-1.0f,
                    -1.0f,-1.0f,-1.0f,
                    -1.0f,-1.0f,-1.0f,
                    -1.0f, 1.0f, 1.0f,
                    -1.0f, 1.0f,-1.0f,
                    1.0f,-1.0f, 1.0f,
                    -1.0f,-1.0f, 1.0f,
                    -1.0f,-1.0f,-1.0f,
                    -1.0f, 1.0f, 1.0f,
                    -1.0f,-1.0f, 1.0f,
                    1.0f,-1.0f, 1.0f,
                    1.0f, 1.0f, 1.0f,
                    1.0f,-1.0f,-1.0f,
                    1.0f, 1.0f,-1.0f,
                    1.0f,-1.0f,-1.0f,
                    1.0f, 1.0f, 1.0f,
                    1.0f,-1.0f, 1.0f,
                    1.0f, 1.0f, 1.0f,
                    1.0f, 1.0f,-1.0f,
                    -1.0f, 1.0f,-1.0f,
                    1.0f, 1.0f, 1.0f,
                    -1.0f, 1.0f,-1.0f,
                    -1.0f, 1.0f, 1.0f,
                    1.0f, 1.0f, 1.0f,
                    -1.0f, 1.0f, 1.0f,
                    1.0f,-1.0f, 1.0f
            )
        }

        val quadCoordinates by lazy {
            floatArrayOf(
                    // Left bottom triangle
                    // Right top triangle
                    0.5f, -0.5f, 0f,
                    0.5f, 0.5f, 0f,
                    -0.5f, 0.5f, 0f,
                    -0.5f, 0.5f, 0f,
                    -0.5f, -0.5f, 0f,
                    0.5f, -0.5f, 0f
            )
        }

        val quadUvs by lazy {
            floatArrayOf(
                    1.0f, 0.0f,
                    1.0f, 1.0f,
                    0.0f, 1.0f,
                    0.0f, 1.0f,
                    0.0f, 0.0f,
                    1.0f, 0.0f
            )
        }
    }
}