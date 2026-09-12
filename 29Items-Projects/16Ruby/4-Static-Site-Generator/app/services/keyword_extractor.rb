# frozen_string_literal: true

class KeywordExtractor
  STOP_WORDS = %w[
    a an and are as at be by for from has in into is it its of on or that the this to with you your
  ].freeze

  def extract(text, limit: 12)
    text.downcase
        .scan(/[a-z][a-z0-9-]{2,}/)
        .reject { |word| STOP_WORDS.include?(word) }
        .reject { |word| word.match?(/\A[0-9]+\z/) }
        .tally
        .sort_by { |word, count| [-count, word] }
        .first(limit)
        .map(&:first)
  end
end
