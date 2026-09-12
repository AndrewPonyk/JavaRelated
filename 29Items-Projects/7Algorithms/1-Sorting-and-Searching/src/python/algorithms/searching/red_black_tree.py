RED = "RED"
BLACK = "BLACK"


class Node:
    def __init__(self, value: int, color: str = RED) -> None:
        self.value = value
        self.color = color
        self.left: Node | None = None
        self.right: Node | None = None
        self.parent: Node | None = None


class RedBlackTree:
    def __init__(self) -> None:
        self.nil = Node(0, BLACK)
        self.root: Node = self.nil

    def insert(self, value: int) -> None:
        node = Node(value)
        node.left = self.nil
        node.right = self.nil

        parent = self.nil
        current = self.root
        while current != self.nil:
            parent = current
            current = current.left if node.value < current.value else current.right

        node.parent = parent
        if parent == self.nil:
            self.root = node
        elif node.value < parent.value:
            parent.left = node
        else:
            parent.right = node

        node.color = RED
        self._fix_insert(node)

    def contains(self, value: int) -> bool:
        current = self.root
        while current != self.nil:
            if value == current.value:
                return True
            current = current.left if value < current.value else current.right
        return False

    def inorder(self) -> list[int]:
        result: list[int] = []
        self._inorder(self.root, result)
        return result

    def _fix_insert(self, node: Node) -> None:
        while node.parent is not None and node.parent.color == RED:
            grandparent = node.parent.parent
            if grandparent is None:
                break
            if node.parent == grandparent.left:
                uncle = grandparent.right
                if uncle is not None and uncle.color == RED:
                    node.parent.color = BLACK
                    uncle.color = BLACK
                    grandparent.color = RED
                    node = grandparent
                else:
                    if node == node.parent.right:
                        node = node.parent
                        self._left_rotate(node)
                    node.parent.color = BLACK
                    grandparent.color = RED
                    self._right_rotate(grandparent)
            else:
                uncle = grandparent.left
                if uncle is not None and uncle.color == RED:
                    node.parent.color = BLACK
                    uncle.color = BLACK
                    grandparent.color = RED
                    node = grandparent
                else:
                    if node == node.parent.left:
                        node = node.parent
                        self._right_rotate(node)
                    node.parent.color = BLACK
                    grandparent.color = RED
                    self._left_rotate(grandparent)
        self.root.color = BLACK

    def _left_rotate(self, node: Node) -> None:
        right = node.right
        if right is None:
            return
        node.right = right.left
        if right.left != self.nil:
            right.left.parent = node
        right.parent = node.parent
        if node.parent == self.nil:
            self.root = right
        elif node == node.parent.left:
            node.parent.left = right
        else:
            node.parent.right = right
        right.left = node
        node.parent = right

    def _right_rotate(self, node: Node) -> None:
        left = node.left
        if left is None:
            return
        node.left = left.right
        if left.right != self.nil:
            left.right.parent = node
        left.parent = node.parent
        if node.parent == self.nil:
            self.root = left
        elif node == node.parent.right:
            node.parent.right = left
        else:
            node.parent.left = left
        left.right = node
        node.parent = left

    def _inorder(self, node: Node, result: list[int]) -> None:
        if node == self.nil:
            return
        self._inorder(node.left, result)
        result.append(node.value)
        self._inorder(node.right, result)


if __name__ == "__main__":
    sample = [42, 7, 19, 3, 99, 1]
    target = 19
    tree = RedBlackTree()
    for value in sample:
        tree.insert(value)

    print("Red-Black Tree")
    print(f"input  : {sample}")
    print(f"inorder: {tree.inorder()}")
    print(f"target : {target}")
    print(f"found  : {tree.contains(target)}")
