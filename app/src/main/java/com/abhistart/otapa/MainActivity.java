package com.abhistart.otapa;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    int oldVersionCode;
    View view;
    TextView  warningMessage;
    AlertDialog.Builder builder;

    AlertDialog alertDialog;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        //Retrieving data from shared prefernces and checking the requirements
        SharedPreferences sharedPreferences  =  MainActivity.this.getSharedPreferences("com.abhistart.otapa",Context.MODE_PRIVATE);
        String appName  =  sharedPreferences.getString(getString(R.string.saved_app_name),"");
        int numberOfTimes  = sharedPreferences.getInt(getString(R.string.number_of_times),1);
        Log.i("Info12",appName);

        if(numberOfTimes==1){
            dialogAndProcess();
        }




    }

    //Function for showing the Dialog box and check whatever needed for further procedings

    public void dialogAndProcess(){

        // Here are the configuration of Dialog box

        builder  = new AlertDialog.Builder(MainActivity.this);
        LayoutInflater inflater =  MainActivity.this.getLayoutInflater();
        view  =  inflater.inflate(R.layout.activity_custom_dialog,null);
        builder.setView(view);
        builder.setCancelable(false);
        Button cancelButton = view.findViewById(R.id.cancelButton);
        Button submitButton = view.findViewById(R.id.submitButton);
         warningMessage  =  view.findViewById(R.id.warningTextView);
         warningMessage.setVisibility(View.INVISIBLE);


        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.finish();
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                process();
            }
        });
       alertDialog = builder.show();


    }

    //Firebase job schedular

    public static void scheduleJob(Context context,String appName){
        FirebaseJobDispatcher jobDispatcher  = new FirebaseJobDispatcher(new GooglePlayDriver(context));
        Job job  = createJob(jobDispatcher,appName);
        jobDispatcher.mustSchedule(job);
    }

   // Function for creating the job and returning it

    public static Job createJob(FirebaseJobDispatcher jobDispatcher,String appName){

        Job job  = jobDispatcher.newJobBuilder()
                .setService(MyJobService.class)
                .setLifetime(Lifetime.FOREVER)
                .setTag("myJob")
                .setTrigger(Trigger.executionWindow(0,60))
                .setReplaceCurrent(true)
                .setRecurring(true)
                .setRetryStrategy(RetryStrategy.DEFAULT_LINEAR)
                .setConstraints(Constraint.ON_ANY_NETWORK)
                .build();


        return job;
    }




    //Function to process the request after clicking submit button

    public void process(){
        alertDialog.dismiss();

        final EditText editText = view.findViewById(R.id.appNameEditText);
        final String appName = editText.getText().toString();
        Log.i("Info11", appName);

        //Firebase database configuration
        final FirebaseDatabase database = FirebaseDatabase.getInstance();
        final DatabaseReference databaseReference = database.getReference();


        // Here checking if the app name exists or not
        databaseReference.addValueEventListener(new ValueEventListener() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean appNameExists = false;
                Log.i("Info","1");
                for (DataSnapshot data : dataSnapshot.getChildren()) {
                    Log.i("Info","2");
                    String databaseAppName  = Objects.requireNonNull(data.child("appName").getValue()).toString();
                    Log.i("Info",databaseAppName);
                    if (appName.equals(databaseAppName)) {
                        appNameExists = true;
                        //Shared prefernces configuration
                        SharedPreferences sharedPreferences  =  MainActivity.this.getSharedPreferences("com.abhistart.otapa",Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor  =  sharedPreferences.edit();
                        editor.putString(getString(R.string.saved_app_name),appName);
                        editor.putInt(getString(R.string.number_of_times),2);
                        editor.apply();
                        break;
                    }

                }

                //Flow will continue from here after a break from for loop
                // Applying the condition of existence if exists then only our code will run else
                //print an error message in the edit text

                // checkAndUpdate(appName);
                if (appNameExists) {
                    scheduleJob(MainActivity.this, appName);
                } else{
                    //Here do the error handling for appName not exists
                    //Toast.makeText(MainActivity.this, "Oops! App name doesn't exists", Toast.LENGTH_LONG).show();

                    editText.setText("");
                    warningMessage.setVisibility(View.VISIBLE);

                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }


    //Function to check the version and update if version found on database is greater

    public void checkAndUpdate(String appName){
        final FirebaseDatabase database = FirebaseDatabase.getInstance();
        final DatabaseReference databaseReference = database.getReference();
        final DatabaseReference verRef = databaseReference.child(appName).child("versionCode");
        final DatabaseReference storageLocationRef = databaseReference.child(appName).child("locationURL");


        // Here I am checking the old version code of my package

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            oldVersionCode = pInfo.versionCode;
            //  Toast.makeText(this,"Version code is: " +oldVersionCode, Toast.LENGTH_SHORT).show();


        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }


        //Fetching new Version code from database
        verRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String value = dataSnapshot.getValue(String.class);
                int newVersionCode = Integer.parseInt(value);
                //Toast.makeText(MainActivity.this,"New version code is: "+ newVersionCode, Toast.LENGTH_LONG).show();

                //Version code verification
                //Here checking the previous version code with the new version code

                if (newVersionCode == oldVersionCode) {

                    Log.i("Info", "Version is up to date");

                } else if (newVersionCode > oldVersionCode) {
                    Log.i("Info", "Very sad buddy!");

                    // Here I am triggering the  code of updating the old version only if new version is greater


                    //Firebase Storage configuration

                    //Getting string from database value

                    storageLocationRef.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            FirebaseStorage storage  = FirebaseStorage.getInstance();

                            //Downloading the files from firebase here
                            File myFile = null;
                            myFile = new File(getFilesDir(), "/app-debug" + ".apk");

                            final File finalMyFile = myFile;

                            String storageURL = dataSnapshot.getValue(String.class);
                            if (storageURL != null) {
                                final StorageReference pathreference = storage.getReferenceFromUrl(storageURL);

                                pathreference.getFile(myFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                                    @Override
                                    public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {

                                        String path = finalMyFile.getAbsolutePath();
                                        Log.i("Info", path);
                                        Toast.makeText(MainActivity.this, path, Toast.LENGTH_SHORT).show();


                                        // Opening the apk file through intent
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        // Using File provider here for handling the opening permissions after target SDK 24
                                        Uri fileUri = FileProvider.getUriForFile(MainActivity.this, "com.abhistart.provider", finalMyFile);
                                        intent.setDataAndType(fileUri, "application/vnd.android.package-archive");
                                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        startActivity(intent);

                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        //File downloading failed

                                    }
                                });
                            }

                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });

                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Here write code for if any error occur while fetching from database

            }
        });

    }

}
