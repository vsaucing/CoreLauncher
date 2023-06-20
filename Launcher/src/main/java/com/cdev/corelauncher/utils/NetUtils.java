package com.cdev.corelauncher.utils;

import com.cdev.corelauncher.utils.entities.LogType;
import com.cdev.corelauncher.utils.entities.NoConnectionException;
import com.cdev.corelauncher.utils.entities.Path;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class NetUtils {
    public static String inputStreamToString(InputStream s){
        String read = "{}";
        try (var stream = new BufferedInputStream(s);
             var buffer = new ByteArrayOutputStream();
        ){
            int r;
            while ((r = stream.read()) != -1){
                buffer.write(r);
            }

            read = buffer.toString(StandardCharsets.UTF_8);
        }
        catch (IOException e){
            Logger.getLogger().log(e);
        }

        return read;
    }
    public static String urlToString(String url) {
        if (url == null)
            return null;

        URL u;
        try {
            u = new URL(url);
        } catch (MalformedURLException e) {
            Logger.getLogger().log(e);
            return null;
        }

        if (offline)
            throw new NoConnectionException();

        try{
            var c = (HttpURLConnection) u.openConnection();
            return inputStreamToString(c.getInputStream());
        }
        catch (FileNotFoundException f){
            return null;
        }
        catch (UnknownHostException h){
            throw new NoConnectionException();
        }
        catch (HttpStatusException e){
            Logger.getLogger().printLog(LogType.ERROR, "Error on request to " + url + ": " + e.getMessage());
            return null;
        }
        catch (IOException e){
            Logger.getLogger().log(e);
            return null;
        }
    }
    public static long getContentLength(String url){

        if (offline)
            throw new NoConnectionException();

        try {
            var uri = new URL(url);
            var conn = (HttpURLConnection)uri.openConnection();
            return conn.getContentLengthLong();
        }
        catch (UnknownHostException h){
            throw new NoConnectionException();
        }
        catch (IOException e){
            Logger.getLogger().log(e);
            return 0;
        }
    }
    public static Path download(String url, Path destination, boolean useOriginalName, Consumer<Double> onProgress){

        if (offline)
            throw new NoConnectionException();

        try{
            var uri = new URL(url);
            if (useOriginalName){
                String[] p = uri.getFile().split("/");
                destination = destination.to(p[p.length - 1]);
            }
            destination.prepare();

            var conn = (HttpURLConnection) uri.openConnection();

            long length = conn.getContentLengthLong();
            long progress = 0;

            try(var stream = conn.getInputStream();
                var file = new FileOutputStream(destination.toFile())
            ){
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1){
                    file.write(buffer, 0, read);
                    progress += buffer.length;
                    if (onProgress != null)
                        onProgress.accept(progress * 1.0 / length);
                }
            }
            destination.toFile().setLastModified(conn.getLastModified());
        }
        catch (UnknownHostException h){
            throw new NoConnectionException();
        }
        catch (IOException ex){
            Logger.getLogger().log(ex);
        }
        return destination;
    }
    public static String post(String url, String content, String contentType){
        String answer = null;

        if (offline)
            throw new NoConnectionException();

        try{
            URL uri = new URL(url);

            var connection = (HttpURLConnection)uri.openConnection();
            connection.addRequestProperty("Content-Type", contentType);

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);

            try (var stream = connection.getOutputStream();
                 var writer = new OutputStreamWriter(stream)){

                writer.write(content);
            }

            try(var stream = connection.getInputStream()){
                answer = streamToString(stream);
            }
            catch (IOException e){
                if (connection.getErrorStream() != null){
                    try(var stream = connection.getErrorStream()){
                        answer = streamToString(stream);
                    }
                    catch (IOException ex){
                        Logger.getLogger().log(ex);
                    }
                }
            }
        }
        catch (UnknownHostException h){
            throw new NoConnectionException();
        }
        catch (IOException e){
            Logger.getLogger().log(e);
        }

        return answer;
    }
    public static Document getDocumentFromUrl(String url) throws IOException {
        if (offline)
            throw new NoConnectionException();
        try{
            return Jsoup.connect(url).get();
        }
        catch (UnknownHostException e){
            throw new NoConnectionException();
        }
    }
    private static String streamToString(InputStream str){
        var answer = new StringBuilder();
        try(var reader = new InputStreamReader(str)){
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1){
                answer.append(buffer, 0, read);
            }
        }
        catch (IOException e){
            Logger.getLogger().log(e);
        }

        return answer.toString();
    }
    public static String listenServer(int port){
        try{
            var socket = new ServerSocket(port);
            var a = socket.accept();

            var input = new BufferedReader(new InputStreamReader(a.getInputStream()));
            StringBuilder content = new StringBuilder();
            String read;
            do {
                content.append(read = input.readLine()).append("\n");
            }while (read != null && !read.isEmpty() && !read.isBlank());

            var output = new BufferedWriter(new OutputStreamWriter(a.getOutputStream()));
            output.write("HTTP/1.1 200 OK");
            output.close();

            a.close();
            return content.toString();
        }
        catch (Exception e){
            Logger.getLogger().log(e);
            return null;
        }
    }

    private static boolean offline;

    public static boolean isOffline(){
        return offline;
    }
    public static void setOffline(boolean offline) {
        NetUtils.offline = offline;
    }

    public static boolean check(){
        try{
            var url = new URL("https://google.com");
            var c = url.openConnection();
            c.getInputStream().read();
            c.getInputStream().close();


            return true;
        }
        catch (UnknownHostException h){
            return false;
        }
        catch (Exception e){
            return true;
        }
    }
}
