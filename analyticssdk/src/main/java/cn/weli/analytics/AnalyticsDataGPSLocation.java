package cn.weli.analytics;

public class AnalyticsDataGPSLocation {
    /**
     * 纬度
     */
    private String latitude;

    /**
     * 经度
     */
    private String longitude;
    private String city_key;

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public void setCityKey(String city_key){
        this.city_key = city_key;

    }
    public String getCityKey(){
        return city_key;
    }
}
