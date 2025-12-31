module com.izo.netpulse {
    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // Spring Framework
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires spring.data.jpa;
    requires spring.tx;

    // Java & Jakarta APIs
    requires java.prefs;
    requires java.sql;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires jakarta.cdi;
    requires org.hibernate.orm.core;

    // Third Party Libraries
    requires okhttp3;
    requires jspeedtest;
    requires org.slf4j;
    requires static lombok;
    requires annotations;

    opens fxml to javafx.fxml;
    opens style to javafx.graphics;

    opens com.izo.netpulse to spring.core, spring.beans, spring.context, javafx.fxml;
    opens com.izo.netpulse.ui to javafx.fxml, spring.beans;
    opens com.izo.netpulse.ui.manager to spring.beans;
    opens com.izo.netpulse.service to spring.core, spring.beans, spring.context;
    opens com.izo.netpulse.model to org.hibernate.orm.core, spring.core;

    exports com.izo.netpulse;
}