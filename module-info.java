/**
 * Módulo principal do TaskManager Linux.
 *
 * O sistema de módulos do Java (JPMS, introduzido no Java 9) exige que
 * cada módulo declare explicitamente:
 *   - "requires": quais módulos externos ele usa
 *   - "opens":    quais pacotes ficam acessíveis ao JavaFX via reflexão
 *                 (necessário para a TableView acessar os campos dos modelos)
 *   - "exports":  quais pacotes são visíveis para outros módulos
 */
module taskmanager.linux {

    // ── Dependências do JavaFX ────────────────────────────────────────────────
    // javafx.controls: TableView, Button, Label, ProgressBar, Scene, Stage, etc.
    requires javafx.controls;
    // javafx.base: ObservableList, SimpleIntegerProperty, etc.
    requires javafx.base;
    // jdk.security.auth: necessário para com.sun.security.auth.module.UnixSystem
    // (usado no MainPanel para resolver EUID → nome de usuário)
    requires jdk.security.auth;

    // ── Abre os pacotes para o JavaFX ─────────────────────────────────────────
    // O JavaFX usa reflexão internamente para acessar as propriedades dos objetos
    // nas TableViews. Sem o "opens", ocorre InaccessibleObjectException em runtime.
    opens view      to javafx.controls, javafx.base;
    opens model     to javafx.controls, javafx.base;
    opens service   to javafx.controls, javafx.base;
    opens persistence to javafx.controls, javafx.base;
    opens exception to javafx.controls, javafx.base;

    // ── Exporta os pacotes do projeto ─────────────────────────────────────────
    exports view;
    exports model;
    exports service;
    exports persistence;
    exports exception;
}
