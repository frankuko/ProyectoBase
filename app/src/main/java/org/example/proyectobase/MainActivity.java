package org.example.proyectobase;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import android.support.annotation.LayoutRes;
import android.util.Log;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraGLSurfaceView;
import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.hardware.Camera;
import android.widget.Toast;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends CardboardActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static  final String TAG = "Ejemplo OCV (MainActivity) + Cardboard";
    private CameraBridgeViewBase cameraView;

    private static final boolean VR_MODE = true; // Set VR_MODE to false to select monocular mode.
    private VrStereoRenderer mStereoRenderer;



    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV cargo");
                    //cameraView.setMaxFrameSize(cam_anchura,cam_altura);
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

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);


        setContentView(R.layout.activity_open_cvar);

        //Dete
        /* cardboard*/
        /*com.google.vrtoolkit.cardboard.CardboardView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:id="@+id/cardboard_view"/>*/

        final CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);

        cardboardView.setVRModeEnabled(VR_MODE);
        cardboardView.setSettingsButtonEnabled(VR_MODE);

        //mStereoRenderer = new VrStereoRenderer(this,cardboardView);

        //PASAR VARIABLES DE OPENCV

        cardboardView.setRenderer(mStereoRenderer);
        setCardboardView(cardboardView);

        /*fin cardboard*/

        /*opencv*/
       /*<org.opencv.android.JavaCameraView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:id="@+id/vista_camara"
        opencv:show_fps="true"/>*/

        cameraView = (CameraBridgeViewBase)findViewById(R.id.vista_camara);

        //cameraView = (CameraBridgeViewBase)findViewById(R.id.cardboard_view);

        //mStereoRenderer.setCameraViewListener(cameraView);
        cameraView.setCvCameraViewListener(this);

        if(savedInstanceState != null){
            indiceCamara = savedInstanceState.getInt(STATE_CAMERA_INDEX,0);
        }
        else{
            indiceCamara = 0;
        }

        cameraView.setCameraIndex(indiceCamara);
        /*fin opencv*/
    }

    @Override
    public void onPause() {
        super.onPause();
        if(cameraView !=null){
            cameraView.disableView();
        }
        //
         //mStereoRenderer.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, loaderCallback);
       // mStereoRenderer.start();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if(cameraView != null)
            cameraView.disableView();
    }

    private void mySetContentView(int resourceId) {
        setContentView(resourceId);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        openOptionsMenu();

        //mySetContentView(R.id.cardboard_view);
        //setContentView(R.layout.activity_open_cvar);
        //CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);


        //cardboardView.renderUiLayer();
        //startActivity(new Intent(MainActivity.this,cardboardView.getClass()));
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.d(TAG, "onWindowFocusChanged(hasFocus=" + hasFocus + ")");
        super.onWindowFocusChanged(hasFocus);
    }
    //Interface cvcameraVierListener2

    public void onCameraViewStarted(int width,int height){
        cam_altura = height;
        cam_anchura = width;
    }

    public void onCameraViewStopped(){}

    Mat entrada = null;

    Mat gris = null;

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputframe){

        if (tipoEntrada == 0){

            if(entrada != null){
                entrada.release();
            }

            if(gris != null)
            {
                gris.release();
            }

            entrada = inputframe.rgba().clone();
            Mat esquina = entrada.submat(0,10,0,10);
            esquina.setTo(new Scalar(255,255,255,255));



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

Log.d(TAG,"AREA: "+area+" esquinaWidth: "+esquinas.width()+" esquinaHeigth: "+esquinas.height()+
                " Esquinas.size "+esquinas.size().toString());



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
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(),RECURSOS_FICHEROS[tipoEntrada]);

                //Convierte el recurso a una map de opencv, despues de pasarlo a bitmap

                Utils.bitmapToMat(bitmap,imagenRecurso);
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

    public void onPackageInstall(int operation, InstallCallbackInterface callback){}


}
