package com.sinchan.faceshare;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.sinchan.faceshare.ProfileActivity.REQUEST_TAKE_PHOTO;
import static com.sinchan.faceshare.ProfileActivity.rotateBitmap;
import static com.sinchan.faceshare.ProfileActivity.uriToBitmap;

public class DownloadPic extends BaseActivity{
    AmazonS3 s3Client;
    String key;
    String bucket = "sinchan.face";
    TransferUtility transferUtility;
    ImageView pic;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloadpic);
        s3credentialsProvider();
        setTransferUtility();
        Intent i=getIntent();
        key=i.getStringExtra("key");
        pic=findViewById(R.id.pic);
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                downloadFileFromS3();
            }
        });
    }


    public void downloadFileFromS3(){

        TransferObserver transferObserver = transferUtility.download(
                bucket,     /* The bucket to upload to */
                key,
                new File(Environment.getExternalStorageDirectory(),"FaceShare/"+key+".jpg")/* The key for the uploaded object */
                /* The file where the data to upload exists */
        );

        transferObserverListener(transferObserver);
    }
    public void setTransferUtility(){

        transferUtility = new TransferUtility(s3Client, getApplicationContext());
    }




    public void s3credentialsProvider(){

        // Initialize the AWS Credential
        CognitoCachingCredentialsProvider cognitoCachingCredentialsProvider =
                new CognitoCachingCredentialsProvider(
                        getApplicationContext(),
                        "us-east-1:7f2d8857-3293-4cc0-8f66-72ab7524e2e5", // Identity Pool ID
                        Regions.US_EAST_1 // Region
                );
        createAmazonS3Client(cognitoCachingCredentialsProvider);
    }
    public void createAmazonS3Client(CognitoCachingCredentialsProvider credentialsProvider){
        s3Client = new AmazonS3Client(credentialsProvider);
        s3Client.setRegion(Region.getRegion(Regions.US_EAST_1));
    }

    public void transferObserverListener(TransferObserver transferObserver){

        transferObserver.setTransferListener(new TransferListener(){

            @Override
            public void onStateChanged(int id, TransferState state) {
                Toast.makeText(getApplicationContext(), "DOWNLOAD " + state, Toast.LENGTH_SHORT).show();
                if(state.toString().equals("IN_PROGRESS"))
                    Toast.makeText(getApplicationContext(),"Download in Progress",Toast.LENGTH_SHORT);
                if(state.toString().equals("COMPLETED")) {
                    Toast.makeText(getApplicationContext(), "Download Completed", Toast.LENGTH_SHORT);
                    pic.setImageURI(Uri.fromFile(new File(Environment.getExternalStorageDirectory(),"FaceShare/"+key+".jpg")));
                }
                if(state.toString().equals("FAILED"))
                    Toast.makeText(getApplicationContext(),"Download Failed",Toast.LENGTH_SHORT);
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                if (bytesTotal != 0) {

                    double percentage = 100.0*((float)bytesCurrent/(float)bytesTotal);
                    String str = String.format("%2.02f", percentage);
                    Log.e("onProgressChanged","yes "+percentage+" bytesCurrent: "+bytesCurrent+" bytesTotal: "+bytesTotal);
                    if(percentage>10&&percentage<30||percentage>50&&percentage<80)
                        Toast.makeText(getApplicationContext(), "Downloading..." + str + "% complete", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int id, Exception ex) {
                Log.e("error","Error Id= "+id+" Exception= "+ex.toString());
            }

        });
    }
}
