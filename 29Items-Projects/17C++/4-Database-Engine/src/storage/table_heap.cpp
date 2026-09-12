#include "minidb/storage/table_heap.hpp"

#include "minidb/storage/table_page.hpp"

namespace minidb {

StatusOr<page_id_t> TableHeap::CreateFirstPage(BufferPoolManager* bpm) {
  page_id_t pid = INVALID_PAGE_ID;
  Page* page = bpm->NewPage(&pid);
  if (page == nullptr) {
    return Status::BufferPoolFull("could not allocate heap first page");
  }
  TablePage(page->data()).Init();
  bpm->UnpinPage(pid, /*is_dirty=*/true);
  return pid;
}

StatusOr<RID> TableHeap::InsertTuple(const std::string& tuple) {
  const auto len = static_cast<std::uint16_t>(tuple.size());
  if (tuple.size() != len) {
    return Status::InvalidArgument("tuple exceeds 64 KiB");
  }

  page_id_t cur = first_page_;
  while (true) {
    Page* page = bpm_->FetchPage(cur);
    if (page == nullptr) return Status::IOError("heap page fetch failed");
    TablePage tp(page->data());

    slot_id_t slot = 0;
    if (tp.InsertTuple(tuple.data(), len, &slot)) {
      bpm_->UnpinPage(cur, /*is_dirty=*/true);
      return RID{cur, slot};
    }

    const page_id_t next = tp.next_page_id();
    if (next != INVALID_PAGE_ID) {
      bpm_->UnpinPage(cur, /*is_dirty=*/false);
      cur = next;
      continue;
    }

    // End of chain and no room: append a fresh page and link it in.
    page_id_t new_id = INVALID_PAGE_ID;
    Page* fresh = bpm_->NewPage(&new_id);
    if (fresh == nullptr) {
      bpm_->UnpinPage(cur, /*is_dirty=*/false);
      return Status::BufferPoolFull("could not grow heap");
    }
    TablePage ntp(fresh->data());
    ntp.Init();
    const bool placed = ntp.InsertTuple(tuple.data(), len, &slot);
    bpm_->UnpinPage(new_id, /*is_dirty=*/true);

    tp.set_next_page_id(new_id);
    bpm_->UnpinPage(cur, /*is_dirty=*/true);

    if (!placed) return Status::InvalidArgument("tuple too large for a page");
    return RID{new_id, slot};
  }
}

bool TableHeap::GetTuple(RID rid, std::string* out) const {
  Page* page = bpm_->FetchPage(rid.page_id);
  if (page == nullptr) return false;
  TablePage tp(page->data());
  const char* data = nullptr;
  std::uint16_t len = 0;
  const bool found = tp.GetTuple(rid.slot, &data, &len);
  if (found) out->assign(data, len);
  bpm_->UnpinPage(rid.page_id, /*is_dirty=*/false);
  return found;
}

std::vector<std::pair<RID, std::string>> TableHeap::Scan() const {
  std::vector<std::pair<RID, std::string>> out;
  page_id_t cur = first_page_;
  while (cur != INVALID_PAGE_ID) {
    Page* page = bpm_->FetchPage(cur);
    if (page == nullptr) break;
    TablePage tp(page->data());
    const std::uint16_t slots = tp.slot_count();
    for (std::uint16_t s = 0; s < slots; ++s) {
      const char* data = nullptr;
      std::uint16_t len = 0;
      if (tp.GetTuple(static_cast<slot_id_t>(s), &data, &len)) {
        out.emplace_back(RID{cur, static_cast<slot_id_t>(s)},
                         std::string(data, len));
      }
    }
    const page_id_t next = tp.next_page_id();
    bpm_->UnpinPage(cur, /*is_dirty=*/false);
    cur = next;
  }
  return out;
}

std::size_t TableHeap::CountTuples() const {
  std::size_t count = 0;
  page_id_t cur = first_page_;
  while (cur != INVALID_PAGE_ID) {
    Page* page = bpm_->FetchPage(cur);
    if (page == nullptr) break;
    TablePage tp(page->data());
    count += tp.slot_count();
    const page_id_t next = tp.next_page_id();
    bpm_->UnpinPage(cur, /*is_dirty=*/false);
    cur = next;
  }
  return count;
}

Status TableHeap::Clear() {
  page_id_t cur = first_page_;
  while (cur != INVALID_PAGE_ID) {
    Page* page = bpm_->FetchPage(cur);
    if (page == nullptr) return Status::IOError("heap page fetch failed");
    TablePage tp(page->data());
    const page_id_t next = tp.next_page_id();
    tp.Clear();
    bpm_->UnpinPage(cur, /*is_dirty=*/true);
    cur = next;
  }
  return Status::Ok();
}

}  // namespace minidb
