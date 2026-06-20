package service;

import java.util.ArrayList;
import java.util.List;

import model.Processo;

public class ProcsTree {

    public static List<Processo> createList(List<Processo> processes, Processo proc) {
        List<Processo> newProcesses = new ArrayList<>();
        newProcesses.add(proc);

        for (int child : proc.getChildren()) {
            for (int i = 0; i < processes.size(); i++) {
                if (processes.get(i).getPid() == child) {
                    newProcesses.add(processes.get(i));
                }
            }
        }

        return newProcesses;
    }

    public static TreeNode<Processo> toTree(List<Processo> processes) {
        TreeNode<Processo> tree = new TreeNode<>(processes.get(0));

        for (int child : processes.get(0).getChildren()) {
            for (int j = 0; j < processes.size(); j++) {
                if (processes.get(j).getPid() == child) {
                    tree.add(toTree(createList(processes, processes.get(j))));
                }
            }
        }

        return tree;
    }
}
