#include "minidb/index/bplus_tree.hpp"

#include "minidb/storage/page.hpp"

namespace minidb {

BPlusTree::BPlusTree(BufferPoolManager* bpm, page_id_t root,
                     std::uint16_t leaf_max, std::uint16_t internal_max)
    : bpm_(bpm),
      root_page_id_(root),
      leaf_max_(leaf_max < 2 ? 2 : leaf_max),
      internal_max_(internal_max < 3 ? 3 : internal_max) {}

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

page_id_t BPlusTree::FindLeaf(KeyType key) const {
  page_id_t cur = root_page_id_;
  while (cur != INVALID_PAGE_ID) {
    Page* pg = bpm_->FetchPage(cur);
    if (pg == nullptr) return INVALID_PAGE_ID;
    BPlusTreePage node(pg->data());
    if (node.is_leaf()) {
      bpm_->UnpinPage(cur, false);
      return cur;
    }
    InternalPage internal(pg->data());
    const page_id_t child = internal.ChildAt(internal.ChildIndexFor(key));
    bpm_->UnpinPage(cur, false);
    cur = child;
  }
  return INVALID_PAGE_ID;
}

page_id_t BPlusTree::LeftmostLeaf() const {
  page_id_t cur = root_page_id_;
  while (cur != INVALID_PAGE_ID) {
    Page* pg = bpm_->FetchPage(cur);
    if (pg == nullptr) return INVALID_PAGE_ID;
    BPlusTreePage node(pg->data());
    if (node.is_leaf()) {
      bpm_->UnpinPage(cur, false);
      return cur;
    }
    InternalPage internal(pg->data());
    const page_id_t child = internal.ChildAt(0);
    bpm_->UnpinPage(cur, false);
    cur = child;
  }
  return INVALID_PAGE_ID;
}

// ---------------------------------------------------------------------------
// Reads
// ---------------------------------------------------------------------------

std::optional<RID> BPlusTree::GetValue(KeyType key) const {
  if (root_page_id_ == INVALID_PAGE_ID) return std::nullopt;
  const page_id_t leaf_id = FindLeaf(key);
  if (leaf_id == INVALID_PAGE_ID) return std::nullopt;

  Page* pg = bpm_->FetchPage(leaf_id);
  if (pg == nullptr) return std::nullopt;
  LeafPage leaf(pg->data());
  std::optional<RID> result;
  const std::uint16_t pos = leaf.LowerBound(key);
  if (pos < leaf.size() && leaf.KeyAt(pos) == key) result = leaf.RidAt(pos);
  bpm_->UnpinPage(leaf_id, false);
  return result;
}

std::vector<RID> BPlusTree::RangeScan(KeyType low, KeyType high) const {
  std::vector<RID> out;
  if (root_page_id_ == INVALID_PAGE_ID || low > high) return out;

  page_id_t leaf_id = FindLeaf(low);
  while (leaf_id != INVALID_PAGE_ID) {
    Page* pg = bpm_->FetchPage(leaf_id);
    if (pg == nullptr) break;
    LeafPage leaf(pg->data());
    bool done = false;
    for (std::uint16_t i = leaf.LowerBound(low); i < leaf.size(); ++i) {
      if (leaf.KeyAt(i) > high) {
        done = true;
        break;
      }
      out.push_back(leaf.RidAt(i));
    }
    const page_id_t next = leaf.next_page_id();
    bpm_->UnpinPage(leaf_id, false);
    if (done) break;
    leaf_id = next;
  }
  return out;
}

std::size_t BPlusTree::size() const {
  if (root_page_id_ == INVALID_PAGE_ID) return 0;
  page_id_t leaf_id = LeftmostLeaf();
  std::size_t total = 0;
  while (leaf_id != INVALID_PAGE_ID) {
    Page* pg = bpm_->FetchPage(leaf_id);
    if (pg == nullptr) break;
    LeafPage leaf(pg->data());
    total += leaf.size();
    const page_id_t next = leaf.next_page_id();
    bpm_->UnpinPage(leaf_id, false);
    leaf_id = next;
  }
  return total;
}

// ---------------------------------------------------------------------------
// Insert (with split propagation)
// ---------------------------------------------------------------------------

Status BPlusTree::Insert(KeyType key, const RID& rid) {
  if (root_page_id_ == INVALID_PAGE_ID) {
    page_id_t leaf_id = INVALID_PAGE_ID;
    Page* pg = bpm_->NewPage(&leaf_id);
    if (pg == nullptr) return Status::BufferPoolFull("b+tree: no root frame");
    LeafPage leaf(pg->data());
    leaf.Init(leaf_max_);
    leaf.Insert(key, rid);
    bpm_->UnpinPage(leaf_id, true);
    root_page_id_ = leaf_id;
    return Status::Ok();
  }

  bool duplicate = false;
  const Split split = InsertInto(root_page_id_, key, rid, &duplicate);
  if (duplicate) return Status::AlreadyExists("duplicate key in unique index");

  if (split.happened) {
    page_id_t new_root = INVALID_PAGE_ID;
    Page* pg = bpm_->NewPage(&new_root);
    if (pg == nullptr) return Status::BufferPoolFull("b+tree: no root frame");
    InternalPage root(pg->data());
    root.Init(internal_max_);
    root.PopulateNewRoot(root_page_id_, split.key, split.new_page);
    bpm_->UnpinPage(new_root, true);
    root_page_id_ = new_root;
  }
  return Status::Ok();
}

BPlusTree::Split BPlusTree::InsertInto(page_id_t page_id, KeyType key,
                                       const RID& rid, bool* duplicate) {
  Page* pg = bpm_->FetchPage(page_id);
  if (pg == nullptr) return Split{};
  BPlusTreePage node(pg->data());

  // --- Leaf ---------------------------------------------------------------
  if (node.is_leaf()) {
    LeafPage leaf(pg->data());
    if (!leaf.Insert(key, rid)) {
      *duplicate = true;
      bpm_->UnpinPage(page_id, false);
      return Split{};
    }
    if (leaf.size() <= leaf.max_size()) {
      bpm_->UnpinPage(page_id, true);
      return Split{};
    }
    // Overflow: split, promoting a copy of the right half's first key.
    page_id_t right_id = INVALID_PAGE_ID;
    Page* rpg = bpm_->NewPage(&right_id);
    if (rpg == nullptr) {  // pool exhausted: keep node (rare; pool is sized up)
      bpm_->UnpinPage(page_id, true);
      return Split{};
    }
    LeafPage right(rpg->data());
    right.Init(leaf_max_);
    const std::uint16_t total = leaf.size();
    const auto left_count = static_cast<std::uint16_t>((total + 1) / 2);
    const auto right_count = static_cast<std::uint16_t>(total - left_count);
    for (std::uint16_t j = 0; j < right_count; ++j) {
      right.SetEntry(j, leaf.KeyAt(static_cast<std::uint16_t>(left_count + j)),
                     leaf.RidAt(static_cast<std::uint16_t>(left_count + j)));
    }
    right.set_size(right_count);
    leaf.set_size(left_count);
    right.set_next_page_id(leaf.next_page_id());
    leaf.set_next_page_id(right_id);
    const KeyType up = right.KeyAt(0);
    bpm_->UnpinPage(right_id, true);
    bpm_->UnpinPage(page_id, true);
    return Split{true, up, right_id};
  }

  // --- Internal -----------------------------------------------------------
  InternalPage internal(pg->data());
  const std::uint16_t child_idx = internal.ChildIndexFor(key);
  const page_id_t child = internal.ChildAt(child_idx);
  const Split child_split = InsertInto(child, key, rid, duplicate);
  if (!child_split.happened) {
    bpm_->UnpinPage(page_id, false);  // this node was not modified
    return Split{};
  }

  internal.InsertAfter(child_idx, child_split.key, child_split.new_page);
  if (internal.size() <= internal.max_size()) {
    bpm_->UnpinPage(page_id, true);
    return Split{};
  }
  // Overflow: split, promoting the middle separator key.
  page_id_t right_id = INVALID_PAGE_ID;
  Page* rpg = bpm_->NewPage(&right_id);
  if (rpg == nullptr) {
    bpm_->UnpinPage(page_id, true);
    return Split{};
  }
  InternalPage right(rpg->data());
  right.Init(internal_max_);
  const std::uint16_t children = internal.size();
  const auto left_children = static_cast<std::uint16_t>((children + 1) / 2);
  const KeyType up = internal.KeyAt(left_children);
  const auto right_children =
      static_cast<std::uint16_t>(children - left_children);
  for (std::uint16_t j = 0; j < right_children; ++j) {
    right.SetChildAt(
        j, internal.ChildAt(static_cast<std::uint16_t>(left_children + j)));
    if (j >= 1) {
      right.SetKeyAt(
          j, internal.KeyAt(static_cast<std::uint16_t>(left_children + j)));
    }
  }
  right.set_size(right_children);
  internal.set_size(left_children);
  bpm_->UnpinPage(right_id, true);
  bpm_->UnpinPage(page_id, true);
  return Split{true, up, right_id};
}

// ---------------------------------------------------------------------------
// Erase (point delete; space reclaimed lazily — no merge in v1)
// ---------------------------------------------------------------------------

Status BPlusTree::Erase(KeyType key) {
  if (root_page_id_ == INVALID_PAGE_ID) return Status::NotFound("empty index");
  const page_id_t leaf_id = FindLeaf(key);
  if (leaf_id == INVALID_PAGE_ID) return Status::NotFound("key not found");

  Page* pg = bpm_->FetchPage(leaf_id);
  if (pg == nullptr) return Status::IOError("b+tree leaf fetch failed");
  LeafPage leaf(pg->data());
  const bool removed = leaf.Remove(key);
  bpm_->UnpinPage(leaf_id, removed);
  if (!removed) return Status::NotFound("key not present in index");
  return Status::Ok();
}

}  // namespace minidb
