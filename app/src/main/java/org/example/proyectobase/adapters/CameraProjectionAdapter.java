package org.example.proyectobase.adapters;

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.opengl.Matrix;

import org.opencv.core.CvType;
import org.opencv.core.MatOfDouble;

/**
 * Created by javier on 12/05/2016.
 */
public class CameraProjectionAdapter {

    //parametros por defecto de camara
    float mFOVY = 43.6f;
    float mFOVX = 65.4f;

    int mHeightPx = 640;
    int mWidthPx = 480;

    float mNear = 1f;
    float mFar = 10000f;

    final float[] mProjectionGL = new float[16];
    boolean mProjectionDirtyGL = true;

    MatOfDouble mProjectionCV;
    boolean mProjectionDirtyCV = true;

    public void setCameraParameters(Parameters parameters, final Size imageSize){

        mFOVX = parameters.getVerticalViewAngle();
        mFOVY = parameters.getHorizontalViewAngle();

        mHeightPx = imageSize.height;
        mWidthPx = imageSize.width;

        mProjectionDirtyGL = true;
        mProjectionDirtyCV = true;

    }

    public float getAspectRatio(){
        return (float)mWidthPx / (float) mHeightPx;
    }

    public void setClipDistances(float near, float far){
        mNear = near;
        mFar = far;
        mProjectionDirtyGL = true;
    }

    public float[] getProjectionGL(){
        if(mProjectionDirtyGL){
            final float right = (float) Math.tan(0.5f * mFOVX * Math.PI / 180f) * mNear;
            //Calcular los limites verticales con los limites horizontales y el aspect ratio
            final float top = right /getAspectRatio();
            //calculo de perspectiva (similar a matriz de proyeccion)
            Matrix.frustumM(mProjectionGL, 0 , -right, right, -top, top, mNear, mFar );
            mProjectionDirtyGL = false;
        }
        return mProjectionGL;
    }

    public MatOfDouble getProjectionCV(){
        if(mProjectionDirtyCV){
            if(mProjectionCV == null){
                mProjectionCV = new MatOfDouble();
                mProjectionCV.create(3,3, CvType.CV_64FC1);
            }
            /**
             * diagonalFov = 2 * atan(0.5 * diagonalPx / focalLengthPx)
             *
             * focalLengthPx = 0.5 * diagonalPx / tan (0.5 * diagonalFOV)
             *
             * la diagonalFov se transforma ---->
             *
             * focalLengthPx = 0.5 * diagonalPx / sqrt((tan(0.5 *fovX))^2 + (tan(0.5 * fovY)^2))
             *
             */
            final float fovAspectRatio = mFOVX / mFOVY;

            final double diagonalPx = Math.sqrt((Math.pow(mWidthPx,2.0) + Math.pow(mWidthPx/fovAspectRatio,2.0)));

            final double focalLengthPx = 0.5 *diagonalPx / Math.sqrt(
                    Math.pow(Math.tan(0.5 * mFOVX * Math.PI / 180f), 2.0) +
                    Math.pow(Math.tan(0.5 * mFOVY * Math.PI / 180f), 2.0));


            // matriz de proyeccion
            mProjectionCV.put(0,0, focalLengthPx);
            mProjectionCV.put(0,1, 0.0);
            mProjectionCV.put(0,2, 0.5 * mWidthPx);
            mProjectionCV.put(1,0, 0.0);
            mProjectionCV.put(1,1, focalLengthPx);
            mProjectionCV.put(1,2, 0.5 * mHeightPx);
            mProjectionCV.put(2,0, 0.0);
            mProjectionCV.put(2,1, 0.0);
            mProjectionCV.put(2,2, 1.0);

        }
        return mProjectionCV;
    }
}
