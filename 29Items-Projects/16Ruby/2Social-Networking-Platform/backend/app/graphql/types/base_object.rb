module Types
  class BaseObject < GraphQL::Schema::Object
    field_class GraphQL::Schema::Field
  end
end
