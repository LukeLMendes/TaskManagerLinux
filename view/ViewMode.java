package view;

/**
 * Enum que controla qual painel principal está visível na tela.
 *
 *   MAIN → MainPanel (lista plana) ou TreePanel (árvore) — alternados por F5
 *   IO   → IOPanel — estatísticas de leitura/escrita por processo
 *
 * A tecla Tab cicla entre MAIN e IO.
 * Dentro do modo MAIN, F5 alterna entre lista plana e árvore hierárquica
 * (comportamento idêntico ao htop: F5 Tree).
 */
public enum ViewMode {

    /**
     * Modo principal: exibe processos com colunas de CPU, memória, estado, etc.
     * Dependendo do flag modoArvore no MainFrame, mostra MainPanel (lista)
     * ou TreePanel (árvore hierárquica por PPID com kthreads em verde).
     */
    MAIN,

    /**
     * Modo I/O: exibe bytes lidos e escritos por processo.
     * Colunas: PID, READ, WRITE.
     */
    IO;
}
