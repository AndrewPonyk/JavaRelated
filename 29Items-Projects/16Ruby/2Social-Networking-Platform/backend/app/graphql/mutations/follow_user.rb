module Mutations
  class FollowUser < Mutations::BaseMutation
    argument :followee_id, ID, required: true

    field :follow, Types::FollowType, null: true
    field :errors, [String], null: false

    def resolve(followee_id:)
      authenticate_user!
      follow = current_user.following_relationships.create!(followee_id: followee_id)
      { follow: follow, errors: [] }
    rescue ActiveRecord::RecordInvalid => e
      { follow: nil, errors: e.record.errors.full_messages }
    end
  end
end
