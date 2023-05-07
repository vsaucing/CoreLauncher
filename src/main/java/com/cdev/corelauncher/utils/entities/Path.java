package com.cdev.corelauncher.utils.entities;

import com.cdev.corelauncher.utils.Logger;
import com.google.gson.*;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class Path{

    public static final class PathFactory implements JsonSerializer<Path>, JsonDeserializer<Path> {
        @Override
        public JsonElement serialize(Path path, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(path.root.toString());
        }

        @Override
        public Path deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return new Path(java.nio.file.Path.of(jsonElement.getAsString()));
        }
    }

    private java.nio.file.Path root;

    public Path(){

    }

    public Path(java.nio.file.Path root){
        this.root = root;
    }

    @Override
    public String toString(){
        return root.toString();
    }

    public static Path begin(java.nio.file.Path r){
        return new Path(r);
    }

    /**
     * Concat multiple paths.
     */
    public Path to(String... keys){
        return new Path(java.nio.file.Path.of(root.toString(), keys));
    }

    /**
     * Concat path;
     */
    public Path to(String key){
        return new Path(root.resolve(key));
    }

    public File toFile(){
        return root.toFile();
    }

    public boolean exists(){
        return toFile().exists();
    }

    public void prepare(){
        var file = toFile();
        if (file.isDirectory())
            file.mkdirs();
        else
            new File(file.getParent()).mkdirs();
    }

    public List<Path> getFiles(){
        var file = toFile();

        if (!file.isDirectory())
            return List.of();

        var files = file.listFiles();

        if (files == null)
            return List.of();

        return Arrays.stream(files).map(x -> new Path(x.toPath())).toList();
    }

    public long getSize(){
        try{
            return Files.size(root);
        }
        catch (IOException e){
            return 0;
        }
    }

    public void write(String content){
        try{
            prepare();
            Files.writeString(root, content);
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    public void append(String content){
        try {
            content = read() + content;
            Files.writeString(root, content);
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    public String read(){
        try{
            prepare();
            return Files.readString(root);
        }
        catch (IOException e){
            return "";
        }
    }
    public void delete(){
        root.toFile().delete();
    }

    public String getName(){
        return root.toFile().getName();
    }

    public String getExtension(){
        String[] all = getName().split("\\.");
        return all[all.length - 1];
    }

    private void extract(Path destination, ArchiveInputStream stream, List<String> exclude) throws IOException {
        ArchiveEntry entry;

        while ((entry = stream.getNextEntry()) != null){
            if (exclude.stream().anyMatch(entry.getName()::contains))
                continue;

            var pp = destination.to(entry.getName());
            var ff = pp.toFile();
            if (entry.isDirectory())
                ff.mkdirs();
            else {
                new File(ff.getParent()).mkdirs();
                try(var f = new FileOutputStream(ff)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = stream.read(buffer)) != -1)
                        f.write(buffer, 0, read);
                }
                pp.execPosix();
                new File(ff.getPath()).setLastModified(entry.getLastModifiedDate().getTime());
            }
        }
    }

    private void extractTar(Path destination, List<String> exclude){
        try(var gzip = new GZIPInputStream(new FileInputStream(toFile()));
            var tar = new TarArchiveInputStream(gzip)){
            extract(destination, tar, exclude);
        }
        catch (IOException e){
            Logger.getLogger().log(e);
        }
    }

    private void extractZip(Path destination, List<String> exclude){
        try(var file = new FileInputStream(root.toFile());
            var zip = new ZipArchiveInputStream(file)){
            extract(destination, zip, exclude);
        }
        catch (IOException e){
            Logger.getLogger().log(e);
        }
    }

    public void execPosix(){
        try{
            var set = new HashSet<PosixFilePermission>();
            set.add(PosixFilePermission.OWNER_WRITE);
            set.add(PosixFilePermission.GROUP_WRITE);
            set.add(PosixFilePermission.OTHERS_WRITE);
            set.add(PosixFilePermission.OWNER_READ);
            set.add(PosixFilePermission.GROUP_READ);
            set.add(PosixFilePermission.OTHERS_READ);
            set.add(PosixFilePermission.OWNER_EXECUTE);
            set.add(PosixFilePermission.GROUP_EXECUTE);
            set.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(root, set);
        }
        catch (IOException e){
            Logger.getLogger().log(e);
        }
        catch (UnsupportedOperationException ignored){

        }
    }

    public void extract(Path destination, List<String> exclude){
        if (destination == null)
            destination = new Path(root.getParent());

        if (exclude == null)
            exclude = List.of();

        if (getExtension().equals("gz")){
            extractTar(destination, exclude);
        }
        else if (getExtension().equals("zip") || getExtension().equals("jar"))
            extractZip(destination, exclude);
    }
}
