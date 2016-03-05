package org.example.proyectobase;

import android.app.Activity;
import android.os.Bundle;

import android.util.Log;

import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static  final String TAG = "Ejemplo OCV (MainActivity)";
    private CameraBridgeViewBase cameraView;
    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV se cargo correctamente");
                    cameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        cameraView = (CameraBridgeViewBase)findViewById(R.id.vista_camara);
        cameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if(cameraView !=null){
            cameraView.disableView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, loaderCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(cameraView != null)
            cameraView.disableView();
    }

   //Interface cvcameraVierListener2

    public void onCameraViewStarted(int width,int height){}

    public void onCameraViewStopped(){}

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputframe){
        return inputframe.rgba();
    }

    public void onPackageInstall(int operation, InstallCallbackInterface callback){}


}
