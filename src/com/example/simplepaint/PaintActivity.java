
package com.example.simplepaint;

import java.io.File;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.example.simplepaint.ics.R;

public class PaintActivity extends Activity {
    private static final String EXTRA_STROKE_FILE = "STROKE_FILE";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        String strokeString = PaintView.readStrokeFile(getIntent(), EXTRA_STROKE_FILE);
        if (strokeString != null) {
            PaintFragment canvas = (PaintFragment) getFragmentManager().findFragmentById(
                    R.id.canvas);
            canvas.setStrokeString(strokeString);
        }
    }

    public static void startActivity(Context context, File strokeFile) {
        Intent intent = new Intent(context, PaintActivity.class);
        if (strokeFile != null) {
            intent.putExtra(EXTRA_STROKE_FILE, strokeFile);
        }
        context.startActivity(intent);
    }
}
