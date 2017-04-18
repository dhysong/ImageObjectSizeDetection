package com.example.dhyso_000.sizedetectionapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class CalibrateActivity extends AppCompatActivity implements View.OnClickListener  {

    private Button btnSave;
    private EditText txtSensorWidth;
    private EditText txtSensorHeight;
    private EditText txtMinWidth;
    private EditText txtMinHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate);


        btnSave = (Button)findViewById(R.id.button3);
        btnSave.setOnClickListener(CalibrateActivity.this);

        Bundle extras = getIntent().getExtras();
        double sensorWidth = extras.getDouble("sensorWidth");
        double sensorHeight = extras.getDouble("sensorHeight");
        int minWidth = extras.getInt("minWidth");
        int minHeight = extras.getInt("minHeight");

        txtSensorWidth = (EditText)findViewById(R.id.editText);
        txtSensorHeight = (EditText)findViewById(R.id.editText2);
        txtMinWidth = (EditText)findViewById(R.id.editText3);
        txtMinHeight = (EditText)findViewById(R.id.editText4);

        txtSensorWidth.setText(Double.toString(sensorWidth));
        txtSensorHeight.setText(Double.toString(sensorHeight));
        txtMinWidth.setText(Integer.toString(minWidth));
        txtMinHeight.setText(Integer.toString(minHeight));
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent();
        intent.putExtra("sensorWidth", Double.parseDouble(txtSensorWidth.getText().toString()));
        intent.putExtra("sensorHeight", Double.parseDouble(txtSensorHeight.getText().toString()));
        intent.putExtra("minWidth", Integer.parseInt(txtMinWidth.getText().toString()));
        intent.putExtra("minHeight", Integer.parseInt(txtMinHeight.getText().toString()));
        setResult(1, intent);
        finish();
    }
}
