module org.pspro.chattcpmultisala {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.pspro.chattcpmultisala to javafx.fxml;
    exports org.pspro.chattcpmultisala;
    exports org.pspro.chattcpmultisala.cliente.controladores;
    opens org.pspro.chattcpmultisala.cliente.controladores to javafx.fxml;
}
