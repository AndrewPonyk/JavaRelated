from collections.abc import Iterable


def print_list(title: str, values: Iterable[object]) -> None:
    items = list(values)
    print(f"{title} ({len(items)})")
    for index, value in enumerate(items, start=1):
        print(f"  {index}. {value}")
