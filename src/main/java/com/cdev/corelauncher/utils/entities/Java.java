package com.cdev.corelauncher.utils.entities;

import com.cdev.corelauncher.utils.Logger;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;

public class Java {
    @SerializedName("component")
    private String codeName;
    public int majorVersion;
    public int arch;
    public String version;

    private final Path path;

    public Java(){
        path = null;
    }

    public Java(int majorVersion){
        this.majorVersion = majorVersion;
        path = null;
    }

    public Java(Path path){
        this.path = path;

        retrieveInfo();
    }

    public void retrieveInfo(){
        if (path == null)
            return;

        try{
            var process = new ProcessBuilder().command(path.toString(), "-version").start();

            String version;
            String arch;
            try(var reader = process.errorReader()){
                version = reader.readLine();
                reader.readLine();
                arch = reader.readLine();
            }

            String[] a = version.split(" ");
            this.version = a[2].replace("\"", "");
            String[] b = this.version.split("\\.");
            majorVersion = Integer.parseInt(b[0].equals("1") ? b[1] : b[0]);
            this.arch = arch.contains("64-Bit") ? 64 : 32;
        }
        catch (IOException e){
            Logger.getLogger().log(e);
        }
    }

    public String getCodeName(){
        if (codeName != null)
            return codeName;

        if (majorVersion == 17)
            codeName = "java-runtime-alpha";
        else if (majorVersion == 16)
            codeName = "java-runtime-gamma";
        else
            codeName = "jre-legacy";

        return codeName;
    }

    public static Java fromVersion(int majorVersion){
        return new Java(majorVersion);
    }

    public static Java fromCodeName(String codeName){
        if (codeName.contains("alpha") || codeName.contains("beta"))
            return new Java(17);
        else if (codeName.equals("gamma"))
            return new Java(16);
        else
            return new Java(8);
    }

    public Path getPath(){
        return path;
    }

}
