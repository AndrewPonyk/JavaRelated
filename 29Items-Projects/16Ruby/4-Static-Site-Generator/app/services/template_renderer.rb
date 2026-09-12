# frozen_string_literal: true

require "erb"
require_relative "../helpers/view_helpers"

class TemplateRenderer
  class RenderContext
    include ViewHelpers

    def initialize(template_root:, component_root:, locals:)
      @template_root = template_root
      @component_root = component_root
      locals.each do |key, value|
        instance_variable_set(:"@#{key}", value)
        define_singleton_method(key) { instance_variable_get(:"@#{key}") }
      end
    end

    def partial(relative_path, locals = {})
      path = partial_path(relative_path)
      context = self.class.new(
        template_root: @template_root,
        component_root: @component_root,
        locals: exported_locals.merge(escape: method(:h)).merge(locals)
      )
      ERB.new(File.read(path)).result(context.get_binding)
    end

    def get_binding
      binding
    end

    private

    def exported_locals
      instance_variables.each_with_object({}) do |name, memo|
        next if [:@template_root, :@component_root].include?(name)

        key = name.to_s.delete_prefix("@").to_sym
        memo[key] = instance_variable_get(name)
      end
    end

    def partial_path(relative_path)
      if relative_path.start_with?("components/")
        File.join(@component_root, relative_path.delete_prefix("components/"))
      else
        File.join(@template_root, relative_path)
      end
    end
  end

  def initialize(template_root: "templates", component_root: File.join("app", "components"))
    @template_root = template_root
    @component_root = component_root
  end

  def render_page(page, versions: [], version_links: {})
    body = render_template("page.erb", page: page, versions: versions, title: page.title)
    render_template(
      "layouts/default.erb",
      content: body,
      page: page,
      versions: versions,
      version_links: version_links,
      title: page.title
    )
  end

  def render_search(versions: [], version_links: {})
    body = render_template("search.erb", versions: versions, title: "Search")
    render_template(
      "layouts/default.erb",
      content: body,
      page: nil,
      versions: versions,
      version_links: version_links,
      title: "Search"
    )
  end

  def render_docs_index(pages:, versions:, version_links:, selected_version: nil)
    title = selected_version ? "Docs #{selected_version}" : "Docs"
    body = render_template(
      "docs_index.erb",
      pages: pages,
      versions: versions,
      version_links: version_links,
      selected_version: selected_version,
      title: title
    )
    render_template(
      "layouts/default.erb",
      content: body,
      page: nil,
      versions: versions,
      version_links: version_links,
      title: title
    )
  end

  private

  def render_template(relative_path, locals)
    path = File.join(@template_root, relative_path)
    context = RenderContext.new(template_root: @template_root, component_root: @component_root, locals: locals)
    ERB.new(File.read(path)).result(context.get_binding)
  end
end
