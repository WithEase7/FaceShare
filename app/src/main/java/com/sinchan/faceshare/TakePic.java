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

public class TakePic extends BaseActivity implements View.OnClickListener{
    int rotationInDegrees;
    APIService1 mAPIService;
    private String email;
    private FirebaseAuth mAuth;
    String mCurrentPhotoPath;
    private String fileName;
    AmazonS3 s3Client;
    String bucket = "sinchan.face";
    TransferUtility transferUtility;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_takepic);
        ImageView camera=findViewById(R.id.camera);
        camera.setOnClickListener(this);
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        email=user.getEmail();
        s3credentialsProvider();
        setTransferUtility();
    }
    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.camera) {
            Log.e("Camera","Clicked");
            dispatchTakePictureIntent();
        }
    }
    private void dispatchTakePictureIntent() {

        if(check()) {
            Log.e("Result of check()",check()+"");
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // Ensure that there's a camera activity to handle the intent
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                // Create the File where the photo should go
                int photoFile = 0;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Log.e("Error", "Error while taking Picture.");
                }
                // Continue only if the File was successfully created
                if (photoFile != 0) {
                    Uri photoURI = FileProvider.getUriForFile(this,
                            "com.sinchan.faceshare",
                            new File(Environment.getExternalStorageDirectory(), "FaceShare/"+fileName+".jpg"));
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
                    Log.e("Photo", "Strored");
                }
            }
        }
        else
        {
            Toast.makeText(getApplicationContext(),"Grant Permission First!!",Toast.LENGTH_SHORT);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == 1) {
            if(resultCode == Activity.RESULT_OK){
                Log.e("Result","OK");
                compress();
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                Log.e("Result","Cancelled");
                //Write your code if there's no result
            }
            Log.e("Request Code", requestCode+"  "+resultCode+"  "+data);
        }
    }

    private void compress()
    {
        Bitmap photo;
        Uri selectedImage = Uri.fromFile(new File(Environment.getExternalStorageDirectory(), "FaceShare/"+fileName+".jpg"));
        try {
            photo = uriToBitmap(getApplicationContext(),selectedImage);
            ExifInterface exif = null;
            try {
                exif = new ExifInterface(selectedImage.getPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            rotationInDegrees= exifToDegrees(rotation);
            Log.e("Exif Degrees",rotationInDegrees+"");
            photo = rotateBitmap(photo, rotationInDegrees);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.JPEG, 25, bytes);
            File f1 = new File(Environment.getExternalStorageDirectory(),"FaceShare/Blah.jpg");
            f1.createNewFile();
            Log.e("Bitmap Path",f1.toString());
            FileOutputStream fo = new FileOutputStream(f1);
            fo.write(bytes.toByteArray());
            fo.close();
            Executors.newSingleThreadExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    uploadFileToS3();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int exifToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) { return 90; }
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {  return 180; }
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {  return 270; }
        return 0;
    }

    public void uploadFileToS3(){

        TransferObserver transferObserver = transferUtility.upload(
                bucket,     /* The bucket to upload to */
                fileName,
                new File(Environment.getExternalStorageDirectory(),"FaceShare/Blah.jpg")/* The key for the uploaded object */
                /* The file where the data to upload exists */
        );

        transferObserverListener(transferObserver);
    }
    public void setTransferUtility(){

        transferUtility = new TransferUtility(s3Client, getApplicationContext());
    }

    private int createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = email.split("@")[0]+"_"+ timeStamp;
        fileName=imageFileName;
        return 1;
    }
    public boolean check()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.e("Permission","Permission is granted");
                return true;
            } else {
                Log.e("Permission","Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else return true;
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
                Toast.makeText(getApplicationContext(), "UPLOAD " + state, Toast.LENGTH_SHORT).show();
                if(state.toString().equals("IN_PROGRESS"))
                    Toast.makeText(getApplicationContext(),"Upload in Progress",Toast.LENGTH_SHORT);
                if(state.toString().equals("COMPLETED")) {
                        Toast.makeText(getApplicationContext(), "Upload Completed", Toast.LENGTH_SHORT);
                        callGive();
                }
                if(state.toString().equals("FAILED"))
                    Toast.makeText(getApplicationContext(),"Upload Failed",Toast.LENGTH_SHORT);
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                if (bytesTotal != 0) {

                    double percentage = 100.0*((float)bytesCurrent/(float)bytesTotal);
                    String str = String.format("%2.02f", percentage);
                    Log.e("onProgressChanged","yes "+percentage+" bytesCurrent: "+bytesCurrent+" bytesTotal: "+bytesTotal);
                    if(percentage>10&&percentage<30||percentage>50&&percentage<80)
                        Toast.makeText(getApplicationContext(), "Uploading..." + str + "% complete", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int id, Exception ex) {
                Log.e("error","error");
            }

        });
    }

    private void callGive()
    {
        final DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String key = (String) snapshot.getKey();
                    Log.e("Key1",key);
                    if(key.equals("python_api"))
                    {
                        String link=(String)snapshot.getValue();
                        mAPIService=APIUtils.getAPIService1(link);
                        mAPIService.savePost(fileName).enqueue(new Callback<OTPResponsePojo>() {
                                                                @Override
                                                                public void onResponse(Call<OTPResponsePojo> call, Response<OTPResponsePojo> response) {

                                                                    if (response.isSuccessful()) {
                                                                        Log.e("Allot", response.body().toString());
                                                                    } else {
                                                                        Toast.makeText(getApplicationContext(), "Sorry. There was a problem.", Toast.LENGTH_SHORT).show();
                                                                    }
                                                                }

                                                                @Override
                                                                public void onFailure(Call<OTPResponsePojo> call, Throwable t) {
                                                                    Log.e("SecurityMain", "Unable to submit post to API.");
                                                                    Toast.makeText(getApplicationContext(), "Sorry. There was a problem.", Toast.LENGTH_SHORT).show();
                                                                }
                                                            }
                        );
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }
}
