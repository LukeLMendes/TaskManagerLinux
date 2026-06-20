module taskmanager.linux {

    requires javafx.controls;
    requires javafx.base;
    requires jdk.security.auth;

    opens view        to javafx.controls, javafx.base;
    opens model       to javafx.controls, javafx.base;
    opens service     to javafx.controls, javafx.base;
    opens persistence to javafx.controls, javafx.base;
    opens exception   to javafx.controls, javafx.base;

    exports view;
    exports model;
    exports service;
    exports persistence;
    exports exception;
}
