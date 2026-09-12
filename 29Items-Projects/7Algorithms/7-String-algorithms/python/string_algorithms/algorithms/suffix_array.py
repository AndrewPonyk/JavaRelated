class SuffixArray:
    def __init__(self, text: str) -> None:
        self._text = text
        self._indexes = sorted(range(len(text)), key=lambda index: text[index:])

    @property
    def indexes(self) -> list[int]:
        return list(self._indexes)

    def search(self, pattern: str) -> list[int]:
        if pattern == "":
            raise ValueError("pattern must not be empty")
        return sorted(
            index for index in self._indexes if self._text.startswith(pattern, index)
        )
