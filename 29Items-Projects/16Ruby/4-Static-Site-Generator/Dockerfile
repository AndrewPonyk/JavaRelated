FROM ruby:3.3-slim

WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends build-essential git libsqlite3-dev pkg-config \
  && rm -rf /var/lib/apt/lists/*

COPY Gemfile ./
RUN bundle install

COPY . .

EXPOSE 4567

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 CMD ruby -rnet/http -e "\
response = Net::HTTP.get_response(URI('http://127.0.0.1:4567/health')); \
exit(response.is_a?(Net::HTTPSuccess) ? 0 : 1)"

CMD ["bundle", "exec", "rackup", "--host", "0.0.0.0", "--port", "4567"]
