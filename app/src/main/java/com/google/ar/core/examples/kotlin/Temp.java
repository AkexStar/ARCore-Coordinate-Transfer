package com.google.ar.core.examples.kotlin;

import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.ar.core.RecordingConfig;
import com.google.ar.core.RecordingStatus;
import com.google.ar.core.examples.kotlin.helloar.R;
import com.google.ar.core.exceptions.RecordingFailedException;

public class Temp {
    public enum AppState{
        Idle,
        Recording
    };
    private AppState appState = AppState.Idle;


//    private void updateRecordButton() {
//        View buttonView = findViewById(R.id.record_button);
//        Button button = (Button) buttonView;
//
//        switch (appState) {
//            case Idle:
//                button.setText("Record");
//                break;
//            case Recording:
//                button.setText("Stop");
//                break;
//        }
//    }


}
