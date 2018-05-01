package com.example.xyz.voicecommands;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements RecognitionListener {
    private final String TAG="VoiceRecognition";
    private Button button,listenButton;
    private ProgressBar progressBar;
    private static SpeechRecognizer speechRecognizer;
    private static Intent recognizerIntent;
    private MediaPlayer mediaPlayer;
    private FloatingActionButton fab,silentfab;
    private NotificationCompat.Builder notificationBuilder;
    private PendingIntent pendingIntent;
    private JSONObject jsonObject;
    private File dir,file;
    private OutputStream os;
    private InputStream is;
    private ArrayList<String> signature;
    boolean listen;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        try {
            initializeValues();
        } catch (IOException e) {
            e.printStackTrace();
        }
        setUpUI();
        readAudio();


        List<ResolveInfo> activities=getPackageManager().queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),0);
        if(activities.size()!=0){
        }else{
            Toast.makeText(this, "Recognizer Not Present", Toast.LENGTH_SHORT).show();
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(speechRecognizer!=null) {
                    speechRecognizer.stopListening();
                    speechRecognizer.destroy();
                }
                listen=false;
                if(signature!=null && !signature.isEmpty()){
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Confirmation")
                            .setMessage("Alreay Have Signature Commands :\n"+TextUtils.join(",",signature)+"\nDo You Want to Continue?")
                            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    createSpeechAgain(MainActivity.this);

                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.cancel();

                                }
                            }).create().show();

                }else {
                    createSpeechAgain(MainActivity.this);
                }

            }
        });
        listenButton.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View view) {
                if(speechRecognizer!=null) {
                    speechRecognizer.stopListening();
                    speechRecognizer.destroy();
                }
                if(signature==null || signature.isEmpty()){

                    Snackbar.make(findViewById(R.id.lowerLayout),"No Signature Command!First Create ",Snackbar.LENGTH_SHORT).show();
                }else {
                    listen = true;
                    listenButton.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                    createSpeechAgain(MainActivity.this);
                }

            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String help;
                if(signature==null || signature.isEmpty())
                    help="No Signature Command Created";
                else {
                    help="Command is one of the following:\n";
                    help += TextUtils.join(",", signature);
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                            }
                        })
                        .setTitle("Help")
                        .setMessage(help)
                        .create().show();
            }
        });

        silentfab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mediaPlayer!=null && mediaPlayer.isPlaying()){
                    mediaPlayer.stop();
                    silentfab.setVisibility(View.INVISIBLE);
                    listenButton.setBackgroundColor(getResources().getColor(R.color.darkHoloRed));
                }else if(mediaPlayer==null){
                    Log.v(TAG,"MP NULL");
                }else{
                    Log.v(TAG,"MP NOT PLAY");
                }
            }
        });
    }

    private void initializeValues() throws IOException {
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED  ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)!= PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},1);
        listen=false;
        dir=new File(getExternalFilesDir(null).toURI());
        file=new File(dir,"commands.txt");
        checkFiles();
        InputStreamReader isr=new InputStreamReader(is);
        BufferedReader bf=new BufferedReader(isr);
        String msg="",line;
        while((line=bf.readLine())!=null){
            msg+=line;
        }
        JSONArray jsonArray=null;
        if(!msg.trim().isEmpty()){
            try {
                jsonObject=new JSONObject(msg);
                jsonArray=jsonObject.getJSONArray("signatureArray");
                Log.v(TAG,"MSG"+msg.equals(jsonObject.toString())+"  "+jsonObject.getJSONArray("signatureArray").length());
            } catch (JSONException e) {
                    e.printStackTrace();
            }
            signature=new ArrayList<>();
//            ArrayList item=jsonObject.optJSONArray("signatureArray");
            try {
                if(jsonArray!=null) {
                    Log.v(TAG,jsonArray.length()+"Is the length");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        signature.add(jsonArray.getString(i));
                        Log.v(TAG,i+" element");
                        Log.v(TAG, jsonArray.getString(i));
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
                Log.v(TAG,e.getMessage());
            }
          //  Toast.makeText(this, signature.size()+ " "+msg, Toast.LENGTH_SHORT).show();
        }else{
            Snackbar.make(findViewById(R.id.mainActivityLayout),"No Signature Command",Snackbar.LENGTH_SHORT);
        }
        isr.close();
        bf.close();

    }

    private void checkFiles() throws IOException {

        if(!dir.exists()){
            Toast.makeText(this, "Error while creating file", Toast.LENGTH_SHORT).show();
        }else{
            if(!file.exists()){
                    file.createNewFile();
            }
        }
        os=new FileOutputStream(file,true);
        is=new FileInputStream(file);

    }

    private void readAudio() {


        mediaPlayer=MediaPlayer.create(this,R.raw.ringtone);
        mediaPlayer.setLooping(true);


    }

    private void createSpeechAgain(MainActivity mainActivity) {
        progressBar.setVisibility(View.INVISIBLE);

        speechRecognizer=SpeechRecognizer.createSpeechRecognizer(mainActivity);
        speechRecognizer.setRecognitionListener(mainActivity);
        recognizerIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"en-US");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, mainActivity.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
//        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, Boolean.TRUE);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 20000);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 20000);

        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);

        speechRecognizer.startListening(recognizerIntent);


    }

    private void setUpUI() {
        button=findViewById(R.id.button);
        progressBar=findViewById(R.id.progressBar);
        listenButton=findViewById(R.id.listen);
        fab=findViewById(R.id.fab);

        silentfab=findViewById(R.id.stopfab);
        silentfab.setVisibility(View.INVISIBLE);


    }

    @Override
    public void onReadyForSpeech(Bundle bundle) {

    Log.v(TAG,"Ready For Speech");

    }

    @Override
    public void onBeginningOfSpeech() {
        Log.v(TAG,"Begin");
        progressBar.setIndeterminate(false);
        progressBar.setMax(10);


    }

    @Override
    public void onRmsChanged(float v) {
        Log.v(TAG,"RMS "+v);
        progressBar.setProgress((int)v);

    }

    @Override
    public void onBufferReceived(byte[] bytes) {
        Log.v(TAG,"Buffer "+bytes.toString());

    }

    @Override
    public void onEndOfSpeech() {
        Log.i(TAG, "onEndOfSpeech");
        progressBar.setIndeterminate(false);
        progressBar.setVisibility(View.INVISIBLE);
        speechRecognizer.stopListening();
    }

    @Override
    public void onError(int i) {
        Log.v(TAG,"Error ");
        String message;
        switch (i) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                Log.v(TAG, message);

                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                speechRecognizer.startListening(recognizerIntent);

                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                Log.v(TAG, message);

                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                speechRecognizer.startListening(recognizerIntent);

                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";

                Log.v(TAG, message);

                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                speechRecognizer.startListening(recognizerIntent);

                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                Log.v(TAG, message);
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                Log.v(TAG, message);
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                Log.v(TAG, message);
                if(!listen) {
                    Toast.makeText(this, "Please Be Clear!", Toast.LENGTH_LONG).show();
                    speechRecognizer.stopListening();
                    speechRecognizer.destroy();
                }else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setIndeterminate(true);
                    speechRecognizer.startListening(recognizerIntent);
                }
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";

                Log.v(TAG, message);
                speechRecognizer.stopListening();
                speechRecognizer.destroy();

                createSpeechAgain(MainActivity.this);

                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                Log.v(TAG, message);
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                Log.v(TAG, message);


                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                speechRecognizer.stopListening();
                speechRecognizer.destroy();

                createSpeechAgain(MainActivity.this);

                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        Snackbar.make(findViewById(R.id.mainActivityLayout),message,Snackbar.LENGTH_SHORT);
    }

    @Override
    public void onResults(Bundle bundle) {
        Log.v(TAG, "onResults");
        if(!listen) {
            final ArrayList<String>temp;
            temp = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = "";
            for (String result : temp)
                text += result + "\n";

            Log.v(TAG, "onResults---> " + text);
            new AlertDialog.Builder(this)
                    .setTitle("Audio Setup")
                    .setMessage("Does Your Command  Matches Any\n" + TextUtils.join(",",temp))
                    .setNegativeButton("No,Repeat Again!", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            createSpeechAgain(MainActivity.this);

                        }
                    })
                    .setPositiveButton("Yes,Save It", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            speechRecognizer.stopListening();
                            speechRecognizer.destroy();
                            try {
                                if(jsonObject==null)
                                    jsonObject=new JSONObject();
                                jsonObject.put("signatureArray",new JSONArray(temp));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            //Toast.makeText(MainActivity.this, jsonObject.toString(), Toast.LENGTH_SHORT).show();
                            signature=temp;
                            try {
                                file.delete();
                                checkFiles();
                                os.write(jsonObject.toString().getBytes());
                                Log.v(TAG,"Written"+jsonObject.toString());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    })
                    .create().show();
        }else {
            ArrayList<String> checkList=bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if(signature==null||signature.isEmpty()){
                Toast.makeText(this, "Create Signature Command First!", Toast.LENGTH_SHORT).show();
                return;
            }
            for(String check:checkList){
                Log.v(TAG,check);
                if(signature.contains(check)){
  //                  currentRingtone.play();
                    mediaPlayer.start();
                    while (!mediaPlayer.isPlaying()) {
                        Log.v(TAG, "When Music Started " + file.length() + mediaPlayer.isPlaying());
                        mediaPlayer=MediaPlayer.create(this,R.raw.ringtone);
                        mediaPlayer.start();
                    }
                    silentfab.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.INVISIBLE);
                    speechRecognizer.stopListening();
                    speechRecognizer.destroy();
                    return;
                }
            }
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setIndeterminate(true);
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    @Override
    public void onPartialResults(Bundle bundle) {
        Log.v(TAG,"PArtial Results");

    }

    @Override
    public void onEvent(int i, Bundle bundle) {
        Log.v(TAG,"onEvent");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            os.close();
            is.close();
            Log.v(TAG,"Lenght is "+file.length());

        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.v(TAG,"Lenght is "+file.length());
   }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
