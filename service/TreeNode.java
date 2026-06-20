package service;

import java.util.List;
import java.util.ArrayList;

public class TreeNode<T> {
    private T data;
    private List<TreeNode<T>> children;

    public TreeNode(T data) {
        this.data = data;
        children = new ArrayList<>();
    }

    public void add(TreeNode<T> child) {
        children.add(child);
    }

    public T getData() { return data; }
    public List<TreeNode<T>> getChildren() { return children; }
}
