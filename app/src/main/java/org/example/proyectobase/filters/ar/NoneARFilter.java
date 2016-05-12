package org.example.proyectobase.filters.ar;

import org.example.proyectobase.filters.NoneFilter;
import org.opencv.core.Mat;

/**
 * Created by javier on 12/05/2016.
 */
public class NoneARFilter extends NoneFilter implements ARFilter {

    @Override
    public float[] getGLPose() {
        return null;
    }

}
