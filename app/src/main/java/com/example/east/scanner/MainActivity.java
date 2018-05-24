package com.example.east.scanner;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.dtr.zxing.activity.CaptureActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setup();
    }

    private void setup(){
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.putExtra(CaptureActivity.KEY_INPUT_MODE, CaptureActivity.INPUT_MODE_QR);
        startActivityForResult(intent, 1111);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        String sn = data.getStringExtra("sn");

        TextView tv = (TextView)findViewById(R.id.textview_msg);
        tv.setText(sn);

        Toast.makeText(this,sn,Toast.LENGTH_LONG);
    }
}
