# frozen_string_literal: true

require "erb"

module ViewHelpers
  def h(value)
    ERB::Util.html_escape(value)
  end

  def partial(relative_path, locals = {})
    path = if relative_path.start_with?("components/")
             File.expand_path(File.join("..", "components", relative_path.delete_prefix("components/")), __dir__)
           else
             File.join(settings.views, relative_path)
           end

    ERB.new(File.read(path)).result_with_hash(locals.merge(escape: method(:h)))
  end

  def active_version?(current_version, target_version)
    current_version.to_s == target_version.to_s
  end

  def asset_path(path)
    "/assets/#{path.sub(%r{\A/assets/}, "")}"
  end
end
