package com.example.east.scanner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dtr.zxing.activity.CaptureActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setup();
    }

    private void setup(){
        findViewById(R.id.btScan).setOnClickListener(this);

        openScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == 1221) {
            String sn = data.getStringExtra("sn");
            TextView tv = (TextView)findViewById(R.id.textview_msg);
            tv.setText(sn);
            Toast.makeText(this,sn,Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.btScan) {
            openScan();
        }
    }

    private void openScan() {
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.putExtra(CaptureActivity.KEY_INPUT_MODE, CaptureActivity.INPUT_MODE_QR);
        startActivityForResult(intent, 1111);
    }

}
