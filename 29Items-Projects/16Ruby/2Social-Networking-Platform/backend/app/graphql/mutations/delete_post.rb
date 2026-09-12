module Mutations
  class DeletePost < Mutations::BaseMutation
    argument :id, ID, required: true

    field :deleted_id, ID, null: true
    field :errors, [String], null: false

    def resolve(id:)
      authenticate_user!
      post = current_user.posts.find(id)
      post.destroy!
      SearchIndexer.new.delete_post(id)
      { deleted_id: id, errors: [] }
    end
  end
end
