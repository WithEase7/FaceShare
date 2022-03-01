package com.sinchan.faceshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.provider.Contacts.SettingsColumns.KEY;

public class ProfileActivity extends BaseActivity implements View.OnClickListener {
    int rotationInDegrees;
    static final int REQUEST_TAKE_PHOTO = 1;
    String mCurrentPhotoPath;
    APIService mAPIService;
    private int numberPics=0;
    private Button gotoForm;

    private static final String TAG = "GoogleActivity";
    private static final int RC_SIGN_IN = 9001;
    private boolean result=false;

    // [START declare_auth]
    private FirebaseAuth mAuth;
    // [END declare_auth]
    String token;
    private GoogleSignInClient mGoogleSignInClient;
    private TextView mStatusTextView;
    private TextView mDetailTextView;
    private ImageView proPic;
    private String email;
    private int presentDatabase=0;
    AmazonS3 s3Client;
    String bucket = "sinchan.face";
    TransferUtility transferUtility;

    @Override
    public void onBackPressed() {
        if (true) {
        } else {
            super.onBackPressed();
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        token = FirebaseInstanceId.getInstance().getToken();
        Log.e("Firebase Token",token);
        numberPics=0;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        // Views
        mStatusTextView = findViewById(R.id.name);
        mDetailTextView = findViewById(R.id.email);
        proPic=findViewById(R.id.proPic);
        findViewById(R.id.logout).setOnClickListener(this);
        findViewById(R.id.gotoForm).setOnClickListener(this);
        findViewById(R.id.gotoForm).setVisibility(View.GONE);
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        mStatusTextView.setText(user.getDisplayName());
        mDetailTextView.setText(user.getEmail());
        email=user.getEmail();
        s3credentialsProvider();
        setTransferUtility();

        Log.e("Key", getString(R.string.default_web_client_id));
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        File file = new File(Environment.getExternalStorageDirectory(), "FaceShare/"+email+".jpg");

        final DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();
        databaseReference.child("proPic").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    for(DataSnapshot snapshot1 :snapshot.getChildren())
                    {
                        String key=snapshot1.getKey();
                        if(key.equals("Email"))
                        {
                            if(snapshot1.getValue().toString().equals(email)) {
                                presentDatabase = 1;
                            }
                        }
                    }
                }
                if(presentDatabase==0)
                {
                    Map<String, String> userData = new HashMap<String, String>();
                    userData.put("Email", email);
                    userData.put("Token", token);
                    databaseReference.child("proPic").push().setValue(userData);
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        if(file.exists())
        {
            Log.e("URI set","True");
            proPic.setImageURI(Uri.fromFile(file));
            ExifInterface exif = null;
            try {
                exif = new ExifInterface(file.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            rotationInDegrees= exifToDegrees(rotation);
            Log.e("Exif Degrees",rotationInDegrees+"");
            proPic.setRotation(rotationInDegrees);
            findViewById(R.id.gotoForm).setVisibility(View.VISIBLE);
        }
        else
        {
            Toast.makeText(getApplicationContext(),"Please set your Profile by Taking 4 Selfies!", Toast.LENGTH_SHORT);
            proPic.setOnClickListener(this);
            numberPics=0;
        }
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }
    // [END signin]

    public void signOut() {
        // Firebase sign out
        mAuth.signOut();
        mGoogleSignInClient.signOut();
        startActivity(new Intent(ProfileActivity.this,LoginActivity.class));

        // Google sign out

    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.logout) {
            signOut();
        } else if (i == R.id.gotoForm) {
            startActivity(new Intent(ProfileActivity.this,TakePic.class));
        }
        else if (i == R.id.proPic) {
            dispatchTakePictureIntent();
        }

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
    private void dispatchTakePictureIntent() {

        if(check()) {
            Log.e("Result of check()",check()+"");
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // Ensure that there's a camera activity to handle the intent
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                // Create the File where the photo should go
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Log.e("Error", "Error while taking Picture.");
                }
                // Continue only if the File was successfully created
                if (photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(this,
                            "com.sinchan.faceshare",
                            new File(Environment.getExternalStorageDirectory(), "FaceShare/"+email+".jpg"));
                    Log.e("photoFile",photoFile.toString());
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
                    Log.e("Photo", "Strored");
                    photoFile.delete();
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
                proPic.setImageURI(Uri.fromFile(new File(Environment.getExternalStorageDirectory(), "FaceShare/"+email+".jpg")));
                ExifInterface exif = null;
                try {
                    exif = new ExifInterface((new File(Environment.getExternalStorageDirectory(), "FaceShare/"+email+".jpg")).toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                rotationInDegrees= exifToDegrees(rotation);
                Log.e("Exif Degrees",rotationInDegrees+"");
                proPic.setRotation(rotationInDegrees);
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
        Uri selectedImage = Uri.fromFile(new File(Environment.getExternalStorageDirectory(), "FaceShare/"+email+".jpg"));
        try {
            photo = uriToBitmap(getApplicationContext(),selectedImage);
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



    public static Bitmap uriToBitmap(Context c, Uri uri) {
        if (c == null && uri == null) {
            return null;
        }
        try {
            return MediaStore.Images.Media.getBitmap(c.getContentResolver(), Uri.fromFile(new File(uri.getPath())));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    private File createImageFile() throws IOException {
        String imageFileName=email;
        Log.e("Email",email);
        File storageDir = new File(Environment.getExternalStorageDirectory(), "FaceShare");
        storageDir.mkdirs();
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        Log.e("image",image.toString());
        return image;
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
    public void setTransferUtility(){

        transferUtility = new TransferUtility(s3Client, getApplicationContext());
    }
    public void uploadFileToS3(){
        TransferObserver transferObserver = transferUtility.upload(
                bucket,     /* The bucket to upload to */
                email+numberPics,
                new File(Environment.getExternalStorageDirectory(),"FaceShare/Blah.jpg")/* The key for the uploaded object */
                /* The file where the data to upload exists */
        );

        transferObserverListener(transferObserver);
    }
    public void transferObserverListener(TransferObserver transferObserver){

        transferObserver.setTransferListener(new TransferListener(){

            @Override
            public void onStateChanged(int id, TransferState state) {
                Toast.makeText(getApplicationContext(), "UPLOAD " + state, Toast.LENGTH_SHORT).show();
                if(state.toString().equals("IN_PROGRESS"))
                    Toast.makeText(getApplicationContext(),"Upload in Progress",Toast.LENGTH_SHORT);
                if(state.toString().equals("COMPLETED")) {
                    numberPics++;
                    if(numberPics<4) {
                        Toast.makeText(getApplicationContext(),"Please Proceed to take Pic Number "+(numberPics+1),Toast.LENGTH_SHORT);
                        dispatchTakePictureIntent();
                    }
                    else {
                        Toast.makeText(getApplicationContext(), "Upload Completed", Toast.LENGTH_SHORT);
                        findViewById(R.id.gotoForm).setVisibility(View.VISIBLE);
                        callPreprocess();
                    }
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
    private void callPreprocess()
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
                        mAPIService=APIUtils.getAPIService(link);
                        mAPIService.savePost(email).enqueue(new Callback<OTPResponsePojo>() {
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
    public static Bitmap rotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}
