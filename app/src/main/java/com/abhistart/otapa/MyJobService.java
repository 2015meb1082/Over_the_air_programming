package com.abhistart.otapa;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
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


public class MyJobService extends JobService {


    private static  final  String TAG  = MyJobService.class.getSimpleName();
    int oldVersionCode;


    @Override
    public boolean onStartJob(final JobParameters params) {

        SharedPreferences sharedPreferences  =  MyJobService.this.getSharedPreferences("com.abhistart.otapa",Context.MODE_PRIVATE);
        String appName  = sharedPreferences.getString(getString(R.string.saved_app_name),"");
        Log.i("Info123",appName);
        checkAndUpdate(appName);
        jobFinished(params, false);

        return true;
    }

    @Override
    public boolean onStopJob(com.firebase.jobdispatcher.JobParameters job) {
        return true;
    }





    //Function for checking and updating the apk file
    public void checkAndUpdate(String appName){
        Toast.makeText(this, "Job running", Toast.LENGTH_SHORT).show();
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

                    Log.i("Message", "Version is up to date");

                } else if (newVersionCode > oldVersionCode) {
                    Log.i("Message", "Very sad buddy!");

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
                                        Log.i("Path", path);
                                       // Toast.makeText(MainActivity.this, path, Toast.LENGTH_SHORT).show();
                                       // progressBarHolder.setVisibility(View.INVISIBLE);

                                        // Opening the apk file through intent
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        // Using File provider here for handling the opening permissions after target SDK 24
                                        Uri fileUri = FileProvider.getUriForFile(MyJobService.this, "com.abhistart.provider", finalMyFile);
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
