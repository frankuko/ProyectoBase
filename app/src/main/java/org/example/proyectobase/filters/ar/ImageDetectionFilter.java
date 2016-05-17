package org.example.proyectobase.filters.ar;

import android.content.Context;

import org.example.proyectobase.adapters.CameraProjectionAdapter;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by javier on 13/05/2016.
 */
public class ImageDetectionFilter implements ARFilter {

    private final Mat mReferenceImage;

    private final MatOfKeyPoint mReferenceKeypoints = new MatOfKeyPoint();

    private final Mat mReferenceDescriptors = new Mat();

    //cada punto esta representado por 2 floats de 32-bits
    private final Mat mReferenceCorners = new Mat(4,1, CvType.CV_32FC2);

    //Features de la escena
    private final MatOfKeyPoint mSceneKeypoints = new MatOfKeyPoint();

    //Descriptores de la escena
    private final Mat mSceneDescriptors = new Mat();

    private final Mat mCandidateSceneCorners = new Mat(4,1, CvType.CV_32FC2);

    private final Mat mSceneCorners = new Mat(0, 0, CvType.CV_32FC2);

    private final MatOfPoint mIntSceneCorners = new MatOfPoint();

    private final Mat mGraySrc = new Mat();


    private final MatOfDMatch mMatches = new MatOfDMatch();

    //feature detector, ORB

    private final FeatureDetector mFeatureDetector =
            FeatureDetector.create(FeatureDetector.ORB);

    private final DescriptorExtractor mDescriptorExtractor =
            DescriptorExtractor.create(DescriptorExtractor.ORB);

    //Descriptor Matcher

    private final DescriptorMatcher mDescriptorMatcher =
            DescriptorMatcher.create(
                    DescriptorMatcher.BRUTEFORCE_HAMMINGLUT);

    //Color para dibujar en la imagen

    private final Scalar mLineColor = new Scalar(0,255,0);


    //FILTRO TRACKING

    private final MatOfDouble mDistCoeffs = new MatOfDouble(0.0, 0.0, 0.0, 0.0);
    private final CameraProjectionAdapter mCameraProjectionAdapter;
    private final MatOfDouble mRVec = new MatOfDouble();
    private final MatOfDouble mTVec = new MatOfDouble();
    private final MatOfDouble mRotation = new MatOfDouble();
    private final float[] mGLPose = new float[16];

    private boolean mTargetFound = false;

    public ImageDetectionFilter(final Context context,
                                final int referenceImageResourceID,
                                final CameraProjectionAdapter cameraProjectionAdapter) throws IOException{

        //guardamos el cameraprojection
        mCameraProjectionAdapter = cameraProjectionAdapter;

        //carga imagen en formato BGR
        mReferenceImage = Utils.loadResource(context,referenceImageResourceID, Imgcodecs.CV_LOAD_IMAGE_COLOR);

        //Crear imagen RGBA y en formato gris
        final Mat referenceImageGray = new Mat();

        Imgproc.cvtColor(mReferenceImage, referenceImageGray,
                Imgproc.COLOR_BGR2GRAY);

        Imgproc.cvtColor(mReferenceImage, mReferenceImage,
                Imgproc.COLOR_BGR2RGBA);

        //guardar las referencias a las esquinas de la imagen

        mReferenceCorners.put(0,0,
                new double[]{0.0, 0.0});
        mReferenceCorners.put(1,0,
                new double[] {referenceImageGray.cols(), 0.0});
        mReferenceCorners.put(2,0,
                new double[]{ referenceImageGray.cols(),referenceImageGray.rows()});
        mReferenceCorners.put(3,0,
                new double[]{0.0, referenceImageGray.rows()});

        mFeatureDetector.detect(referenceImageGray,
                mReferenceKeypoints);

        mDescriptorExtractor.compute(referenceImageGray, mReferenceKeypoints,
                mReferenceDescriptors);


    }




    @Override
    public void apply(Mat src, Mat dst) {

        //Convertir la escena a escala de gris
        Imgproc.cvtColor(src,mGraySrc, Imgproc.COLOR_RGBA2GRAY);

        mFeatureDetector.detect(mGraySrc,mSceneKeypoints);

        mDescriptorExtractor.compute(mGraySrc,mSceneKeypoints,mSceneDescriptors);

        mDescriptorMatcher.match(mSceneDescriptors,mReferenceDescriptors,mMatches);

        //encontrar las esquinas en la imagen
        //findSceneCorners();

        //Necesitamos convertir los keypoints a 3d para poder computarlos
        //cogemos la matriz de proyeccion de opencv con el camera adapter
        //Resolvemos la posicion y rotacion del objeto con la ayuda de los keypoints
        //...
        findPose();

        //Dibujar
        draw(src,dst);



    }

    private void findPose(){
        final List<DMatch> matchesList = mMatches.toList();

        if(matchesList.size()<4){
            //hay pocas coincidencias
            return;
        }

        final  List<KeyPoint> referenceKeypointsList =
                mReferenceKeypoints.toList();

        final List<KeyPoint> sceneKeypointsList =
                mSceneKeypoints.toList();

        double maxDist = 0.0;
        double minDist = Double.MAX_VALUE;
        //Calcular las distancias max y min entre keypoints

        for(final DMatch match : matchesList){
            final double dist = match.distance;
            if(dist < minDist) {
                minDist = dist;
            }
            if(dist > maxDist){
                maxDist = dist;
            }

        }

        //PROBAR VARIOS UMBRALES AQUI en los puntos

        if(minDist > 50.0) {
            mSceneCorners.create(0,0, mSceneCorners.type());
            return;
        }
        else if(minDist > 25.0){
            //puede que este cerca el objetivo
            return;
        }

        //encontrar los puntos utiles dependiendo de la distancia

        final List<Point3> goodReferencePointsList =
                new ArrayList<Point3>();

        final ArrayList<Point> goodScenePointsList =
                new ArrayList<Point>();

        final double maxGoodMatchDist = 1.75 * minDist;

        for (final DMatch match : matchesList) {
            if (match.distance < maxGoodMatchDist) {

                //cambiamos por point3
                Point point = referenceKeypointsList.get(match.trainIdx).pt;
                Point3 point3 = new Point3(point.x,point.y,0.0);

                goodReferencePointsList.add(point3);

                goodScenePointsList.add(
                        sceneKeypointsList.get(match.queryIdx).pt);
            }
        }

        if (goodReferencePointsList.size() < 4 ||
                goodScenePointsList.size() <4){
            //pocas coincidencias
            return;
        }

        //Suficientes puntos en comun para encontrar la homografia
        //Convertimos los puntos a MatOfPoint3f

        final MatOfPoint3f goodReferencePoints = new MatOfPoint3f();
        goodReferencePoints.fromList(goodReferencePointsList);

        final MatOfPoint2f goodScenePoints = new MatOfPoint2f();
        goodScenePoints.fromList(goodScenePointsList);

        //proyeccion de camara
        MatOfDouble projection =
                mCameraProjectionAdapter.getProjectionCV();

        Calib3d.solvePnP(goodReferencePoints,goodScenePoints,projection,
                mDistCoeffs,mRVec,mTVec);

        //cambiamos el signo de vecArray
        double[] rVecArray = mRVec.toArray();
        rVecArray[1] *= -1.0;
        rVecArray[2] *= -1.0;
        mRVec.fromArray(rVecArray);

        Calib3d.Rodrigues(mRVec,mRotation);

        double[] tVecArray = mTVec.toArray();

        mGLPose[0] = (float)mRotation.get(0, 0)[0];
        mGLPose[1] = (float)mRotation.get(1, 0)[0];
        mGLPose[2] = (float)mRotation.get(2, 0)[0];
        mGLPose[3] = 0f;
        mGLPose[4] = (float)mRotation.get(0, 1)[0];
        mGLPose[5] = (float)mRotation.get(1, 1)[0];
        mGLPose[6] = (float)mRotation.get(2, 1)[0];
        mGLPose[7] = 0f;
        mGLPose[8] = (float)mRotation.get(0, 2)[0];
        mGLPose[9] = (float)mRotation.get(1, 2)[0];
        mGLPose[10] = (float)mRotation.get(2, 2)[0];
        mGLPose[11] = 0f;
        mGLPose[12] = (float)tVecArray[0];
        mGLPose[13] = (float)tVecArray[1];
        mGLPose[14] = (float)tVecArray[2];
        mGLPose[15] = 1f;

        mTargetFound = true;



    }

    private void findSceneCorners(){
        final List<DMatch> matchesList = mMatches.toList();

        if(matchesList.size()<4){
            //hay pocas coincidencias
            return;
        }

        final  List<KeyPoint> referenceKeypointsList =
                mReferenceKeypoints.toList();

        final List<KeyPoint> sceneKeypointsList =
                mSceneKeypoints.toList();

        double maxDist = 0.0;
        double minDist = Double.MAX_VALUE;
        //Calcular las distancias max y min entre keypoints

        for(final DMatch match : matchesList){
            final double dist = match.distance;
            if(dist < minDist) {
                minDist = dist;
            }
            if(dist > maxDist){
                maxDist = dist;
            }

        }

        //PROBAR VARIOS UMBRALES AQUI en los puntos

        if(minDist > 50.0) {
            mSceneCorners.create(0,0, mSceneCorners.type());
            return;
        }
        else if(minDist > 25.0){
            //puede que este cerca el objetivo
            return;
        }

        //encontrar los puntos utiles dependiendo de la distancia

        final ArrayList<Point> goodReferencePointsList =
                new ArrayList<Point>();

        final ArrayList<Point> goodScenePointsList =
                new ArrayList<Point>();

        final double maxGoodMatchDist = 1.75 * minDist;

        for (final DMatch match : matchesList) {
            if (match.distance < maxGoodMatchDist) {
                goodReferencePointsList.add(
                        referenceKeypointsList.get(match.trainIdx).pt);
                goodScenePointsList.add(
                        sceneKeypointsList.get(match.queryIdx).pt);
            }
        }

        if (goodReferencePointsList.size() < 4 ||
            goodScenePointsList.size() <4){
            //pocas coincidencias
            return;
        }

        //Suficientes puntos en comun para encontrar la homografia
        //Convertimos los puntos a MatOfPoint2f

        final MatOfPoint2f goodReferencePoints = new MatOfPoint2f();
        goodReferencePoints.fromList(goodReferencePointsList);

        final MatOfPoint2f goodScenePoints = new MatOfPoint2f();
        goodScenePoints.fromList(goodScenePointsList);

        //homografia
        Mat homography = Calib3d.findHomography(goodReferencePoints,goodScenePoints);

        //Usamos la homografia para proyectar los puntos en la imagen

        homography = Imgproc.getPerspectiveTransform(mReferenceCorners,
                mCandidateSceneCorners);

        mCandidateSceneCorners.convertTo(mIntSceneCorners, CvType.CV_32S);

        //Si  forman un poligono convexo se copian

        if(Imgproc.isContourConvex(mIntSceneCorners)){
            mCandidateSceneCorners.copyTo(mSceneCorners);
        }

    }

    protected void draw(final Mat src, final Mat dst){
        if(dst != src){
            src.copyTo(dst);
        }

        if(!mTargetFound){
            //objetivo no encontrado
            //dibujar el objeto que hay que encontrar

            int height = mReferenceImage.height();
            int width = mReferenceImage.width();

            final int maxDimension = Math.min(dst.width(),
                    dst.height() / 2);

            final double aspectRatio = width / (double) height;

            if(height > width){
                height = maxDimension;
                width = (int) (height * aspectRatio);
            } else{
                width = maxDimension;
                height = (int) (width / aspectRatio);
            }

            //Region of Intereset donde se dibujara el objetivo

            final Mat dstROI = dst.submat(0, height, 0, width);

            Imgproc.resize(mReferenceImage, dstROI,dstROI.size(),
                    0.0,0.0,Imgproc.INTER_AREA);

            return;
        }

        //Dibuja el objetivo

        Imgproc.line(dst, new Point(mSceneCorners.get(0,0)),
                new Point(mSceneCorners.get(1,0)), mLineColor, 4);
        Imgproc.line(dst, new Point(mSceneCorners.get(1,0)),
                new Point(mSceneCorners.get(2,0)), mLineColor, 4);
        Imgproc.line(dst, new Point(mSceneCorners.get(2,0)),
                new Point(mSceneCorners.get(3,0)), mLineColor, 4);
        Imgproc.line(dst, new Point(mSceneCorners.get(3,0)),
                new Point(mSceneCorners.get(0,0)), mLineColor, 4);
    }

    @Override
    public float[] getGLPose() {
        return (mTargetFound ? mGLPose : null);
    }

    /*protected void draw(final Mat src, final Mat dst){
        if(dst != src){
            src.copyTo(dst);
        }

        if(mSceneCorners.height() < 4){
            //objetivo no encontrado
            //dibujar el objeto que hay que encontrar

            int height = mReferenceImage.height();
            int width = mReferenceImage.width();

            final int maxDimension = Math.min(dst.width(),
                    dst.height() / 2);

            final double aspectRatio = width / (double) height;

            if(height > width){
                height = maxDimension;
                width = (int) (height * aspectRatio);
            } else{
                width = maxDimension;
                height = (int) (width / aspectRatio);
            }

            //Region of Intereset donde se dibujara el objetivo

            final Mat dstROI = dst.submat(0, height, 0, width);

            Imgproc.resize(mReferenceImage, dstROI,dstROI.size(),
                    0.0,0.0,Imgproc.INTER_AREA);

            return;
        }

        //Dibuja el objetivo

        Imgproc.line(dst, new Point(mSceneCorners.get(0,0)),
                new Point(mSceneCorners.get(1,0)), mLineColor, 4);
        Imgproc.line(dst, new Point(mSceneCorners.get(1,0)),
                new Point(mSceneCorners.get(2,0)), mLineColor, 4);
        Imgproc.line(dst, new Point(mSceneCorners.get(2,0)),
                new Point(mSceneCorners.get(3,0)), mLineColor, 4);
        Imgproc.line(dst, new Point(mSceneCorners.get(3,0)),
                new Point(mSceneCorners.get(0,0)), mLineColor, 4);
    }*/
}
