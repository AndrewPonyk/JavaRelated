class SocialNetworkSchema < GraphQL::Schema
  query Types::QueryType
  mutation Types::MutationType

  use GraphQL::Dataloader

  max_depth 12
  max_complexity 300

  def self.resolve_type(_abstract_type, _object, _context)
    raise GraphQL::RequiredImplementationMissingError
  end
end
