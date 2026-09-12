from collections import Counter
from dataclasses import dataclass, field
from heapq import heapify, heappop, heappush
from itertools import count


@dataclass(order=True)
class _Node:
    frequency: int
    order: int
    symbol: str | None = field(default=None, compare=False)
    left: "_Node | None" = field(default=None, compare=False)
    right: "_Node | None" = field(default=None, compare=False)


def huffman_codes(text: str) -> dict[str, str]:
    if not text:
        raise ValueError("text must not be empty")

    sequence = count()
    heap = [_Node(freq, next(sequence), symbol) for symbol, freq in Counter(text).items()]
    heapify(heap)

    if len(heap) == 1:
        return {heap[0].symbol or "": "0"}

    while len(heap) > 1:
        left = heappop(heap)
        right = heappop(heap)
        parent = _Node(left.frequency + right.frequency, next(sequence), None, left, right)
        heappush(heap, parent)

    codes: dict[str, str] = {}

    def walk(node: _Node, prefix: str) -> None:
        if node.symbol is not None:
            codes[node.symbol] = prefix
            return
        if node.left is not None:
            walk(node.left, prefix + "0")
        if node.right is not None:
            walk(node.right, prefix + "1")

    walk(heap[0], "")
    return dict(sorted(codes.items()))
