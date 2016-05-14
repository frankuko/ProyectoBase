package org.example.proyectobase;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;

public class OpenCVARActivity extends CardboardActivity {

    private static final String TAG = "OpenCVARActivity";
    private static final boolean VR_MODE = true; // Set VR_MODE to false to select monocular mode.
    private VrStereoRenderer mStereoRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_cvar);

        final CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);

        cardboardView.setVRModeEnabled(VR_MODE);
        cardboardView.setSettingsButtonEnabled(VR_MODE);


        //mStereoRenderer = new VrStereoRenderer(this, cardboardView);

        // Associate a CardboardView.StereoRenderer with cardboardView.
        cardboardView.setRenderer(mStereoRenderer);
        // Associate the cardboardView with this activity.
        setCardboardView(cardboardView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        mStereoRenderer.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mStereoRenderer.start();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.d(TAG, "onWindowFocusChanged(hasFocus=" + hasFocus + ")");
        super.onWindowFocusChanged(hasFocus);
    }
}
