"""Segment tree construction and range-sum queries."""


class SegmentTree:
    """A static range-sum segment tree built recursively."""

    def __init__(self, values: list[int]) -> None:
        if not values:
            raise ValueError("values must not be empty")
        self._size = len(values)
        self._tree = [0] * (4 * self._size)
        self._build(values, 1, 0, self._size - 1)

    def range_sum(self, left: int, right: int) -> int:
        if left < 0 or right >= self._size or left > right:
            raise ValueError("invalid query range")
        return self._range_sum(1, 0, self._size - 1, left, right)

    def _build(self, values: list[int], node: int, left: int, right: int) -> None:
        if left == right:
            self._tree[node] = values[left]
            return
        mid = left + (right - left) // 2
        self._build(values, node * 2, left, mid)
        self._build(values, node * 2 + 1, mid + 1, right)
        self._tree[node] = self._tree[node * 2] + self._tree[node * 2 + 1]

    def _range_sum(self, node: int, left: int, right: int, query_left: int, query_right: int) -> int:
        if query_left <= left and right <= query_right:
            return self._tree[node]
        mid = left + (right - left) // 2
        total = 0
        if query_left <= mid:
            total += self._range_sum(node * 2, left, mid, query_left, query_right)
        if query_right > mid:
            total += self._range_sum(node * 2 + 1, mid + 1, right, query_left, query_right)
        return total

