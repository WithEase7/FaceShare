package com.sinchan.faceshare;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public interface APIService1 {

    @POST("give")
    @FormUrlEncoded
    Call<OTPResponsePojo> savePost(@Field("key") String key);
}