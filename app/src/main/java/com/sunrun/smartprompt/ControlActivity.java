package com.sunrun.smartprompt;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import android.widget.EditText;


import com.sunrun.smartprompt.model.Status;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Scanner;

public class ControlActivity extends AppCompatActivity {

    EditText script_container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        script_container = findViewById(R.id.txt_script_entry);
        Intent intent = getIntent();
        String scriptText = "SD";
        scriptText = intent.getStringExtra(Intent.EXTRA_TEXT);

        //App sent plain text in the intent
        if (scriptText != null) {
            script_container.setText(scriptText);
        }
        //Google docs returns a "content://" Uri
        else{
            //Try to extract data from uri content
            InputStream inputStream = null;
            try {
                Uri uri = (Uri) intent.getExtras().get("android.intent.extra.STREAM");
                ContentResolver resolver = getContentResolver();
                inputStream = resolver.openInputStream(uri);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            String text = convertStreamToString(inputStream);
            Status.setScript(text);
            script_container.setText(text);
        }

    }

    private String convertStreamToString(InputStream is) {
        Scanner scanner = new Scanner(is).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }


}