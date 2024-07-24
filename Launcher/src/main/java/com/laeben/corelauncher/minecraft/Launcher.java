package com.laeben.corelauncher.minecraft;

import com.laeben.core.entity.exception.HttpException;
import com.laeben.core.entity.exception.NoConnectionException;
import com.laeben.core.entity.exception.StopException;
import com.laeben.core.util.events.BaseEvent;
import com.laeben.core.util.events.ValueEvent;
import com.laeben.corelauncher.api.exception.PerformException;
import com.laeben.corelauncher.api.Configurator;
import com.laeben.corelauncher.api.entity.Profile;
import com.laeben.corelauncher.minecraft.entity.ExecutionInfo;
import com.laeben.corelauncher.minecraft.entity.VersionNotFoundException;
import com.laeben.corelauncher.minecraft.modding.Modder;
import com.laeben.corelauncher.minecraft.util.CommandConcat;
import com.laeben.corelauncher.util.EventHandler;
import com.laeben.corelauncher.util.JavaManager;
import com.laeben.corelauncher.api.entity.Logger;
import com.laeben.corelauncher.util.entity.LogType;
import com.laeben.core.entity.Path;
import com.laeben.core.util.events.KeyEvent;

import java.io.File;
import java.io.FileNotFoundException;

public class Launcher {
    public static Launcher instance;
    private Path gameDir;
    private final EventHandler<BaseEvent> handler;


    public Launcher(){
        this.gameDir = Configurator.getConfig().getGamePath();
        handler = new EventHandler<>();

        Configurator.getConfigurator().getHandler().addHandler("launcher", (a) -> {
            if (!a.getKey().equals("gamePathChange"))
                return;

            gameDir = (Path) a.getNewValue();
        }, false);

        instance = this;
    }

    public static Launcher getLauncher(){
        return instance;
    }

    public EventHandler<BaseEvent> getHandler(){
        return handler;
    }

    private void handleState(String key){
        handler.execute(new KeyEvent(key));
    }

    /**
     * Prepares the profile to launch.
     * @param profile target profile
     */
    public void prepare(Profile profile) throws NoConnectionException, StopException, PerformException, HttpException, FileNotFoundException {
        handleState("prepare" + profile.getName());

        profile.getWrapper().install(profile.getWrapper().getVersion(profile.getVersionId(), profile.getWrapperVersion()));

        if (!profile.getWrapper().getType().isNative()){
            Modder.getModder().installModpacks(profile, profile.getModpacks());
            Modder.getModder().installMods(profile, profile.getMods());
        }

        Modder.getModder().installWorlds(profile, profile.getOnlineWorlds());
        Modder.getModder().installResourcepacks(profile, profile.getResources());
        Modder.getModder().installShaders(profile, profile.getShaders());
    }

    /**
     * Launch the game.
     * @param info info for the execution
     */
    public void launch(ExecutionInfo info) throws StopException, VersionNotFoundException {
        if (info.version == null || info.version.id == null)
            return;

        if (info.dir == null)
            info.dir = gameDir;

        if (info.args == null)
            info.args = new String[0];

        try {
            var linfo = info.new LaunchInfo();

            if (info.account == null)
                info.account = Configurator.getConfig().getUser();

            // Checkup for Java
            if (info.java == null){
                // If profile Java is null, try to get default Java from config
                info.java = Configurator.getConfig().getDefaultJava();
                if (info.java == null || info.java.majorVersion != linfo.java.majorVersion){
                    // If this Java is unusable, try to get any Java that fits to version from all
                    info.java = JavaManager.getManager().tryGet(linfo.java);
                    if (info.java == null){
                        // If no result, use launcher's Java
                        info.java = JavaManager.getDefault();
                        if (info.java.majorVersion != linfo.java.majorVersion){
                            // Last solution, download new Java and relaunch (if there is internet here of course...)
                            info.java = null;

                            handleState("java" + linfo.java.majorVersion);
                            Logger.getLogger().log(LogType.INFO, "Downloading Java " + linfo.java.majorVersion);
                            try{
                                JavaManager.getManager().downloadAndInclude(linfo.java);
                                //handler.execute(new KeyEvent("jvdown"));
                            } catch (NoConnectionException e){
                                handleState(".error.launch.java");
                                return;
                            }
                            launch(info);
                            return;
                        }
                    }
                }

            }

            if (!info.java.getExecutable().exists()){
                JavaManager.getManager().reload();
                info.java = null;
                launch(info);
                return;
            }

            Logger.getLogger().log(LogType.INFO, "Java Path: " + info.java.getPath());

            String libPath = "-Djava.library.path=" + linfo.nativePath;
            String c = String.valueOf(File.pathSeparatorChar);
            String cp = linfo.clientPath + c + String.join(c, linfo.libraries);

            // Static commands
            String[] rootCmds = {
                    info.java.getExecutable().toString(),
                    libPath,
                    "-javaagent:" + linfo.agentPath, // Don't worry (yup), this contains launcher's patches to mc like 1.16.4 multiplayer bug
                    "-Dorg.lwjgl.util.Debug=true",
                    "-Dfml.ignoreInvalidMinecraftCertificates=true"
            };

            // If the version lower than 1.7.2 (very legacy) then copy all textures to resources folder in profile folder
            if (linfo.assets.isVeryLegacy()){
                var resources = info.dir.to("resources");
                gameDir.to("assets", "virtual", "verylegacy").copy(resources);
            }

            String[] gameCmds = linfo.getGameArguments();

            // Final commands
            var finalCmds = new CommandConcat()
                    .add(rootCmds)
                    .add(linfo.getJvmArguments())
                    .add(info.args)
                    .add("-cp", cp)
                    .add(linfo.mainClass)
                    .add(gameCmds)
                    .generate();

            // Due to some reasons, authentication process does not complete without requesting to the certificate URL, so we are requesting here.
            info.account.validate();

            // Start a new session
            var session = new Session(info.dir, finalCmds);

            handleState("sessionStart" + info.executor);
            session.start();

            handler.execute(new ValueEvent("sessionEnd" + info.executor, session.getExitCode()));
        }
        catch (StopException | VersionNotFoundException e){
            throw e;
        }
        catch (Exception e){
            Logger.getLogger().log(e);
        }
    }

}
