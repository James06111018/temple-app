module tw.org.il.dongsheng.templeapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires javafx.swing;
    requires java.base;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;

    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
//    requires org.kordamp.bootstrapfx.core;

    opens tw.org.il.dongsheng.templeapp.util to javafx.fxml;
    opens tw.org.il.dongsheng.templeapp to javafx.fxml;

    exports tw.org.il.dongsheng.templeapp;
    exports tw.org.il.dongsheng.templeapp.model;
    opens tw.org.il.dongsheng.templeapp.model to javafx.fxml;
}