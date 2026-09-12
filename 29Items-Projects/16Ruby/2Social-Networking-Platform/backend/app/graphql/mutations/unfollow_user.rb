module Mutations
  class UnfollowUser < Mutations::BaseMutation
    argument :followee_id, ID, required: true

    field :deleted, Boolean, null: false

    def resolve(followee_id:)
      authenticate_user!
      current_user.following_relationships.find_by!(followee_id: followee_id).destroy!
      { deleted: true }
    end
  end
end
