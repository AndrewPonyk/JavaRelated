module Mutations
  class UpdatePost < Mutations::BaseMutation
    argument :id, ID, required: true
    argument :body, String, required: false
    argument :visibility, String, required: false

    field :post, Types::PostType, null: true
    field :errors, [String], null: false

    def resolve(id:, **attributes)
      authenticate_user!
      post = current_user.posts.find(id)
      post.update!(attributes.compact)
      IndexPostJob.perform_later(post.id)
      { post: post, errors: [] }
    rescue ActiveRecord::RecordInvalid => e
      { post: nil, errors: e.record.errors.full_messages }
    end
  end
end
