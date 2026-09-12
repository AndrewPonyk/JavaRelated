class IndexPostJob < ApplicationJob
  queue_as :search

  def perform(post_id)
    post = Post.includes(:user, :toxicity_result).find(post_id)
    SearchIndexer.new.index_post(post)
  end
end
