package org.example.proyectobase;

import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.example.proyectobase.adapters.CameraProjectionAdapter;
import org.example.proyectobase.filters.ar.ARFilter;
import org.example.proyectobase.filters.ar.ImageDetectionFilter;
import org.example.proyectobase.filters.ar.NoneARFilter;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.io.IOException;
import java.util.List;

/**
 * Created by javier on 13/05/2016.
 */
@SuppressWarnings("deprecation")
public class CameraActivity extends ActionBarActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    //tag
    private static final String TAG =
            CameraActivity.class.getSimpleName();

    private static final String STATE_CAMERA_INDEX = "cameraIndex";

    private static final String STATE_CAMERA_SIZE_INDEX = "imageSizeIndex";

    private static final String STATE_IMAGE_DETECTION_FILTER_INDEX = "imageDetectionFilterIndex";

    private static final int MENU_GROUP_ID_SIZE = 2;

    private ARFilter[] mImageDetectionFilters;

    private int mImageDetectionFilterIndex;

    private int mCameraIndex;

    private int mImageSizeIndex;

    private boolean mIsCameraFrontFacing;

    private int mNumCameras;

    private List<Camera.Size> mSupporteImageSizes;

    private CameraBridgeViewBase mCameraView;

    private CameraProjectionAdapter mCameraProjectionAdapter;

    private ARCubeRenderer mARRenderer;

    private boolean mIsPhotoPending;

    private Mat mBgr;

    //menu sincrono
    private boolean mIsMenuLocked;

    //callback de opencV

    private BaseLoaderCallback mLoaderCallback =
            new BaseLoaderCallback(this) {
                @Override
                public void onManagerConnected(int status) {
                    switch (status) {
                        case LoaderCallbackInterface.SUCCESS:
                            Log.d(TAG, "OpenCV se cargo correctamente");
                            mCameraView.enableView();
                            mBgr = new Mat();

                            final ARFilter starryNight;

                            try {
                                starryNight = new ImageDetectionFilter(
                                        CameraActivity.this,
                                        R.drawable.starry_night,
                                        mCameraProjectionAdapter);
                            } catch (IOException e) {
                                Log.e(TAG, "Fallo al cargar");
                                e.printStackTrace();
                                break;
                            }

                            mImageDetectionFilters = new ARFilter[]{
                                    new NoneARFilter(),
                                    starryNight
                            };
                            break;
                        default:
                            super.onManagerConnected(status);
                            break;

                    }
                }

            };


    @Override
    protected void onCreate(final Bundle savedInstance){
        super.onCreate(savedInstance);

        final Window window = getWindow();
        window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        if(savedInstance != null){
            mCameraIndex = savedInstance.getInt(STATE_CAMERA_INDEX, 0);

            mImageSizeIndex = savedInstance.getInt(STATE_CAMERA_SIZE_INDEX, 0);

            mImageDetectionFilterIndex = savedInstance.getInt(STATE_IMAGE_DETECTION_FILTER_INDEX,0);

        } else {
            mCameraIndex = 0;
            mImageSizeIndex = 0;
            mImageDetectionFilterIndex = 0;
        }

        final FrameLayout layout = new FrameLayout(this);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(layout);

        mCameraView = new JavaCameraView(this, mCameraIndex);
        mCameraView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        layout.addView(mCameraView);

        //OPENGL
        GLSurfaceView glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        glSurfaceView.setZOrderOnTop(true);
        glSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        layout.addView(glSurfaceView);

        mCameraProjectionAdapter = new CameraProjectionAdapter();

        mARRenderer = new ARCubeRenderer();
        mARRenderer.cameraProjectionAdapter = mCameraProjectionAdapter;

        //escala de la imagen a 1.0, definimos el cubo a 0.5f
        glSurfaceView.setRenderer(mARRenderer);

        final Camera camera;

        //Version de android
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraIndex,cameraInfo);

        mIsCameraFrontFacing = (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        mNumCameras = Camera.getNumberOfCameras();

        //por cada open de camara un release
        camera = Camera.open(mCameraIndex);

        final Camera.Parameters parameters = camera.getParameters();

        camera.release();

        mSupporteImageSizes = parameters.getSupportedPreviewSizes();

        final Camera.Size size = mSupporteImageSizes.get(mImageSizeIndex);

        mCameraProjectionAdapter.setCameraParameters(parameters,size);

        mCameraView.setMaxFrameSize(size.width, size.height);
        mCameraView.setCvCameraViewListener(this);

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){
        savedInstanceState.putInt(STATE_CAMERA_INDEX,mCameraIndex);
        savedInstanceState.putInt(STATE_CAMERA_SIZE_INDEX,mImageSizeIndex);
        savedInstanceState.putInt(STATE_IMAGE_DETECTION_FILTER_INDEX,mImageDetectionFilterIndex);

        super.onSaveInstanceState(savedInstanceState);
    }


    @Override
    public void onPause(){
        if(mCameraView != null){
            mCameraView.disableView();
        }
        super.onPause();
    }
    @Override
    public void onResume(){
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0,this,mLoaderCallback);
        mIsMenuLocked = false;
    }

    @Override
    public void onDestroy(){
        if(mCameraView != null){
            mCameraView.disableView();
        }
        super.onDestroy();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        final Mat rgba = inputFrame.rgba();

        if(mImageDetectionFilters != null) {
            mImageDetectionFilters[mImageDetectionFilterIndex].apply(rgba,rgba);
        }
        if(mIsCameraFrontFacing){
            Core.flip(rgba, rgba,1);
        }
        return rgba;
    }
}
