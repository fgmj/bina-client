package br.com.bomgas.bina;

public final class ConfigSingleton {

    private static ConfigSingleton INSTANCE;

    private String ip = "";

    private String url = "";

    private ConfigSingleton() {
    }

    public static ConfigSingleton getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new ConfigSingleton();
        }

        return INSTANCE;
    }

    public String getIP(){
        return ip;
    }

    public void setIP(String ip){
        this.ip = ip;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }


}
