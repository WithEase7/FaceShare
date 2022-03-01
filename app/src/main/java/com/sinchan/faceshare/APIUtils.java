package com.sinchan.faceshare;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class APIUtils {

    private APIUtils() {}

    public static final String BASE_URL = "http://sinchan.ml/";

    public static APIService getAPIService(String link) {
        if(link.charAt(link.length()-1)=='/')
            ;
        else
            link+='/';
        Log.e("Link",link);
        return RetrofitClient.getClient(link).create(APIService.class);
    }
    public static APIService1 getAPIService1(String link) {
        if(link.charAt(link.length()-1)=='/')
            ;
        else
            link+='/';
        Log.e("Link",link);
        return RetrofitClient.getClient(link).create(APIService1.class);
    }
}