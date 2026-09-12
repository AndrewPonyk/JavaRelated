class ApplicationResult
  attr_reader :resource, :errors

  def initialize(success, resource, errors)
    @success = success
    @resource = resource
    @errors = errors
  end

  def success?
    @success
  end

  alias post resource
  alias message resource
end
