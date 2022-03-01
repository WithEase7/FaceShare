
package com.sinchan.faceshare;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class OTPResponsePojo {

    @SerializedName("new_token")
    @Expose
    private String newToken;

    @SerializedName("status")
    @Expose
    private Integer status;
    @SerializedName("message")
    @Expose
    private String message;
    @SerializedName("user_otp")
    @Expose
    private Integer userOtp;
    @SerializedName("c_otp")
    @Expose
    private Object cOtp;
    @SerializedName("enc_data")
    @Expose
    private String encData;

    public String getNewToken() {
        return newToken;
    }

    public void setNewToken(String newtoken) {
        this.newToken = newtoken;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getUserOtp() {
        return userOtp;
    }

    public void setUserOtp(Integer userOtp) {
        this.userOtp = userOtp;
    }

    public Object getCOtp() {
        return cOtp;
    }

    public void setCOtp(Object cOtp) {
        this.cOtp = cOtp;
    }

    public String getEncData() {
        return encData;
    }

    public void setEncData(String encData) {
        this.encData = encData;
    }
    @Override
    public String toString() {
        return "Post{ status=" +status+"  messages="+message+"  otp="+userOtp+"  cotp="+cOtp+" encData="+encData+"}";
    }

}