package org.example.proyectobase.filters;

import org.opencv.core.Mat;

/**
 * Created by javier on 12/05/2016.
 */
public class NoneFilter implements Filter {

    @Override
    public void apply(Mat src, Mat dst) {
        //Do nothing
    }
}
