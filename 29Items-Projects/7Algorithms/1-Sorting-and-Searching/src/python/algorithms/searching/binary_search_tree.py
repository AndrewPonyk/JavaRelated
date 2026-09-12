class Node:
    def __init__(self, value: int) -> None:
        self.value = value
        self.left: Node | None = None
        self.right: Node | None = None


class BinarySearchTree:
    def __init__(self) -> None:
        self.root: Node | None = None

    def insert(self, value: int) -> None:
        self.root = self._insert(self.root, value)

    def contains(self, value: int) -> bool:
        current = self.root
        while current is not None:
            if value == current.value:
                return True
            current = current.left if value < current.value else current.right
        return False

    def inorder(self) -> list[int]:
        result: list[int] = []
        self._inorder(self.root, result)
        return result

    def _insert(self, node: Node | None, value: int) -> Node:
        if node is None:
            return Node(value)
        if value < node.value:
            node.left = self._insert(node.left, value)
        elif value > node.value:
            node.right = self._insert(node.right, value)
        return node

    def _inorder(self, node: Node | None, result: list[int]) -> None:
        if node is None:
            return
        self._inorder(node.left, result)
        result.append(node.value)
        self._inorder(node.right, result)


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 99, 1]
    target = 19
    tree = BinarySearchTree()
    for value in sample:
        tree.insert(value)

    print("Binary Search Tree")
    print(f"input  : {sample}")
    print(f"inorder: {tree.inorder()}")
    print(f"target : {target}")
    print(f"found  : {tree.contains(target)}")
