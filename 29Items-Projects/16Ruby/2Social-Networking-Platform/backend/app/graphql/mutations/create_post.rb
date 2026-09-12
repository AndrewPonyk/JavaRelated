module Mutations
  class CreatePost < Mutations::BaseMutation
    argument :body, String, required: true
    argument :visibility, String, required: false, default_value: "public"

    field :post, Types::PostType, null: true
    field :errors, [String], null: false

    def resolve(body:, visibility:)
      authenticate_user!
      result = Posts::CreatePost.call(
        user: context[:current_user],
        attributes: { body: body, visibility: visibility }
      )

      {
        post: result.post,
        errors: result.errors
      }
    end
  end
end
