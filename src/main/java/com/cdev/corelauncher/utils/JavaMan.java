package com.cdev.corelauncher.utils;

import com.cdev.corelauncher.CoreLauncher;
import com.cdev.corelauncher.data.Configurator;
import com.cdev.corelauncher.data.Profiler;
import com.cdev.corelauncher.data.entities.ChangeEvent;
import com.cdev.corelauncher.data.entities.Config;
import com.cdev.corelauncher.ui.utils.EventHandler;
import com.cdev.corelauncher.utils.entities.Java;
import com.cdev.corelauncher.utils.entities.Path;
import com.google.gson.Gson;
import com.google.gson.JsonArray;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaMan {
    static class JavaDownloadInfo{
        public String name;
        public String url;
        public int majorVersion;

        public JavaDownloadInfo(String name, String url, int majorVersion){
            this.name = name;
            this.url = url;
            this.majorVersion = majorVersion;
        }
    }
    private static final String ADOPTIUM = "https://api.adoptium.net/v3/assets/latest/";

    private static JavaMan instance;

    private EventHandler<ChangeEvent> handler;
    private List<Java> javaVersions;

    private Path javaDir;

    public JavaMan(){
        setJavaDir(gameDirToJavaDir(Configurator.getConfig().getGamePath()));

        Configurator.getConfigurator().getHandler().addHandler("jvman", (a) -> {
            if (!a.getKey().equals("gamePathChange"))
                return;

            setJavaDir(gameDirToJavaDir((Path) a.getNewValue()));

            reload();
        });

        handler = new EventHandler<>();

        instance = this;
    }

    private static Path gameDirToJavaDir(Path gameDir){
        return gameDir.to("launcher", "java");
    }

    public void setJavaDir(Path javaDir){
        this.javaDir = javaDir;
    }

    public static JavaMan getManager(){
        return instance;
    }
    public EventHandler<ChangeEvent> getHandler(){
        return handler;
    }

    private List<Java> getAll(){
        var files = javaDir.getFiles();
        var own = files.stream().filter(Path::isDirectory).map(Java::new);
        var cst = Configurator.getConfig().getCustomJavaVersions();
        return (cst != null ? Stream.concat(own, cst.stream()) : own).collect(Collectors.toList());
    }

    public List<Java> getAllJavaVersions(){
        return javaVersions;
    }

    public static Java getDefault(){
        return new Java(Path.begin(OSUtils.getRunningJavaDir()));
    }

    private static JavaDownloadInfo getJavaInfo(Java j, boolean is64Bit){
        var os = CoreLauncher.SYSTEM_OS;
        String url = ADOPTIUM + j.majorVersion + "/hotspot?os=" + os.getName() + "&image_type=jdk&architecture=" + (is64Bit ? "x64" : "x86");
        var object = new Gson().fromJson(NetUtils.urlToString(url), JsonArray.class).get(0);
        if (object == null)
            return null;
        var obj = object.getAsJsonObject();
        return new JavaDownloadInfo(obj.get("release_name").getAsString(), obj.getAsJsonObject("binary").getAsJsonObject("package").get("link").getAsString(), j.majorVersion);
    }

    public Java tryGet(Java j){
        return javaVersions.stream().filter(x -> x.getName().equals(j.getName()) || x.majorVersion == j.majorVersion).findFirst().orElse(null);
    }

    public void download(Java java, Consumer<Double> onProgress){
        if (java.majorVersion == 0)
            return;

        var info = getJavaInfo(java, true);

        if (info == null)
            return;

        var file = NetUtils.download(info.url, javaDir, true, onProgress);
        file.extract(null, null);
        file.delete();

        var j = new Java(javaDir.to(info.name));
        javaVersions.add(j);
        handler.execute(new ChangeEvent("addJava", null, j, null));
    }

    public void reload(){
        javaVersions = getAll();
    }

    public void deleteJava(Java j){
        javaVersions.remove(j);

        if (Configurator.getConfig().getCustomJavaVersions().contains(j)){
            Configurator.getConfig().getCustomJavaVersions().remove(j);
            Configurator.save();
        }
        else
            j.getPath().delete();

        Profiler.getProfiler().getAllProfiles().stream().filter(x -> x.getJava() != null && x.getJava().equals(j)).forEach(x -> Profiler.getProfiler().setProfile(x.getName(), y -> y.setJava(null)));

        if (Configurator.getConfig().getDefaultJava() != null && Configurator.getConfig().getDefaultJava().equals(j)){
            Configurator.getConfig().setDefaultJava(null);
            Configurator.save();
        }

        handler.execute(new ChangeEvent("delJava", j, null, null));
    }

    public boolean addCustomJava(Java j){
        if (javaVersions.stream().anyMatch(x -> x.equals(j) || (x.getName() != null && x.getName().equals(j.getName()))))
            return false;
        javaVersions.add(j);

        Configurator.getConfig().getCustomJavaVersions().add(j);
        Configurator.save();

        handler.execute(new ChangeEvent("addJava", null, j, null));

        return true;
    }
}
