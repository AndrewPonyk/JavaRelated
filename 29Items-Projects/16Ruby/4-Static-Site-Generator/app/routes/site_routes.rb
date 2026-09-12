# frozen_string_literal: true

require_relative "../services/site_builder"
require_relative "../services/version_index"
require_relative "../repositories/page_repository"
require_relative "../errors"

module SiteRoutes
  def self.registered(app)
    app.get "/" do
      pages = PageRepository.new.all(sync_metadata: false)
      latest = discovered_versions(pages).first
      page = pages.select { |candidate| candidate.version == latest }.min_by(&:slug)
      redirect(page ? "/docs/#{page.version}/#{page.slug}" : "/search")
    end

    app.get "/health" do
      content_type :json
      { status: "ok" }.to_json
    end

    app.get "/search" do
      pages = PageRepository.new.all(sync_metadata: false)
      erb :search, layout: :"layouts/default", locals: {
        title: "Search",
        versions: discovered_versions(pages),
        version_links: version_links_for(pages)
      }
    end

    app.get "/docs/?" do
      pages = PageRepository.new.all(sync_metadata: false)
      erb :docs_index, layout: :"layouts/default", locals: {
        title: "Docs",
        pages: pages,
        versions: discovered_versions(pages),
        version_links: version_links_for(pages),
        selected_version: nil
      }
    end

    app.get "/docs/:version" do
      pages = PageRepository.new.all(sync_metadata: false)
      version_pages = pages.select { |candidate| candidate.version == params[:version] }
      raise NotFoundError, "Documentation version #{params[:version]} not found" if version_pages.empty?

      erb :docs_index, layout: :"layouts/default", locals: {
        title: "Docs #{params[:version]}",
        pages: version_pages,
        versions: discovered_versions(pages),
        version_links: version_links_for(pages),
        selected_version: params[:version]
      }
    end

    app.get "/docs/:version/:slug" do
      builder = SiteBuilder.new
      page = builder.find_page(version: params[:version], slug: params[:slug])
      pages = PageRepository.new.all(sync_metadata: false)

      erb :page, layout: :"layouts/default", locals: {
        title: page.title,
        page: page,
        versions: discovered_versions(pages),
        version_links: version_links_for(pages)
      }
    end

    app.helpers do
      def discovered_versions(pages)
        pages.map(&:version).uniq.sort_by { |version| version_key(version) }.reverse
      end

      def version_links_for(pages)
        pages.group_by(&:version).transform_values do |version_pages|
          preferred = version_pages.find { |candidate| candidate.slug == "getting-started" }
          (preferred || version_pages.min_by(&:slug)).slug
        end
      end

      def version_key(version)
        version.to_s.sub(/\Av/, "").split(".").map(&:to_i)
      end
    end
  end
end
