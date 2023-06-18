package com.cdev.corelauncher.ui.controls;

import com.cdev.corelauncher.CoreLauncherFX;
import com.cdev.corelauncher.data.Translator;
import com.cdev.corelauncher.ui.utils.FXManager;
import com.cdev.corelauncher.utils.JavaMan;
import com.cdev.corelauncher.utils.Logger;
import com.cdev.corelauncher.utils.entities.Java;
import com.cdev.corelauncher.utils.entities.Path;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;

public class CJava extends ListCell<Java> {

    private final Node gr;
    private Java java;

    public CJava(){
        var path = CoreLauncherFX.class.getResource("/com/cdev/corelauncher/entities/cjava.fxml");
        setGraphic(gr = FXManager.getManager().open(this, path));
    }

    @FXML
    private Label lblVersion;

    @FXML
    private TextField txtName;
    @FXML TextField txtPath;
    @FXML
    private CButton btnSelect;
    @FXML
    private CButton btnAction;

    @Override
    protected void updateItem(Java item, boolean empty) {
        super.updateItem(item, empty);

        if (empty){
            setGraphic(null);
            return;
        }

        if (java == item)
            return;

        load(item);
    }
    private void load(Java item){
        java = item;

        if (java != null && !java.isEmpty()){
            txtName.setText(java.getName());
            txtName.setEditable(false);
            txtPath.setText(java.getPath().toString());
            btnSelect.setText(">");
            btnSelect.setOnMouseClicked((a) -> {

            });
            btnAction.setOnMouseClicked((a) -> JavaMan.getManager().deleteJava(java));

            lblVersion.setText(java.majorVersion + "");

            btnAction.setText("-");
        }
        else{
            txtName.clear();
            txtName.setEditable(true);
            txtPath.setText(Translator.translate("selectPath"));
            btnSelect.setText("...");
            btnSelect.setOnMouseClicked((a) -> {
                var d = new DirectoryChooser().showDialog(btnSelect.getScene().getWindow());
                if (d == null)
                    return;
                var path = Path.begin(d.toPath());
                try{
                    if (java == null)
                        java = new Java(path);
                    else
                        java.setPath(path).retrieveInfo();
                    txtPath.setText(path.toString());
                }
                catch (Exception e){
                    Logger.getLogger().log(e);
                    if (java != null)
                        java.setPath(null);
                    txtPath.setText("Wrong Path");
                }
                if (java != null && !java.isLoaded())
                    txtPath.setText("Wrong Path");

            });
            btnAction.setOnMouseClicked((a) -> {
                java.setName(txtName.getText());
                if (java.isEmpty() || (java.getName() == null || java.getName().isEmpty()) || !java.isLoaded() || !JavaMan.getManager().addCustomJava(java))
                    return;

                load(java);
            });

            lblVersion.setText("0");

            btnAction.setText("+");
        }

        setGraphic(gr);
    }
}
