package org.example.proyectobase;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.util.Log;

import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * Created by javier on 08/05/2016.
 */
public class OCVStereoRenderer implements CardboardView.StereoRenderer, CameraBridgeViewBase.CvCameraViewListener2 {



    /****************CARDBOARD******************************/
    private static final String TAG = "VrStereoRenderer";
    private final static int FLOAT_SIZE_BYTES = 4;
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;

    private volatile boolean mIsStarting;
    private volatile boolean mIsReady;

    private final Context mContext;
    private final CardboardView mCardboardView;

    @SuppressWarnings("deprecation")
    private Camera mCamera;

    private boolean mSurfaceChanged;
    private int mViewWidth;
    private int mViewHeight;

    private int mGLProgram;
    private int mTexHandle;
    private int mTexCoordHandle;
    private int mTriangleVerticesHandle;
    private int mTransformHandle;
    private int mRotateHandle;
    private SurfaceTexture mSurfaceTexture = null;
    private final FloatBuffer mTextureVertices;
    private final FloatBuffer mQuadVertices;
    private final float[] mTransformMatrix;
    private final float[] mRotateMatrix;

    private AtomicInteger mCameraFrameCount = new AtomicInteger();
    private int mLastLeftEyeFrameCount;
    private int mLastRightEyeFrameCount;

    public OCVStereoRenderer(final Context context, final CardboardView cardboardView) {

        mContext = context;
        mCardboardView = cardboardView;

        final float[] textureVertices = { 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f };
        mTextureVertices = ByteBuffer.allocateDirect(textureVertices.length *
                FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTextureVertices.put(textureVertices).position(0);
        // WARNING! mTextureVertices buffer is defined again later in changeTextureVertices() method.

        final float[] quadVertices = { 1.0f, 1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f };
        mQuadVertices = ByteBuffer.allocateDirect(quadVertices.length *
                FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mQuadVertices.put(quadVertices).position(0);

        mTransformMatrix = new float[16];
        mRotateMatrix = new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};


        /*opencv*/

    }

    public synchronized void start() {
        if (mIsReady || mIsStarting) {
            return;
        }

        mCameraFrameCount.set(0);
        mLastLeftEyeFrameCount = 0;
        mLastRightEyeFrameCount = 0;

        mIsStarting = true;
    }

    public synchronized void stop() {
        if (!mIsReady) {
            return;
        }

        mIsReady = false;
        mIsStarting = false;
        mSurfaceChanged = false;

        try {
            mCamera.setPreviewTexture(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;

        mSurfaceTexture.release();

        Log.d(TAG, "Camera.release");
    }

    @SuppressWarnings("unused")
    public synchronized boolean isStarted() {
        return mIsReady;
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        mSurfaceChanged = false;
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        mViewWidth = width;
        mViewHeight = height;
        mSurfaceChanged = true;
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
    }

    @Override
    public void onDrawEye(Eye eye) {
        if (!mIsReady && mIsStarting) {
            final Viewport viewport = eye.getViewport();
            final int width = viewport.width;
            final int height = viewport.height;
            doStart(width, height);
        }

        if (!(mIsReady && mSurfaceChanged)) {
            GLES20.glClearColor(0, 0, 0, 0);
            checkGlError("draw eye [glClearColor]");
            return;
        }

        final int cameraFrameCount = mCameraFrameCount.get();
        if ((mLastLeftEyeFrameCount != cameraFrameCount) ||
                (mLastRightEyeFrameCount != cameraFrameCount) ||
                (!mCardboardView.getVRMode())) { // getVRMode should be removed. There's a flickering bug in monocular mode.
            // Missed onDrawEye methods cause black screen (only in monocular). So it's a fix.

            GLES20.glUseProgram(mGLProgram);
            checkGlError("draw eye [glUseProgram]");

            //COGEMOS NUEVA IMAGEN
            mSurfaceTexture.updateTexImage();
            mSurfaceTexture.getTransformMatrix(mTransformMatrix);

            GLES20.glUniformMatrix4fv(mTransformHandle, 1, false, mTransformMatrix, 0);
            checkGlError("draw eye [glUniformMatrix4fv #1]");

            GLES20.glUniformMatrix4fv(mRotateHandle, 1, false, mRotateMatrix, 0);
            checkGlError("draw eye [glUniformMatrix4fv #2]");

            GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT,
                    false, 0, mTextureVertices);
            checkGlError("draw eye [glVertexAttribPointer #1]");
            GLES20.glVertexAttribPointer(mTriangleVerticesHandle, 2, GLES20.GL_FLOAT,
                    false, 0, mQuadVertices);
            checkGlError("draw eye [glVertexAttribPointer #2]");
            GLES20.glUniform1i(mTexHandle, 0);
            checkGlError("draw eye [glUniform1i]");
            GLES20.glEnableVertexAttribArray(mTexCoordHandle);
            checkGlError("draw eye [glEnableVertexAttribArray #1]");
            GLES20.glEnableVertexAttribArray(mTriangleVerticesHandle);
            checkGlError("draw eye [glEnableVertexAttribArray #2]");

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4);
            checkGlError("draw eye [glDrawArrays]");

            switch (eye.getType()) {
                case Eye.Type.MONOCULAR:
                    mLastLeftEyeFrameCount = cameraFrameCount;
                    mLastRightEyeFrameCount = cameraFrameCount;
                    break;
                case Eye.Type.LEFT:
                    mLastLeftEyeFrameCount = cameraFrameCount;
                    break;
                case Eye.Type.RIGHT:
                    mLastRightEyeFrameCount = cameraFrameCount;
                    break;
            }
        }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    @Override
    public void onRendererShutdown() {
        // Doesn't work :(
        Log.d(TAG, "onRendererShutdown");

        mSurfaceChanged = false;
    }

    private int createTexture()
    {
        int[] texture = new int[1];
        GLES20.glGenTextures(1,texture, 0);
        checkGlError("createTexture #1");
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture[0]);
        checkGlError("createTexture #2");
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        checkGlError("createTexture #3");
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        checkGlError("createTexture #4");
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        checkGlError("createTexture #5");
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        checkGlError("createTexture #6");
        return texture[0];
    }

    private void doStart(final int eyeViewportWidth, final int eyeViewportHeight) {
        @SuppressWarnings("deprecation")
        final Camera camera = openFacingBackCamera();
        if (camera == null) {
            return;
        }
        mCamera = camera;
        Log.d(TAG, "Camera.open");


        mGLProgram = createProgram(R.raw.vertex, R.raw.fragment);
        mTexHandle = GLES20.glGetUniformLocation(mGLProgram, "s_texture");
        mTexCoordHandle = GLES20.glGetAttribLocation(mGLProgram, "a_texCoord");
        mTriangleVerticesHandle = GLES20.glGetAttribLocation(mGLProgram, "vPosition");
        mTransformHandle = GLES20.glGetUniformLocation(mGLProgram, "u_xform");
        mRotateHandle = GLES20.glGetUniformLocation(mGLProgram, "u_rotation");
        GLES20.glUseProgram(mGLProgram);
        checkGlError("initialization #1");

        changeCameraPreviewSize(eyeViewportWidth, eyeViewportHeight);

        final SurfaceTexture oldSurfaceTexture = mSurfaceTexture;

        mSurfaceTexture = new SurfaceTexture(createTexture());
        mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {

                mCameraFrameCount.incrementAndGet();
//                if (mCardboardView != null) {
//                    mCardboardView.requestRender();
//                }
            }
        });
        if (oldSurfaceTexture != null) {
            oldSurfaceTexture.release();
        }

        try {
            mCamera.setPreviewTexture(mSurfaceTexture);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
        Log.d(TAG, "Camera.startPreview");

        mIsReady = true;
        mIsStarting = false;
    }

    @SuppressWarnings("deprecation")
    private void changeCameraPreviewSize(final int eyeViewportWidth, final int eyeViewportHeight) {
        final CameraPreviewSizes previewSizes = new CameraPreviewSizes();

        final Camera.Parameters cameraParameters = mCamera.getParameters();

        List<Camera.Size> sizes = cameraParameters.getSupportedPreviewSizes();
        String sizesText = "";
        previewSizes.clear();
        for (final Camera.Size size : sizes) {
            previewSizes.add(size.width, size.height);
            if (!sizesText.equals("")) {
                sizesText += ", ";
            }
            sizesText += getSizeDescription(size.width, size.height);
        }

        Log.d(TAG, "Preview sizes: [" + sizesText + "]");
        Log.d(TAG, "CardboardView size: " + getSizeDescription(mCardboardView.getWidth(), mCardboardView.getHeight()));
        Log.d(TAG, "CardboardView screen size: " + getSizeDescription(mCardboardView.getScreenParams().getWidth(), mCardboardView.getScreenParams().getHeight()));
        Log.d(TAG, "onSurfaceChanged viewport: " + getSizeDescription(mViewWidth, mViewHeight));
        Log.d(TAG, "Eye viewport size: " + getSizeDescription(eyeViewportWidth, eyeViewportHeight));

        final float eyeRatio = (float) eyeViewportWidth / eyeViewportHeight;
        final CameraPreviewSizes.CameraPreviewSize bestSize = previewSizes.getBestSize(eyeRatio);
        if (bestSize != null) {
            Log.d(TAG, "Best preview size: " + getSizeDescription(bestSize.getWidth(), bestSize.getHeight()));
            cameraParameters.setPreviewSize(bestSize.getWidth(), bestSize.getHeight());
            mCamera.setParameters(cameraParameters);

            // Eye pixel ratio and camera preview pixel ratio are not the same.
            // That's why we cannot use all of the preview area.
            changeTextureVertices(eyeRatio, bestSize.getRatio());
        }
    }

    private void changeTextureVertices(final float eyeRatio, final float previewRatio) {
        float X1, X2, Y1, Y2;
        if (previewRatio >= eyeRatio) {
            X1 = 0.5f - 0.5f * eyeRatio / previewRatio;
            X2 = 1.0f - X1;
            Y1 = 0.0f;
            Y2 = 1.0f;
        } else {
            X1 = 0.0f;
            X2 = 1.0f;
            Y1 = 0.5f - 0.5f * previewRatio / eyeRatio;
            Y2 = 1.0f - Y1;
        }
        final float[] textureVertices = { X2, Y2, X1, Y2, X1, Y1, X2, Y1 };
        mTextureVertices.clear();
        mTextureVertices.put(textureVertices).position(0);
    }


    private static String getSizeDescription(final int width, final int height) {
        return String.format("%dx%d(%.2f)", width, height, (float) width / height);
    }

    @SuppressWarnings("deprecation")
    private Camera openFacingBackCamera() {
        final int cameraCount = Camera.getNumberOfCameras();
        final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int cameraIndex = 0; cameraIndex < cameraCount; cameraIndex++) {
            Camera.getCameraInfo(cameraIndex, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return Camera.open(cameraIndex);
            }
        }
        return null;
    }

    private String readRawTextFile(int resId) {
        InputStream inputStream = mContext.getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private int loadShader(final int shaderType, final int shaderResId) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            final String source = readRawTextFile(shaderResId);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    private void checkGlError(String op) {
        int error;
        //noinspection LoopStatementThatDoesntLoop
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
        }
    }

    private int createProgram(final int vertexShaderResId, final int fragmentShaderResId) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderResId);
        if (vertexShader == 0) {
            return 0;
        }
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderResId);
        if (fragmentShader == 0) {
            return 0;
        }
        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("createProgram [attach vertex shader]");
            GLES20.glAttachShader(program, fragmentShader);
            checkGlError("createProgram [attach fragment shader]");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    /****************OPENCV******************************/
    private CameraBridgeViewBase cameraView;

    private int indiceCamara; // 0 -> camara trasera y 1 -> camara delantera
    private int cam_anchura = 1920;
    private int cam_altura = 1080;
    private static final String STATE_CAMERA_INDEX = "cameraIndex";

    private int tipoEntrada = 0; // 0 -> camara 1 -> fichero1 2-> fichero2
    Mat imagenRecurso;
    boolean recargarRecurso = false;


    Mat entrada = null;

    Mat gris = null;

    @Override
    public void onCameraViewStarted(int width, int height) {
        cam_altura = height;
        cam_anchura = width;
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputframe){

        if (tipoEntrada == 0){

            if(entrada != null){
                entrada.release();
            }

            if(gris != null)
            {
                gris.release();
            }

        /*entrada = inputframe.rgba().clone();
        Mat esquina = entrada.submat(0,10,0,10);
        esquina.setTo(new Scalar(255,255,255,255));*/


            entrada = inputframe.rgba().clone();

            gris = inputframe.gray().clone();


            //Filtro gaussiano y canny para poder ver las lineas de la imagen
            Size s = new Size(3,3);
            Imgproc.GaussianBlur(gris,gris,s,1.5);
            Imgproc.Canny(gris,gris,45,60,3,true);

            List<MatOfPoint> contornos = new ArrayList<MatOfPoint>();

            //encontramos los contornos en la imagen gris
            Imgproc.findContours(gris,contornos,new Mat(),Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);

            MatOfPoint2f mMOP2f1 = new MatOfPoint2f();
            MatOfPoint2f esquinas = new MatOfPoint2f();

            for (int i = 0; i < contornos.size(); i++) {

                //Convertir contornos(i) de MatOfPoint a MatOfPoint2f
                contornos.get(i).convertTo(mMOP2f1, CvType.CV_32FC2);


                //Processing on mMOP2f1 which is in type MatOfPoint2f
                Imgproc.approxPolyDP(mMOP2f1, esquinas, 5, true);

                //calculamos el area del contorno
                double area = Imgproc.contourArea(esquinas);

            /*Log.d(TAG,"AREA: "+area+" esquinaWidth: "+esquinas.width()+" esquinaHeigth: "+esquinas.height()+
            " Esquinas.size "+esquinas.size().toString());*/


                if(((esquinas.height() % 2 == 0)) && (area > 300)){

                    //Convertir de nuevo a MatOfPoint y colocar los nuevos valores
                    esquinas.convertTo(contornos.get(i), CvType.CV_32S);

                    Imgproc.drawContours(entrada, contornos, i, new Scalar(0, 0, 255), -1);
                }
            }


        }else{
            if(recargarRecurso == true){
                imagenRecurso = new Mat();

                //Metemos a un array los ficheros que tenemos
                int RECURSOS_FICHEROS[] = {0, R.raw.logoastronotuya,R.raw.logoninadelsur};

               // Bitmap bitmap = BitmapFactory.decodeResource( getResources(),RECURSOS_FICHEROS[tipoEntrada]);

                //Convierte el recurso a una map de opencv, despues de pasarlo a bitmap

               // Utils.bitmapToMat(bitmap,imagenRecurso);

                recargarRecurso=false;
            }
            entrada = imagenRecurso;
        }

        Mat salida = entrada;
        //entrada.release();
        if(tipoEntrada > 0) //Si estamos mostrando una imagen entonces tenemos que adaptarla a la camara
            Imgproc.resize(salida,salida,new Size(cam_anchura,cam_altura));
        return salida;
    }

    @Override
    public void onCameraViewStopped() {  }


    public void setCameraViewListener(CameraBridgeViewBase cameraView) {
        cameraView.setCvCameraViewListener(this);
    }
}
