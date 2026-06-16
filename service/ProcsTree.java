package service;

import java.util.ArrayList;
import java.util.List;

import model.Processo;

/**
 * Utilitário da camada de serviço para montar a árvore de processos.
 *
 * Esta classe pertence ao pacote service (não persistence) porque
 * constrói a estrutura lógica TreeNode<Processo> a partir de uma lista
 * plana de processos — é uma operação de negócio, não de leitura de disco.
 *
 * Obs.: o nome foi padronizado para ProcsTree (PascalCase) seguindo
 * a convenção Java para nomes de classe.
 */
public class ProcsTree {

    /**
     * Coleta um processo raiz e todos os seus filhos diretos em uma lista plana.
     *
     * Percorre a lista de filhos de {@code proc} (armazenada como PIDs inteiros
     * via Processo.getChildren()) e localiza os objetos correspondentes em
     * {@code processes}.
     *
     * @param processes lista completa de processos onde os filhos serão procurados
     * @param proc      processo raiz cujos filhos devem ser incluídos
     * @return nova lista contendo proc seguido de seus filhos diretos
     */
    public static List<Processo> createList(List<Processo> processes, Processo proc) {

        // Começa com a raiz; os filhos serão adicionados a seguir.
        List<Processo> newProcesses = new ArrayList<>();
        newProcesses.add(proc);

        // Para cada PID filho registrado em proc, busca o objeto correspondente.
        for (int child : proc.getChildren()) {
            for (int i = 0; i < processes.size(); i++) {
                if (processes.get(i).getPid() == child) {
                    newProcesses.add(processes.get(i));
                }
            }
        }

        return newProcesses;
    }

    /**
     * Converte uma lista plana de processos em uma TreeNode<Processo> hierárquica.
     *
     * O primeiro elemento da lista ({@code processes.get(0)}) é tratado como raiz.
     * O algoritmo percorre os filhos da raiz recursivamente, construindo a árvore
     * de forma que cada nó contenha seus filhos como subnós.
     *
     * @param processes lista de processos onde o índice 0 é a raiz da subárvore
     * @return nó raiz com toda a hierarquia abaixo dele montada
     */
    public static TreeNode<Processo> toTree(List<Processo> processes) {

        // Cria o nó raiz com o primeiro processo da lista.
        TreeNode<Processo> tree = new TreeNode<>(processes.get(0));

        // Para cada PID filho da raiz, localiza o processo e chama toTree recursivamente.
        for (int child : processes.get(0).getChildren()) {
            for (int j = 0; j < processes.size(); j++) {
                if (processes.get(j).getPid() == child) {
                    // createList reúne o filho e os descendentes dele numa sublista,
                    // que é passada recursivamente para montar a subárvore.
                    tree.add(toTree(createList(processes, processes.get(j))));
                }
            }
        }

        return tree;
    }
}
