module Mutations
  class BaseMutation < GraphQL::Schema::Mutation
    null false

    private

    def current_user
      context[:current_user]
    end

    def authenticate_user!
      current_user || raise(GraphQL::ExecutionError, "Authentication required")
    end
  end
end
