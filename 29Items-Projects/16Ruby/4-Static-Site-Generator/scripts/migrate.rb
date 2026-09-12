#!/usr/bin/env ruby
# frozen_string_literal: true

require_relative "../app/repositories/page_repository"

PageRepository.new.migrate!
puts "Database migrated."
