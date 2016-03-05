package org.example.proyectobase;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import android.util.Log;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.hardware.Camera;
import android.widget.Toast;


public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static  final String TAG = "Ejemplo OCV (MainActivity)";
    private CameraBridgeViewBase cameraView;
    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV cargo");
                    cameraView.setMaxFrameSize(cam_anchura,cam_altura);
                    cameraView.enableView();
                    break;
                default:
                    Log.e(TAG, "OpenCV no se cargo");
                    Toast.makeText(MainActivity.this,"OpenCV no se cargo",Toast.LENGTH_LONG).show();
                    finish();
                    break;
            }
        }
    };

    private int indiceCamara; // 0 -> camara trasera y 1 -> camara delantera
    private int cam_anchura = 1920;
    private int cam_altura = 1080;
    private static final String STATE_CAMERA_INDEX = "cameraIndex";

    private int tipoEntrada = 0; // 0 -> camara 1 -> fichero1 2-> fichero2
    Mat imagenRecurso;
    boolean recargarRecurso = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);


        cameraView = (CameraBridgeViewBase)findViewById(R.id.vista_camara);
        cameraView.setCvCameraViewListener(this);

        if(savedInstanceState != null){
            indiceCamara = savedInstanceState.getInt(STATE_CAMERA_INDEX,0);
        }
        else{
            indiceCamara = 0;
        }

        cameraView.setCameraIndex(indiceCamara);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        openOptionsMenu();
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstaceState) {
        //guarda el indice actual de la camara
        savedInstaceState.putInt(STATE_CAMERA_INDEX,indiceCamara);
        super.onSaveInstanceState(savedInstaceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //super.onCreateOptionsMenu(menu);
        //menu.clear();
       // MenuInflater inflater = getMenuInflater();
        getMenuInflater().inflate(R.menu.menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.cambiarCamara:
                indiceCamara++;
                if (indiceCamara == Camera.getNumberOfCameras()){
                    indiceCamara = 0;
                }
                recreate();
                break;
            case R.id.resolucion_1920x1080:
                cam_anchura = 1920;
                cam_altura = 1080;
                reiniciarResolucion();
                break;
            case R.id.resolucion_800x600:
                cam_anchura = 800;
                cam_altura = 600;
                reiniciarResolucion();
                break;
            case R.id.resolucion_640x480:
                cam_anchura = 640;
                cam_altura = 480;
                reiniciarResolucion();
                break;
            case R.id.resolucion_320x240:
                cam_anchura = 320;
                cam_altura = 240;
                reiniciarResolucion();
                break;
            case R.id.entrada_camara:
                tipoEntrada = 0;
                break;
            case R.id.entrada_fichero1:
                tipoEntrada = 1;
                recargarRecurso = true;
                break;
            case R.id.entrada_fichero2:
                tipoEntrada = 2;
                recargarRecurso = true;
                break;

        }
        String msg = "W="+Integer.toString(cam_anchura)+" H="+
                Integer.toString(cam_altura)+ " Cam ="+
                Integer.toBinaryString(indiceCamara);
        Toast.makeText(MainActivity.this,msg,Toast.LENGTH_LONG).show();
        return true;
    }


    public void reiniciarResolucion(){
        cameraView.disableView();
        cameraView.setMaxFrameSize(cam_anchura,cam_altura);
        cameraView.enableView();

    }

    public void OnCameraViewStarted(int width, int height){
        cam_altura = height;
        cam_anchura = width;
    }

    //Interface cvcameraVierListener2

    public void onCameraViewStarted(int width,int height){}

    public void onCameraViewStopped(){}

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputframe){
       Mat entrada;
        if (tipoEntrada == 0){
            entrada = inputframe.rgba();

        }else{
            if(recargarRecurso == true){
                imagenRecurso = new Mat();

                //Metemos a un array los ficheros que tenemos
                int RECURSOS_FICHEROS[] = {0, R.raw.logoastronotuya,R.raw.logoninadelsur};
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(),RECURSOS_FICHEROS[tipoEntrada]);

                //Convierte el recurso a una map de opencv, despues de pasarlo a bitmap

                Utils.bitmapToMat(bitmap,imagenRecurso);
                recargarRecurso=false;
            }
            entrada = imagenRecurso;
        }

        Mat salida = entrada.clone();
        if(tipoEntrada > 0) //Si estamos mostrando una imagen entonces tenemos que adaptarla a la camara
            Imgproc.resize(salida,salida,new Size(cam_anchura,cam_altura));
        return salida;
    }

    public void onPackageInstall(int operation, InstallCallbackInterface callback){}


}
